package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.HeuristicPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ExplorePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRuleResult;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.UnresolvedInputContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.UnresolvedInputRule;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.service.TagResolvePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingVariantKeys;
import dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard;
import dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Responsiveness-first planner.
 *
 * <p>This is the default planner used by {@link dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode#standard}.
 * It performs a greedy depth-first search over the recipe graph, resolving each
 * input demand by:
 * <ol>
 *     <li>Consuming as much as possible from existing stock and byproducts.</li>
 *     <li>Evaluating candidate producer recipes through the rule pipeline
 *         (hard-rule filtering → score-rule ranking → tie-break ordering).</li>
 *     <li>Resolving candidate recipes through an explicit depth-first stack.</li>
 * </ol>
 *
 * <p>Key design choices:
 * <ul>
 *     <li><b>Deterministic heuristic selection</b> — candidates are ranked by a
 *         multi-dimensional score vector with lexicographic comparison, then by
 *         a stable tie-break policy based on recipe identity.</li>
 *     <li><b>Small rollback width</b> — when the best candidate fails, the planner
 *         tries a bounded number of ranked alternatives before
 *         reporting failure.</li>
 *     <li><b>Tag-aware variant reordering</b> — for tag-based (multi-variant) inputs,
 *         the planner reorders candidates based on the active
 *         {@link dev.vfyjxf.cirrus.api.recipe.routing.service.TagResolvePolicy}.</li>
 *     <li><b>Cycle awareness</b> — a {@link CycleGuard} prevents uncontrolled
 *         recursive expansion and enforces amplification risk thresholds.</li>
 * </ul>
 *
 * @see AssuredPlanner
 * @see RoutePlanner
 */
public final class StandardPlanner implements RoutePlanner {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");
    private static final int detailLogLimit = 256;

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        long startNanos = System.nanoTime();
        PlannerContext.Counters counters = context.counters();

        RoutingRequest request = context.request();
        if (!(request.target() instanceof RoutingRequest.RecipeTarget(RecipeEntry<?> recipe, int times))) {
            return invalidRequestOutcome(context, startNanos, "target_mode_mismatch");
        }
        logger.info(
            "planner.start mode={} target={} times={} catalog={} allowed={} budgetNodes={} budgetCandidates={} loop={} heuristic={}",
            request.plannerMode(),
            describeRecipe(recipe),
            times,
            context.catalog().size(),
            context.sourcePolicyDecision().allowedRecipes().cardinality(),
            request.budget().maxExpandedNodes(),
            request.budget().maxGeneratedCandidates(),
            request.loopPolicy(),
            request.heuristicPolicy()
        );

        int targetIndex = context.catalog().indexOf(recipe);
        if (targetIndex < 0) {
            return failureOutcome(
                context,
                startNanos,
                RoutingFailureCode.invalidRequest,
                "target_recipe_not_in_graph",
                null,
                Lists.immutable.empty()
            );
        }

        RecipeGraph.Node targetNode = context.catalog().nodeAt(targetIndex);
        PlanBuilder planBuilder = new PlanBuilder(
            System.currentTimeMillis(),
            context.tagSnapshot().version(),
				recipe,
				times
        );
        ResourceLedger ledger = new ResourceLedger(request.stock().copy());
        CycleGuard cycleGuard = new CycleGuard(request.loopPolicy(), request.loopBudget());
        Int2IntOpenHashMap activePath = new Int2IntOpenHashMap();
        MutableList<RoutingIssue> issues = Lists.mutable.empty();
        AtomicInteger issueSeq = new AtomicInteger();
        MutableList<RoutingTrace.TraceStage> traceStages = Lists.mutable.empty();

        boolean success = resolveRecipe(
            context,
            targetNode,
            targetIndex,
				times,
            0,
            ledger,
            cycleGuard,
            activePath,
            planBuilder,
            traceStages,
            issues,
            issueSeq
        );

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        RoutingStatus status;
        RoutingPlan plan = null;
        if (success) {
            status = RoutingStatus.success;
            plan = planBuilder.build();
        } else if (planBuilder.hasActions()) {
            status = RoutingStatus.partial;
            plan = planBuilder.build();
        } else {
            status = RoutingStatus.failed;
        }

        if (context.request().diagnosticLevel() != DiagnosticLevel.none) {
            traceStages.add(new RoutingTrace.TraceStage(
                RoutingTrace.TraceStageKind.sourcePolicy,
                Maps.immutable.of(
                    "initialRecipeCount", Integer.toString(context.catalog().size()),
                    "finalRecipeCount", Integer.toString(context.sourcePolicyDecision().allowedRecipes().cardinality())
                )
            ));
        }

        RoutingMetrics metrics = new RoutingMetrics(
            elapsedMs,
            counters.expandedNodes,
            counters.generatedCandidates,
            counters.acceptedCandidates,
            PlannerMode.standard,
            request.inventorySensitivityProfile(),
            request.effectiveTagResolvePolicy(),
            request.ruleProfileId(),
            request.sourcePolicyId(),
            context.tagSnapshot().version(),
            counters.adapterAttemptCount,
            Lists.immutable.empty(),
            null,
            request.budget().maxExpandedNodes(),
            counters.budgetUsed,
            counters.budgetExhausted,
            true,
            Lists.immutable.empty(),
            counters.budgetStopReason,
            counters.explorationBranchesExpanded,
            counters.explorationBudgetUsed,
            request.explorationSeed() == null ? 0 : request.explorationSeed(),
            request.loopPolicy()
        );

        RoutingTrace trace = null;
        if (context.request().diagnosticLevel() != DiagnosticLevel.none) {
            trace = new RoutingTrace(1, buildRequestFingerprint(context), traceStages.toImmutable());
        }
        logger.info(
            "planner.finish status={} target={} planActions={} issues={} elapsedMs={} expanded={} generated={} accepted={} budgetUsed={} budgetExhausted={} budgetStop={}",
            status,
            describeRecipe(recipe),
            plan == null ? 0 : plan.actions().size(),
            issues.size(),
            elapsedMs,
            counters.expandedNodes,
            counters.generatedCandidates,
            counters.acceptedCandidates,
            counters.budgetUsed,
            counters.budgetExhausted,
            counters.budgetStopReason
        );
        return new RoutingOutcome(status, plan, issues.toImmutable(), metrics, trace);
    }

    private boolean resolveRecipe(
        PlannerContext context,
        RecipeGraph.Node recipeNode,
        int recipeIndex,
        int times,
        int depth,
        ResourceLedger ledger,
        CycleGuard cycleGuard,
        Int2IntOpenHashMap activePath,
        PlanBuilder planBuilder,
        MutableList<RoutingTrace.TraceStage> traceStages,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq
    ) {
        PlannerContext.Counters counters = context.counters();
        PlannerDebugLog debugLog = new PlannerDebugLog();
        ArrayList<PlanningFrame> stack = new ArrayList<>();
        stack.add(new PlanningFrame(recipeNode, recipeIndex, times, depth));

        while (!stack.isEmpty()) {
            PlanningFrame frame = stack.get(stack.size() - 1);
            switch (frame.state) {
                case entering -> {
                    if (isBudgetExceeded(context, counters)) {
                        debugLog.info(
                            "planner.budget recipe={} depth={} expanded={} budgetUsed={} budgetLimit={} generated={} reason={}",
                            describeRecipe(frame.recipeNode.recipe()),
                            frame.depth,
                            counters.expandedNodes,
                            counters.budgetUsed,
                            context.request().budget().maxExpandedNodes(),
                            counters.generatedCandidates,
                            counters.budgetStopReason
                        );
                        addIssue(
                            issues,
                            issueSeq,
                            RoutingFailureCode.budgetExhausted,
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing.budget.exhausted"
                        );
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    int sccId = context.sccAnalysis().sccOf(frame.recipeIndex);
                    double amplificationRisk = estimateAmplificationRisk(context, isActive(activePath, frame.recipeIndex), sccId, frame.depth);
                    CycleGuard.Decision decision = cycleGuard.enter(
                        frame.recipeIndex,
                        sccId,
                        amplificationRisk,
                        context.ruleProfile().amplificationHardThreshold()
                    );
                    if (!decision.allowed()) {
                        debugLog.info(
                            "planner.cycle.reject recipe={} depth={} scc={} risk={} reason={}",
                            describeRecipe(frame.recipeNode.recipe()),
                            frame.depth,
                            sccId,
                            amplificationRisk,
                            decision.reason()
                        );
                        addIssue(
                            issues,
                            issueSeq,
                            decision.failureCode(),
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing." + decision.reason()
                        );
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    enterActive(activePath, frame.recipeIndex);
                    counters.expandedNodes++;
                    debugLog.info(
                        "planner.expand recipe={} index={} times={} depth={} inputs={} outputs={} scc={} risk={} pathDepth={}",
                        describeRecipe(frame.recipeNode.recipe()),
                        frame.recipeIndex,
                        frame.times,
                        frame.depth,
                        frame.mergedInputs.size(),
                        frame.recipeNode.mergedOutputs().size(),
                        sccId,
                        amplificationRisk,
                        activePath.size()
                    );
                    frame.entered = true;
                    frame.activeSccId = sccId;
                    frame.state = FrameState.processingInputs;
                }
                case processingInputs -> {
                    if (frame.inputIndex >= frame.mergedInputs.size()) {
                        frame.state = FrameState.finalizing;
                        continue;
                    }

                    InputContent input = frame.mergedInputs.get(frame.inputIndex);
                    int inputIndex = frame.inputIndex;
                    frame.inputIndex++;

                    long required = input.amount() * frame.times;
                    if (required <= 0) {
                        continue;
                    }
                    debugLog.info(
                        "planner.input parent={} input={} required={} depth={} inputIndex={} notConsumed={}",
                        describeRecipe(frame.recipeNode.recipe()),
                        describeInput(input),
                        required,
                        frame.depth,
                        inputIndex,
                        input.notConsumed()
                    );

                    frame.inputBindings.add(new PlanAction.InputBinding(input, required));
                    if (input.notConsumed()) {
                        planBuilder.addReserveCatalyst(input, input.amount(), frame.recipeNode.recipe());
                        continue;
                    }

                    long remaining = consumeDemandWithPolicy(
                        context,
                        input,
                        required,
                        ledger,
                        planBuilder,
                        traceStages,
                        inputIndex
                    );
                    if (remaining <= 0) {
                        debugLog.info(
                            "planner.input.covered parent={} input={} required={} remaining=0",
                            describeRecipe(frame.recipeNode.recipe()),
                            describeInput(input),
                            required
                        );
                        continue;
                    }

                    List<CandidateScore> candidates = evaluateCandidates(
                        context,
                        input,
                        remaining,
                        frame.depth + 1,
                        activePath,
                        ledger
                    );
                    if (candidates.isEmpty()) {
                        debugLog.info(
                            "planner.unresolved.noCandidates parent={} input={} remaining={} depth={} generated={} accepted={} budgetExhausted={} budgetStop={}",
                            describeRecipe(frame.recipeNode.recipe()),
                            describeInput(input),
                            remaining,
                            frame.depth,
                            counters.generatedCandidates,
                            counters.acceptedCandidates,
                            counters.budgetExhausted,
                            counters.budgetStopReason
                        );
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            remaining,
                            UnresolvedInputContext.Phase.noSource,
                            false,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq
                        )) {
                            continue;
                        }
                        addIssue(
                            issues,
                            issueSeq,
                            RoutingFailureCode.noFeasibleSource,
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing.no_source",
                            inputIssuePayload(context, frame.recipeNode.recipe(), input, required)
                        );
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    if (candidates.size() > 1) {
                        candidates.sort(candidateComparator(context.ruleProfile()));
                        reorderCandidatesByTagPolicy(context, input, remaining, ledger, candidates);
                    }
                    if (context.request().explorationEnabled()) {
                        context.ruleProfile().explorePolicy().reorderNearTies(
                            toRankedCandidates(candidates),
                            context.ruleProfile().nearTieEpsilon(),
                            context.request().explorationSeed() == null ? 0 : context.request().explorationSeed()
                        );
                    }

                    frame.beginCandidateResolution(input, inputIndex, remaining, candidates, candidateAttemptLimit(context, candidates.size()));
                    debugLog.info(
                        "planner.candidates parent={} input={} remaining={} candidates={} attemptLimit={} best={}",
                        describeRecipe(frame.recipeNode.recipe()),
                        describeInput(input),
                        remaining,
                        candidates.size(),
                        frame.candidateAttemptLimit,
                        candidates.isEmpty() ? "<none>" : describeRecipe(candidates.getFirst().node().recipe())
                    );
                    frame.state = FrameState.tryingCandidates;
                }
                case tryingCandidates -> {
                    InputContent input = frame.activeInput();
                    if (frame.candidateAttemptIndex >= frame.candidateAttemptLimit) {
                        debugLog.info(
                            "planner.unresolved.exhausted parent={} input={} remaining={} attempted={} candidates={} candidateIssues={}",
                            describeRecipe(frame.recipeNode.recipe()),
                            describeInput(input),
                            frame.remaining,
                            frame.candidateAttemptIndex,
                            frame.candidates().size(),
                            frame.candidateIssues.size()
                        );
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            frame.remaining,
                            UnresolvedInputContext.Phase.candidateExhausted,
                            frame.candidateSetHasActiveCycleCandidate,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq
                        )) {
                            frame.clearCandidateResolution();
                            frame.state = FrameState.processingInputs;
                            continue;
                        }
                        if (frame.candidateIssues.notEmpty() && shouldPropagateCandidateIssues(input)) {
                            issues.addAllIterable(frame.candidateIssues);
                            Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                            if (completed != null) {
                                return completed;
                            }
                            continue;
                        }
                        addIssue(
                            issues,
                            issueSeq,
                            RoutingFailureCode.unsatisfiedInput,
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing.unsatisfied_input",
                            inputIssuePayload(context, frame.recipeNode.recipe(), input, frame.remaining)
                        );
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    CandidateScore candidate = frame.candidates().get(frame.candidateAttemptIndex);
                    debugLog.info(
                        "planner.candidate.try parent={} input={} candidate={} attempt={} craftTimes={} score={} risk={} variant={}",
                        describeRecipe(frame.recipeNode.recipe()),
                        describeInput(input),
                        describeRecipe(candidate.node().recipe()),
                        frame.candidateAttemptIndex,
                        candidate.craftTimes(),
                        java.util.Arrays.toString(candidate.score()),
                        candidate.amplificationRisk(),
                        candidate.matchedVariantIndex()
                    );
                    if (frame.candidateAttemptIndex > 0) {
                        counters.explorationBranchesExpanded++;
                        counters.explorationBudgetUsed++;
                    }
                    frame.candidateAttemptIndex++;

                    if (candidate.amplificationRisk >= context.ruleProfile().amplificationHardThreshold()
                        && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                        debugLog.info(
                            "planner.candidate.reject candidate={} reason=cycleAmplification risk={} threshold={}",
                            describeRecipe(candidate.node().recipe()),
                            candidate.amplificationRisk,
                            context.ruleProfile().amplificationHardThreshold()
                        );
                        addIssue(
                            frame.candidateIssues,
                            issueSeq,
                            RoutingFailureCode.cycleAmplificationBlocked,
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing.cycle_amplification_blocked"
                        );
                        continue;
                    }

                    CandidateRollbackState rollback = new CandidateRollbackState(
                        ledger.copy(),
                        planBuilder.snapshot(),
                        issues.size(),
                        traceStages.size()
                    );
                    frame.rollback = rollback;
                    frame.state = FrameState.awaitingChildResult;
                    stack.add(new PlanningFrame(candidate.node(), candidate.recipeIndex(), candidate.craftTimes(), frame.depth + 1));
                }
                case awaitingChildResult -> {
                    InputContent input = frame.activeInput();
                    CandidateRollbackState rollback = frame.rollback();
                    if (!frame.lastChildSucceeded) {
                        debugLog.info(
                            "planner.candidate.rollback parent={} input={} remaining={} candidateIssues={}",
                            describeRecipe(frame.recipeNode.recipe()),
                            describeInput(input),
                            frame.remaining,
                            frame.candidateIssues.size()
                        );
                        captureIssuesSince(issues, rollback.issueCount(), frame.candidateIssues, visibleIssueRecipe(context, frame.recipeNode.recipe()));
                        rollback.restore(ledger, planBuilder, issues, traceStages);
                        frame.rollback = null;
                        frame.state = FrameState.tryingCandidates;
                        continue;
                    }

                    frame.rollback = null;
                    int childIssueStart = rollback.issueCount();
                    boolean liftedToParentFallback = false;
                    if (context.ruleProfile().liftMultiSemanticChildShortages()
                        && preservesOriginalInputShortage(input)
                        && hasOnlyNonBlockingShortages(issues, childIssueStart)) {
                        rollback.restore(ledger, planBuilder, issues, traceStages);
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            frame.remaining,
                            UnresolvedInputContext.Phase.noSource,
                            false,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq
                        )) {
                            frame.remaining = 0;
                            liftedToParentFallback = true;
                        }
                    }
                    if (!liftedToParentFallback) {
                        long previousRemaining = frame.remaining;
                        frame.remaining = consumeDemandWithPolicy(
                            context,
                            input,
                            frame.remaining,
                            ledger,
                            planBuilder,
                            traceStages,
                            frame.activeInputIndex
                        );
                        liftSuccessfulChildIssuesToParentInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            previousRemaining - frame.remaining,
                            childIssueStart,
                            issues,
                            issueSeq
                        );
                    }
                    if (frame.remaining <= 0) {
                        debugLog.info(
                            "planner.input.resolved parent={} input={} remaining=0",
                            describeRecipe(frame.recipeNode.recipe()),
                            describeInput(input)
                        );
                        frame.clearCandidateResolution();
                        frame.state = FrameState.processingInputs;
                        continue;
                    }
                    frame.state = FrameState.tryingCandidates;
                }
                case finalizing -> {
                    for (OutputContent output : frame.recipeNode.mergedOutputs()) {
                        long produced = output.amount() * frame.times;
                        if (produced <= 0) {
                            continue;
                        }
                        frame.outputBindings.add(new PlanAction.OutputBinding(output.asResource(), produced));
                    }

                    planBuilder.addExecuteRecipe(
                        frame.recipeNode.recipe(),
                        frame.times,
                        Lists.immutable.ofAll(frame.inputBindings),
                        Lists.immutable.ofAll(frame.outputBindings)
                    );
                    for (PlanAction.OutputBinding binding : frame.outputBindings) {
                        ledger.addByproduct(binding.resource(), binding.amount());
                    }
                    debugLog.info(
                        "planner.finalize recipe={} times={} inputs={} outputs={}",
                        describeRecipe(frame.recipeNode.recipe()),
                        frame.times,
                        frame.inputBindings.size(),
                        frame.outputBindings.size()
                    );

                    Boolean completed = completeFrame(stack, true, activePath, cycleGuard);
                    if (completed != null) {
                        return completed;
                    }
                }
            }
        }

        return false;
    }

    private List<CandidateScore> evaluateCandidates(
        PlannerContext context,
        InputContent input,
        long remaining,
        int depth,
        Int2IntOpenHashMap activePath,
        ResourceLedger ledger
    ) {
        BitSet allowed = context.sourcePolicyDecision().allowedRecipes();
        if (!requiresCandidateScanFallback(input)) {
            ObjectSet<RecipeGraph.OutputRef> producerRefs = indexedProducerRefs(context.graph(), input);
            List<CandidateScore> candidates = new ArrayList<>(producerRefs.size());
            for (RecipeGraph.OutputRef producer : producerRefs) {
                if (candidateBudgetReached(context)) {
                    break;
                }
                int recipeIndex = context.catalog().indexOf(producer.node().recipe());
                if (recipeIndex < 0 || !allowed.get(recipeIndex)) {
                    continue;
                }
                addCandidateIfAccepted(
                    context,
                    input,
                    remaining,
                    depth,
                    activePath,
                    ledger,
                    producer.node(),
                    recipeIndex,
                    producer.output(),
                    candidates
                );
            }
            return candidates;
        }

        List<CandidateScore> candidates = new ArrayList<>();
        for (int recipeIndex = allowed.nextSetBit(0); recipeIndex >= 0; recipeIndex = allowed.nextSetBit(recipeIndex + 1)) {
            if (candidateBudgetReached(context)) {
                break;
            }
            RecipeGraph.Node node = context.catalog().nodeAt(recipeIndex);
            OutputContent matchedOutput = findMatchedOutput(node, input);
            addCandidateIfAccepted(
                context,
                input,
                remaining,
                depth,
                activePath,
                ledger,
                node,
                recipeIndex,
                matchedOutput,
                candidates
            );
        }
        return candidates;
    }

    private boolean candidateBudgetReached(PlannerContext context) {
        PlannerContext.Counters counters = context.counters();
        if (counters.generatedCandidates < context.request().budget().maxGeneratedCandidates()) {
            return false;
        }
        counters.budgetExhausted = true;
        counters.budgetStopReason = "maxGeneratedCandidates";
        return true;
    }

    private void addCandidateIfAccepted(
        PlannerContext context,
        InputContent input,
        long remaining,
        int depth,
        Int2IntOpenHashMap activePath,
        ResourceLedger ledger,
        RecipeGraph.Node node,
        int recipeIndex,
        @Nullable OutputContent matchedOutput,
        List<CandidateScore> candidates
    ) {
        if (matchedOutput == null || matchedOutput.amount() <= 0) {
            return;
        }
        if (!heuristicAllowsCandidate(context.request(), node)) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "planner.candidate.filtered reason=heuristic recipe={} policy={} hintPolicy={} penalty={}",
                    describeRecipe(node.recipe()),
                    context.request().heuristicPolicy(),
                    node.routingHints().usePolicy(),
                    node.routingHints().penalty()
                );
            }
            return;
        }
        int matchedVariantIndex = resolveMatchedVariantIndex(input, matchedOutput);
        if (matchedVariantIndex < 0) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "planner.candidate.filtered reason=variantMismatch recipe={} input={} outputAmount={}",
                    describeRecipe(node.recipe()),
                    describeInput(input),
                    matchedOutput.amount()
                );
            }
            return;
        }
        context.counters().generatedCandidates++;
        markCandidateBudgetIfReached(context);

        RuleProfile profile = context.ruleProfile();
        int craftTimes = (int) Math.ceil((double) remaining / matchedOutput.amount());
        boolean cycleCandidate = isActive(activePath, recipeIndex);
        int sccId = context.sccAnalysis().sccOf(recipeIndex);
        double amplificationRisk = estimateAmplificationRisk(context, cycleCandidate, sccId, depth);
        double availableCoverage = estimateCandidateAvailableCoverage(node, craftTimes, ledger);
        CandidateContext candidateContext = new CandidateContext(
            node,
            input,
            remaining,
            craftTimes,
            depth,
            cycleCandidate,
            amplificationRisk,
            availableCoverage
        );

        HardRuleResult hardResult = evaluateHardRules(profile.hardRules(), candidateContext);
        if (!hardResult.accepted()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "planner.candidate.filtered reason=hardRule recipe={} input={} remaining={} depth={} risk={} coverage={}",
                    describeRecipe(node.recipe()),
                    describeInput(input),
                    remaining,
                    depth,
                    amplificationRisk,
                    availableCoverage
                );
            }
            return;
        }
        context.counters().acceptedCandidates++;
        double[] score = profile.newScoreVector();
        var accumulator = profile.newAccumulator(score);
        for (var rule : profile.scoreRules()) {
            rule.contribute(candidateContext, accumulator);
        }
        applyHeuristicScore(node.routingHints(), accumulator);
        candidates.add(new CandidateScore(recipeIndex, node, craftTimes, score, candidateContext, amplificationRisk, matchedVariantIndex));
    }

    private boolean heuristicAllowsCandidate(RoutingRequest request, RecipeGraph.Node node) {
        if (request.heuristicPolicy() == HeuristicPolicy.ignoreHints) {
            return true;
        }
        RecipeRoutingHints hints = node.routingHints();
        return switch (hints.usePolicy()) {
            case normal, discouraged -> true;
            case lastResort -> request.heuristicPolicy() == HeuristicPolicy.allowLastResort
                || request.heuristicPolicy() == HeuristicPolicy.includeAll;
            case disabledByDefault -> request.heuristicPolicy() == HeuristicPolicy.includeAll;
            case targetOnly -> request.heuristicPolicy() == HeuristicPolicy.includeAll
                || request.target() instanceof RoutingRequest.RecipeTarget target && target.recipe().equals(node.recipe());
        };
    }

    private void applyHeuristicScore(
        RecipeRoutingHints hints,
        dev.vfyjxf.cirrus.api.recipe.routing.rule.ScoreAccumulator accumulator
    ) {
        int penalty = hints.penalty();
        if (hints.usePolicy() == RecipeUsePolicy.discouraged) {
            penalty += 1_000;
        } else if (hints.usePolicy() == RecipeUsePolicy.lastResort) {
            penalty += 50_000;
        }
        if (penalty > 0) {
            accumulator.add("craft", penalty);
        }
    }

    private void markCandidateBudgetIfReached(PlannerContext context) {
        PlannerContext.Counters counters = context.counters();
        if (counters.generatedCandidates >= context.request().budget().maxGeneratedCandidates()) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxGeneratedCandidates";
        }
    }

    private ObjectSet<RecipeGraph.OutputRef> indexedProducerRefs(RecipeGraph graph, InputContent input) {
        ContentResource direct = directInputResource(input);
        if (direct != null) {
            return graph.producersFor(direct);
        }
        if (!input.isEnumerable()) {
            return it.unimi.dsi.fastutil.objects.ObjectSets.emptySet();
        }

        ObjectOpenHashSet<RecipeGraph.OutputRef> refs = new ObjectOpenHashSet<>();
        for (ContentVariant variant : input.enumerate()) {
            refs.addAll(graph.producersFor(variant));
        }
        return refs;
    }

    private @Nullable ContentResource directInputResource(InputContent input) {
        if (input instanceof StackContent<?, ?> stack) {
            return stack.content();
        }
        if (input instanceof ValueContent<?> value) {
            return value.content();
        }
        return null;
    }

    private boolean requiresCandidateScanFallback(InputContent input) {
        return input instanceof ComplexContent && !input.isEnumerable();
    }

    private double estimateCandidateAvailableCoverage(RecipeGraph.Node node, int craftTimes, ResourceLedger ledger) {
        double totalRequired = 0.0d;
        double totalCovered = 0.0d;
        for (InputContent input : node.mergedInputs()) {
            long required = requiredCandidateInputAmount(input, craftTimes);
            if (required <= 0L) {
                continue;
            }

            long available = Math.min(required, ledger.peekAvailable(input));
            totalRequired += required;
            totalCovered += available;
        }
        if (totalRequired <= 0.0d) {
            return 1.0d;
        }
        return Math.min(1.0d, totalCovered / totalRequired);
    }

    private long requiredCandidateInputAmount(InputContent input, int craftTimes) {
        if (input.notConsumed()) {
            return input.amount();
        }
        return input.amount() * craftTimes;
    }

    private Comparator<CandidateScore> candidateComparator(RuleProfile profile) {
        return (left, right) -> {
            int cmp = profile.rankPolicy().compare(left.score, right.score);
            if (cmp != 0) {
                return cmp;
            }
            return profile.tieBreakPolicy().compare(left.context, right.context);
        };
    }

    private MutableList<ExplorePolicy.RankedCandidate> toRankedCandidates(List<CandidateScore> candidates) {
        MutableList<ExplorePolicy.RankedCandidate> ranked = Lists.mutable.ofInitialCapacity(candidates.size());
        for (CandidateScore candidate : candidates) {
            ranked.add(new ExplorePolicy.RankedCandidate(candidate.context, candidate.score));
        }
        return ranked;
    }

    private RoutingIssuePayload.InputShortage inputIssuePayload(
        PlannerContext context,
        RecipeEntry<?> actualRecipe,
        InputContent input,
        long amount
    ) {
        RecipeEntry<?> visibleRecipe = issueRecipe(context, actualRecipe);
        String visibleRecipeId = describeRecipe(visibleRecipe);
        String actualRecipeId = describeRecipe(actualRecipe);
        return new RoutingIssuePayload.InputShortage(
            describeInput(input),
            visibleRecipeId,
            amount,
            Objects.equals(visibleRecipeId, actualRecipeId) ? null : actualRecipeId
        );
    }

    private RecipeEntry<?> issueRecipe(PlannerContext context, RecipeEntry<?> fallbackRecipe) {
        return switch (context.request().target()) {
            case RoutingRequest.RecipeTarget(var recipe, int ignored) -> recipe;
            default -> fallbackRecipe;
        };
    }

    private String describeRecipe(RecipeEntry<?> recipe) {
        ResourceLocation id = recipe.id();
        if (id != null) {
            return id.toString();
        }
        return recipe.systemId() + "#" + recipe.id();
    }

    private String describeInput(InputContent input) {
        return switch (input) {
            case TagStackContent<?> tag -> "#" + tag.tagKey().location();
            case StackContent<?, ?> stack -> describeStack(stack.content());
            case TypedStackListContent<?, ?> typedList -> describeVariants(typedList.stacks());
            case StackListContent stackList -> describeVariants(stackList.stacks());
            case ValueContent<?> value -> describeValue(value.content());
            default -> input.isEnumerable() ? describeVariants(input.enumerate()) : Objects.toString(input, "unknown");
        };
    }

    private String describeVariants(Iterable<? extends ContentVariant> variants) {
        ArrayList<String> labels = new ArrayList<>(3);
        int total = 0;
        for (ContentVariant variant : variants) {
            if (labels.size() < 3) {
                labels.add(describeVariant(variant));
            }
            total++;
        }
        if (labels.isEmpty()) {
            return "unknown";
        }
        return total > labels.size()
            ? String.join(" | ", labels) + " | ..."
            : String.join(" | ", labels);
    }

    private String describeVariant(ContentVariant variant) {
        return switch (variant) {
            case ContentStack<?, ?> stack -> describeStack(stack);
            case ContentKind<?> kind -> describeKind(kind);
            case ContentValue<?> value -> describeValue(value);
            default -> Objects.toString(variant, "unknown");
        };
    }

    private String describeStack(ContentStack<?, ?> stack) {
        if (stack.isItem()) {
            Item item = stack.<Item, ItemStack>cast().content();
            return Objects.toString(BuiltInRegistries.ITEM.getKey(item), "minecraft:air");
        }
        if (stack.isFluid()) {
            Fluid fluid = stack.<Fluid, FluidStack>cast().content();
            return Objects.toString(BuiltInRegistries.FLUID.getKey(fluid), "minecraft:empty");
        }
        ResourceLocation location = stack.kind().getLocation();
        return location != null ? location.toString() : Objects.toString(stack.content(), "unknown");
    }

    private String describeKind(ContentKind<?> kind) {
        ResourceLocation location = kind.getLocation();
        if (location != null) {
            return location.toString();
        }
        return kind.type().typeId() + "=" + Objects.toString(kind.value(), "unknown");
    }

    private String describeValue(ContentValue<?> value) {
        return value.type().typeId() + "=" + value.value();
    }

    private boolean resolveUnresolvedInput(
        PlannerContext context,
        RecipeEntry<?> recipe,
        InputContent input,
        long amount,
        UnresolvedInputContext.Phase phase,
        boolean activeCycleCandidate,
        ResourceLedger ledger,
        PlanBuilder planBuilder,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq
    ) {
        UnresolvedInputRule.Resolution resolution = context.ruleProfile().unresolvedInputRule().resolve(
            new UnresolvedInputContext(recipe, input, amount, phase, activeCycleCandidate)
        );
        if (resolution != UnresolvedInputRule.Resolution.pendingInput) {
            return false;
        }

        // Execution-oriented fallback: preserve the original input semantics instead
        // of collapsing tags/lists into a planner-chosen concrete stack.
        ledger.recordPendingInput(input, amount, planBuilder);
        addIssue(
            issues,
            issueSeq,
            unresolvedInputFailureCode(phase),
            RoutingIssue.Severity.warning,
            false,
            unresolvedInputMessageKey(phase),
            inputIssuePayload(context, recipe, input, amount)
        );
        return true;
    }

    private RoutingFailureCode unresolvedInputFailureCode(UnresolvedInputContext.Phase phase) {
        return switch (phase) {
            case noSource -> RoutingFailureCode.noFeasibleSource;
            case candidateExhausted -> RoutingFailureCode.unsatisfiedInput;
        };
    }

    private String unresolvedInputMessageKey(UnresolvedInputContext.Phase phase) {
        return switch (phase) {
            case noSource -> "cirrus.routing.no_source";
            case candidateExhausted -> "cirrus.routing.unsatisfied_input";
        };
    }

    private int candidateAttemptLimit(PlannerContext context, int candidateCount) {
        if (candidateCount <= 0) {
            return 0;
        }
        RuleProfile profile = context.ruleProfile();
        int shortlist = Math.max(1, profile.shortlistK());
        int rollbackWindow = Math.max(1, profile.rollbackWidth() + 1);
        int boundedWindow = Math.max(shortlist, rollbackWindow);
        int modeWindow = switch (context.request().plannerMode()) {
            case standard -> Math.min(shortlist, rollbackWindow);
            case assuredBounded, assuredProven -> boundedWindow;
        };
        return Math.min(candidateCount, Math.max(1, modeWindow));
    }

    /**
     * Candidate-specific child failures are only meaningful when the parent input already
     * points at one exact semantic target.
     *
     * <p>For tag/list/multi-variant inputs, surfacing child shortages would leak whichever
     * expanded candidate happened to be tried, which loses fidelity and misrepresents the
     * real unresolved demand. In those cases the planner must report the original input.
     */
    private boolean shouldPropagateCandidateIssues(InputContent input) {
        return !preservesOriginalInputShortage(input);
    }

    private boolean hasOnlyNonBlockingShortages(MutableList<RoutingIssue> issues, int issueStart) {
        if (issueStart >= issues.size()) {
            return false;
        }
        for (int i = issueStart; i < issues.size(); i++) {
            RoutingIssue issue = issues.get(i);
            if (issue.blocking() || !(issue.payload() instanceof RoutingIssuePayload.InputShortage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Successful child expansion may still emit execution-profile warnings when that child
     * fell back to "consume from current stock later" at one of its leaves.
     *
     * <p>For multi-semantic parent inputs such as tags or stack lists, exposing the child
     * warning leaks whichever candidate happened to be selected (for example turning a root
     * {@code #minecraft:logs} shortage into {@code minecraft:acacia_log}). In that case the
     * warning should be lifted back to the parent input semantics while keeping the expanded
     * route intact for execution.
     */
    private void liftSuccessfulChildIssuesToParentInput(
        PlannerContext context,
        RecipeEntry<?> parentRecipe,
        InputContent parentInput,
        long resolvedAmount,
        int issueStart,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq
    ) {
        if (resolvedAmount <= 0
            || !context.ruleProfile().liftMultiSemanticChildShortages()
            || !preservesOriginalInputShortage(parentInput)
            || issueStart >= issues.size()) {
            return;
        }

        RoutingIssue template = null;
        for (int i = issueStart; i < issues.size(); i++) {
            RoutingIssue issue = issues.get(i);
            if (issue.blocking() || !(issue.payload() instanceof RoutingIssuePayload.InputShortage)) {
                return;
            }
            if (template == null) {
                template = issue;
            }
        }
        if (template == null) {
            return;
        }

        trimToSize(issues, issueStart);
        addIssue(
            issues,
            issueSeq,
            template.code(),
            template.severity(),
            template.blocking(),
            template.messageKey(),
            inputIssuePayload(context, parentRecipe, parentInput, resolvedAmount)
        );
    }

    private boolean preservesOriginalInputShortage(InputContent input) {
        return input.preserveOriginalShortageSemantics() || hasMultipleSemanticAlternatives(input);
    }

    private boolean hasMultipleSemanticAlternatives(InputContent input) {
        if (!input.isEnumerable()) {
            return false;
        }
        int count = 0;
        for (ContentVariant ignored : input.enumerate()) {
            count++;
            if (count > 1) {
                return true;
            }
        }
        return false;
    }

    private long consumeDemandWithPolicy(
        PlannerContext context,
        InputContent input,
        long required,
        ResourceLedger ledger,
        PlanBuilder planBuilder,
        MutableList<RoutingTrace.TraceStage> traceStages,
        int inputIndex
    ) {
        if (!input.isEnumerable()) {
            return ledger.consumeDemand(input, required, planBuilder);
        }

        List<VariantCoverage> ordered = collectOrderedVariantCoverage(
            context,
            context.request().effectiveTagResolvePolicy(),
            input,
            required,
            ledger,
            null
        );
        long remaining = required;
        for (VariantCoverage coverage : ordered) {
            if (remaining <= 0) {
                break;
            }
            long consumed = ledger.consumeVariantDemand(input, coverage.variant(), remaining, planBuilder);
            remaining -= consumed;
        }

        if (context.request().diagnosticLevel() != DiagnosticLevel.none && !ordered.isEmpty()) {
            VariantCoverage first = ordered.getFirst();
            var traceValues = Maps.mutable.<String, String>withInitialCapacity(6);
            traceValues.put("inputIndex", Integer.toString(inputIndex));
            traceValues.put("policy", context.request().effectiveTagResolvePolicy().name());
            traceValues.put("firstVariant", first.stableKey());
            traceValues.put("firstCoverage", Double.toString(first.coverage()));
            traceValues.put("required", Long.toString(required));
            traceValues.put("remaining", Long.toString(remaining));
            traceStages.add(new RoutingTrace.TraceStage(
                RoutingTrace.TraceStageKind.tagResolve,
                traceValues.toImmutable()
            ));
        }
        return remaining;
    }

    private void reorderCandidatesByTagPolicy(
        PlannerContext context,
        InputContent input,
        long required,
        ResourceLedger ledger,
        List<CandidateScore> candidates
    ) {
        if (!input.isEnumerable() || candidates.size() <= 1) {
            return;
        }
        List<VariantCoverage> ordered = collectOrderedVariantCoverage(
            context,
            context.request().effectiveTagResolvePolicy(),
            input,
            required,
            ledger,
            candidates
        );
        if (ordered.size() <= 1) {
            return;
        }

        int maxVariantIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            maxVariantIndex = Math.max(maxVariantIndex, ordered.get(i).variantIndex());
        }
        if (maxVariantIndex < 0) {
            return;
        }
        int[] orderByVariant = new int[maxVariantIndex + 1];
        java.util.Arrays.fill(orderByVariant, Integer.MAX_VALUE);
        for (int i = 0; i < ordered.size(); i++) {
            int variantIndex = ordered.get(i).variantIndex();
            if (variantIndex >= 0 && variantIndex < orderByVariant.length) {
                orderByVariant[variantIndex] = i;
            }
        }
        candidates.sort((left, right) -> {
            int leftOrder = candidateVariantOrder(orderByVariant, left.matchedVariantIndex());
            int rightOrder = candidateVariantOrder(orderByVariant, right.matchedVariantIndex());
            int cmp = Integer.compare(leftOrder, rightOrder);
            if (cmp != 0) {
                return cmp;
            }
            return 0;
        });
    }

    private int candidateVariantOrder(int[] orderByVariant, int variantIndex) {
        if (variantIndex < 0 || variantIndex >= orderByVariant.length) {
            return Integer.MAX_VALUE;
        }
        return orderByVariant[variantIndex];
    }

    private List<VariantCoverage> collectOrderedVariantCoverage(
        PlannerContext context,
        TagResolvePolicy policy,
        InputContent input,
        long required,
        ResourceLedger ledger,
        List<CandidateScore> candidates
    ) {
        int[] bestCandidateRanks = candidates == null ? null : bestCandidateRanksByVariant(input, candidates);
        List<VariantCoverage> variants = new ArrayList<>();
        int index = 0;
        for (ContentVariant variant : input.enumerate()) {
            long stockAvail = ledger.peekStockAvailable(variant);
            long byproductAvail = ledger.peekByproductAvailable(variant);
            long totalAvail = stockAvail + byproductAvail;
            double coverage = required <= 0 ? 0 : Math.min(required, totalAvail) / (double) required;
            int bestCandidateRank = bestCandidateRanks == null || index >= bestCandidateRanks.length
                ? Integer.MAX_VALUE
                : bestCandidateRanks[index];
            variants.add(new VariantCoverage(index, variant, stockAvail, byproductAvail, coverage, bestCandidateRank, variantStableKey(variant)));
            index++;
        }

        variants.sort((left, right) -> compareVariantCoverage(policy, left, right));
        String preferredVariantKey = context.request().preferredVariants().get(RoutingVariantKeys.inputKey(input));
        if (preferredVariantKey != null && !preferredVariantKey.isBlank()) {
            variants.sort((left, right) -> Boolean.compare(
                !preferredVariantKey.equals(left.stableKey()),
                !preferredVariantKey.equals(right.stableKey())
            ));
        }
        return variants;
    }

    private int[] bestCandidateRanksByVariant(InputContent input, List<CandidateScore> candidates) {
        int variantCount = 0;
        for (ContentVariant ignored : input.enumerate()) {
            variantCount++;
        }
        int[] ranks = new int[variantCount];
        java.util.Arrays.fill(ranks, Integer.MAX_VALUE);
        for (int i = 0; i < candidates.size(); i++) {
            int variantIndex = candidates.get(i).matchedVariantIndex();
            if (variantIndex >= 0 && variantIndex < ranks.length && ranks[variantIndex] == Integer.MAX_VALUE) {
                ranks[variantIndex] = i;
            }
        }
        return ranks;
    }

    private int compareVariantCoverage(TagResolvePolicy policy, VariantCoverage left, VariantCoverage right) {
        return switch (policy) {
            case stockBiased -> compareForStockBiased(left, right);
            case balanced -> compareForBalanced(left, right);
            case pathBiased -> compareForPathBiased(left, right);
        };
    }

    private int compareForStockBiased(VariantCoverage left, VariantCoverage right) {
        int cmp = Double.compare(right.coverage(), left.coverage());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Long.compare(right.totalAvailable(), left.totalAvailable());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(left.bestCandidateRank(), right.bestCandidateRank());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(left.variantIndex(), right.variantIndex());
    }

    private int compareForBalanced(VariantCoverage left, VariantCoverage right) {
        int cmp = Double.compare(right.coverage(), left.coverage());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(left.bestCandidateRank(), right.bestCandidateRank());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Long.compare(right.totalAvailable(), left.totalAvailable());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(left.variantIndex(), right.variantIndex());
    }

    private int compareForPathBiased(VariantCoverage left, VariantCoverage right) {
        int cmp = Integer.compare(left.bestCandidateRank(), right.bestCandidateRank());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Double.compare(right.coverage(), left.coverage());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(left.variantIndex(), right.variantIndex());
    }

    private int resolveMatchedVariantIndex(InputContent input, OutputContent output) {
        if (!input.isEnumerable()) {
            return input.satisfy(output.asResource()) ? 0 : -1;
        }
        int index = 0;
        for (ContentVariant variant : input.enumerate()) {
            if (variantMatchesOutput(variant, output)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private boolean variantMatchesOutput(ContentVariant variant, OutputContent output) {
        ContentResource outputResource = output.asResource();
        return switch (variant) {
            case ContentStack<?, ?> stack when outputResource instanceof ContentStack<?, ?> outputStack ->
                stack.matches(outputStack, MatchStrategy.identityAndComponent);
            case ContentValue<?> value when outputResource instanceof ContentValue<?> outputValue ->
                value.matches(outputValue);
            case ContentKind<?> kind when outputResource instanceof ContentStack<?, ?> outputStack ->
                outputStack.matchesContent(kind);
            case ContentResource resource -> resource.matches(outputResource, MatchStrategy.identityAndComponent);
            default -> false;
        };
    }

    private String variantStableKey(ContentVariant variant) {
        return RoutingVariantKeys.variantKey(variant);
    }

    private HardRuleResult evaluateHardRules(ImmutableList<HardRule> hardRules, CandidateContext context) {
        for (HardRule hardRule : hardRules) {
            HardRuleResult result = hardRule.evaluate(context);
            if (!result.accepted()) {
                return result;
            }
        }
        return HardRuleResult.accept();
    }

    private OutputContent findMatchedOutput(RecipeGraph.Node node, InputContent input) {
        for (OutputContent output : node.mergedOutputs()) {
            if (input.satisfy(output.asResource())) {
                return output;
            }
        }
        return null;
    }

    private boolean isBudgetExceeded(PlannerContext context, PlannerContext.Counters counters) {
        counters.budgetUsed = counters.expandedNodes;
        if (counters.expandedNodes >= context.request().budget().maxExpandedNodes()) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxExpandedNodes";
            return true;
        }
        return false;
    }

    private String visibleIssueRecipe(PlannerContext context, RecipeEntry<?> fallbackRecipe) {
        return describeRecipe(issueRecipe(context, fallbackRecipe));
    }

    private static boolean isActive(Int2IntOpenHashMap activePath, int recipeIndex) {
        return activePath.get(recipeIndex) > 0;
    }

    private static void enterActive(Int2IntOpenHashMap activePath, int recipeIndex) {
        activePath.put(recipeIndex, activePath.get(recipeIndex) + 1);
    }

    private static void leaveActive(Int2IntOpenHashMap activePath, int recipeIndex) {
        int count = activePath.get(recipeIndex);
        if (count <= 1) {
            activePath.remove(recipeIndex);
        } else {
            activePath.put(recipeIndex, count - 1);
        }
    }

    private static boolean hasActiveCycleCandidate(List<CandidateScore> candidates) {
        for (CandidateScore candidate : candidates) {
            if (candidate.context().cycleCandidate()) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Boolean completeFrame(
        ArrayList<PlanningFrame> stack,
        boolean success,
        Int2IntOpenHashMap activePath,
        CycleGuard cycleGuard
    ) {
        PlanningFrame frame = stack.remove(stack.size() - 1);
        frame.close(activePath, cycleGuard);
        if (stack.isEmpty()) {
            return success;
        }
        PlanningFrame parent = stack.get(stack.size() - 1);
        parent.lastChildSucceeded = success;
        return null;
    }

    private double estimateAmplificationRisk(PlannerContext context, boolean cycleCandidate, int sccId, int depth) {
        double risk = 0.05 + Math.min(depth, 20) * 0.02;
        if (cycleCandidate) {
            risk += 0.45;
        }
        if (context.sccAnalysis().isCyclicScc(sccId)) {
            risk += 0.25;
        }
        return Math.min(1.0, risk);
    }

    private String buildRequestFingerprint(PlannerContext context) {
        return context.request().plannerMode()
            + "|" + context.request().ruleProfileId()
            + "|" + context.request().sourcePolicyId()
            + "|" + context.tagSnapshot().version();
    }

    private RoutingOutcome invalidRequestOutcome(PlannerContext context, long startNanos, String reason) {
        return failureOutcome(context, startNanos, RoutingFailureCode.invalidRequest, reason, null, Lists.immutable.empty());
    }

    private RoutingOutcome failureOutcome(
        PlannerContext context,
        long startNanos,
        RoutingFailureCode code,
        String reason,
        RoutingPlan plan,
        ImmutableList<RoutingIssue> extraIssues
    ) {
        MutableList<RoutingIssue> issues = Lists.mutable.ofAll(extraIssues);
        issues.add(new RoutingIssue(
            "issue-" + issues.size(),
            code,
            RoutingIssue.Severity.error,
            true,
            "cirrus.routing." + reason,
            Maps.immutable.empty(),
            new RoutingIssuePayload.Empty(),
            null,
            null,
            Maps.immutable.empty()
        ));
        RoutingMetrics metrics = new RoutingMetrics(
            (System.nanoTime() - startNanos) / 1_000_000L,
            context.counters().expandedNodes,
            context.counters().generatedCandidates,
            context.counters().acceptedCandidates,
            PlannerMode.standard,
            context.request().inventorySensitivityProfile(),
            context.request().effectiveTagResolvePolicy(),
            context.request().ruleProfileId(),
            context.request().sourcePolicyId(),
            context.tagSnapshot().version(),
            context.counters().adapterAttemptCount,
            Lists.immutable.empty(),
            null,
            context.request().budget().maxExpandedNodes(),
            context.counters().budgetUsed,
            context.counters().budgetExhausted,
            true,
            Lists.immutable.empty(),
            context.counters().budgetStopReason,
            context.counters().explorationBranchesExpanded,
            context.counters().explorationBudgetUsed,
            context.request().explorationSeed() == null ? 0 : context.request().explorationSeed(),
            context.request().loopPolicy()
        );
        return new RoutingOutcome(RoutingStatus.failed, plan, issues.toImmutable(), metrics, null);
    }

    private void addIssue(
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        RoutingFailureCode code,
        RoutingIssue.Severity severity,
        boolean blocking,
        String messageKey
    ) {
        addIssue(issues, issueSeq, code, severity, blocking, messageKey, Maps.immutable.empty());
    }

    private void addIssue(
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        RoutingFailureCode code,
        RoutingIssue.Severity severity,
        boolean blocking,
        String messageKey,
        ImmutableMap<String, String> messageArgs
    ) {
        addIssue(issues, issueSeq, code, severity, blocking, messageKey, messageArgs, new RoutingIssuePayload.Empty(), Maps.immutable.empty());
    }

    private void addIssue(
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        RoutingFailureCode code,
        RoutingIssue.Severity severity,
        boolean blocking,
        String messageKey,
        RoutingIssuePayload payload
    ) {
        issues.add(new RoutingIssue(
            "issue-" + issueSeq.getAndIncrement(),
            code,
            severity,
            blocking,
            messageKey,
            payload.messageArgs(),
            payload,
            null,
            null,
            payload.details()
        ));
    }

    private void addIssue(
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        RoutingFailureCode code,
        RoutingIssue.Severity severity,
        boolean blocking,
        String messageKey,
        ImmutableMap<String, String> messageArgs,
        RoutingIssuePayload payload,
        ImmutableMap<String, String> details
    ) {
        issues.add(new RoutingIssue(
            "issue-" + issueSeq.getAndIncrement(),
            code,
            severity,
            blocking,
            messageKey,
            messageArgs,
            payload,
            null,
            null,
            details
        ));
    }

    private record CandidateScore(
        int recipeIndex,
        RecipeGraph.Node node,
        int craftTimes,
        double[] score,
        CandidateContext context,
        double amplificationRisk,
        int matchedVariantIndex
    ) {
    }

    private static final class PlannerDebugLog {
        private int count;
        private boolean omitted;

        private void info(String format, Object... arguments) {
            if (count < detailLogLimit) {
                logger.info(format, arguments);
            } else if (!omitted) {
                logger.info("planner.detail.omitted after={} reason=detailLogLimit", detailLogLimit);
                omitted = true;
            }
            count++;
        }
    }

    private record VariantCoverage(
        int variantIndex,
        ContentVariant variant,
        long stockAvailable,
        long byproductAvailable,
        double coverage,
        int bestCandidateRank,
        String stableKey
    ) {
        long totalAvailable() {
            return stockAvailable + byproductAvailable;
        }
    }

    private enum FrameState {
        entering,
        processingInputs,
        tryingCandidates,
        awaitingChildResult,
        finalizing
    }

    private static final class PlanningFrame {
        private final RecipeGraph.Node recipeNode;
        private final int recipeIndex;
        private final int times;
        private final int depth;
        private final ImmutableList<InputContent> mergedInputs;
        private final List<PlanAction.InputBinding> inputBindings = new ArrayList<>();
        private final List<PlanAction.OutputBinding> outputBindings = new ArrayList<>();
        private boolean entered;
        private int activeSccId = -1;
        private int inputIndex;
        private FrameState state = FrameState.entering;
        private @Nullable InputContent activeInput;
        private int activeInputIndex = -1;
        private long remaining;
        private List<CandidateScore> candidates = List.of();
        private int candidateAttemptLimit;
        private int candidateAttemptIndex;
        private boolean candidateSetHasActiveCycleCandidate;
        private MutableList<RoutingIssue> candidateIssues = Lists.mutable.empty();
        private @Nullable CandidateRollbackState rollback;
        private boolean lastChildSucceeded;

        private PlanningFrame(RecipeGraph.Node recipeNode, int recipeIndex, int times, int depth) {
            this.recipeNode = recipeNode;
            this.recipeIndex = recipeIndex;
            this.times = times;
            this.depth = depth;
            this.mergedInputs = recipeNode.mergedInputs();
        }

        private void beginCandidateResolution(
            InputContent input,
            int inputIndex,
            long remaining,
            List<CandidateScore> candidates,
            int candidateAttemptLimit
        ) {
            this.activeInput = input;
            this.activeInputIndex = inputIndex;
            this.remaining = remaining;
            this.candidates = candidates;
            this.candidateAttemptLimit = Math.min(candidates.size(), Math.max(1, candidateAttemptLimit));
            this.candidateAttemptIndex = 0;
            this.candidateSetHasActiveCycleCandidate = hasActiveCycleCandidate(candidates);
            this.candidateIssues = Lists.mutable.empty();
            this.rollback = null;
            this.lastChildSucceeded = false;
        }

        private void clearCandidateResolution() {
            activeInput = null;
            activeInputIndex = -1;
            remaining = 0;
            candidates = List.of();
            candidateAttemptLimit = 0;
            candidateAttemptIndex = 0;
            candidateSetHasActiveCycleCandidate = false;
            candidateIssues = Lists.mutable.empty();
            rollback = null;
            lastChildSucceeded = false;
        }

        private InputContent activeInput() {
            return Objects.requireNonNull(activeInput, "activeInput");
        }

        private List<CandidateScore> candidates() {
            return candidates;
        }

        private CandidateRollbackState rollback() {
            return Objects.requireNonNull(rollback, "rollback");
        }

        private void close(Int2IntOpenHashMap activePath, CycleGuard cycleGuard) {
            if (!entered) {
                return;
            }
            leaveActive(activePath, recipeIndex);
            cycleGuard.leave(recipeIndex, activeSccId);
        }
    }

    private record CandidateRollbackState(
        ResourceLedger ledger,
        PlanBuilder.Snapshot plan,
        int issueCount,
        int traceCount
    ) {
        private void restore(
            ResourceLedger currentLedger,
            PlanBuilder currentPlan,
            MutableList<RoutingIssue> currentIssues,
            MutableList<RoutingTrace.TraceStage> currentTraceStages
        ) {
            currentLedger.restoreFrom(ledger);
            currentPlan.restore(plan);
            trimToSize(currentIssues, issueCount);
            trimToSize(currentTraceStages, traceCount);
        }
    }

    private static <T> void trimToSize(MutableList<T> list, int size) {
        while (list.size() > size) {
            list.remove(list.size() - 1);
        }
    }

    private static void captureIssuesSince(
        MutableList<RoutingIssue> source,
        int start,
        MutableList<RoutingIssue> target,
        String visibleRecipe
    ) {
        for (int i = start; i < source.size(); i++) {
            target.add(rebindIssue(source.get(i), visibleRecipe));
        }
    }

    private static RoutingIssue rebindIssue(RoutingIssue issue, String visibleRecipe) {
        RoutingIssuePayload payload = switch (issue.payload()) {
            case RoutingIssuePayload.InputShortage shortage -> shortage.withVisibleRecipe(visibleRecipe);
            default -> issue.payload();
        };
        return new RoutingIssue(
            issue.issueId(),
            issue.code(),
            issue.severity(),
            issue.blocking(),
            issue.messageKey(),
            payload.messageArgs(),
            payload,
            issue.anchorActionId(),
            issue.anchorActionIndex(),
            payload.details()
        );
    }
}

