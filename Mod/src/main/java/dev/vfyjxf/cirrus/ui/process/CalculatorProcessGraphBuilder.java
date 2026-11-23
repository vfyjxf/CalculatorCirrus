package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.content.stock.Stock;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the calculator's {@link ProcessGraph} from resolved recipe projections.
 *
 * <p>Plan-to-graph translation was carried by the removed routing plan model; until the planning
 * bridge exposes a production plan the builder renders the target recipe as a single projected
 * node with material sources for its inputs.
 */
public final class CalculatorProcessGraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");
    private static final String materialSourceMachine = "Material";
    private static final Namespace timeValueTypeId = Namespace.of("calculator_cirrus", "time");

    private CalculatorProcessGraphBuilder() {
    }

    public enum RouteMode {
        calculator,
        execution
    }

    public record BuildResult(
        ProcessGraph graph,
        RouteMode routeMode,
        CalculatorBuildDebugInfo debugInfo,
        List<String> debugTrace
    ) {
        public BuildResult {
            graph = graph == null ? new ProcessGraph() : graph;
            routeMode = routeMode == null ? RouteMode.calculator : routeMode;
            debugInfo = debugInfo == null ? CalculatorBuildDebugInfo.empty() : debugInfo;
            debugTrace = debugTrace == null ? List.of() : List.copyOf(debugTrace);
        }
    }

    public record CalculatorBuildDebugInfo(
        String target,
        int times,
        RouteMode routeMode,
        boolean fallbackUsed,
        int graphNodes,
        int graphEdges,
        @Nullable String exception
    ) {
        public static CalculatorBuildDebugInfo empty() {
            return new CalculatorBuildDebugInfo("none", 0, RouteMode.calculator, false, 0, 0, null);
        }

        public String toDebugText() {
            return "target=" + target
                + " times=" + times
                + " mode=" + routeMode
                + " fallback=" + fallbackUsed
                + " graph=" + graphNodes + "/" + graphEdges
                + (exception == null ? "" : " exception=" + exception);
        }

        public CalculatorBuildDebugInfo withGraph(ProcessGraph graph, boolean fallbackUsed) {
            return new CalculatorBuildDebugInfo(
                target,
                times,
                routeMode,
                fallbackUsed,
                graph.nodeCount(),
                graph.edgeCount(),
                exception
            );
        }
    }

    private static ExecutionContext calculatorProjectionContext(RecipeEntry<?> recipe, int times) {
        return ProjectionContexts.calculator(
            ProjectionContexts.global(),
            new CalculatorProjectionContext(recipe, times, 0L)
        );
    }

    public static ProcessGraph build(RecipeEntry<?> target, int times) {
        return build(target, times, Stock.empty());
    }

    public static ProcessGraph build(RecipeEntry<?> target, int times, Stock stock) {
        return build(target, times, stock, Map.of());
    }

    public static ProcessGraph build(RecipeEntry<?> target, int times, Stock stock, Map<String, String> preferredVariants) {
        return build(target, times, stock, preferredVariants, RouteMode.calculator);
    }

    public static ProcessGraph build(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode
    ) {
        return buildResult(target, times, stock, preferredVariants, routeMode).graph();
    }

    public static BuildResult buildResult(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode
    ) {
        return buildResult(target, times, stock, preferredVariants, routeMode, false);
    }

    public static BuildResult buildResult(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode,
        boolean forceDeepen
    ) {
        // TODO(planning-ui): 由新规划器 ProductionPlan 生成完整过程图（旧路由已删除，待 bridge 接线）
        int safeTimes = Math.max(1, times);
        FallbackDebugTrace trace = new FallbackDebugTrace();
        ProcessGraph graph = fromSingleProjectedRecipe(target, safeTimes, trace);
        CalculatorBuildDebugInfo debugInfo = new CalculatorBuildDebugInfo(
            describeRecipe(target),
            safeTimes,
            routeMode == null ? RouteMode.calculator : routeMode,
            true,
            0,
            0,
            null
        ).withGraph(graph, true);
        ArrayList<String> lines = new ArrayList<>();
        lines.add("summary planGraph=projectedSingle");
        lines.addAll(trace.lines());
        return new BuildResult(graph, debugInfo.routeMode(), debugInfo, lines);
    }

    public static @Nullable String debugTargetGraphIssueForTests(RecipeEntry<?> target, ProcessGraph graph) {
        return targetGraphIssue(target, graph);
    }

    private static ProcessGraph fromSingleProjectedRecipe(RecipeEntry<?> target, int times) {
        return fromSingleProjectedRecipe(target, times, new FallbackDebugTrace());
    }

    private static ProcessGraph fromSingleProjectedRecipe(RecipeEntry<?> target, int times, FallbackDebugTrace trace) {
        ResolveResult result;
        try {
            result = target.resolveResult();
        } catch (RuntimeException | LinkageError exception) {
            trace.add(
                "fallback.single.resolveException target=" + describeRecipe(target)
                    + " times=" + times
                    + " cause=" + exception.getClass().getSimpleName() + ":" + nullToEmpty(exception.getMessage())
            );
            logger.debug(
                "calculator.fallback.single.resolveException target={} times={} cause={}",
                describeRecipe(target),
                times,
                exception.toString(),
                exception
            );
            return new ProcessGraph();
        }
        RecipeProjection projection = result.projection();
        if (projection == null) {
            trace.add(
                "fallback.single.unresolved target=" + describeRecipe(target)
                    + " times=" + times
                    + " error=" + result.error()
            );
            logger.debug(
                "calculator.fallback.single.unresolved target={} times={} error={}",
                describeRecipe(target),
                times,
                result.error()
            );
            return new ProcessGraph();
        }
        ProjectedRecipe projected;
        try {
            projected = new ProjectionEngine().project(calculatorProjectionContext(target, times), projection);
        } catch (RuntimeException | LinkageError exception) {
            trace.add(
                "fallback.single.projectException target=" + describeRecipe(target)
                    + " times=" + times
                    + " cause=" + exception.getClass().getSimpleName() + ":" + nullToEmpty(exception.getMessage())
            );
            logger.debug(
                "calculator.fallback.single.projectException target={} times={} cause={}",
                describeRecipe(target),
                times,
                exception.toString(),
                exception
            );
            return new ProcessGraph();
        }
        List<MaterialSlot> inputs = new ArrayList<>(projected.inputs().size());
        for (InputContent input : projected.inputs()) {
            if (!input.isEmpty()) {
                inputs.add(inputSlot(input, Math.multiplyExact(input.amount(), times)));
            }
        }
        List<MaterialSlot> outputs = new ArrayList<>(projected.outputs().size());
        for (OutputContent output : projected.outputs()) {
            if (!output.isEmpty()) {
                outputs.add(outputSlot(output, Math.multiplyExact(output.amount(), times)));
            }
        }
        ProcessGraph graph = new ProcessGraph(1 + inputs.size(), inputs.size());
        BuildContext buildContext = new BuildContext();
        MachineDisplay machine = buildContext.machineDisplay(target);
        String rootNodeId = recipeNodeId(target, 0);
        graph.addNode(new ProcessNode(
            rootNodeId,
            recipeName(target, outputs),
            machine.name(),
            inputs,
            outputs,
            durationTicks(outputs),
            machine.options(),
            target
        ));
        graph.preferredRootNodeId(rootNodeId);
        connectSourceInputs(graph, rootNodeId, 0, inputs);
        trace.add("fallback.single.finish nodes=" + graph.nodeCount() + " edges=" + graph.edgeCount());
        return graph;
    }

    private static void connectSourceInputs(
        ProcessGraph graph,
        String targetNodeId,
        int actionId,
        List<MaterialSlot> inputs
    ) {
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            MaterialSlot sourceSlot = inputs.get(inputIndex);
            String sourceNodeId = materialSourceNodeId(targetNodeId, actionId, inputIndex, sourceSlot);
            graph.addNode(materialSourceNode(sourceNodeId, sourceSlot));
            graph.addEdge(new ProcessEdge(
                sourceNodeId,
                targetNodeId,
                sourceSlot.materialName(),
                sourceSlot.amount(),
                sourceSlot.type()
            ));
        }
    }

    private static ProcessNode materialSourceNode(String nodeId, MaterialSlot sourceSlot) {
        return materialSourceNode(nodeId, sourceSlot, materialSourceMachine);
    }

    private static ProcessNode materialSourceNode(String nodeId, MaterialSlot sourceSlot, String machine) {
        return new ProcessNode(
            nodeId,
            sourceSlot.materialName(),
            machine,
            List.of(),
            List.of(sourceSlot),
            0,
            List.of(),
            null
        );
    }

    private static MaterialSlot inputSlot(InputContent input, long amount) {
        InputContent scaled = input.amount() == amount ? input : input.copyWithAmount(amount);
        return materialSlot(scaled, amount, input.notConsumed());
    }

    private static MaterialSlot outputSlot(OutputContent output, long amount) {
        return materialSlot(output.amount() == amount ? output : output.copyWithAmount(amount), amount, false);
    }

    private static MaterialSlot materialSlot(RecipeContent content, long amount, boolean catalyst) {
        ContentStack<?, ?> stack = stackContent(content);
        MaterialCategory category = catalyst
            ? MaterialCategories.CATALYST
            : stack.notEmpty() && stack.isFluid()
                ? MaterialCategories.FLUID
                : content instanceof ValueContent<?>
                    ? MaterialCategories.VALUE
                    : MaterialCategories.ITEM;
        return new MaterialSlot(
            contentName(content, stack),
            clampedPositiveInt(amount),
            category,
            content.chance(),
            catalyst,
            content,
            stack.notEmpty() ? stack.copyWithAmount(Math.max(1, amount)) : ContentStack.empty()
        );
    }

    private static ContentStack<?, ?> stackContent(RecipeContent content) {
        if (content instanceof StackContent<?, ?> stackContent) {
            return stackContent.content();
        }
        if (content instanceof InputContent input && input.isEnumerable()) {
            var variant = input.enumerate().detect(each -> each instanceof ContentStack<?, ?>);
            if (variant instanceof ContentStack<?, ?> stack) {
                return stack;
            }
        }
        return ContentStack.empty();
    }

    private static String contentName(RecipeContent content, ContentStack<?, ?> stack) {
        if (stack.notEmpty()) {
            if (stack.isItem()) {
                return stack.<Item, ItemStack>cast().value().getHoverName().getString();
            }
            if (stack.isFluid()) {
                return stack.<Fluid, FluidStack>cast().value().getHoverName().getString();
            }
        }
        if (content instanceof TagStackContent<?> tag) {
            return "#" + tag.tagKey().location();
        }
        if (content instanceof ValueContent<?> value) {
            return valueName(value);
        }
        return content.toString();
    }

    private static <T> String valueName(ValueContent<T> value) {
        return value.type().toString(value.content().content());
    }

    private static String recipeName(RecipeEntry<?> recipe, List<MaterialSlot> outputs) {
        if (!outputs.isEmpty()) {
            return outputs.getFirst().materialName();
        }
        ResourceLocation id = recipe.id();
        return id == null ? recipe.systemId().toString() : id.toString();
    }

    private static List<MachineInfo> machineOptions(RecipeSystem<?> system) {
        List<MachineInfo> machines = new ArrayList<>();
        MachineEntry convention = system.conventionMachine();
        addMachineInfo(machines, convention);
        try {
            RecipeApi.instance().getMachines(system.systemId(), true).forEach(machine -> addMachineInfo(machines, machine));
        } catch (RuntimeException ignored) {
            // Machine metadata is optional for calculator rendering.
        }
        return dedupeMachineOptions(machines);
    }

    private static void addMachineInfo(List<MachineInfo> machines, MachineEntry machine) {
        if (machine == null || machine.isEmpty()) {
            return;
        }
        ItemStack stack = machine.toItemStack();
        String name = stack.isEmpty() ? machine.toString() : stack.getHoverName().getString();
        machines.add(new MachineInfo(name, stack.isEmpty() ? null : stack.copy()));
    }

    private static List<MachineInfo> dedupeMachineOptions(List<MachineInfo> machines) {
        if (machines.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MachineInfo> byKey = new LinkedHashMap<>();
        for (MachineInfo machine : machines) {
            if (machine == null || machine.name() == null || machine.name().isBlank()) {
                continue;
            }
            String key = machineOptionKey(machine);
            MachineInfo existing = byKey.get(key);
            if (existing == null || preferMachineOption(machine, existing)) {
                byKey.put(key, machine);
            }
        }
        return List.copyOf(byKey.values());
    }

    private static boolean preferMachineOption(MachineInfo candidate, MachineInfo existing) {
        ContentStack<?, ?> candidateIcon = candidate.icon();
        ContentStack<?, ?> existingIcon = existing.icon();
        return candidateIcon.notEmpty() && existingIcon.isEmpty();
    }

    private static String machineOptionKey(MachineInfo machine) {
        ContentStack<?, ?> icon = machine.icon();
        if (icon.notEmpty() && icon.isItem()) {
            ContentStack<Item, ItemStack> item = icon.cast();
            return item.value().getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(() -> machine.name().strip().toLowerCase(Locale.ROOT));
        }
        return machine.name().strip().toLowerCase(Locale.ROOT);
    }

    private static int durationTicks(List<MaterialSlot> outputs) {
        for (MaterialSlot slot : outputs) {
            if (slot.content() instanceof ValueContent<?> value && value.type().typeId().equals(timeValueTypeId)) {
                return slot.amount();
            }
        }
        return 0;
    }

    private static int clampedPositiveInt(long amount) {
        return Math.max(1, clampedNonNegativeInt(amount));
    }

    private static int clampedNonNegativeInt(long amount) {
        if (amount >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) amount);
    }

    private static String describeRecipe(RecipeEntry<?> recipe) {
        try {
            ResourceLocation id = recipe.id();
            return id == null ? recipe.systemId() + "#" + recipe.hashCode() : id.toString();
        } catch (RuntimeException | LinkageError ignored) {
            return recipe.systemId() + "#" + recipe.hashCode();
        }
    }

    private static @Nullable String targetGraphIssue(RecipeEntry<?> target, ProcessGraph graph) {
        if (graph.nodeCount() == 0) {
            return "emptyGraph";
        }
        String preferredRoot = graph.preferredRootNodeId();
        if (preferredRoot == null) {
            return "missingPreferredRoot";
        }
        ProcessNode root = graph.node(preferredRoot);
        if (root == null) {
            return "preferredRootMissingNode:" + preferredRoot;
        }
        if (!target.equals(root.recipe())) {
            return "preferredRootNotTarget rootRecipe=" + describeNodeRecipe(root);
        }
        List<ProcessNode> sinks = graph.sinks();
        if (sinks.size() != 1) {
            return "sinkCount=" + sinks.size();
        }
        if (!root.id().equals(sinks.getFirst().id())) {
            return "sinkIsNotPreferredRoot:" + sinks.getFirst().id();
        }
        return null;
    }

    private static String describeNodeRecipe(ProcessNode node) {
        RecipeEntry<?> recipe = node.recipe();
        return recipe == null ? "none" : describeRecipe(recipe);
    }

    private static String recipeNodeId(RecipeEntry<?> recipe, int index) {
        ResourceLocation id = recipe.id();
        String base = id == null ? recipe.systemId() + "/" + Integer.toHexString(recipe.hashCode()) : id.toString();
        return "recipe/" + base.replace(':', '/') + "/" + index;
    }

    private static String materialSourceNodeId(String targetNodeId, int actionId, int inputIndex, MaterialSlot slot) {
        return targetNodeId + "/source/" + actionId + "/" + inputIndex + "/" + Integer.toHexString(slot.materialName().hashCode());
    }

    private static String nullToEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static final class BuildContext {
        private final Map<RecipeSystem<?>, MachineDisplay> machineBySystem = new HashMap<>();

        private MachineDisplay machineDisplay(RecipeEntry<?> recipe) {
            return machineBySystem.computeIfAbsent(recipe.system(), BuildContext::createMachineDisplay);
        }

        private static MachineDisplay createMachineDisplay(RecipeSystem<?> system) {
            List<MachineInfo> options = machineOptions(system);
            String name = options.isEmpty() ? system.systemId().toString() : options.getFirst().name();
            return new MachineDisplay(name, options);
        }
    }

    private record MachineDisplay(
        String name,
        List<MachineInfo> options
    ) {
    }

    private static final class FallbackDebugTrace {
        private static final int maxLines = 160;
        private final ArrayList<String> lines = new ArrayList<>();
        private int omitted;

        private void add(String line) {
            if (line == null || line.isBlank()) {
                return;
            }
            if (lines.size() < maxLines) {
                lines.add(line);
            } else {
                omitted++;
            }
        }

        private List<String> lines() {
            if (omitted <= 0) {
                return List.copyOf(lines);
            }
            ArrayList<String> out = new ArrayList<>(lines.size() + 1);
            out.addAll(lines);
            out.add("fallback.trace.omitted=" + omitted);
            return List.copyOf(out);
        }
    }
}
