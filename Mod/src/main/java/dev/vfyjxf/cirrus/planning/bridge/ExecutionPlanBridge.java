package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class ExecutionPlanBridge {

    private static final int maxUnrollCycleIters = 64;

    public enum TaskKind {
        acquire, provisionMachine, setupLine, linePull
    }

    public record TaskItem(String stepId, TaskKind kind, String subjectId, long amount) {
        public TaskItem {
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(subjectId, "subjectId");
        }
    }

    public record RecipeBatch(
        String stepId,
        CompiledRecipeKey recipeKey,
        int times,
        List<InputBinding> inputs,
        List<OutputBinding> outputs,
        List<InputContent> catalysts
    ) {
        public RecipeBatch {
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(recipeKey, "recipeKey");
            if (times <= 0) {
                throw new IllegalArgumentException("times must be > 0, got " + times);
            }
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            catalysts = List.copyOf(Objects.requireNonNull(catalysts, "catalysts"));
        }
    }

    public record ExecutionOutline(String planId, List<RecipeBatch> batches, List<TaskItem> tasks) {
        public ExecutionOutline {
            Objects.requireNonNull(planId, "planId");
            batches = List.copyOf(Objects.requireNonNull(batches, "batches"));
            tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        }
    }

    private ExecutionPlanBridge() {}

    public static Optional<RecipeEntry<?>> resolveEntry(
        CompiledRecipeGraph graph,
        CompiledRecipeKey key,
        List<RecipeEntry<?>> entriesBySourceRef
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(key, "key");
        if (entriesBySourceRef == null || entriesBySourceRef.isEmpty()) {
            return Optional.empty();
        }
        Optional<CompiledRecipe> compiled = graph.recipeByKey(key);
        if (compiled.isEmpty()) {
            return Optional.empty();
        }
        long ref = graph.sourceRefOf(compiled.get().compactId());
        if (ref <= 0L || ref > entriesBySourceRef.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entriesBySourceRef.get((int) (ref - 1L)));
    }

    public static ExecutionOutline buildOutline(
        ProductionPlan plan,
        Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> recipeResolver
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(recipeResolver, "recipeResolver");

        List<RecipeBatch> batches = new ArrayList<>();
        List<TaskItem> tasks = new ArrayList<>();

        for (PlanStep step : plan.steps()) {
            switch (step) {
                case CraftStep cs -> appendRecipeBatches(
                    cs.stepId(),
                    cs.recipeKey(),
                    cs.batches(),
                    cs.slotBindings(),
                    cs.catalystBindings(),
                    recipeResolver,
                    batches
                );
                case ConvertStep cv -> appendRecipeBatches(
                    cv.stepId(),
                    cv.conversionRecipeKey(),
                    cv.batches(),
                    cv.slotBindings(),
                    Map.of(),
                    recipeResolver,
                    batches
                );
                case CycleLoopStep cl -> appendCycleLoop(cl, recipeResolver, batches);
                case AcquireStep ac ->
                    tasks.add(new TaskItem(ac.stepId(), TaskKind.acquire, ac.resource().shortString(), ac.amount()));
                case ProvisionMachineStep pm ->
                    tasks.add(new TaskItem(pm.stepId(), TaskKind.provisionMachine, pm.machineId(), pm.countToBuild()));
                case SetupLineStep sl ->
                    tasks.add(new TaskItem(sl.stepId(), TaskKind.setupLine, sl.primaryMachineId(), 1L));
                case LinePullStep lp ->
                    tasks.add(new TaskItem(lp.stepId(), TaskKind.linePull, lp.resource().shortString(), lp.amount()));
            }
        }
        return new ExecutionOutline(plan.planId(), batches, tasks);
    }

    private static void appendCycleLoop(
        CycleLoopStep cl,
        Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> recipeResolver,
        List<RecipeBatch> out
    ) {
        List<CraftStep> template = cl.singleIterationTemplate();
        if (template.isEmpty() || cl.iterations() <= 0L) {
            return;
        }
        if (template.size() == 1 || cl.iterations() > maxUnrollCycleIters) {
            for (CraftStep ts : template) {
                long total = SafeMath.mulSaturating(ts.batches(), cl.iterations());
                appendRecipeBatches(
                    cl.stepId() + ":" + ts.stepId(),
                    ts.recipeKey(),
                    total,
                    ts.slotBindings(),
                    ts.catalystBindings(),
                    recipeResolver,
                    out
                );
            }
            return;
        }
        for (long iter = 0L; iter < cl.iterations(); iter++) {
            for (CraftStep ts : template) {
                appendRecipeBatches(
                    cl.stepId() + "#" + iter + ":" + ts.stepId(),
                    ts.recipeKey(),
                    ts.batches(),
                    ts.slotBindings(),
                    ts.catalystBindings(),
                    recipeResolver,
                    out
                );
            }
        }
    }

    private static void appendRecipeBatches(
        String stepId,
        CompiledRecipeKey key,
        long totalBatches,
        Map<Integer, SlotBinding> slotBindings,
        Map<Integer, ResourceKey> catalystBindings,
        Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> recipeResolver,
        List<RecipeBatch> out
    ) {
        if (totalBatches <= 0L) {
            return;
        }
        List<InputBinding> inputs = List.of();
        List<OutputBinding> outputs = List.of();
        List<InputContent> catalysts = List.of();

        Optional<RecipeEntry<?>> entry = recipeResolver.apply(key);
        if (entry.isPresent()) {
            Optional<CirrusCatalogAdapter.RecipeContentSource> srcOpt = CirrusCatalogAdapter.contentSource(entry.get());
            if (srcOpt.isPresent()) {
                CirrusCatalogAdapter.RecipeContentSource src = srcOpt.get();
                List<InputBinding> inList = new ArrayList<>();
                List<InputContent> catList = new ArrayList<>();
                Set<Integer> matchedSlots = slotBindings.isEmpty() ? Set.of() : new HashSet<>();
                Set<Integer> matchedCats = catalystBindings.isEmpty() ? Set.of() : new HashSet<>();
                int consumedIdx = 0;
                int catIdx = 0;
                for (InputContent in : src.inputs()) {
                    if (in == null || in.isEmpty()) continue;
                    if (in.notConsumed()) {
                        catList.add(bindCatalystInput(in, catIdx++, catalystBindings, matchedCats));
                    } else if (in.amount() > 0L) {
                        InputContent bound = bindConsumedInput(in, consumedIdx++, slotBindings, matchedSlots);
                        inList.add(new InputBinding(bound, bound.amount()));
                    }
                }
                List<OutputBinding> outList = new ArrayList<>();
                for (OutputContent o : src.outputs()) {
                    if (o instanceof StackContent<?, ?> stack && !stack.isEmpty() && stack.amount() > 0L) {
                        outList.add(new OutputBinding(stack.content(), stack.amount()));
                    }
                }
                inputs = inList;
                outputs = outList;
                catalysts = catList;
            }
        }

        long remaining = totalBatches;
        int chunk = 0;
        while (remaining > 0L) {
            int times = (int) Math.min(remaining, Integer.MAX_VALUE);
            String chunkId = chunk == 0 ? stepId : stepId + "_p" + chunk;
            out.add(new RecipeBatch(chunkId, key, times, inputs, outputs, catalysts));
            remaining -= times;
            chunk++;
        }
    }

    private static InputContent bindConsumedInput(
        InputContent rawIn,
        int preferredSlotIdx,
        Map<Integer, SlotBinding> slotBindings,
        Set<Integer> matchedSlots
    ) {
        if (slotBindings.isEmpty() || rawIn instanceof StackContent<?, ?>) {
            return rawIn;
        }
        CirrusContentBridge.InputMapping mapped = CirrusContentBridge.mapInput(rawIn, 0);
        RawIngredientSlot rawSlot = mapped.slot();
        if (rawSlot == null || rawSlot.candidates().size() <= 1) {
            return rawIn;
        }
        ResourceKey chosen = matchSlotBinding(preferredSlotIdx, rawSlot.candidates(), slotBindings, matchedSlots);
        if (chosen == null || chosen.canonicalVariantHash().isPresent()) {
            return rawIn;
        }
        Optional<ContentStack<?, ?>> concrete = CirrusContentBridge.toBestEffortContentStack(chosen, rawIn.amount());
        if (concrete.isEmpty()) {
            return rawIn;
        }
        InputContent bound = InputContent.fromStack(concrete.get());
        return bound.isEmpty() ? rawIn : bound;
    }

    private static InputContent bindCatalystInput(
        InputContent rawIn,
        int preferredCatIdx,
        Map<Integer, ResourceKey> catalystBindings,
        Set<Integer> matchedCats
    ) {
        if (catalystBindings.isEmpty() || rawIn instanceof StackContent<?, ?>) {
            return rawIn;
        }
        CirrusContentBridge.InputMapping mapped = CirrusContentBridge.mapInput(rawIn, 0);
        RawIngredientSlot rawSlot = mapped.slot();
        if (rawSlot == null || rawSlot.candidates().size() <= 1) {
            return rawIn;
        }
        ResourceKey chosen = matchCatalystBinding(preferredCatIdx, rawSlot.candidates(), catalystBindings, matchedCats);
        if (chosen == null || chosen.canonicalVariantHash().isPresent()) {
            return rawIn;
        }
        long amount = rawIn.amount() > 0L ? rawIn.amount() : 1L;
        Optional<ContentStack<?, ?>> concrete = CirrusContentBridge.toBestEffortContentStack(chosen, amount);
        if (concrete.isEmpty()) {
            return rawIn;
        }
        InputContent bound = InputContent.fromStack(concrete.get(), InputContent.MIN_CHANCE);
        return bound.isEmpty() ? rawIn : bound;
    }

    private static @Nullable ResourceKey matchSlotBinding(
        int preferredIdx,
        List<ResourceKey> candidates,
        Map<Integer, SlotBinding> slotBindings,
        Set<Integer> matchedSlots
    ) {
        SlotBinding direct = slotBindings.get(preferredIdx);
        if (direct != null && !matchedSlots.contains(preferredIdx) && candidates.contains(direct.chosenResource())) {
            matchedSlots.add(preferredIdx);
            return direct.chosenResource();
        }
        for (Map.Entry<Integer, SlotBinding> e : slotBindings.entrySet()) {
            if (!matchedSlots.contains(e.getKey()) && candidates.contains(e.getValue().chosenResource())) {
                matchedSlots.add(e.getKey());
                return e.getValue().chosenResource();
            }
        }
        return null;
    }

    private static @Nullable ResourceKey matchCatalystBinding(
        int preferredIdx,
        List<ResourceKey> candidates,
        Map<Integer, ResourceKey> catalystBindings,
        Set<Integer> matchedCats
    ) {
        ResourceKey direct = catalystBindings.get(preferredIdx);
        if (direct != null && !matchedCats.contains(preferredIdx) && candidates.contains(direct)) {
            matchedCats.add(preferredIdx);
            return direct;
        }
        for (Map.Entry<Integer, ResourceKey> e : catalystBindings.entrySet()) {
            if (!matchedCats.contains(e.getKey()) && candidates.contains(e.getValue())) {
                matchedCats.add(e.getKey());
                return e.getValue();
            }
        }
        return null;
    }
}
