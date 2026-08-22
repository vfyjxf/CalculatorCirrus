package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseRecipeRef;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureCapture;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
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
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CalculatorProcessGraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");
    private static final String materialSourceMachine = "Material";
    private static final String pendingSourceMachine = "Pending";
    private static final Namespace timeValueTypeId = Namespace.of("calculator_cirrus", "time");
    private static final Namespace energyValueTypeId = Namespace.of("calculator_cirrus", "energy");
    private static final int maxFallbackProjectionIndexCacheSize = 3;
    private static final int fastPreviewMaxInputs = 2;
    private static final int fastPreviewMaxCatalogRecipes = 256;
    private static final Object fallbackProjectionIndexCacheLock = new Object();
    private static final LinkedHashMap<FallbackProjectionIndexCacheKey, FallbackProjectionIndexCacheEntry> fallbackProjectionIndexCache = new LinkedHashMap<>();

    private CalculatorProcessGraphBuilder() {
    }

    public enum RouteMode {
        calculator,
        execution
    }

    public record BuildResult(
        ProcessGraph graph,
        @Nullable RoutingPlan routingPlan,
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
        int catalogSize,
        boolean fastPreview,
        boolean fallbackUsed,
        @Nullable RoutingStatus routingStatus,
        String routingIssueKeys,
        int routePlanActions,
        long routingElapsedMs,
        int expandedNodes,
        int generatedCandidates,
        int acceptedCandidates,
        boolean budgetExhausted,
        String budgetStopReason,
        String budgetCulpritRecipe,
        String budgetCulpritInput,
        int budgetCulpritDepth,
        long budgetCulpritRemaining,
        boolean budgetCulpritActiveCycle,
        int graphNodes,
        int graphEdges,
        @Nullable String exception
    ) {
        public static CalculatorBuildDebugInfo empty() {
            return new CalculatorBuildDebugInfo(
                "none",
                0,
                RouteMode.calculator,
                0,
                false,
                false,
                null,
                "",
                0,
                0,
                0,
                0,
                0,
                false,
                "none",
                "none",
                "none",
                -1,
                0,
                false,
                0,
                0,
                null
            );
        }

        public String toDebugText() {
            return "target=" + target
                + " times=" + times
                + " mode=" + routeMode
                + " catalog=" + catalogSize
                + " fastPreview=" + fastPreview
                + " fallback=" + fallbackUsed
                + " status=" + (routingStatus == null ? "none" : routingStatus)
                + " actions=" + routePlanActions
                + " elapsedMs=" + routingElapsedMs
                + " expanded=" + expandedNodes
                + " generated=" + generatedCandidates
                + " accepted=" + acceptedCandidates
                + " budgetExhausted=" + budgetExhausted
                + " budgetStop=" + budgetStopReason
                + (budgetExhausted
                ? " budgetCulprit=" + budgetCulpritRecipe
                    + " input=" + budgetCulpritInput
                    + " depth=" + budgetCulpritDepth
                    + " remaining=" + budgetCulpritRemaining
                    + " activeCycle=" + budgetCulpritActiveCycle
                : "")
                + " graph=" + graphNodes + "/" + graphEdges
                + (routingIssueKeys.isBlank() ? "" : " issues=" + routingIssueKeys)
                + (exception == null ? "" : " exception=" + exception);
        }

        public CalculatorBuildDebugInfo withGraph(ProcessGraph graph, boolean fallbackUsed) {
            return new CalculatorBuildDebugInfo(
                target,
                times,
                routeMode,
                catalogSize,
                fastPreview,
                fallbackUsed,
                routingStatus,
                routingIssueKeys,
                routePlanActions,
                routingElapsedMs,
                expandedNodes,
                generatedCandidates,
                acceptedCandidates,
                budgetExhausted,
                budgetStopReason,
                budgetCulpritRecipe,
                budgetCulpritInput,
                budgetCulpritDepth,
                budgetCulpritRemaining,
                budgetCulpritActiveCycle,
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
        return build(target, times, RecipePathSettings.calculatorStock());
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
        int safeTimes = Math.max(1, times);
        RecipePathSettings.Catalog catalog = RecipePathSettings.calculatorCatalog(target);
        if (usesFastCalculatorPreview(target, safeTimes, stock, preferredVariants, routeMode)) {
            FallbackDebugTrace fallbackTrace = new FallbackDebugTrace();
            ProcessGraph graph = fromProjectedRecipe(target, safeTimes, fallbackTrace);
            CalculatorBuildDebugInfo debugInfo = baseDebug(target, safeTimes, RouteMode.calculator, catalog.recipes().size())
                .withGraph(graph, false);
            logSuspiciousBuild(debugInfo, null);
            ArrayList<String> trace = new ArrayList<>();
            trace.add("summary fastPreview=true");
            trace.add("route skipped reason=smallTargetPreview");
            trace.addAll(fallbackTrace.lines());
            String graphIssue = targetGraphIssue(target, graph);
            if (graphIssue != null) {
                trace.add("graph fastPreviewInvalid reason=" + graphIssue + " " + graphSummary(graph));
            }
            return new BuildResult(graph, null, RouteMode.calculator, debugInfo, trace);
        }

        RouteResult routeResult = routeResult(target, safeTimes, stock, preferredVariants, routeMode, forceDeepen);
        ArrayList<String> trace = new ArrayList<>();
        appendRouteTrace(trace, routeResult);
        RoutingPlan plan = routeResult.plan();
        if (plan != null) {
            try {
                ProcessGraph graph = fromRoutingPlan(plan, routeMode);
                String graphIssue = targetGraphIssue(plan.targetRecipe(), graph);
                if (graph.nodeCount() > 0) {
                    CalculatorBuildDebugInfo debugInfo = routeResult.debugInfo().withGraph(graph, false);
                    logSuspiciousBuild(debugInfo, routeResult.outcome());
                    trace.add("graph builtFrom=routePlan nodes=" + graph.nodeCount() + " edges=" + graph.edgeCount());
                    if (graphIssue != null) {
                        trace.add("graph routePlanIssue reason=" + graphIssue + " " + graphSummary(graph));
                        logger.warn("Calculator route graph diagnostics: reason={} {}", graphIssue, graphSummary(graph));
                    }
                    return new BuildResult(graph, plan, routeMode, debugInfo, trace);
                }
                trace.add("graph routePlanRejected reason=" + graphIssue + " " + graphSummary(graph));
            } catch (RuntimeException exception) {
                routeResult = routeResult.withException(exception);
                trace.add("graph routePlanConversionException=" + exception.getClass().getSimpleName() + ":" + exception.getMessage());
                // Fall through to the direct projected-recipe graph below.
            }
        }
        FallbackDebugTrace fallbackTrace = new FallbackDebugTrace();
        ProcessGraph fallback = fromProjectedRecipe(target, safeTimes, fallbackTrace);
        trace.addAll(fallbackTrace.lines());
        CalculatorBuildDebugInfo debugInfo = routeResult.debugInfo().withGraph(fallback, true);
        logSuspiciousBuild(debugInfo, routeResult.outcome());
        trace.add("graph builtFrom=fallback nodes=" + fallback.nodeCount() + " edges=" + fallback.edgeCount());
        String fallbackIssue = targetGraphIssue(target, fallback);
        if (fallbackIssue != null) {
            trace.add("graph fallbackInvalid reason=" + fallbackIssue + " " + graphSummary(fallback));
        }
        return new BuildResult(fallback, plan, routeMode, debugInfo, trace);
    }

    private static boolean usesFastCalculatorPreview(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode
    ) {
        if (routeMode != RouteMode.calculator
            || (stock != null && !stock.isEmpty())
            || (preferredVariants != null && !preferredVariants.isEmpty())) {
            return false;
        }
        RecipePathSettings.Catalog catalog = RecipePathSettings.calculatorCatalog(target);
        if (catalog.recipes().size() > fastPreviewMaxCatalogRecipes) {
            return false;
        }
        FallbackProjectionIndex projectionIndex = cachedFallbackProjectionIndex(catalog);
        ProjectedRecipe projected;
        try {
            projected = projectionIndex.project(target, 1);
        } catch (RuntimeException ignored) {
            return true;
        }

        int inputCount = 0;
        for (InputContent input : projected.inputs()) {
            if (input.isEmpty()) {
                continue;
            }
            inputCount++;
            if (directInputResource(input) == null) {
                return false;
            }
            if (inputCount > fastPreviewMaxInputs) {
                return true;
            }
            if (projectionIndex.findProducer(target, input, Set.of()) != null) {
                return true;
            }
        }
        return true;
    }

    public static ProcessGraph fromRoutingPlan(RoutingPlan plan) {
        return fromRoutingPlan(plan, RouteMode.calculator);
    }

    public static ProcessGraph fromRoutingPlan(RoutingPlan plan, RouteMode routeMode) {
        PlanGraphSize graphSize = estimatePlanGraphSize(plan);
        ProcessGraph graph = new ProcessGraph(graphSize.nodes(), graphSize.edges());
        ProducedPool produced = new ProducedPool(graphSize.outputSlots());
        PendingPool pending = new PendingPool(graphSize.pendingInputs());
        BuildContext buildContext = new BuildContext();
        String targetRootNodeId = null;

        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.PendingInput pendingInput) {
                String nodeId = pendingNodeId(pendingInput.input(), pendingInput.actionId());
                MaterialSlot slot = inputSlot(pendingInput.input(), pendingInput.amount());
                graph.addNode(materialSourceNode(nodeId, slot, unresolvedSourceMachine(routeMode)));
                pending.add(new PendingSlot(nodeId, slot, pendingInput.amount()));
                continue;
            }
            if (!(action instanceof PlanAction.ExecuteRecipe execute)) {
                continue;
            }

            String nodeId = recipeNodeId(execute.recipe(), execute.actionId());
            if (execute.recipe().equals(plan.targetRecipe())) {
                targetRootNodeId = nodeId;
            }
            ImmutableList<PlanAction.InputBinding> visibleInputs = execute.boundInputs();
            List<MaterialSlot> inputs = new ArrayList<>(visibleInputs.size());
            for (PlanAction.InputBinding binding : visibleInputs) {
                inputs.add(inputSlot(binding.input(), binding.amount()));
            }
            var expectedOutputs = execute.expectedOutputs();
            List<MaterialSlot> outputs = new ArrayList<>(expectedOutputs.size());
            for (PlanAction.OutputBinding binding : expectedOutputs) {
                outputs.add(outputSlot(binding.resource(), binding.amount()));
            }
            MachineDisplay machine = buildContext.machineDisplay(execute.recipe());

            ProcessNode node = new ProcessNode(
                nodeId,
                recipeName(execute.recipe(), outputs),
                machine.name(),
                inputs,
                outputs,
                durationTicks(outputs),
                machine.options(),
                execute.recipe()
            );
            graph.addNode(node);
            connectInputs(graph, nodeId, execute.actionId(), visibleInputs, produced, pending, routeMode);
            for (int i = 0; i < expectedOutputs.size(); i++) {
                PlanAction.OutputBinding output = expectedOutputs.get(i);
                MaterialSlot slot = outputs.get(i);
                produced.add(new ProducedSlot(nodeId, output.resource(), output.amount(), slot.materialName(), slot.type()));
            }
        }
        if (targetRootNodeId != null) {
            graph.preferredRootNodeId(targetRootNodeId);
        }
        return graph;
    }

    private static PlanGraphSize estimatePlanGraphSize(RoutingPlan plan) {
        int nodes = 0;
        int edges = 0;
        int outputSlots = 0;
        int pendingInputs = 0;
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.PendingInput) {
                nodes++;
                pendingInputs++;
                continue;
            }
            if (action instanceof PlanAction.ExecuteRecipe execute) {
                nodes++;
                edges += execute.boundInputs().size();
                outputSlots += execute.expectedOutputs().size();
            }
        }
        return new PlanGraphSize(nodes + edges, edges, outputSlots, pendingInputs);
    }

    public static @Nullable RoutingPlan routePlan(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode
    ) {
        return routeResult(target, times, stock, preferredVariants, routeMode).plan();
    }

    private static RouteResult routeResult(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode
    ) {
        return routeResult(target, times, stock, preferredVariants, routeMode, false);
    }

    private static RouteResult routeResult(
        RecipeEntry<?> target,
        int times,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode,
        boolean forceDeepen
    ) {
        RecipePathSettings.Catalog catalog = RecipePathSettings.calculatorCatalog(target);
        try {
            RecipePathSettings.RoutingContext routingContext = switch (routeMode) {
                case calculator -> RecipePathSettings.calculatorRoutingContext(target);
                case execution -> RecipePathSettings.executionRoutingContext(target);
            };
            RoutingService service = routingContext.service();
            RuleDefinition calculatorRule = routingContext.rule();
            service.registerRuleDefinition(calculatorRule);
            RoutingBudget budget = routingBudget(
                target,
                times,
                routingContext.catalog(),
                service,
                routingContext.plannerMode(),
                routeMode
            );
            logger.info(
                "calculator.route.request target={} times={} mode={} catalog={} planner={} rule={} sourcePolicy={} budgetNodes={} budgetCandidates={} rootBudgetNodes={} rootBudgetCandidates={} stockEmpty={} preferredVariants={}",
                describeRecipe(target),
                times,
                routeMode,
                catalog.recipes().size(),
                routingContext.plannerMode(),
                calculatorRule.profile().profileId(),
                calculatorRule.sourcePolicy().policyId(),
                budget.maxExpandedNodes(),
                budget.maxGeneratedCandidates(),
                budget.maxRootInputExpandedNodes(),
                budget.maxRootInputGeneratedCandidates(),
                stock == null || stock.isEmpty(),
                preferredVariants == null ? 0 : preferredVariants.size()
            );
            RoutingOutcome outcome = routeWithDeepenWaves(
                service,
                routingContext,
                calculatorRule,
                target,
                times,
                budget,
                stock,
                preferredVariants,
                routeMode,
                forceDeepen
            );
            logger.info(
                "calculator.route.result target={} status={} planActions={} issues={} elapsedMs={} expanded={} generated={} accepted={} budgetExhausted={} budgetStop={} budgetCulprit={} budgetInput={} budgetDepth={} budgetRemaining={} budgetCycle={}",
                describeRecipe(target),
                outcome.status(),
                outcome.plan() == null ? 0 : outcome.plan().actions().size(),
                outcome.issues().size(),
                outcome.metrics().elapsedMs(),
                outcome.metrics().expandedNodes(),
                outcome.metrics().generatedCandidates(),
                outcome.metrics().acceptedCandidates(),
                outcome.metrics().budgetExhausted(),
                outcome.metrics().budgetStopReason(),
                outcome.metrics().budgetCulpritRecipe(),
                outcome.metrics().budgetCulpritInput(),
                outcome.metrics().budgetCulpritDepth(),
                outcome.metrics().budgetCulpritRemaining(),
                outcome.metrics().budgetCulpritActiveCycle()
            );
            return new RouteResult(outcome.plan(), outcome, debugInfo(target, times, routeMode, catalog.recipes().size(), outcome, null));
        } catch (RuntimeException exception) {
            logger.error(
                "calculator.route.exception target={} times={} mode={} catalog={}",
                describeRecipe(target),
                times,
                routeMode,
                catalog.recipes().size(),
                exception
            );
            return new RouteResult(null, null, debugInfo(target, times, routeMode, catalog.recipes().size(), null, exception));
        }
    }

    /**
     * Runs the calculator route as a sequence of budget waves: the first wave uses a
     * quarter of the full cone-sized budget so the UI gets a partial plan quickly;
     * when a wave stops on budget limits with pending inputs left, the next wave
     * continues the same planning session (producer shortlists and cost memos are
     * reused, never re-scanned) with double the budget. The loop stops when a wave
     * resolves nothing new, when the plan has no pending inputs, or when the
     * user-configured generated-candidate cap would be exceeded.
     */
    private static RoutingOutcome routeWithDeepenWaves(
        RoutingService service,
        RecipePathSettings.RoutingContext routingContext,
        RuleDefinition calculatorRule,
        RecipeEntry<?> target,
        int times,
        RoutingBudget fullBudget,
        Stock stock,
        Map<String, String> preferredVariants,
        RouteMode routeMode,
        boolean forceDeepen
    ) {
        int maxWaves = routeMode == RouteMode.calculator
            && (forceDeepen || ExecutionOverlayClientConfig.calculatorAutoDeepenEnabled()) ? 3 : 1;
        int deepenMaxGenerated = ExecutionOverlayClientConfig.calculatorDeepenMaxGenerated();
        RoutingOutcome outcome = null;
        String token = null;
        long previousPending = -1;
        long previousActions = -1;
        for (int wave = 1; wave <= maxWaves; wave++) {
            RoutingBudget waveBudget = waveBudget(fullBudget, wave, maxWaves);
            if (wave > 1 && waveBudget.maxGeneratedCandidates() > deepenMaxGenerated) {
                // Big cones used to stop here: the last wave asks for the full
                // cone-sized candidate budget, which for pack-sized targets sits
                // above the configured cap, and abandoning the wave left pending
                // branches unresolved for good. Instead the wave is clamped to the
                // cap and still runs — the cap bounds the requested allocation,
                // the wall-time budget bounds the actual work, and deepening keeps
                // making progress on whatever it can reach.
                int requestedCandidates = waveBudget.maxGeneratedCandidates();
                waveBudget = waveBudget.withMaxGeneratedCandidates(deepenMaxGenerated);
                logger.info(
                    "calculator.route.deepen.clamp wave={} requested={} cap={} actions={} pending={}",
                    wave,
                    requestedCandidates,
                    deepenMaxGenerated,
                    previousActions,
                    previousPending
                );
            }
            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(routingContext.plannerMode())
                .targetRecipe(target, times)
                .ruleProfileId(calculatorRule.profile().profileId())
                .sourcePolicyId(calculatorRule.sourcePolicy().policyId())
                .budget(waveBudget)
                .maxProducerCandidates(maxProducerCandidates(waveBudget, routingContext.plannerMode()))
                .stock(stock == null ? Stock.empty() : stock.copy())
                .inventorySensitivityProfile(routingContext.inventorySensitivity())
                .loopPolicy(routingContext.loopPolicy())
                .preferredVariants(preferredVariants)
                .build();
            RoutingOutcome waveOutcome = token == null
                ? service.route(request)
                : service.routeContinuation(token, request);
            long pending = countPendingInputs(waveOutcome.plan());
            long actions = waveOutcome.plan() == null ? 0 : waveOutcome.plan().actions().size();
            // Waves with only genuinely-unreachable pendings cannot make progress
            // by deepening (TRUE_NO_SOURCE / CASCADE_EXHAUSTED); waves whose
            // pendings were budget/rank-truncated still can. The old
            // equal-count noProgress heuristic conflated the two and both
            // stopped too early on renamings and burned waves on dead demands.
            boolean anyTruncated = anyTruncatedPending(waveOutcome.plan());
            boolean noProgress = wave > 1 && !anyTruncated && pending == previousPending && actions == previousActions;
            outcome = waveOutcome;
            if (noProgress) {
                logger.info(
                    "calculator.route.deepen.stop reason=noProgress wave={} pending={} actions={}",
                    wave,
                    pending,
                    actions
                );
                break;
            }
            previousPending = pending;
            previousActions = actions;
            token = waveOutcome.continuationToken();
            boolean deepenable = anyTruncated || pending == 0;
            if (token == null || !waveOutcome.metrics().budgetExhausted() || !deepenable) {
                break;
            }
            logger.info(
                "calculator.route.deepen.next wave={} nextWave={} pending={} actions={} generated={} stop={}",
                wave,
                wave + 1,
                pending,
                actions,
                waveOutcome.metrics().generatedCandidates(),
                waveOutcome.metrics().budgetStopReason()
            );
        }
        return outcome;
    }

    /**
     * Geometric wave budget: with {@code maxWaves} waves, wave 1 gets
     * {@code 1 / 2^(maxWaves-1)} of the full budget and each later wave doubles it,
     * so the last wave runs with the full cone-sized budget.
     */
    private static RoutingBudget waveBudget(RoutingBudget full, int wave, int maxWaves) {
        if (wave >= maxWaves) {
            return full;
        }
        int shift = maxWaves - wave;
        double scale = 1.0d / (1 << shift);
        return new RoutingBudget(
            Math.max(600, (int) (full.maxExpandedNodes() * scale)),
            Math.max(4_000, (int) (full.maxGeneratedCandidates() * scale)),
            full.maxWallTimeMs() == 0 ? 0 : Math.max(800, (int) (full.maxWallTimeMs() * scale)),
            Math.max(1_000, (int) (full.maxMemoryStates() * scale)),
            full.maxRootInputExpandedNodes() == 0 ? 0 : Math.max(256, (int) (full.maxRootInputExpandedNodes() * scale)),
            full.maxRootInputGeneratedCandidates() == 0 ? 0 : Math.max(2_000, (int) (full.maxRootInputGeneratedCandidates() * scale)),
            full.explorationBudgetShare(),
            full.loopBudget()
        );
    }

    private static long countPendingInputs(@Nullable RoutingPlan plan) {
        if (plan == null) {
            return 0;
        }
        long count = 0;
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.PendingInput) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether any pending input was budget/rank-truncated — i.e. a deeper wave
     * with a larger budget could still resolve it, as opposed to genuinely
     * unreachable demands which deepening can never fix.
     */
    private static boolean anyTruncatedPending(@Nullable RoutingPlan plan) {
        if (plan == null) {
            return false;
        }
        for (PlanAction action : plan.actions()) {
            if (action instanceof PlanAction.PendingInput pending && pending.truncated()) {
                return true;
            }
        }
        return false;
    }

    private static CalculatorBuildDebugInfo baseDebug(
        RecipeEntry<?> target,
        int times,
        RouteMode routeMode,
        int catalogSize
    ) {
        return new CalculatorBuildDebugInfo(
            describeRecipe(target),
            times,
            routeMode,
            catalogSize,
            true,
            false,
            null,
            "",
            0,
            0,
            0,
            0,
            0,
            false,
            "none",
            "none",
            "none",
            -1,
            0,
            false,
            0,
            0,
            null
        );
    }

    private static CalculatorBuildDebugInfo debugInfo(
        RecipeEntry<?> target,
        int times,
        RouteMode routeMode,
        int catalogSize,
        @Nullable RoutingOutcome outcome,
        @Nullable RuntimeException exception
    ) {
        RoutingMetrics metrics = outcome == null ? null : outcome.metrics();
        RoutingPlan plan = outcome == null ? null : outcome.plan();
        return new CalculatorBuildDebugInfo(
            describeRecipe(target),
            times,
            routeMode,
            catalogSize,
            false,
            false,
            outcome == null ? null : outcome.status(),
            issueKeys(outcome),
            plan == null ? 0 : plan.actions().size(),
            metrics == null ? 0 : metrics.elapsedMs(),
            metrics == null ? 0 : metrics.expandedNodes(),
            metrics == null ? 0 : metrics.generatedCandidates(),
            metrics == null ? 0 : metrics.acceptedCandidates(),
            metrics != null && metrics.budgetExhausted(),
            metrics == null ? "none" : metrics.budgetStopReason(),
            metrics == null ? "none" : metrics.budgetCulpritRecipe(),
            metrics == null ? "none" : metrics.budgetCulpritInput(),
            metrics == null ? -1 : metrics.budgetCulpritDepth(),
            metrics == null ? 0 : metrics.budgetCulpritRemaining(),
            metrics != null && metrics.budgetCulpritActiveCycle(),
            0,
            0,
            exception == null ? null : exception.getClass().getSimpleName() + ":" + exception.getMessage()
        );
    }

    private static String issueKeys(@Nullable RoutingOutcome outcome) {
        if (outcome == null || outcome.issues().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (var issue : outcome.issues()) {
            if (count++ >= 6) {
                builder.append(",...");
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(issue.messageKey());
        }
        return builder.toString();
    }

    private static void appendRouteTrace(ArrayList<String> trace, RouteResult routeResult) {
        CalculatorBuildDebugInfo debug = routeResult.debugInfo();
        trace.add("summary " + debug.toDebugText());
        RoutingOutcome outcome = routeResult.outcome();
        if (outcome == null) {
            trace.add("route outcome=null exception=" + debug.exception());
            return;
        }
        RoutingMetrics metrics = outcome.metrics();
        trace.add(
            "route status=" + outcome.status()
                + " actions=" + (outcome.plan() == null ? 0 : outcome.plan().actions().size())
                + " issues=" + outcome.issues().size()
                + " elapsedMs=" + metrics.elapsedMs()
                + " expanded=" + metrics.expandedNodes()
                + " generated=" + metrics.generatedCandidates()
                + " accepted=" + metrics.acceptedCandidates()
                + " budget=" + metrics.budgetUsed() + "/" + metrics.budgetLimit()
                + " exhausted=" + metrics.budgetExhausted()
                + " stop=" + metrics.budgetStopReason()
        );
        int issueLimit = Math.min(8, outcome.issues().size());
        for (int index = 0; index < issueLimit; index++) {
            var issue = outcome.issues().get(index);
            trace.add(
                "issue[" + index + "] code=" + issue.code()
                    + " blocking=" + issue.blocking()
                    + " message=" + issue.messageKey()
                    + " payload=" + issue.payload()
            );
        }
        if (outcome.issues().size() > issueLimit) {
            trace.add("issue omitted=" + (outcome.issues().size() - issueLimit));
        }
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

    public static @Nullable String debugTargetGraphIssueForTests(RecipeEntry<?> target, ProcessGraph graph) {
        return targetGraphIssue(target, graph);
    }

    private static String graphSummary(ProcessGraph graph) {
        return "nodes=" + graph.nodeCount()
            + " edges=" + graph.edgeCount()
            + " sinks=" + graph.sinks().size()
            + " sources=" + graph.sources().size()
            + " preferredRoot=" + graph.preferredRootNodeId()
            + " sinkSample=" + nodeSample(graph.sinks(), 4);
    }

    private static String nodeSample(List<ProcessNode> nodes, int limit) {
        if (nodes.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (ProcessNode node : nodes) {
            if (count >= limit) {
                builder.append(",...");
                break;
            }
            if (count > 0) {
                builder.append(',');
            }
            builder.append(node.id()).append('|').append(describeNodeRecipe(node));
            count++;
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeNodeRecipe(ProcessNode node) {
        RecipeEntry<?> recipe = node.recipe();
        return recipe == null ? "source" : describeRecipe(recipe);
    }

    private static String nullToEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static @Nullable DatabaseRecipeRef recipeRef(@Nullable RecipeEntry<?> entry) {
        if (entry == null) {
            return null;
        }
        try {
            return DatabaseRecipeRef.fromEntry(entry);
        } catch (RuntimeException | LinkageError exception) {
            logger.debug(
                "calculator.recipeRef.unavailable system={} recipe={}",
                entry.systemId(),
                entry.recipe(),
                exception
            );
            return null;
        }
    }

    private static String recipeClassName(@Nullable RecipeEntry<?> entry) {
        if (entry == null || entry.recipe() == null) {
            return "";
        }
        Object recipe = entry.recipe();
        if (recipe instanceof RecipeHolder<?> holder) {
            return "RecipeHolder(" + holder.value().getClass().getName() + ")";
        }
        return recipe.getClass().getName();
    }

    private static boolean cacheableUnsupported(@Nullable ResolveError error) {
        return error == ResolveError.unsupported || error == ResolveError.invalidRecipe;
    }

    private static void logSuspiciousBuild(CalculatorBuildDebugInfo debugInfo, @Nullable RoutingOutcome outcome) {
        if (debugInfo.graphNodes() <= 1
            || debugInfo.fallbackUsed()
            || debugInfo.routingStatus() == RoutingStatus.failed
            || debugInfo.budgetExhausted()) {
            logger.warn("Calculator build diagnostics: {}", debugInfo.toDebugText());
        } else {
            logger.info("Calculator build diagnostics: {}", debugInfo.toDebugText());
        }
        if (outcome != null && !outcome.issues().isEmpty()) {
            int limit = Math.min(8, outcome.issues().size());
            for (int index = 0; index < limit; index++) {
                var issue = outcome.issues().get(index);
                logger.debug(
                    "calculator.route.issue index={} code={} severity={} blocking={} message={} payload={}",
                    index,
                    issue.code(),
                    issue.severity(),
                    issue.blocking(),
                    issue.messageKey(),
                    issue.payload()
                );
            }
            if (outcome.issues().size() > limit) {
                logger.debug("calculator.route.issue omitted={}", outcome.issues().size() - limit);
            }
        }
    }

    private record RouteResult(
        @Nullable RoutingPlan plan,
        @Nullable RoutingOutcome outcome,
        CalculatorBuildDebugInfo debugInfo
    ) {
        private RouteResult withException(RuntimeException exception) {
            return new RouteResult(
                plan,
                outcome,
                new CalculatorBuildDebugInfo(
                    debugInfo.target(),
                    debugInfo.times(),
                    debugInfo.routeMode(),
                    debugInfo.catalogSize(),
                    debugInfo.fastPreview(),
                    debugInfo.fallbackUsed(),
                    debugInfo.routingStatus(),
                    debugInfo.routingIssueKeys(),
                    debugInfo.routePlanActions(),
                    debugInfo.routingElapsedMs(),
                    debugInfo.expandedNodes(),
                    debugInfo.generatedCandidates(),
                    debugInfo.acceptedCandidates(),
                    debugInfo.budgetExhausted(),
                    debugInfo.budgetStopReason(),
                    debugInfo.budgetCulpritRecipe(),
                    debugInfo.budgetCulpritInput(),
                    debugInfo.budgetCulpritDepth(),
                    debugInfo.budgetCulpritRemaining(),
                    debugInfo.budgetCulpritActiveCycle(),
                    debugInfo.graphNodes(),
                    debugInfo.graphEdges(),
                    exception.getClass().getSimpleName() + ":" + exception.getMessage()
                )
            );
        }
    }

    /** Visible for the testmod plan-dump hook so it can reuse the exact budget sizing. */
    public static RoutingBudget routingBudget(
        RecipeEntry<?> target,
        int times,
        RecipePathSettings.Catalog catalog,
        RoutingService service,
        PlannerMode plannerMode,
        RouteMode routeMode
    ) {
        if (routeMode == RouteMode.execution) {
            return plannerMode == PlannerMode.standard ? RoutingBudget.standardDefault() : RoutingBudget.assuredDefault();
        }

        // Calculator modes size the budget from the actual request cone: one
        // backward-reachability pass over the full graph (cached on the service)
        // replaces the old projection-score proxy, so deep/large targets get
        // proportionally more room without hand-tuned score tables.
        int targetIndex = service.catalog().indexOf(target);
        ConeStats cone = targetIndex >= 0
            ? service.coneStats(targetIndex, allRecipesAllowed(service.catalog().size()))
            : ConeStats.empty;
        int roots = Math.max(1, mergedInputCount(target, service));
        RoutingBudget budget = RoutingBudget.fromConeStats(cone, plannerMode, roots);
        if (logger.isDebugEnabled()) {
            logger.debug(
                "calculator.route.budget target={} mode={} coneNodes={} coneResources={} coneLoops={} coneCandidates={} roots={} expanded={} generated={} wall={} rootExpanded={} rootGenerated={}",
                describeRecipe(target),
                plannerMode,
                cone.nodeCount(),
                cone.resourceCount(),
                cone.loopSccCount(),
                cone.fatInputCandidateEstimate(),
                roots,
                budget.maxExpandedNodes(),
                budget.maxGeneratedCandidates(),
                budget.maxWallTimeMs(),
                budget.maxRootInputExpandedNodes(),
                budget.maxRootInputGeneratedCandidates()
            );
        }
        return budget;
    }

    private static BitSet allRecipesAllowed(int catalogSize) {
        BitSet all = new BitSet(catalogSize);
        all.set(0, Math.max(0, catalogSize));
        return all;
    }

    /** Visible for the testmod plan-dump hook so it can reuse the exact request shaping. */
    public static int maxProducerCandidates(RoutingBudget budget, PlannerMode plannerMode) {
        if (plannerMode != PlannerMode.standard) {
            return 16;
        }
        if (budget.maxExpandedNodes() >= 6_000) {
            return 16;
        }
        if (budget.maxExpandedNodes() >= 3_000) {
            return 12;
        }
        return 8;
    }

    private static int mergedInputCount(RecipeEntry<?> target, RoutingService service) {
        try {
            int index = service.catalog().indexOf(target);
            if (index < 0) {
                return -1;
            }
            return service.catalog().nodeAt(index).inputCount();
        } catch (RuntimeException | LinkageError exception) {
            logger.debug(
                "calculator.projectionComplexity.mergedInputUnavailable target={} cause={}",
                describeRecipe(target),
                exception.toString(),
                exception
            );
            return -1;
        }
    }

    private static ProcessGraph fromProjectedRecipe(RecipeEntry<?> target, int times) {
        return fromProjectedRecipe(target, times, new FallbackDebugTrace());
    }

    private static ProcessGraph fromProjectedRecipe(RecipeEntry<?> target, int times, FallbackDebugTrace trace) {
        RecipePathSettings.Catalog catalog = RecipePathSettings.calculatorCatalog(target);
        ProcessGraph graph = new ProcessGraph(
            projectedGraphExpectedNodes(catalog.recipes().size()),
            projectedGraphExpectedEdges(catalog.recipes().size())
        );
        FallbackProjectionIndex projectionIndex = cachedFallbackProjectionIndex(catalog);
        BuildContext buildContext = new BuildContext();
        ArrayList<ProjectedBuildFrame> stack = new ArrayList<>();
        HashSet<RecipeEntry<?>> active = new HashSet<>();
        int[] nodeCounter = new int[]{0};
        trace.add("fallback.start target=" + describeRecipe(target) + " times=" + times + " catalog=" + catalog.recipes().size());
        stack.add(ProjectedBuildFrame.enter(target, times, null));
        while (!stack.isEmpty()) {
            ProjectedBuildFrame frame = stack.remove(stack.size() - 1);
            if (frame.phase() == ProjectedBuildPhase.exit) {
                active.remove(frame.target());
                if (frame.parent() != null && frame.nodeId() != null) {
                    frame.parent().connectProducer(frame.nodeId());
                }
                continue;
            }
            if (!active.add(frame.target())) {
                trace.add("fallback.cycle target=" + describeRecipe(frame.target()) + " action=source");
                if (frame.parent() != null) {
                    frame.parent().connectSource();
                }
                continue;
            }

            ProjectedNodeBuild nodeBuild = createProjectedNodeBuild(graph, projectionIndex, frame.target(), frame.times(), buildContext, nodeCounter);
            if (nodeBuild == null) {
                trace.add(
                    "fallback.nodeUnavailable target=" + describeRecipe(frame.target())
                        + " times=" + frame.times()
                        + " reason=" + projectionIndex.unavailableReason(frame.target(), frame.times())
                );
                active.remove(frame.target());
                if (frame.parent() != null) {
                    frame.parent().connectSource();
                }
                continue;
            }
            trace.add(
                "fallback.expand target=" + describeRecipe(frame.target())
                    + " times=" + frame.times()
                    + " inputs=" + nodeBuild.projected().inputs().size()
                    + " outputs=" + nodeBuild.projected().outputs().size()
            );
            if (frame.parent() == null) {
                graph.preferredRootNodeId(nodeBuild.nodeId());
            }

            ProjectedBuildFrame exit = ProjectedBuildFrame.exit(frame.target(), nodeBuild.nodeId(), frame.parent());
            stack.add(exit);
            for (int inputIndex = nodeBuild.projected().inputs().size() - 1; inputIndex >= 0; inputIndex--) {
                InputContent input = nodeBuild.projected().inputs().get(inputIndex);
                if (input.isEmpty()) {
                    continue;
                }
                long required = Math.multiplyExact(input.amount(), frame.times());
                ProducerMatch producer = projectionIndex.findProducer(frame.target(), input, active);
                if (producer == null) {
                    MaterialSlot sourceSlot = inputSlot(input, required);
                    trace.add(
                        "fallback.inputSource parent=" + describeRecipe(frame.target())
                            + " input=" + sourceSlot.materialName()
                            + " required=" + required
                            + " reason=noProjectedProducer"
                    );
                    String sourceNodeId = materialSourceNodeId(nodeBuild.nodeId(), 0, inputIndex, sourceSlot);
                    graph.addNode(materialSourceNode(sourceNodeId, sourceSlot));
                    graph.addEdge(new ProcessEdge(sourceNodeId, nodeBuild.nodeId(), sourceSlot.materialName(), sourceSlot.amount(), sourceSlot.type()));
                    continue;
                }
                PendingProjectedEdge edge = new PendingProjectedEdge(
                    graph,
                    nodeBuild.nodeId(),
                    inputIndex,
                    inputSlot(input, required)
                );
                int producerTimes = clampedPositiveInt(ceilDiv(required, producer.amountPerRun()));
                trace.add(
                    "fallback.producer parent=" + describeRecipe(frame.target())
                        + " input=" + edge.slot.materialName()
                        + " required=" + required
                        + " producer=" + describeRecipe(producer.recipe())
                        + " amountPerRun=" + producer.amountPerRun()
                        + " producerTimes=" + producerTimes
                );
                stack.add(ProjectedBuildFrame.enter(producer.recipe(), producerTimes, edge));
            }
        }
        if (graph.nodeCount() == 0) {
            trace.add("fallback.empty action=singleProjectedRecipe");
            return fromSingleProjectedRecipe(target, times, trace);
        }
        trace.add("fallback.finish nodes=" + graph.nodeCount() + " edges=" + graph.edgeCount());
        return graph;
    }

    private static FallbackProjectionIndex cachedFallbackProjectionIndex(RecipePathSettings.Catalog catalog) {
        FallbackProjectionIndexCacheKey key = new FallbackProjectionIndexCacheKey(catalog.fingerprint(), catalog.databaseId());
        synchronized (fallbackProjectionIndexCacheLock) {
            FallbackProjectionIndexCacheEntry cached = fallbackProjectionIndexCache.remove(key);
            if (cached != null) {
                FallbackProjectionIndexCacheEntry touched = cached.touch();
                fallbackProjectionIndexCache.put(key, touched);
                return touched.index();
            }
        }
        FallbackProjectionIndex built = FallbackProjectionIndex.build(catalog.recipes(), catalog.databaseId());
        FallbackProjectionIndexCacheEntry entry = new FallbackProjectionIndexCacheEntry(built, System.nanoTime());
        synchronized (fallbackProjectionIndexCacheLock) {
            FallbackProjectionIndexCacheEntry existing = fallbackProjectionIndexCache.remove(key);
            if (existing != null) {
                FallbackProjectionIndexCacheEntry touched = existing.touch();
                fallbackProjectionIndexCache.put(key, touched);
                return touched.index();
            }
            fallbackProjectionIndexCache.put(key, entry);
            trimFallbackProjectionIndexCache();
            return built;
        }
    }

    private static void trimFallbackProjectionIndexCache() {
        while (fallbackProjectionIndexCache.size() > maxFallbackProjectionIndexCacheSize) {
            FallbackProjectionIndexCacheKey oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (Map.Entry<FallbackProjectionIndexCacheKey, FallbackProjectionIndexCacheEntry> entry : fallbackProjectionIndexCache.entrySet()) {
                if (entry.getValue().lastAccessNanos() < oldestAccess) {
                    oldestAccess = entry.getValue().lastAccessNanos();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            fallbackProjectionIndexCache.remove(oldest);
        }
    }

    private static @Nullable ProjectedNodeBuild createProjectedNodeBuild(
        ProcessGraph graph,
        FallbackProjectionIndex projectionIndex,
        RecipeEntry<?> target,
        int times,
        BuildContext buildContext,
        int[] nodeCounter
    ) {
        RecipeProjection projection = projectionIndex.projection(target);
        if (projection == null) {
            return null;
        }
        ProjectedRecipe projected;
        try {
            projected = projectionIndex.project(target, times);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
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
        String nodeId = recipeNodeId(target, nodeCounter[0]++);
        MachineDisplay machine = buildContext.machineDisplay(target);
        graph.addNode(new ProcessNode(
            nodeId,
            recipeName(target, outputs),
            machine.name(),
            inputs,
            outputs,
            durationTicks(outputs),
            machine.options(),
            target
        ));
        return new ProjectedNodeBuild(nodeId, projected);
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
        ImmutableList<InputContent> projectedInputs = projected.inputs();
        ImmutableList<OutputContent> projectedOutputs = projected.outputs();
        List<MaterialSlot> inputs = new ArrayList<>(projectedInputs.size());
        for (InputContent input : projectedInputs) {
            if (!input.isEmpty()) {
                inputs.add(inputSlot(input, Math.multiplyExact(input.amount(), times)));
            }
        }
        List<MaterialSlot> outputs = new ArrayList<>(projectedOutputs.size());
        for (OutputContent output : projectedOutputs) {
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

    private static void connectInputs(
        ProcessGraph graph,
        String targetNodeId,
        int actionId,
        ImmutableList<PlanAction.InputBinding> inputs,
        ProducedPool produced,
        PendingPool pending,
        RouteMode routeMode
    ) {
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            PlanAction.InputBinding input = inputs.get(inputIndex);
            long remaining = produced.connect(graph, targetNodeId, input);
            if (remaining > 0) {
                remaining = pending.connect(graph, targetNodeId, input, remaining);
            }
            if (remaining > 0) {
                MaterialSlot sourceSlot = inputSlot(input.input(), remaining);
                String sourceNodeId = materialSourceNodeId(targetNodeId, actionId, inputIndex, sourceSlot);
                graph.addNode(materialSourceNode(sourceNodeId, sourceSlot, unresolvedSourceMachine(routeMode)));
                graph.addEdge(new ProcessEdge(
                    sourceNodeId,
                    targetNodeId,
                    sourceSlot.materialName(),
                    sourceSlot.amount(),
                    sourceSlot.type()
                ));
            }
        }
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

    private static MaterialSlot outputSlot(ContentResource resource, long amount) {
        return outputSlot(outputContent(resource, amount), amount);
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

    private static OutputContent outputContent(ContentResource resource, long amount) {
        return switch (resource) {
            case ContentStack<?, ?> stack -> outputContent(stack, amount);
            case ContentValue<?> value -> outputContent(value, amount);
        };
    }

    private static <T, S> OutputContent outputContent(ContentStack<T, S> stack, long amount) {
        return OutputContent.fromStack(stack.type(), stack.value(), amount, RecipeContent.MAX_CHANCE);
    }

    private static <T> OutputContent outputContent(ContentValue<T> value, long amount) {
        return OutputContent.fromValue(ContentValue.fromValue(value.type(), value.type().fromValue(amount)));
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

    private static @Nullable ContentResource contentResource(RecipeContent content) {
        ContentStack<?, ?> stack = stackContent(content);
        if (stack.notEmpty()) {
            return stack;
        }
        if (content instanceof ValueContent<?> value) {
            return value.content();
        }
        return null;
    }

    private static @Nullable ItemStack itemStack(ContentStack<?, ?> stack, long amount) {
        if (stack.isEmpty() || !stack.isItem()) {
            return null;
        }
        ContentStack<Item, ItemStack> item = stack.cast();
        ItemStack copy = item.value().copy();
        copy.setCount(clampedPositiveInt(amount));
        return copy;
    }

    private static @Nullable FluidStack fluidStack(ContentStack<?, ?> stack, long amount) {
        if (stack.isEmpty() || !stack.isFluid()) {
            return null;
        }
        ContentStack<Fluid, FluidStack> fluid = stack.cast();
        return fluid.value().copyWithAmount(clampedPositiveInt(amount));
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

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static int clampedNonNegativeInt(long amount) {
        if (amount >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) amount);
    }

    private static int expectedMapCapacity(int expectedSize) {
        if (expectedSize <= 0) {
            return 0;
        }
        if (expectedSize >= 1 << 29) {
            return 1 << 30;
        }
        return (int) (expectedSize / 0.75f) + 1;
    }

    private static int projectedGraphExpectedNodes(int catalogSize) {
        return clampInt(catalogSize * 2, 8, 512);
    }

    private static int projectedGraphExpectedEdges(int catalogSize) {
        return clampInt(catalogSize, 4, 256);
    }

    private static long ceilDiv(long left, long right) {
        return left <= 0 ? 0 : (left + Math.max(1L, right) - 1L) / Math.max(1L, right);
    }

    private static String recipeNodeId(RecipeEntry<?> recipe, int index) {
        ResourceLocation id = recipe.id();
        String base = id == null ? recipe.systemId() + "/" + Integer.toHexString(recipe.hashCode()) : id.toString();
        return "recipe/" + base.replace(':', '/') + "/" + index;
    }

    private static String materialSourceNodeId(String targetNodeId, int actionId, int inputIndex, MaterialSlot slot) {
        return targetNodeId + "/material/" + actionId + "/" + inputIndex + "/" + Integer.toHexString(slot.materialName().hashCode());
    }

    private static String pendingNodeId(InputContent input, int actionId) {
        return "pending/" + actionId + "/" + Integer.toHexString(input.hashCode());
    }

    private static String unresolvedSourceMachine(RouteMode routeMode) {
        return switch (routeMode) {
            case calculator -> materialSourceMachine;
            case execution -> pendingSourceMachine;
        };
    }

    private static final class ProducedSlot {
        private final String nodeId;
        private final ContentResource resource;
        private long remaining;
        private final String materialName;
        private final MaterialCategory category;

        private ProducedSlot(String nodeId, ContentResource resource, long amount, String materialName, MaterialCategory category) {
            this.nodeId = nodeId;
            this.resource = resource;
            this.remaining = amount;
            this.materialName = materialName;
            this.category = category;
        }
    }

    private static final class PendingSlot {
        private final String nodeId;
        private final MaterialSlot slot;
        private long remaining;

        private PendingSlot(String nodeId, MaterialSlot slot, long amount) {
            this.nodeId = nodeId;
            this.slot = slot;
            this.remaining = amount;
        }
    }

    private static final class ProducedPool {
        private final List<ProducedSlot> all;
        private final Map<String, List<ProducedSlot>> byResourceKey;

        private ProducedPool(int expectedSlots) {
            this.all = new ArrayList<>(Math.max(0, expectedSlots));
            this.byResourceKey = new HashMap<>(expectedMapCapacity(expectedSlots));
        }

        private void add(ProducedSlot slot) {
            all.add(slot);
            byResourceKey.computeIfAbsent(resourceKey(slot.resource), ignored -> new ArrayList<>()).add(slot);
        }

        private long connect(ProcessGraph graph, String targetNodeId, PlanAction.InputBinding input) {
            ContentResource directResource = directMergeKeyResource(input.input());
            if (directResource != null) {
                return connectCandidates(
                    graph,
                    targetNodeId,
                    input,
                    byResourceKey.getOrDefault(resourceKey(directResource), List.of())
                );
            }
            return connectCandidates(graph, targetNodeId, input, all);
        }

        private static long connectCandidates(
            ProcessGraph graph,
            String targetNodeId,
            PlanAction.InputBinding input,
            List<ProducedSlot> candidates
        ) {
            long remaining = input.amount();
            for (ProducedSlot candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                if (candidate.remaining <= 0 || !input.input().satisfy(candidate.resource)) {
                    continue;
                }
                long amount = Math.min(remaining, candidate.remaining);
                graph.addEdge(new ProcessEdge(
                    candidate.nodeId,
                    targetNodeId,
                    candidate.materialName,
                    clampedPositiveInt(amount),
                    candidate.category
                ));
                candidate.remaining -= amount;
                remaining -= amount;
            }
            return remaining;
        }
    }

    private static final class PendingPool {
        private final List<PendingSlot> all;
        private final Map<String, List<PendingSlot>> byDirectResourceKey;

        private PendingPool(int expectedSlots) {
            this.all = new ArrayList<>(Math.max(0, expectedSlots));
            this.byDirectResourceKey = new HashMap<>(expectedMapCapacity(expectedSlots));
        }

        private void add(PendingSlot slot) {
            all.add(slot);
            ContentResource directResource = directMergeKeyResource(slot.slot.content());
            if (directResource != null) {
                byDirectResourceKey.computeIfAbsent(resourceKey(directResource), ignored -> new ArrayList<>()).add(slot);
            }
        }

        private long connect(ProcessGraph graph, String targetNodeId, PlanAction.InputBinding input, long amount) {
            ContentResource directResource = directMergeKeyResource(input.input());
            if (directResource != null) {
                return connectCandidates(
                    graph,
                    targetNodeId,
                    input,
                    amount,
                    byDirectResourceKey.getOrDefault(resourceKey(directResource), List.of())
                );
            }
            return connectCandidates(graph, targetNodeId, input, amount, all);
        }

        private static long connectCandidates(
            ProcessGraph graph,
            String targetNodeId,
            PlanAction.InputBinding input,
            long amount,
            List<PendingSlot> candidates
        ) {
            long remaining = amount;
            for (PendingSlot candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                if (candidate.remaining <= 0 || !input.input().mergeable(candidate.slot.content())) {
                    continue;
                }
                long connected = Math.min(remaining, candidate.remaining);
                graph.addEdge(new ProcessEdge(
                    candidate.nodeId,
                    targetNodeId,
                    candidate.slot.materialName(),
                    clampedPositiveInt(connected),
                    candidate.slot.type()
                ));
                candidate.remaining -= connected;
                remaining -= connected;
            }
            return remaining;
        }
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

    private record PlanGraphSize(
        int nodes,
        int edges,
        int outputSlots,
        int pendingInputs
    ) {
    }

    private record ProducerMatch(
        RecipeEntry<?> recipe,
        long amountPerRun
    ) {}

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

    private record FallbackProjectionIndexCacheKey(
        long catalogFingerprint,
        String databaseId
    ) {
    }

    private record FallbackProjectionIndexCacheEntry(
        FallbackProjectionIndex index,
        long lastAccessNanos
    ) {
        private FallbackProjectionIndexCacheEntry touch() {
            return new FallbackProjectionIndexCacheEntry(index, System.nanoTime());
        }
    }

    private enum ProjectedBuildPhase {
        enter,
        exit
    }

    private record ProjectedNodeBuild(
        String nodeId,
        ProjectedRecipe projected
    ) {
    }

    private static final class PendingProjectedEdge {
        private final ProcessGraph graph;
        private final String targetNodeId;
        private final int inputIndex;
        private final MaterialSlot slot;

        private PendingProjectedEdge(ProcessGraph graph, String targetNodeId, int inputIndex, MaterialSlot slot) {
            this.graph = graph;
            this.targetNodeId = targetNodeId;
            this.inputIndex = inputIndex;
            this.slot = slot;
        }

        private void connectProducer(String producerNodeId) {
            graph.addEdge(new ProcessEdge(
                producerNodeId,
                targetNodeId,
                slot.materialName(),
                slot.amount(),
                slot.type()
            ));
        }

        private void connectSource() {
            String sourceNodeId = materialSourceNodeId(targetNodeId, 0, inputIndex, slot);
            graph.addNode(materialSourceNode(sourceNodeId, slot));
            graph.addEdge(new ProcessEdge(sourceNodeId, targetNodeId, slot.materialName(), slot.amount(), slot.type()));
        }
    }

    private record ProjectedBuildFrame(
        ProjectedBuildPhase phase,
        RecipeEntry<?> target,
        int times,
        @Nullable String nodeId,
        @Nullable PendingProjectedEdge parent
    ) {
        private static ProjectedBuildFrame enter(RecipeEntry<?> target, int times, @Nullable PendingProjectedEdge parent) {
            return new ProjectedBuildFrame(ProjectedBuildPhase.enter, target, times, null, parent);
        }

        private static ProjectedBuildFrame exit(RecipeEntry<?> target, String nodeId, @Nullable PendingProjectedEdge parent) {
            return new ProjectedBuildFrame(ProjectedBuildPhase.exit, target, 1, nodeId, parent);
        }
    }

    private static final class FallbackProjectionIndex {
        private final ProjectionEngine projectionEngine = new ProjectionEngine();
        private final String databaseId;
        private final ImmutableList<RecipeEntry<?>> candidates;
        private final Map<RecipeEntry<?>, RecipeProjection> projections = new HashMap<>();
        private final Map<RecipeEntry<?>, ProjectedRecipe> baseProjects = new HashMap<>();
        private final Map<String, ArrayList<ProjectedOutput>> outputsByResource = new HashMap<>();
        private final Map<String, ArrayList<ProjectedOutput>> outputsByKind = new HashMap<>();
        private final Map<RecipeEntry<?>, List<ProjectedOutput>> projectedOutputsByRecipe = new HashMap<>();
        private final Set<DatabaseRecipeRef> skippedRefs = new HashSet<>();
        private final Set<RecipeEntry<?>> skippedRecipes = new HashSet<>();
        private final Map<RecipeEntry<?>, String> unavailableReasons = new HashMap<>();
        private final List<ProjectedOutput> outputs = new ArrayList<>();
        private final Map<ContentResource, String> resourceKeys = new IdentityHashMap<>();
        private long failureRegistryVersion = Long.MIN_VALUE;
        private int lazyScanIndex;
        private boolean scannedAllOutputs;

        private FallbackProjectionIndex(ImmutableList<RecipeEntry<?>> candidates, String databaseId) {
            this.databaseId = databaseId == null ? "" : databaseId;
            this.candidates = candidates == null ? Lists.immutable.empty() : candidates;
            refreshFailureSkips();
        }

        private static FallbackProjectionIndex build(ImmutableList<RecipeEntry<?>> candidates, String databaseId) {
            return new FallbackProjectionIndex(candidates, databaseId);
        }

        private @Nullable RecipeProjection projection(RecipeEntry<?> recipe) {
            if (shouldSkip(recipe)) {
                return null;
            }
            RecipeProjection projection = projections.get(recipe);
            if (projection != null) {
                return projection;
            }
            ResolveResult result;
            try {
                ArrayList<RecipeDatabaseFailureRegistry.Failure> capturedFailures = new ArrayList<>(1);
                result = RecipeDatabaseFailureCapture.capture(
                    databaseId,
                    recipeRef(recipe),
                    recipeClassName(recipe),
                    capturedFailures::add,
                    recipe::resolveResult
                );
                if (!capturedFailures.isEmpty()) {
                    skippedRecipes.add(recipe);
                    recordFailures(capturedFailures);
                    unavailableReasons.put(recipe, "resolveFailureCaptured");
                    return null;
                }
            } catch (RuntimeException | LinkageError exception) {
                recordFailure(recipe, exception);
                unavailableReasons.put(
                    recipe,
                    "resolveException:" + exception.getClass().getSimpleName() + ":" + nullToEmpty(exception.getMessage())
                );
                return null;
            }
            projection = result.projection();
            if (projection != null) {
                projections.put(recipe, projection);
            } else {
                if (cacheableUnsupported(result.error())) {
                    recordUnsupported(recipe, result.error());
                }
                unavailableReasons.put(recipe, "resolveError:" + result.error());
            }
            return projection;
        }

        private String unavailableReason(RecipeEntry<?> recipe, int times) {
            String cached = unavailableReasons.get(recipe);
            if (cached != null) {
                return cached;
            }
            RecipeProjection projection = projection(recipe);
            if (projection == null) {
                return unavailableReasons.getOrDefault(recipe, "projectionUnavailable");
            }
            try {
                projectionEngine.project(calculatorProjectionContext(recipe, times), projection);
                return "unknown";
            } catch (RuntimeException | LinkageError exception) {
                String reason = "projectException:" + exception.getClass().getSimpleName() + ":" + nullToEmpty(exception.getMessage());
                unavailableReasons.put(recipe, reason);
                return reason;
            }
        }

        private ProjectedRecipe project(RecipeEntry<?> recipe, int times) {
            RecipeProjection projection = projection(recipe);
            if (projection == null) {
                throw new IllegalStateException("Recipe projection is unavailable: " + recipe);
            }
            return projectionEngine.project(calculatorProjectionContext(recipe, times), projection);
        }

        private @Nullable ProjectedRecipe baseProject(RecipeEntry<?> recipe) {
            if (shouldSkip(recipe)) {
                return null;
            }
            ProjectedRecipe cached = baseProjects.get(recipe);
            if (cached != null) {
                return cached;
            }
            RecipeProjection projection = projection(recipe);
            if (projection == null) {
                return null;
            }
            try {
                cached = projectionEngine.project(calculatorProjectionContext(recipe, 1), projection);
            } catch (RuntimeException | LinkageError exception) {
                unavailableReasons.put(
                    recipe,
                    "projectException:" + exception.getClass().getSimpleName() + ":" + nullToEmpty(exception.getMessage())
                );
                return null;
            }
            baseProjects.put(recipe, cached);
            return cached;
        }

        private @Nullable ProducerMatch findProducer(RecipeEntry<?> target, InputContent input, Set<RecipeEntry<?>> activePath) {
            ProducerMatch indexed = findIndexedProducer(target, input, activePath);
            if (indexed != null || scannedAllOutputs) {
                return indexed;
            }
            return scanRemainingCandidates(target, input, activePath);
        }

        private @Nullable ProducerMatch findIndexedProducer(RecipeEntry<?> target, InputContent input, Set<RecipeEntry<?>> activePath) {
            ContentResource direct = directInputResource(input);
            List<ProjectedOutput> candidates = direct == null ? outputsForEnumerableInput(input) : outputsForResource(direct);
            for (ProjectedOutput output : candidates) {
                if (canUseProducer(output.recipe(), target, activePath) && input.satisfy(output.resource())) {
                    return new ProducerMatch(output.recipe(), output.amount());
                }
            }
            return null;
        }

        private List<ProjectedOutput> outputsForEnumerableInput(InputContent input) {
            if (!input.isEnumerable()) {
                return scannedAllOutputs ? outputs : List.of();
            }
            ArrayList<ProjectedOutput> out = null;
            HashSet<String> seenKeys = null;
            for (ContentVariant variant : input.enumerate()) {
                ContentResource resource = variantResource(variant);
                String key = resource == null ? variantKindKey(variant) : cachedResourceKey(resource);
                if (key == null) {
                    continue;
                }
                if (seenKeys == null) {
                    seenKeys = new HashSet<>();
                }
                if (!seenKeys.add(key)) {
                    continue;
                }
                List<ProjectedOutput> bucket = resource == null ? outputsForKind(key) : outputsForResource(resource);
                if (bucket.isEmpty()) {
                    continue;
                }
                if (out == null) {
                    out = new ArrayList<>(bucket);
                } else {
                    out.addAll(bucket);
                }
            }
            if (out == null) {
                return scannedAllOutputs ? outputs : List.of();
            }
            return out;
        }

        private List<ProjectedOutput> outputsForResource(ContentResource resource) {
            String key = cachedResourceKey(resource);
            ArrayList<ProjectedOutput> cached = outputsByResource.get(key);
            return cached == null ? List.of() : cached;
        }

        private List<ProjectedOutput> projectedOutputs(RecipeEntry<?> candidate) {
            List<ProjectedOutput> cached = projectedOutputsByRecipe.get(candidate);
            if (cached != null) {
                return cached;
            }
            ProjectedRecipe projected = baseProject(candidate);
            if (projected == null) {
                projectedOutputsByRecipe.put(candidate, List.of());
                return List.of();
            }
            ArrayList<ProjectedOutput> out = new ArrayList<>(projected.outputs().size());
            for (OutputContent output : projected.outputs()) {
                ContentResource resource = contentResource(output);
                if (resource == null) {
                    continue;
                }
                ProjectedOutput projectedOutput = new ProjectedOutput(candidate, resource, Math.max(1L, output.amount()));
                out.add(projectedOutput);
                outputs.add(projectedOutput);
                outputsByResource.computeIfAbsent(cachedResourceKey(resource), ignored -> new ArrayList<>()).add(projectedOutput);
                outputsByKind.computeIfAbsent(resourceKindKey(resource), ignored -> new ArrayList<>()).add(projectedOutput);
            }
            List<ProjectedOutput> snapshot = out.isEmpty() ? List.of() : List.copyOf(out);
            projectedOutputsByRecipe.put(candidate, snapshot);
            return snapshot;
        }

        private String cachedResourceKey(ContentResource resource) {
            String key = resourceKeys.get(resource);
            if (key == null) {
                key = resourceKey(resource);
                resourceKeys.put(resource, key);
            }
            return key;
        }

        private List<ProjectedOutput> outputsForKind(String key) {
            ArrayList<ProjectedOutput> cached = outputsByKind.get(key);
            return cached == null ? List.of() : cached;
        }

        private @Nullable ProducerMatch scanRemainingCandidates(RecipeEntry<?> target, InputContent input, Set<RecipeEntry<?>> activePath) {
            while (lazyScanIndex < candidates.size()) {
                RecipeEntry<?> candidate = candidates.get(lazyScanIndex++);
                if (shouldSkip(candidate) || !canUseProducer(candidate, target, activePath)) {
                    continue;
                }
                List<ProjectedOutput> candidateOutputs = projectedOutputs(candidate);
                for (ProjectedOutput output : candidateOutputs) {
                    if (input.satisfy(output.resource())) {
                        return new ProducerMatch(output.recipe(), output.amount());
                    }
                }
            }
            scannedAllOutputs = true;
            return null;
        }

        private static boolean canUseProducer(
            RecipeEntry<?> producer,
            RecipeEntry<?> target,
            Set<RecipeEntry<?>> activePath
        ) {
            return !producer.equals(target) && !activePath.contains(producer);
        }

        private boolean shouldSkip(RecipeEntry<?> recipe) {
            if (skippedRecipes.contains(recipe)) {
                return true;
            }
            DatabaseRecipeRef ref = recipeRef(recipe);
            if (ref == null) {
                return false;
            }
            refreshFailureSkips();
            return skippedRefs.contains(ref);
        }

        private void refreshFailureSkips() {
            RecipeDatabaseFailureRegistry registry = RecipeDatabaseFailureRegistry.shared();
            long version = registry.version();
            if (version == failureRegistryVersion) {
                return;
            }
            skippedRefs.clear();
            skippedRefs.addAll(registry.failedRecipeRefs());
            skippedRefs.addAll(registry.unsupportedRecipeRefs());
            failureRegistryVersion = version;
        }

        private void recordFailure(RecipeEntry<?> recipe, Throwable throwable) {
            DatabaseRecipeRef ref = recipeRef(recipe);
            if (ref == null) {
                skippedRecipes.add(recipe);
                logger.warn(
                    "Skipped calculator fallback recipe after resolver failure: database={}, recipe={}, recipeClass={}, exception={}, message={}",
                    databaseId,
                    describeRecipe(recipe),
                    recipeClassName(recipe),
                    throwable.getClass().getName(),
                    throwable.getMessage(),
                    throwable
                );
                return;
            }
            skippedRecipes.add(recipe);
            RecipeDatabaseFailureRegistry.shared().recordFailures(List.of(
                RecipeDatabaseFailureRegistry.Failure.from(ref, databaseId, recipeClassName(recipe), throwable)
            ));
            skippedRefs.add(ref);
            failureRegistryVersion = RecipeDatabaseFailureRegistry.shared().version();
            logger.warn(
                "Skipped calculator fallback recipe after resolver failure: database={}, system={}, recipe={}, recipeClass={}, exception={}, message={}",
                databaseId,
                ref.system(),
                ref.recipe(),
                recipeClassName(recipe),
                throwable.getClass().getName(),
                throwable.getMessage(),
                throwable
            );
        }

        private void recordFailures(List<RecipeDatabaseFailureRegistry.Failure> failures) {
            RecipeDatabaseFailureRegistry.shared().recordFailures(failures);
            for (RecipeDatabaseFailureRegistry.Failure failure : failures) {
                skippedRefs.add(failure.ref());
                logger.warn(
                    "Skipped calculator fallback recipe after captured resolver failure: database={}, system={}, recipe={}, recipeClass={}, exception={}, message={}",
                    failure.database(),
                    failure.ref().system(),
                    failure.ref().recipe(),
                    failure.recipeClass(),
                    failure.exception(),
                    failure.message(),
                    failure.cause()
                );
            }
            failureRegistryVersion = RecipeDatabaseFailureRegistry.shared().version();
        }

        private void recordUnsupported(RecipeEntry<?> recipe, ResolveError error) {
            DatabaseRecipeRef ref = recipeRef(recipe);
            if (ref == null) {
                skippedRecipes.add(recipe);
                return;
            }
            skippedRecipes.add(recipe);
            RecipeDatabaseFailureRegistry.shared().recordUnsupported(List.of(
                new RecipeDatabaseFailureRegistry.Unsupported(
                    ref,
                    databaseId,
                    recipeClassName(recipe),
                    error == null ? "" : error.name()
                )
            ));
            skippedRefs.add(ref);
            failureRegistryVersion = RecipeDatabaseFailureRegistry.shared().version();
        }
    }

    private record ProjectedOutput(
        RecipeEntry<?> recipe,
        ContentResource resource,
        long amount
    ) {
    }

    private static @Nullable ContentResource directInputResource(InputContent input) {
        if (input instanceof StackContent<?, ?> stack) {
            return stack.content();
        }
        if (input instanceof ValueContent<?> value) {
            return value.content();
        }
        return null;
    }

    private static @Nullable ContentResource directMergeKeyResource(RecipeContent content) {
        if (content instanceof StackContent<?, ?> stack && stack.matchStrategy().equals(MatchStrategy.identityAndComponent)) {
            return stack.content();
        }
        if (content instanceof ValueContent<?> value) {
            return value.content();
        }
        return null;
    }

    private static @Nullable ContentResource variantResource(ContentVariant variant) {
        return switch (variant) {
            case ContentResource resource -> resource;
            default -> null;
        };
    }

    private static @Nullable String variantKindKey(ContentVariant variant) {
        return switch (variant) {
            case ContentKind<?> kind when kind.notEmpty() -> contentKindKey(kind);
            default -> null;
        };
    }

    private static String resourceKey(ContentResource resource) {
        return switch (resource.normalize()) {
            case ContentStack<?, ?> stack -> "stack:" + contentStackKey(stack);
            case ContentValue<?> value -> "value:" + value.type().typeId();
        };
    }

    private static String resourceKindKey(ContentResource resource) {
        return switch (resource.normalize()) {
            case ContentStack<?, ?> stack -> contentStackKindKey(stack);
            case ContentValue<?> value -> "value:" + value.type().typeId();
        };
    }

    private static String contentKindKey(ContentKind<?> kind) {
        return contentKindKeyTyped(kind.cast());
    }

    private static <T> String contentKindKeyTyped(ContentKind<T> kind) {
        return "kind:" + kind.type().typeId() + ":" + kind.type().hash(kind.value());
    }

    private static String contentStackKey(ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return contentStackKeyTyped(stack.cast());
    }

    private static <T, S> String contentStackKeyTyped(ContentStack<T, S> stack) {
        return stack.type().typeId()
            + ":" + stack.contentType().typeId()
            + ":" + stack.contentType().hash(stack.content())
            + ":" + stack.variantType().hashIdentity(stack.value())
            + ":" + stack.componentsPatch();
    }

    private static String contentStackKindKey(ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return contentStackKindKeyTyped(stack.cast());
    }

    private static <T, S> String contentStackKindKeyTyped(ContentStack<T, S> stack) {
        return "kind:" + stack.contentType().typeId() + ":" + stack.contentType().hash(stack.content());
    }
}
