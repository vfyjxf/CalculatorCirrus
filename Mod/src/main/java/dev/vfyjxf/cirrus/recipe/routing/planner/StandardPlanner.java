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
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRecipeRules;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRuleContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ExplorePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRuleResult;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.rule.FeatureType;
import dev.vfyjxf.cirrus.api.recipe.rule.FeatureVector;
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
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.service.TagResolvePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingVariantKeys;
import dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard;
import dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cirrus.recipe.routing.cost.UnitCostOracle;
import dev.vfyjxf.cirrus.recipe.routing.topology.GraphTopology;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
    private static final int activeDemandDependencyNodeLimit = 128;
    private static final int activeDemandDependencyProducerLimit = 512;
    private static final int candidatePreShortlistMinimum = 64;
    private static final int candidatePreShortlistMultiplier = 64;
    private static final int candidatePreShortlistVariantRetentionLimit = 512;
    /**
     * Minimum producer refs evaluated per input before a budget gate may cut
     * enumeration: a {@code candidateCount == 0} pending must never be caused by
     * the budget running out before this input's producers were even visited.
     */
    private static final int minEvalFloorCap = 8;
    /** Experiments/toggles: disable impurity scoring or round-trip filtering. */
    private static final boolean impurityEnabled = Boolean.parseBoolean(System.getProperty("cirrus.exp.impurity", "true"));
    private static final boolean roundTripEnabled = Boolean.parseBoolean(System.getProperty("cirrus.exp.roundtrip", "true"));
    private static final ContentVariant[] emptyVariants = new ContentVariant[0];
    private static final ActiveDemandSnapshot emptyActiveDemandSnapshot =
        new ActiveDemandSnapshot(new InputContent[0], 1);

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        long startNanos = System.nanoTime();
        PlannerContext.Counters counters = context.counters();
        counters.wallDeadlineNanos = context.request().budget().maxWallTimeMs() <= 0
            ? Long.MAX_VALUE
            : startNanos + context.request().budget().maxWallTimeMs() * 1_000_000L;

        RoutingRequest request = context.request();
        if (!(request.target() instanceof RoutingRequest.RecipeTarget(RecipeEntry<?> recipe, int times))) {
            return invalidRequestOutcome(context, startNanos, "target_mode_mismatch");
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
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
        }

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
        InputVariantCache variantCache = new InputVariantCache();
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
            issueSeq,
            variantCache
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
            counters.scannedRefs,
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
            counters.budgetCulpritRecipe,
            counters.budgetCulpritInput,
            counters.budgetCulpritDepth,
            counters.budgetCulpritRemaining,
            counters.budgetCulpritActiveCycle,
            counters.explorationBranchesExpanded,
            counters.explorationBudgetUsed,
            request.explorationSeed() == null ? 0 : request.explorationSeed(),
            request.loopPolicy()
        );

        RoutingTrace trace = null;
        if (context.request().diagnosticLevel() != DiagnosticLevel.none) {
            trace = new RoutingTrace(1, buildRequestFingerprint(context), traceStages.toImmutable());
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
                "planner.finish status={} target={} planActions={} issues={} elapsedMs={} expanded={} generated={} accepted={} budgetUsed={} budgetExhausted={} budgetStop={} budgetCulprit={} budgetInput={} budgetDepth={} budgetRemaining={} budgetCycle={}",
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
                counters.budgetStopReason,
                counters.budgetCulpritRecipe,
                counters.budgetCulpritInput,
                counters.budgetCulpritDepth,
                counters.budgetCulpritRemaining,
                counters.budgetCulpritActiveCycle
            );
        }
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
        AtomicInteger issueSeq,
        InputVariantCache variantCache
    ) {
        PlannerContext.Counters counters = context.counters();
        PlannerDebugLog debugLog = new PlannerDebugLog();
        ArrayList<PlanningFrame> stack = new ArrayList<>();
        stack.add(new PlanningFrame(recipeNode, recipeIndex, times, depth));
        ArrayDeque<InputContent> activeDemands = new ArrayDeque<>();

        while (!stack.isEmpty()) {
            if (System.nanoTime() >= counters.wallDeadlineNanos && !counters.budgetExhausted) {
                // Wall clock passed: flag exhaustion and let the frame machine wind
                // down through its regular budget paths (entering -> isBudgetExceeded,
                // candidate generation -> candidateBudgetReached). Those paths roll
                // back in-flight candidate branches in LIFO order and degrade the
                // frontier into pending inputs, so work already committed survives as
                // a partial plan — exactly like a size-budget stop. A hard
                // rollback-everything abort here would discard completed actions and
                // flip the outcome to failed depending on which statement the
                // deadline happened to land on, making results machine-speed
                // dependent.
                counters.budgetExhausted = true;
                counters.budgetStopReason = "maxWallTimeMs";
                recordBudgetCulprit(context, stack, stack.get(stack.size() - 1), activePath);
            }
            PlanningFrame frame = stack.get(stack.size() - 1);
            switch (frame.state) {
                case entering -> {
                    if (isBudgetExceeded(context, counters)) {
                        // The target frame has no parent frame to degrade it into a
                        // pending input, so failing it here throws the whole plan away
                        // and reports zero actions. Only the wall clock can trip this
                        // early (the size limits cannot be met before the first
                        // expansion), and it can: the deadline is armed before the
                        // loop, so a stop-the-world pause landing right there — or a
                        // deliberately tiny wall budget — pushes it past the deadline
                        // before any work happens. Enter the target anyway; its inputs
                        // then take the budget-limited unresolved-input path and become
                        // pending inputs, so the user gets "craft this, supply these"
                        // instead of nothing at all.
                        boolean targetFrame = stack.size() == 1;
                        recordBudgetCulprit(context, stack, frame, activePath);
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.budget recipe={} depth={} target={} expanded={} budgetUsed={} budgetLimit={} generated={} reason={} culpritRecipe={} culpritInput={} culpritRemaining={} culpritCycle={}",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> frame.depth,
                                () -> targetFrame,
                                () -> counters.expandedNodes,
                                () -> counters.budgetUsed,
                                () -> context.request().budget().maxExpandedNodes(),
                                () -> counters.generatedCandidates,
                                () -> counters.budgetStopReason,
                                () -> counters.budgetCulpritRecipe,
                                () -> counters.budgetCulpritInput,
                                () -> counters.budgetCulpritRemaining,
                                () -> counters.budgetCulpritActiveCycle
                            );
                        }
                        addIssue(
                            issues,
                            issueSeq,
                            RoutingFailureCode.budgetExhausted,
                            // Non-blocking on the target frame: the plan survives as a
                            // degraded one, matching how a per-root-input cut reports.
                            targetFrame ? RoutingIssue.Severity.warning : RoutingIssue.Severity.error,
                            !targetFrame,
                            "cirrus.routing.budget.exhausted",
                            budgetIssuePayload(counters)
                        );
                        if (!targetFrame) {
                            Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                            if (completed != null) {
                                return completed;
                            }
                            continue;
                        }
                    }

                    boolean cycleReentry = isActive(activePath, frame.recipeIndex);
                    double amplificationRisk = estimateAmplificationRisk(cycleReentry, frame.depth);
                    CycleGuard.Decision decision = cycleGuard.enter(
                        frame.recipeIndex,
                        cycleReentry,
                        amplificationRisk,
                        context.ruleProfile().amplificationHardThreshold()
                    );
                    if (!decision.allowed()) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.cycle.reject recipe={} depth={} reentry={} risk={} reason={}",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> frame.depth,
                                () -> cycleReentry,
                                () -> amplificationRisk,
                                decision::reason
                            );
                        }
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
                    if (debugLog.enabled()) {
                        debugLog.debug(
                            "planner.expand recipe={} index={} times={} depth={} inputs={} outputs={} reentry={} risk={} pathDepth={}",
                            () -> describeRecipe(frame.recipeNode.recipe()),
                            () -> frame.recipeIndex,
                            () -> frame.times,
                            () -> frame.depth,
                            () -> frame.recipeNode.inputCount(),
                            () -> frame.recipeNode.outputCount(),
                            () -> cycleReentry,
                            () -> amplificationRisk,
                            activePath::size
                        );
                    }
                    frame.entered = true;
                    frame.state = FrameState.processingInputs;
                }
                case processingInputs -> {
                    if (frame.inputIndex >= frame.recipeNode.inputCount()) {
                        frame.state = FrameState.finalizing;
                        continue;
                    }

                    InputContent input = frame.recipeNode.inputAt(frame.inputIndex);
                    int inputIndex = frame.inputIndex;
                    frame.inputIndex++;
                    boolean rootInput = frame.depth == 0;

                    long required = input.amount() * frame.times;
                    if (required <= 0) {
                        continue;
                    }
                    if (rootInput) {
                        counters.beginRootInput(inputIndex, describeInput(input));
                    }
                    if (debugLog.enabled()) {
                        debugLog.debug(
                            "planner.input parent={} input={} required={} depth={} inputIndex={} notConsumed={}",
                            () -> describeRecipe(frame.recipeNode.recipe()),
                            () -> describeInput(input),
                            () -> required,
                            () -> frame.depth,
                            () -> inputIndex,
                            input::notConsumed
                        );
                    }

                    if (input.notConsumed()) {
                        planBuilder.addReserveCatalyst(input, input.amount(), frame.recipeNode.recipe());
                        if (rootInput) {
                            counters.clearRootInput();
                        }
                        continue;
                    }

                    long remaining = consumeDemandWithPolicy(
                        context,
                        input,
                        required,
                        ledger,
                        planBuilder,
                        traceStages,
                        inputIndex,
                        variantCache
                    );
                    if (remaining <= 0) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.input.covered parent={} input={} required={} remaining=0",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> describeInput(input),
                                () -> required
                            );
                        }
                        if (rootInput) {
                            counters.clearRootInput();
                        }
                        continue;
                    }

                    activeDemands.addLast(input);
                    CandidateEvaluation evaluation = evaluateCandidates(
                        context,
                        frame.recipeNode,
                        input,
                        remaining,
                        frame.depth + 1,
                        debugLog,
                        activePath,
                        activeDemands,
                        ledger,
                        rootInput,
                        variantCache
                    );
                    List<CandidateScore> candidates = evaluation.candidates();
                    if (candidates.isEmpty()) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.unresolved.noCandidates parent={} input={} remaining={} depth={} generated={} accepted={} budgetExhausted={} budgetStop={}",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> describeInput(input),
                                () -> remaining,
                                () -> frame.depth,
                                () -> counters.generatedCandidates,
                                () -> counters.acceptedCandidates,
                                () -> counters.budgetExhausted,
                                () -> counters.budgetStopReason
                            );
                        }
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            remaining,
                            UnresolvedInputContext.Phase.noSource,
                            false,
                            counters.budgetExhausted,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq,
                            variantCache,
                            frame,
                            0,
                            0,
                            evaluation.truncatedByBudget(),
                            null
                        )) {
                            if (evaluation.hasActiveCycleCandidate()
                                && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                                addIssue(
                                    issues,
                                    issueSeq,
                                    RoutingFailureCode.cycleAmplificationBlocked,
                                    RoutingIssue.Severity.warning,
                                    false,
                                    "cirrus.routing.cycle_amplification_blocked",
                                    inputIssuePayload(context, frame.recipeNode.recipe(), input, remaining)
                                );
                            }
                            if (rootInput) {
                                counters.clearRootInput();
                            }
                            activeDemands.removeLast();
                            continue;
                        }
                        if (evaluation.hasActiveCycleCandidate()
                            && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                            addIssue(
                                issues,
                                issueSeq,
                                RoutingFailureCode.cycleAmplificationBlocked,
                                RoutingIssue.Severity.error,
                                true,
                                "cirrus.routing.cycle_amplification_blocked",
                                inputIssuePayload(context, frame.recipeNode.recipe(), input, remaining)
                            );
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
                        if (rootInput) {
                            counters.clearRootInput();
                        }
                        activeDemands.removeLast();
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    if (candidates.size() > 1) {
                        candidates.sort(candidateComparator(context.ruleProfile()));
                        reorderCandidatesByTagPolicy(context, input, remaining, ledger, candidates, variantCache);
                    }
                    if (context.request().explorationEnabled()) {
                        context.ruleProfile().explorePolicy().reorderNearTies(
                            toRankedCandidates(candidates),
                            context.ruleProfile().nearTieEpsilon(),
                            context.request().explorationSeed() == null ? 0 : context.request().explorationSeed()
                        );
                    }

                    int attemptLimit = candidateAttemptLimit(context, candidates.size());
                    if (candidates.size() > attemptLimit) {
                        candidates = new ArrayList<>(candidates.subList(0, attemptLimit));
                    }
                    frame.beginCandidateResolution(
                        input,
                        inputIndex,
                        remaining,
                        candidates,
                        attemptLimit,
                        evaluation.hasActiveCycleCandidate(),
                        rootInput
                    );
                    frame.fullCandidateCount = evaluation.candidates().size();
                    List<CandidateScore> loggedCandidates = candidates;
                    if (debugLog.enabled()) {
                        debugLog.debug(
                            "planner.candidates parent={} input={} remaining={} candidates={} attemptLimit={} best={}",
                            () -> describeRecipe(frame.recipeNode.recipe()),
                            () -> describeInput(input),
                            () -> remaining,
                            loggedCandidates::size,
                            () -> frame.candidateAttemptLimit,
                            () -> loggedCandidates.isEmpty() ? "<none>" : describeRecipe(loggedCandidates.getFirst().node().recipe())
                        );
                    }
                    frame.state = FrameState.tryingCandidates;
                }
                case tryingCandidates -> {
                    InputContent input = frame.activeInput();
                    // Once the cut has been applied (rootInputBudgetCut), do not re-enter
                    // this branch: the exceeded condition stays true for the rest of the
                    // root input and would spin forever without ever reaching the
                    // exhaustion handling below.
                    if (frame.rootInput && !frame.rootInputBudgetCut && rootInputBudgetExceeded(context, counters)) {
                        recordBudgetCulprit(context, frame.recipeNode, input, frame.remaining, frame.depth + 1, frame.candidateSetHasActiveCycleCandidate);
                        counters.budgetExhausted = true;
                        counters.budgetStopReason = "maxRootInputBudget";
                        frame.rootInputBudgetCut = true;
                        frame.candidateAttemptIndex = frame.candidateAttemptLimit;
                        continue;
                    }
                    if (frame.candidateAttemptIndex >= frame.candidateAttemptLimit) {
                        if (frame.bestCandidateIndex >= 0 && !frame.recommittingBest && !frame.rootInputBudgetCut
                            && !counters.budgetExhausted) {
                            // Every probe left pending inputs behind; re-run the branch
                            // with the lowest pending cost and commit it for real this
                            // time (deterministic, so the subtree replays identically).
                            // Skipped once a budget is exhausted: the re-run would fail
                            // on entry, so go straight to the exhaustion path instead.
                            frame.candidateAttemptIndex = frame.bestCandidateIndex;
                            frame.recommittingBest = true;
                            continue;
                        }
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.unresolved.exhausted parent={} input={} remaining={} attempted={} candidates={} candidateIssues={}",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> describeInput(input),
                                () -> frame.remaining,
                                () -> frame.candidateAttemptIndex,
                                () -> frame.candidates().size(),
                                () -> frame.candidateIssues.size()
                            );
                        }
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            frame.remaining,
                            UnresolvedInputContext.Phase.candidateExhausted,
                            frame.candidateSetHasActiveCycleCandidate,
                            counters.budgetExhausted || frame.rootInputBudgetCut,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq,
                            variantCache,
                            frame,
                            frame.candidates().size(),
                            frame.candidatesTried,
                            false,
                            frame.fullCandidateCount
                        )) {
                            if (frame.rootInputBudgetCut) {
                                addIssue(
                                    issues,
                                    issueSeq,
                                    RoutingFailureCode.budgetExhausted,
                                    RoutingIssue.Severity.warning,
                                    false,
                                    "cirrus.routing.budget.exhausted",
                                    budgetIssuePayload(counters)
                                );
                            }
                            if (frame.candidateSetHasActiveCycleCandidate
                                && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                                addIssue(
                                    issues,
                                    issueSeq,
                                    RoutingFailureCode.cycleAmplificationBlocked,
                                    RoutingIssue.Severity.warning,
                                    false,
                                    "cirrus.routing.cycle_amplification_blocked",
                                    inputIssuePayload(context, frame.recipeNode.recipe(), input, frame.remaining)
                                );
                            }
                            frame.clearCandidateResolution();
                            if (frame.rootInput) {
                                counters.clearRootInput();
                            }
                            activeDemands.removeLast();
                            frame.state = FrameState.processingInputs;
                            continue;
                        }
                        if (frame.candidateIssues.notEmpty() && shouldPropagateCandidateIssues(input, variantCache)) {
                            issues.addAllIterable(frame.candidateIssues);
                            activeDemands.removeLast();
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
                        if (frame.rootInput) {
                            counters.clearRootInput();
                        }
                        activeDemands.removeLast();
                        Boolean completed = completeFrame(stack, false, activePath, cycleGuard);
                        if (completed != null) {
                            return completed;
                        }
                        continue;
                    }

                    CandidateScore candidate = frame.candidates().get(frame.candidateAttemptIndex);
                    if (debugLog.enabled()) {
                        debugLog.debug(
                            "planner.candidate.try parent={} input={} candidate={} attempt={} craftTimes={} score={} risk={} variant={}",
                            () -> describeRecipe(frame.recipeNode.recipe()),
                            () -> describeInput(input),
                            () -> describeRecipe(candidate.node().recipe()),
                            () -> frame.candidateAttemptIndex,
                            candidate::craftTimes,
                            () -> java.util.Arrays.toString(candidate.score()),
                            candidate::amplificationRisk,
                            candidate::matchedVariantIndex
                        );
                    }
                    if (frame.candidateAttemptIndex > 0) {
                        counters.explorationBranchesExpanded++;
                        counters.explorationBudgetUsed++;
                    }
                    frame.candidateAttemptIndex++;
                    frame.candidatesTried++;

                    if (candidate.cycleCandidate()
                        && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.candidate.reject candidate={} reason=cycleAmplification risk={} threshold={}",
                                () -> describeRecipe(candidate.node().recipe()),
                                () -> candidate.amplificationRisk,
                                () -> context.ruleProfile().amplificationHardThreshold()
                            );
                        }
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
                        ledger.mark(),
                        planBuilder.actionCount(),
                        planBuilder.nextActionId(),
                        issues.size(),
                        traceStages.size(),
                        frame.pendingCost
                    );
                    frame.rollback = rollback;
                    frame.state = FrameState.awaitingChildResult;
                    stack.add(new PlanningFrame(candidate.node(), candidate.recipeIndex(), candidate.craftTimes(), frame.depth + 1));
                }
                case awaitingChildResult -> {
                    InputContent input = frame.activeInput();
                    CandidateRollbackState rollback = frame.rollback();
                    if (!frame.lastChildSucceeded) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.candidate.rollback parent={} input={} remaining={} candidateIssues={}",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> describeInput(input),
                                () -> frame.remaining,
                                () -> frame.candidateIssues.size()
                            );
                        }
                        captureIssuesSince(issues, rollback.issueCount(), frame.candidateIssues, visibleIssueRecipe(context, frame.recipeNode.recipe()));
                        rollback.restore(ledger, planBuilder, issues, traceStages);
                        frame.pendingCost = rollback.pendingCostAtMark();
                        frame.rollback = null;
                        if (frame.recommittingBest) {
                            // The previously-best candidate no longer succeeds (budget
                            // cut mid-retry); force the exhaustion path instead of
                            // looping back onto the same candidate.
                            frame.candidateAttemptIndex = frame.candidateAttemptLimit;
                        }
                        frame.state = FrameState.tryingCandidates;
                        continue;
                    }

                    frame.rollback = null;
                    int childIssueStart = rollback.issueCount();
                    double subtreePendingCost = frame.pendingCost - rollback.pendingCostAtMark();
                    if (!frame.recommittingBest && subtreePendingCost > 0 && !counters.budgetExhausted) {
                        // Soft failure: the candidate resolved but left pending inputs
                        // behind. Track the lowest-pending-cost branch, roll back and
                        // probe the next candidate while candidates, the exploration
                        // budget and the near-tie band last; otherwise hand over to the
                        // exhaustion path, which re-commits the recorded best. Once any
                        // budget is exhausted the planner is winding down: keep the
                        // incumbent instead of rolling it back for probes whose re-run
                        // would be cut off anyway.
                        if (subtreePendingCost < frame.bestPendingCost) {
                            frame.bestPendingCost = subtreePendingCost;
                            frame.bestCandidateIndex = frame.candidateAttemptIndex - 1;
                        }
                        boolean canProbeMore = frame.candidateAttemptIndex < frame.candidateAttemptLimit
                            && !explorationBudgetExhausted(context, counters)
                            && nextProbeWithinTieBand(context, frame);
                        if (canProbeMore) {
                            rollback.restore(ledger, planBuilder, issues, traceStages);
                            frame.pendingCost = rollback.pendingCostAtMark();
                            frame.state = FrameState.tryingCandidates;
                            continue;
                        }
                        if (frame.bestCandidateIndex != frame.candidateAttemptIndex - 1) {
                            rollback.restore(ledger, planBuilder, issues, traceStages);
                            frame.pendingCost = rollback.pendingCostAtMark();
                            frame.candidateAttemptIndex = frame.candidateAttemptLimit;
                            frame.state = FrameState.tryingCandidates;
                            continue;
                        }
                        // The current candidate is the best so far and no more probes are
                        // possible: fall through and commit it directly, saving a re-run.
                    }
                    boolean liftedToParentFallback = false;
                    boolean rollbackRestoredForParentFallback = false;
                    if (context.ruleProfile().liftMultiSemanticChildShortages()
                        && preservesOriginalInputShortage(input, variantCache)
                        && hasOnlyNonBlockingShortages(issues, childIssueStart)) {
                        rollback.restore(ledger, planBuilder, issues, traceStages);
                        frame.pendingCost = rollback.pendingCostAtMark();
                        rollbackRestoredForParentFallback = true;
                        if (resolveUnresolvedInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            frame.remaining,
                            UnresolvedInputContext.Phase.noSource,
                            false,
                            false,
                            ledger,
                            planBuilder,
                            issues,
                            issueSeq,
                            variantCache,
                            frame,
                            frame.candidates().size(),
                            frame.candidatesTried
                        )) {
                            frame.remaining = 0;
                            liftedToParentFallback = true;
                        }
                    }
                    if (!liftedToParentFallback) {
                        if (!rollbackRestoredForParentFallback) {
                            rollback.commit(ledger);
                        }
                        long previousRemaining = frame.remaining;
                        frame.remaining = consumeDemandWithPolicy(
                            context,
                            input,
                            frame.remaining,
                            ledger,
                            planBuilder,
                            traceStages,
                            frame.activeInputIndex,
                            variantCache
                        );
                        liftSuccessfulChildIssuesToParentInput(
                            context,
                            frame.recipeNode.recipe(),
                            input,
                            previousRemaining - frame.remaining,
                            childIssueStart,
                            issues,
                            issueSeq,
                            variantCache
                        );
                    }
                    frame.resetBestExploration();
                    if (frame.remaining <= 0) {
                        if (debugLog.enabled()) {
                            debugLog.debug(
                                "planner.input.resolved parent={} input={} remaining=0",
                                () -> describeRecipe(frame.recipeNode.recipe()),
                                () -> describeInput(input)
                            );
                        }
                        frame.clearCandidateResolution();
                        if (frame.rootInput) {
                            counters.clearRootInput();
                        }
                        activeDemands.removeLast();
                        frame.state = FrameState.processingInputs;
                        continue;
                    }
                    frame.state = FrameState.tryingCandidates;
                }
                case finalizing -> {
                    ImmutableList<PlanAction.InputBinding> inputBindings = frame.buildInputBindings();
                    ImmutableList<PlanAction.OutputBinding> outputBindings = frame.buildOutputBindings();
                    planBuilder.addExecuteRecipe(
                        frame.recipeNode.recipe(),
                        frame.times,
                        inputBindings,
                        outputBindings
                    );
                    for (PlanAction.OutputBinding binding : outputBindings) {
                        ledger.addByproduct(binding.resource(), binding.amount());
                    }
                    if (debugLog.enabled()) {
                        debugLog.debug(
                            "planner.finalize recipe={} times={} inputs={} outputs={}",
                            () -> describeRecipe(frame.recipeNode.recipe()),
                            () -> frame.times,
                            inputBindings::size,
                            outputBindings::size
                        );
                    }

                    Boolean completed = completeFrame(stack, true, activePath, cycleGuard);
                    if (completed != null) {
                        return completed;
                    }
                }
            }
        }

        return false;
    }

    private CandidateEvaluation evaluateCandidates(
        PlannerContext context,
        RecipeGraph.Node parentNode,
        InputContent input,
        long remaining,
        int depth,
        PlannerDebugLog debugLog,
        Int2IntOpenHashMap activePath,
        ArrayDeque<InputContent> activeDemands,
        ResourceLedger ledger,
        boolean rootInput,
        InputVariantCache variantCache
    ) {
        int variantCount = enumerableVariantCount(input, variantCache);
        int demandLoopScc = loopSccOfInput(context, input);
        String targetResourceId = targetResourceIdOf(context);
        CandidateShortlist candidates = new CandidateShortlist(
            candidateShortlistLimit(context),
            candidateComparator(context.ruleProfile()),
            variantCount
        );
        ScoringScratch scoringScratch = new ScoringScratch(context.ruleProfile());
        ActiveDemandDependencyScratch dependencyScratch = new ActiveDemandDependencyScratch(variantCache);
        ActiveDemandSnapshot activeDemandSnapshot = dependencyScratch.snapshot(activeDemands);
        CandidateAvailabilityScratch availabilityScratch = new CandidateAvailabilityScratch(ledger, variantCache);
        // Eval floor: the budget gate may only cut producer enumeration after at
        // least this many refs were visited, so a candidateCount==0 pending can
        // never be a "budget ran out before this input was reached" artifact.
        int evalFloor = minEvalFloor(context, input, variantCache);
        if (useCandidatePreShortlist(context)) {
            CandidatePreShortlist preCandidates = new CandidatePreShortlist(
                candidatePreShortlistLimit(context, variantCount),
                context.ruleProfile(),
                variantCount
            );
            ScoringScratch preScoringScratch = new ScoringScratch(context.ruleProfile());
            int visited = 0;
            boolean truncated = false;
            for (PlannerRequestCaches.CachedProducer producer : filteredProducersFor(context, input, variantCache)) {
                if (visited >= evalFloor && candidateBudgetReached(context, parentNode, input, remaining, depth, rootInput)) {
                    truncated = true;
                    break;
                }
                visited++;
                addCandidateSeedIfAccepted(
                    context,
                    input,
                    remaining,
                    depth,
                    activePath,
                    producer,
                    preCandidates,
                    preScoringScratch,
                    availabilityScratch,
                    variantCache
                );
            }

            for (CandidateSeed seed : preCandidates.toCandidates()) {
                addCandidateIfAccepted(
                    context,
                    input,
                    remaining,
                    depth,
                    debugLog,
                    activePath,
                    seed.node(),
                    seed.recipeIndex(),
                    seed.matchedOutput(),
                    seed.matchedVariantIndex(),
                    demandLoopScc,
                    targetResourceId,
                    candidates,
                    scoringScratch,
                    dependencyScratch,
                    activeDemandSnapshot,
                    availabilityScratch,
                    variantCache,
                    false
                );
            }
            return candidates.toEvaluation(truncated, visited);
        }

        int visited = 0;
        boolean truncated = false;
        for (PlannerRequestCaches.CachedProducer producer : filteredProducersFor(context, input, variantCache)) {
            if (visited >= evalFloor && candidateBudgetReached(context, parentNode, input, remaining, depth, rootInput)) {
                truncated = true;
                break;
            }
            visited++;
            addCandidateIfAccepted(
                context,
                input,
                remaining,
                depth,
                debugLog,
                activePath,
                producer.node(),
                producer.recipeIndex(),
                producer.matchedOutput(),
                producer.matchedVariantIndex(),
                demandLoopScc,
                targetResourceId,
                candidates,
                scoringScratch,
                dependencyScratch,
                activeDemandSnapshot,
                availabilityScratch,
                variantCache,
                true
            );
        }
        return candidates.toEvaluation(truncated, visited);
    }

    /**
     * Minimum producer refs evaluated per input before budget gates may cut
     * enumeration: {@code max(1, min(minEvalFloorCap, producerScanLimit / 4))}.
     */
    private int minEvalFloor(PlannerContext context, InputContent input, InputVariantCache variantCache) {
        int scan = producerScanLimit(context, input, variantCache);
        return Math.max(1, Math.min(minEvalFloorCap, scan / 4));
    }

    /**
     * The per-request unit-cost oracle. Its producer provider is the same
     * request-static, scan-capped filter pipeline the planner uses, so cost
     * estimation never explores producers the planner itself would reject.
     * The advisory multipliers (generation / soft advisory) come from the
     * active score model; a second, multiplier-free base oracle backs the
     * {@code advisoryCostExcess} feature so the cost tier stays additive.
     */
    private UnitCostOracle unitCostOracle(PlannerContext context, InputVariantCache variantCache) {
        RuleProfile profile = context.ruleProfile();
        return context.caches().unitCostOracle(() -> new UnitCostOracle(
            context.catalog(),
            input -> filteredProducersFor(context, input, variantCache),
            advisoryPenalty(context, profile.model().generationCostMultiplier(), profile.model().softAdvisoryMultiplier())
        ));
    }

    /** The per-request base oracle: identical producer pipeline, no advisory multipliers. */
    private UnitCostOracle baseUnitCostOracle(PlannerContext context, InputVariantCache variantCache) {
        return context.caches().baseUnitCostOracle(() -> new UnitCostOracle(
            context.catalog(),
            input -> filteredProducersFor(context, input, variantCache),
            null
        ));
    }

    /**
     * Implements the advisory screening semantics inside the cost oracle:
     * generation recipes (produce without consuming — cobblegen, drills,
     * void/resource generators) have raw cost 1 and would otherwise always
     * win; the multiplier sinks them below real chains while keeping them
     * usable when they are the only source.
     */
    private static @Nullable java.util.function.IntToDoubleFunction advisoryPenalty(
        PlannerContext context,
        double generationMultiplier,
        double softMultiplier
    ) {
        java.util.BitSet generation = context.tagSnapshot().recipesForTag(dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags.GENERATION);
        java.util.BitSet soft = new java.util.BitSet();
        for (String tag : new String[]{
            dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags.RECYCLING,
            dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags.EXCHANGE,
            dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags.RECOLOR
        }) {
            java.util.BitSet bits = context.tagSnapshot().recipesForTag(tag);
            if (bits != null && !bits.isEmpty()) {
                soft.or(bits);
            }
        }
        if ((generation == null || generation.isEmpty()) && soft.isEmpty()) {
            return null;
        }
        java.util.BitSet generationBits = generation == null ? new java.util.BitSet() : generation;
        return recipeIndex -> generationBits.get(recipeIndex)
            ? generationMultiplier
            : soft.get(recipeIndex) ? softMultiplier : 1.0d;
    }

    /**
     * The request-static producer list for this input: the full producer scan
     * runs once per demand key and is cached on the request; later occurrences
     * of the same demand reuse it and only redo the dynamic scoring.
     */
    private List<PlannerRequestCaches.CachedProducer> filteredProducersFor(
        PlannerContext context,
        InputContent input,
        InputVariantCache variantCache
    ) {
        String cacheKey = context.caches().producerCacheKey(input, StandardPlanner::producerCacheKey);
        if (cacheKey != null) {
            List<PlannerRequestCaches.CachedProducer> cached = context.caches().filteredProducers(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        List<PlannerRequestCaches.CachedProducer> scanned = scanFilteredProducers(context, input, variantCache);
        if (cacheKey != null) {
            context.caches().putFilteredProducers(cacheKey, scanned);
        }
        return scanned;
    }

    /**
     * Full producer-index scan applying only request-static gates (allowed
     * recipe, non-empty matched output, heuristic policy, variant resolution).
     * Bounded by {@link #producerScanLimit} so inputs with thousands of
     * producers cannot dominate the whole planning budget; the deterministic
     * index order keeps the truncation reproducible.
     */
    private List<PlannerRequestCaches.CachedProducer> scanFilteredProducers(
        PlannerContext context,
        InputContent input,
        InputVariantCache variantCache
    ) {
        BitSet allowed = context.sourcePolicyDecision().allowedRecipes();
        PlannerContext.Counters counters = context.counters();
        List<PlannerRequestCaches.CachedProducer> producers = new ArrayList<>();
        if (!requiresCandidateScanFallback(input)) {
            int scanLimit = producerScanLimit(context, input, variantCache);
            int visited = 0;
            ProducerRefIterator producerRefs = indexedProducerRefs(context.graph(), input, variantCache);
            while (producerRefs.next()) {
                if (visited >= scanLimit) {
                    break;
                }
                visited++;
                counters.scannedRefs++;
                int recipeIndex = producerRefs.recipeIndex();
                if (!allowed.get(recipeIndex)) {
                    continue;
                }
                RecipeGraph.Node node = context.catalog().nodeAt(recipeIndex);
                PlannerRequestCaches.CachedProducer producer = staticallyFilterProducer(
                    context,
                    input,
                    node,
                    recipeIndex,
                    node.outputAt(producerRefs.outputIndex()),
                    producerRefs.matchedVariantIndex(),
                    variantCache
                );
                if (producer != null) {
                    producers.add(producer);
                }
            }
            return producers;
        }

        // Complex-content fallback: no index exists, so the whole allowed set is
        // scanned. Keep the historical unbounded scan here — complex inputs are
        // rare and truncating could drop the only matching producer.
        for (int recipeIndex = allowed.nextSetBit(0); recipeIndex >= 0; recipeIndex = allowed.nextSetBit(recipeIndex + 1)) {
            counters.scannedRefs++;
            RecipeGraph.Node node = context.catalog().nodeAt(recipeIndex);
            PlannerRequestCaches.CachedProducer producer = staticallyFilterProducer(
                context,
                input,
                node,
                recipeIndex,
                findMatchedOutput(node, input),
                -1,
                variantCache
            );
            if (producer != null) {
                producers.add(producer);
            }
        }
        return producers;
    }

    /**
     * Request-static candidate gates; returns {@code null} when the producer is
     * unusable regardless of the dynamic planning state.
     */
    private @Nullable PlannerRequestCaches.CachedProducer staticallyFilterProducer(
        PlannerContext context,
        InputContent input,
        RecipeGraph.Node node,
        int recipeIndex,
        @Nullable OutputContent matchedOutput,
        int knownMatchedVariantIndex,
        InputVariantCache variantCache
    ) {
        if (matchedOutput == null || matchedOutput.amount() <= 0) {
            return null;
        }
        if (!heuristicAllowsCandidate(context.request(), node)) {
            return null;
        }
        int matchedVariantIndex = knownMatchedVariantIndex >= 0
            ? knownMatchedVariantIndex
            : resolveMatchedVariantIndex(input, matchedOutput, variantCache);
        if (matchedVariantIndex < 0) {
            return null;
        }
        return new PlannerRequestCaches.CachedProducer(recipeIndex, node, matchedOutput, matchedVariantIndex);
    }

    /**
     * Per-input cap on producer refs visited during a full scan: proportional
     * to the shortlist size with a floor, so fat tag inputs (thousands of
     * producers) cannot starve the rest of the plan.
     */
    private int producerScanLimit(PlannerContext context, InputContent input, InputVariantCache variantCache) {
        int shortlistBased = candidateShortlistLimit(context) * 8;
        int variantBased = enumerableVariantCount(input, variantCache) * 2;
        return Math.max(256, Math.max(shortlistBased, variantBased));
    }

    /**
     * Cache key for the per-request producer filter cache: the demand key plus
     * the full tag key for tag inputs, so an item tag and a fluid tag with the
     * same location never share an entry.
     */
    private static @Nullable String producerCacheKey(InputContent input) {
        String key = ClassifyViewFactory.demandKey(input);
        if (key == null) {
            return null;
        }
        if (input instanceof TagStackContent<?> tag) {
            return key + "|" + tag.tagKey();
        }
        return key;
    }

    private boolean candidateBudgetReached(
        PlannerContext context,
        RecipeGraph.Node parentNode,
        InputContent input,
        long remaining,
        int depth
    ) {
        PlannerContext.Counters counters = context.counters();
        if (counters.generatedCandidates >= context.request().budget().maxGeneratedCandidates()) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxGeneratedCandidates";
            recordBudgetCulprit(context, parentNode, input, remaining, depth, false);
            return true;
        }
        if (System.nanoTime() >= counters.wallDeadlineNanos) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxWallTimeMs";
            recordBudgetCulprit(context, parentNode, input, remaining, depth, false);
            return true;
        }
        return false;
    }

    private boolean candidateBudgetReached(
        PlannerContext context,
        RecipeGraph.Node parentNode,
        InputContent input,
        long remaining,
        int depth,
        boolean rootInput
    ) {
        if (rootInputBudgetExceeded(context, context.counters())) {
            PlannerContext.Counters counters = context.counters();
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxRootInputBudget";
            recordBudgetCulprit(context, parentNode, input, remaining, depth, false);
            return true;
        }
        return candidateBudgetReached(context, parentNode, input, remaining, depth);
    }

    private void addCandidateSeedIfAccepted(
        PlannerContext context,
        InputContent input,
        long remaining,
        int depth,
        Int2IntOpenHashMap activePath,
        PlannerRequestCaches.CachedProducer producer,
        CandidatePreShortlist candidates,
        ScoringScratch scoringScratch,
        CandidateAvailabilityScratch availabilityScratch,
        InputVariantCache variantCache
    ) {
        RecipeGraph.Node node = producer.node();
        OutputContent matchedOutput = producer.matchedOutput();
        int craftTimes = (int) Math.ceil((double) remaining / matchedOutput.amount());
        double estimatedSubtreeCost = unitCostOracle(context, variantCache).subtreeCost(node, craftTimes);
        double baseSubtreeCost = baseSubtreeCost(context, variantCache, node, craftTimes, estimatedSubtreeCost);
        // Impurity on the candidate's own primary ranking axis: when the demand
        // has a pure sibling producer, impure (multi-guaranteed-output) recipes
        // must lose the first axis instead of tying on base cost and winning on
        // the stable tiebreak. When the impure recipe is the only kind of
        // producer for this demand, the factor is waived (sole-source exemption)
        // so last-resort sources stay reachable.
        double impurity = impurityFactorFor(context, input, node, matchedOutput, variantCache);
        if (impurity > 1.0d) {
            estimatedSubtreeCost *= impurity;
            baseSubtreeCost *= impurity;
        }
        // Round-trip filtering happens in the full addCandidateIfAccepted pass
        // (seeds carry no debug log / cycle marking here); seeds only carry the
        // impurity signal, and hard filtering is applied before any try.

        RuleProfile profile = context.ruleProfile();
        boolean cycleCandidate = isActive(activePath, producer.recipeIndex());
        double amplificationRisk = estimateAmplificationRisk(cycleCandidate, depth);
        double availableCoverage = estimateCandidateAvailableCoverage(node, craftTimes, availabilityScratch);
        boolean fastScoring = useFastScoring(context, profile);
        double[] score = scoringScratch.clear();
        if (fastScoring) {
            if (!fastHardRulesAccept(node)) {
                return;
            }
            applyModelScore(
                profile, scoringScratch.features(), node, craftTimes, depth, amplificationRisk, availableCoverage,
                baseSubtreeCost, estimatedSubtreeCost - baseSubtreeCost, 0.0d,
                false, false, false, score);
        } else {
            CandidateContext candidateContext = new CandidateContext(
                node,
                input,
                remaining,
                craftTimes,
                depth,
                cycleCandidate,
                amplificationRisk,
                availableCoverage,
                estimatedSubtreeCost
            );
            HardRuleResult hardResult = evaluateHardRules(profile.hardRules(), candidateContext);
            if (!hardResult.accepted()) {
                return;
            }
            applyModelScore(
                profile, scoringScratch.features(), node, craftTimes, depth, amplificationRisk, availableCoverage,
                baseSubtreeCost, estimatedSubtreeCost - baseSubtreeCost, 0.0d,
                false, false, false, score);
            var accumulator = scoringScratch.accumulator();
            for (var rule : profile.scoreRules()) {
                rule.contribute(candidateContext, accumulator);
            }
        }
        // Count only candidates that survived the hard gates: the generation
        // budget measures real evaluation work, not refs discarded by the
        // static filters or hard rules.
        context.counters().generatedCandidates++;
        markCandidateBudgetIfReached(context, node, input, remaining, depth, cycleCandidate, context.counters().activeRootInputIndex >= 0);
        candidates.add(
            producer.recipeIndex(),
            node,
            matchedOutput,
            craftTimes,
            producer.matchedVariantIndex(),
            score,
            context.catalog().stableRecipeKey(producer.recipeIndex())
        );
    }

    private void addCandidateIfAccepted(
        PlannerContext context,
        InputContent input,
        long remaining,
        int depth,
        PlannerDebugLog debugLog,
        Int2IntOpenHashMap activePath,
        RecipeGraph.Node node,
        int recipeIndex,
        @Nullable OutputContent matchedOutput,
        int knownMatchedVariantIndex,
        int demandLoopScc,
        @Nullable String targetResourceId,
        CandidateShortlist candidates,
        ScoringScratch scoringScratch,
        ActiveDemandDependencyScratch dependencyScratch,
        ActiveDemandSnapshot activeDemandSnapshot,
        CandidateAvailabilityScratch availabilityScratch,
        InputVariantCache variantCache,
        boolean countGenerated
    ) {
        if (matchedOutput == null || matchedOutput.amount() <= 0) {
            return;
        }
        if (!heuristicAllowsCandidate(context.request(), node)) {
            if (debugLog.enabled()) {
                debugLog.debug(
                    "planner.candidate.filtered reason=heuristic recipe={} policy={} hintPolicy={} penalty={}",
                    () -> describeRecipe(node.recipe()),
                    () -> context.request().heuristicPolicy(),
                    () -> node.routingHints().usePolicy(),
                    () -> node.routingHints().penalty()
                );
            }
            return;
        }
        int matchedVariantIndex = knownMatchedVariantIndex >= 0
            ? knownMatchedVariantIndex
            : resolveMatchedVariantIndex(input, matchedOutput, variantCache);
        if (matchedVariantIndex < 0) {
            if (debugLog.enabled()) {
                debugLog.debug(
                    "planner.candidate.filtered reason=variantMismatch recipe={} input={} outputAmount={}",
                    () -> describeRecipe(node.recipe()),
                    () -> describeInput(input),
                    matchedOutput::amount
                );
            }
            return;
        }
        int craftTimes = (int) Math.ceil((double) remaining / matchedOutput.amount());
        if (context.request().loopPolicy() == LoopPolicy.forbidAmplification
            && candidateRequiresActiveDemand(node, activeDemandSnapshot, dependencyScratch)) {
            candidates.markActiveCycleCandidate();
            if (debugLog.enabled()) {
                debugLog.debug(
                    "planner.candidate.filtered reason=activeDemandCycle recipe={} input={} depth={}",
                    () -> describeRecipe(node.recipe()),
                    () -> describeInput(input),
                    () -> depth
                );
            }
            return;
        }
        boolean indirectActiveDemandCycle = context.request().loopPolicy() == LoopPolicy.forbidAmplification
            && candidateHasUncoveredActiveDemandDependency(
                context,
                node,
                craftTimes,
                activeDemandSnapshot,
                dependencyScratch,
                availabilityScratch
            );
        if (indirectActiveDemandCycle) {
            candidates.markActiveCycleCandidate();
            if (debugLog.enabled()) {
                debugLog.debug(
                    "planner.candidate.penalized reason=indirectActiveDemandCycle recipe={} input={} depth={}",
                    () -> describeRecipe(node.recipe()),
                    () -> describeInput(input),
                    () -> depth
                );
            }
        }
        // SCC-aware loop guard: when the demanded resource belongs to a looping
        // family (e.g. the 16 concrete colors), candidates that consume another
        // member of the same family are conversion steps that can ping-pong;
        // penalize them so direct "exit the family" crafts win the scoring.
        // Tag/list demands have no concrete resource id; fall back to the
        // matched output's family so the guard still fires there.
        int effectiveDemandLoopScc = demandLoopScc >= 0 ? demandLoopScc : loopSccOfOutput(context, matchedOutput);
        boolean intraLoopConversion = effectiveDemandLoopScc >= 0 && candidateConvertsWithinLoop(context, node, effectiveDemandLoopScc);
        if (intraLoopConversion) {
            candidates.markActiveCycleCandidate();
            if (debugLog.enabled()) {
                debugLog.debug(
                    "planner.candidate.penalized reason=intraLoopConversion recipe={} input={} depth={}",
                    () -> describeRecipe(node.recipe()),
                    () -> describeInput(input),
                    () -> depth
                );
            }
        }
        // Contextual screening: request-scoped rules (target family, machine
        // ownership) add their penalties on top of the static classification.
        double contextualPenalty = contextualPenalty(context, node, input, targetResourceId, depth);
        if (contextualPenalty > 0.0d && debugLog.enabled()) {
            debugLog.debug(
                "planner.candidate.penalized reason=contextual recipe={} input={} depth={} penalty={}",
                () -> describeRecipe(node.recipe()),
                () -> describeInput(input),
                () -> depth,
                () -> contextualPenalty
            );
        }

        RuleProfile profile = context.ruleProfile();
        boolean cycleCandidate = isActive(activePath, recipeIndex);
        double amplificationRisk = estimateAmplificationRisk(cycleCandidate, depth);
        double availableCoverage = estimateCandidateAvailableCoverage(node, craftTimes, availabilityScratch);
        double estimatedSubtreeCost = unitCostOracle(context, variantCache).subtreeCost(node, craftTimes);
        double baseSubtreeCost = baseSubtreeCost(context, variantCache, node, craftTimes, estimatedSubtreeCost);
        // Same impurity/round-trip handling as the seed path: primary-axis
        // impurity (waived when no pure sibling exists) and hard round-trip
        // filtering under forbidAmplification / soft penalty otherwise.
        double impurity = impurityFactorFor(context, input, node, matchedOutput, variantCache);
        if (impurity > 1.0d) {
            estimatedSubtreeCost *= impurity;
            baseSubtreeCost *= impurity;
        }
        boolean roundTripWithActive = hasActiveRoundTripPartner(context, recipeIndex, activePath);
        if (roundTripWithActive) {
            if (context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                candidates.markActiveCycleCandidate();
                if (debugLog.enabled()) {
                    debugLog.debug(
                        "planner.candidate.filtered reason=roundTrip recipe={} input={} depth={}",
                        () -> describeRecipe(node.recipe()),
                        () -> describeInput(input),
                        () -> depth
                    );
                }
                return;
            }
        }
        boolean fastScoring = useFastScoring(context, profile);
        double[] score = scoringScratch.clear();
        CandidateContext candidateContext = null;
        if (fastScoring) {
            if (!fastHardRulesAccept(node)) {
                if (debugLog.enabled()) {
                    debugLog.debug(
                        "planner.candidate.filtered reason=hardRule recipe={} input={} remaining={} depth={} risk={} coverage={}",
                        () -> describeRecipe(node.recipe()),
                        () -> describeInput(input),
                        () -> remaining,
                        () -> depth,
                        () -> amplificationRisk,
                        () -> availableCoverage
                    );
                }
                return;
            }
            applyModelScore(
                profile, scoringScratch.features(), node, craftTimes, depth, amplificationRisk, availableCoverage,
                baseSubtreeCost, estimatedSubtreeCost - baseSubtreeCost, contextualPenalty,
                indirectActiveDemandCycle, intraLoopConversion, roundTripWithActive, score);
        } else {
            candidateContext = new CandidateContext(
                node,
                input,
                remaining,
                craftTimes,
                depth,
                cycleCandidate,
                amplificationRisk,
                availableCoverage,
                estimatedSubtreeCost,
                indirectActiveDemandCycle,
                intraLoopConversion,
                roundTripWithActive,
                node.routingHints(),
                contextualPenalty
            );
            HardRuleResult hardResult = evaluateHardRules(profile.hardRules(), candidateContext);
            if (!hardResult.accepted()) {
                if (debugLog.enabled()) {
                    debugLog.debug(
                        "planner.candidate.filtered reason=hardRule recipe={} input={} remaining={} depth={} risk={} coverage={}",
                        () -> describeRecipe(node.recipe()),
                        () -> describeInput(input),
                        () -> remaining,
                        () -> depth,
                        () -> amplificationRisk,
                        () -> availableCoverage
                    );
                }
                return;
            }
            applyModelScore(
                profile, scoringScratch.features(), node, craftTimes, depth, amplificationRisk, availableCoverage,
                baseSubtreeCost, estimatedSubtreeCost - baseSubtreeCost, contextualPenalty,
                indirectActiveDemandCycle, intraLoopConversion, roundTripWithActive, score);
            var accumulator = scoringScratch.accumulator();
            for (var rule : profile.scoreRules()) {
                rule.contribute(candidateContext, accumulator);
            }
        }
        // Count only candidates that survived the hard gates: the generation
        // budget measures real evaluation work, not refs discarded by the
        // static filters or hard rules.
        if (countGenerated) {
            context.counters().generatedCandidates++;
            markCandidateBudgetIfReached(context, node, input, remaining, depth, cycleCandidate, context.counters().activeRootInputIndex >= 0);
        }
        context.counters().acceptedCandidates++;
        candidates.add(
            recipeIndex,
            node,
            craftTimes,
            score,
            candidateContext,
            cycleCandidate,
            amplificationRisk,
            matchedVariantIndex,
            context.catalog().stableRecipeKey(recipeIndex)
        );
    }

    private boolean useFastScoring(PlannerContext context, RuleProfile profile) {
        return profile.fastPathEligible()
            && DefaultRuleProfiles.usesStableRecipeTieBreak(profile)
            && !context.request().explorationEnabled();
    }

    private boolean fastHardRulesAccept(RecipeGraph.Node node) {
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            if (!node.inputAt(inputIndex).constantly()) {
                return false;
            }
        }
        for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
            if (!node.outputAt(outputIndex).constantly()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fills the candidate's feature vector from the planner's per-candidate
     * scalars and evaluates the profile's compiled score model into the
     * tier-sum vector. This is the single place where candidate state becomes
     * score input — every magnitude applied afterwards lives in the score
     * model's declared weights, not in code.
     */
    private void applyModelScore(
        RuleProfile profile,
        FeatureVector features,
        RecipeGraph.Node node,
        int craftTimes,
        int depth,
        double amplificationRisk,
        double availableCoverage,
        double baseSubtreeCost,
        double advisoryCostExcess,
        double contextualPenalty,
        boolean indirectActiveDemandCycle,
        boolean intraLoopConversion,
        boolean roundTripWithActive,
        double[] tierSums
    ) {
        features.clear();
        features.set(FeatureType.subtreeCost, baseSubtreeCost);
        features.set(FeatureType.advisoryCostExcess, advisoryCostExcess);
        features.set(FeatureType.contextualPenalty, contextualPenalty);
        features.set(FeatureType.craftTimes, craftTimes);
        features.set(FeatureType.inventoryShortage, 1.0d - availableCoverage);
        features.set(FeatureType.depth, depth);
        features.set(FeatureType.amplificationRisk, amplificationRisk);
        features.set(FeatureType.activeDemandCycle, indirectActiveDemandCycle ? 1.0d : 0.0d);
        features.set(FeatureType.roundTrip, roundTripWithActive ? 1.0d : 0.0d);
        features.set(FeatureType.intraLoopConversion, intraLoopConversion ? 1.0d : 0.0d);
        dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints hints = node.routingHints();
        features.set(FeatureType.hintDiscouraged, hints.usePolicy() == RecipeUsePolicy.discouraged ? 1.0d : 0.0d);
        features.set(FeatureType.hintLastResort, hints.usePolicy() == RecipeUsePolicy.lastResort ? 1.0d : 0.0d);
        features.set(FeatureType.hintPenalty, hints.penalty());
        profile.evaluateFeatures(features, tierSums);
    }

    /**
     * The multiplier-free base cost of the candidate. When the request carries
     * no advisory-tagged recipes the advisory oracle is identical to the base
     * oracle, so the cached full cost is returned directly and no second
     * oracle is ever built.
     */
    private double baseSubtreeCost(
        PlannerContext context,
        InputVariantCache variantCache,
        RecipeGraph.Node node,
        int craftTimes,
        double fullSubtreeCost
    ) {
        if (advisoryPenalty(
            context,
            context.ruleProfile().model().generationCostMultiplier(),
            context.ruleProfile().model().softAdvisoryMultiplier()
        ) == null) {
            return fullSubtreeCost;
        }
        return baseUnitCostOracle(context, variantCache).subtreeCost(node, craftTimes);
    }

    /**
     * Whether any round-trip partner (equal-amount inverse recipe — compress↔
     * decompress, revert↔_revert) of the candidate is already on the active
     * path: accepting the candidate would close a net-zero loop.
     */
    private boolean hasActiveRoundTripPartner(PlannerContext context, int recipeIndex, Int2IntOpenHashMap activePath) {
        if (!roundTripEnabled) {
            return false;
        }
        int[] partners = context.service().topology().roundTripPartnerOf(recipeIndex);
        if (partners.length == 0) {
            return false;
        }
        for (int partner : partners) {
            if (isActive(activePath, partner)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Primary-axis impurity factor for one (demand, candidate) pair: the
     * candidate's guaranteed byproduct count relative to the demanded output,
     * waived entirely when no pure producer exists for this demand (sole-source
     * exemption keeps impure last-resort sources reachable).
     */
    private double impurityFactorFor(
        PlannerContext context,
        InputContent input,
        RecipeGraph.Node node,
        dev.vfyjxf.cirrus.api.recipe.content.OutputContent matchedOutput,
        InputVariantCache variantCache
    ) {
        if (!impurityEnabled) {
            return 1.0d;
        }
        UnitCostOracle oracle = unitCostOracle(context, variantCache);
        if (oracle.impurityFactor(node, matchedOutput) <= 1.0d) {
            return 1.0d;
        }
        // Sole-source exemption: scan the same filtered producer set the planner
        // uses; if any producer is pure for this demand, the impure candidate is
        // fairly penalized, otherwise it is the only kind of source and stays.
        // (The impure candidate itself never counts as the pure sibling.)
        for (PlannerRequestCaches.CachedProducer producer : filteredProducersFor(context, input, variantCache)) {
            if (producer.node() != node && oracle.impurityFactor(producer.node(), producer.matchedOutput()) <= 1.0d) {
                return oracle.impurityFactor(node, matchedOutput);
            }
        }
        return 1.0d;
    }

    /**
     * The looping SCC of the demanded input's resource, or -1 when the input
     * is not a concrete stack or its resource belongs to no loop family.
     */
    private int loopSccOfInput(PlannerContext context, InputContent input) {
        String resourceId = ClassifyViewFactory.resourceIdOf(input);
        if (resourceId == null) {
            return -1;
        }
        return context.service().topology().loopingSccIdOf(resourceId);
    }

    /**
     * The looping SCC of a matched output's resource, or -1. Used as the
     * demand-family fallback for tag/list demands, where the input slot has
     * no single resource id but the producing candidate's output does.
     */
    private int loopSccOfOutput(PlannerContext context, @Nullable OutputContent output) {
        String resourceId = ClassifyViewFactory.resourceIdOf(output);
        if (resourceId == null) {
            return -1;
        }
        return context.service().topology().loopingSccIdOf(resourceId);
    }

    /**
     * The concrete resource id of the request target's primary output, or
     * null when the target has no concrete-stack output. Derived per demand
     * evaluation; both the catalog lookup and the output scan are O(1)-ish.
     */
    private @Nullable String targetResourceIdOf(PlannerContext context) {
        if (!(context.request().target() instanceof RoutingRequest.RecipeTarget(RecipeEntry<?> recipe, int ignored))) {
            return null;
        }
        int targetIndex = context.catalog().indexOf(recipe);
        if (targetIndex < 0) {
            return null;
        }
        RecipeGraph.Node targetNode = context.catalog().nodeAt(targetIndex);
        for (int outputIndex = 0; outputIndex < targetNode.outputCount(); outputIndex++) {
            String resourceId = ClassifyViewFactory.resourceIdOf(targetNode.outputAt(outputIndex));
            if (resourceId != null) {
                return resourceId;
            }
        }
        return null;
    }

    /**
     * Total contextual screening penalty for the candidate, or 0 when
     * screening is disabled, no contextual rules are registered, or no rule
     * has an opinion on this candidate.
     */
    private double contextualPenalty(
        PlannerContext context,
        RecipeGraph.Node node,
        InputContent input,
        @Nullable String targetResourceId,
        int depth
    ) {
        if (!dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig.calculatorRecipeScreeningEnabled()
            || ContextualRecipeRules.isEmpty()) {
            return 0.0d;
        }
        return ContextualRecipeRules.totalPenalty(new ContextualRuleContext(
            context.service(),
            context.request(),
            node,
            input,
            ClassifyViewFactory.resourceIdOf(input),
            targetResourceId,
            depth
        ));
    }

    /**
     * Whether the candidate recipe consumes another member of the demanded
     * resource's loop family — the recolor/exchange pattern that can ping-pong
     * (white -&gt; red -&gt; blue -&gt; white) when no direct craft wins.
     */
    private boolean candidateConvertsWithinLoop(PlannerContext context, RecipeGraph.Node node, int demandLoopScc) {
        GraphTopology topology = context.service().topology();
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            InputContent candidateInput = node.inputAt(inputIndex);
            if (candidateInput.isEmpty() || candidateInput.notConsumed()) {
                continue;
            }
            String id = ClassifyViewFactory.resourceIdOf(candidateInput);
            if (id != null && topology.sccIdOf(id) == demandLoopScc) {
                return true;
            }
        }
        return false;
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

    private void markCandidateBudgetIfReached(
        PlannerContext context,
        RecipeGraph.Node node,
        InputContent input,
        long remaining,
        int depth,
        boolean cycleCandidate,
        boolean rootInput
    ) {
        PlannerContext.Counters counters = context.counters();
        if (counters.generatedCandidates >= context.request().budget().maxGeneratedCandidates()) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxGeneratedCandidates";
            recordBudgetCulprit(context, node, input, remaining, depth, cycleCandidate);
        }
        if (rootInput && rootInputBudgetExceeded(context, counters)) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxRootInputBudget";
            recordBudgetCulprit(context, node, input, remaining, depth, cycleCandidate);
        }
    }

    private ProducerRefIterator indexedProducerRefs(RecipeGraph graph, InputContent input, InputVariantCache variantCache) {
        ContentResource direct = directInputResource(input);
        if (direct != null) {
            return new CursorProducerRefIterator(graph.producerCursorFor(direct), 0);
        }
        if (!input.isEnumerable()) {
            return EmptyProducerRefIterator.instance;
        }

        return variantCache.producerRefs(graph, input).iterator();
    }

    private static ProducerRefSnapshot copyProducerRefs(RecipeGraph graph, InputContent input, InputVariantCache variantCache) {
        LongOpenHashSet refs = new LongOpenHashSet();
        Long2IntOpenHashMap matchedVariantIndices = new Long2IntOpenHashMap();
        matchedVariantIndices.defaultReturnValue(-1);
        ContentVariant[] variants = variantCache.variants(input);
        for (int variantIndex = 0; variantIndex < variants.length; variantIndex++) {
            ContentVariant variant = variants[variantIndex];
            RecipeGraph.OutputRefCursor cursor = graph.producerCursorFor(variant);
            while (cursor.next()) {
                long ref = packProducerRef(cursor.recipeIndex(), cursor.outputIndex());
                if (refs.add(ref)) {
                    matchedVariantIndices.put(ref, variantIndex);
                }
            }
        }
        if (refs.isEmpty()) {
            return ProducerRefSnapshot.empty;
        }
        long[] snapshotRefs = refs.toLongArray();
        Arrays.sort(snapshotRefs);
        int[] snapshotVariantIndices = new int[snapshotRefs.length];
        for (int i = 0; i < snapshotRefs.length; i++) {
            snapshotVariantIndices[i] = matchedVariantIndices.get(snapshotRefs[i]);
        }
        return new ProducerRefSnapshot(snapshotRefs, snapshotVariantIndices);
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

    private static long packProducerRef(int recipeIndex, int outputIndex) {
        return ((long) recipeIndex << 32) | (outputIndex & 0xFFFF_FFFFL);
    }

    private static int producerRecipeIndex(long ref) {
        return (int) (ref >>> 32);
    }

    private static int producerOutputIndex(long ref) {
        return (int) ref;
    }

    private double estimateCandidateAvailableCoverage(
        RecipeGraph.Node node,
        int craftTimes,
        CandidateAvailabilityScratch availabilityScratch
    ) {
        double totalRequired = 0.0d;
        double totalCovered = 0.0d;
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            InputContent input = node.inputAt(inputIndex);
            long required = requiredCandidateInputAmount(input, craftTimes);
            if (required <= 0L) {
                continue;
            }

            long available = Math.min(required, availabilityScratch.peekAvailable(input));
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
        boolean defaultStableTieBreak = DefaultRuleProfiles.usesStableRecipeTieBreak(profile);
        return (left, right) -> {
            int cmp = profile.rankPolicy().compare(left.score, right.score);
            if (cmp != 0) {
                return cmp;
            }
            if (defaultStableTieBreak) {
                return left.stableKey.compareTo(right.stableKey);
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
        boolean budgetLimited,
        ResourceLedger ledger,
        PlanBuilder planBuilder,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        InputVariantCache variantCache,
        PlanningFrame frame,
        int candidateCount,
        int attemptedCount
    ) {
        return resolveUnresolvedInput(
            context, recipe, input, amount, phase, activeCycleCandidate, budgetLimited,
            ledger, planBuilder, issues, issueSeq, variantCache, frame,
            candidateCount, attemptedCount, false, null
        );
    }

    private boolean resolveUnresolvedInput(
        PlannerContext context,
        RecipeEntry<?> recipe,
        InputContent input,
        long amount,
        UnresolvedInputContext.Phase phase,
        boolean activeCycleCandidate,
        boolean budgetLimited,
        ResourceLedger ledger,
        PlanBuilder planBuilder,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq,
        InputVariantCache variantCache,
        PlanningFrame frame,
        int candidateCount,
        int attemptedCount,
        boolean truncatedByBudget,
        @Nullable Integer fullCandidateCount
    ) {
        UnresolvedInputRule.Resolution resolution = context.ruleProfile().unresolvedInputRule().resolve(
            new UnresolvedInputContext(recipe, input, amount, phase, activeCycleCandidate, budgetLimited)
        );
        if (resolution != UnresolvedInputRule.Resolution.pendingInput) {
            return false;
        }

        // Truncation analysis: separate pendings a deeper wave could still
        // resolve (budget/ranking cut) from genuinely unreachable demands, so
        // continuation runs and audits stop conflating the two.
        boolean truncated;
        PlanAction.PendingReason reason;
        if (phase == UnresolvedInputContext.Phase.noSource) {
            if (truncatedByBudget) {
                truncated = true;
                reason = PlanAction.PendingReason.BUDGET_TRUNCATED;
            } else {
                truncated = false;
                reason = PlanAction.PendingReason.TRUE_NO_SOURCE;
            }
        } else {
            if (budgetLimited) {
                truncated = true;
                reason = PlanAction.PendingReason.BUDGET_TRUNCATED;
            } else if (fullCandidateCount != null && fullCandidateCount > candidateCount) {
                truncated = true;
                reason = PlanAction.PendingReason.RANK_TRUNCATED;
            } else {
                truncated = false;
                reason = PlanAction.PendingReason.CASCADE_EXHAUSTED;
            }
        }

        // Execution-oriented fallback: preserve the original input semantics instead
        // of collapsing tags/lists into a planner-chosen concrete stack.
        ledger.recordPendingInput(input, amount, planBuilder, candidateCount, attemptedCount, truncated, reason);
        frame.pendingCost += amount * unitCostOracle(context, variantCache).unitCost(input);
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

    /**
     * Exploration (re-trying candidates after soft or hard failures) shares the
     * generated-candidate budget: at most {@code explorationBudgetShare} of it may
     * be spent on branches beyond the first attempt.
     */
    private boolean explorationBudgetExhausted(PlannerContext context, PlannerContext.Counters counters) {
        double share = context.request().budget().explorationBudgetShare();
        if (share <= 0) {
            return true;
        }
        long limit = Math.max(1L, (long) (context.request().budget().maxGeneratedCandidates() * share));
        return counters.explorationBudgetUsed >= limit;
    }

    /**
     * Soft-failure probes are only worth their budget while the next candidate is
     * a near-tie of the greedy first choice on the leading score axis (predicted
     * subtree cost): once candidates leave the band, the heuristic ordering is
     * trusted and the recorded best is recommitted instead. Ungated probing on fat
     * filtered catalogs burns the whole per-root budget on clearly-worse branches
     * (e.g. decompress ping-pong chains) and leaves the input pending with nothing
     * committed.
     */
    private boolean nextProbeWithinTieBand(PlannerContext context, PlanningFrame frame) {
        List<CandidateScore> candidates = frame.candidates();
        if (candidates.isEmpty() || frame.candidateAttemptIndex >= candidates.size()) {
            return false;
        }
        double[] first = candidates.getFirst().score();
        double[] next = candidates.get(frame.candidateAttemptIndex).score();
        if (first.length == 0 || next.length == 0) {
            return true;
        }
        // Tiers before the cost tier (safety flags) must tie exactly: a
        // cycle-flagged candidate is never a near-tie of a clean one.
        int costTier = Math.min(context.ruleProfile().costTierIndex(), Math.min(first.length, next.length) - 1);
        for (int tier = 0; tier < costTier; tier++) {
            if (Double.compare(first[tier], next[tier]) != 0) {
                return false;
            }
        }
        double epsilon = Math.max(0.0d, context.ruleProfile().nearTieEpsilon());
        double reference = Math.max(1.0d, Math.abs(first[costTier]));
        return next[costTier] <= first[costTier] + epsilon * reference;
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
            case auto, assuredBounded, assuredProven -> boundedWindow;
        };
        return Math.min(candidateCount, Math.max(1, modeWindow));
    }

    private int candidateShortlistLimit(PlannerContext context) {
        RuleProfile profile = context.ruleProfile();
        int shortlist = Math.max(1, profile.shortlistK());
        int rollbackWindow = Math.max(1, profile.rollbackWidth() + 1);
        return switch (context.request().plannerMode()) {
            case standard -> Math.min(shortlist, rollbackWindow);
            case auto, assuredBounded, assuredProven -> Math.max(shortlist, rollbackWindow);
        };
    }

    private boolean useCandidatePreShortlist(PlannerContext context) {
        return useFastScoring(context, context.ruleProfile())
            && context.sourcePolicyDecision().allowedRecipes().cardinality() > candidatePreShortlistMinimum;
    }

    private int candidatePreShortlistLimit(PlannerContext context, int variantCount) {
        int shortlistLimit = candidateShortlistLimit(context);
        int variantRetention = Math.min(Math.max(0, variantCount), candidatePreShortlistVariantRetentionLimit);
        return Math.max(candidatePreShortlistMinimum, shortlistLimit * candidatePreShortlistMultiplier + variantRetention);
    }

    private int enumerableVariantCount(InputContent input, InputVariantCache variantCache) {
        return input.isEnumerable() ? variantCache.count(input) : 0;
    }

    /**
     * Candidate-specific child failures are only meaningful when the parent input already
     * points at one exact semantic target.
     *
     * <p>For tag/list/multi-variant inputs, surfacing child shortages would leak whichever
     * expanded candidate happened to be tried, which loses fidelity and misrepresents the
     * real unresolved demand. In those cases the planner must report the original input.
     */
    private boolean shouldPropagateCandidateIssues(InputContent input, InputVariantCache variantCache) {
        return !preservesOriginalInputShortage(input, variantCache);
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
        AtomicInteger issueSeq,
        InputVariantCache variantCache
    ) {
        if (resolvedAmount <= 0
            || !context.ruleProfile().liftMultiSemanticChildShortages()
            || !preservesOriginalInputShortage(parentInput, variantCache)
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

    private boolean preservesOriginalInputShortage(InputContent input, InputVariantCache variantCache) {
        return input.preserveOriginalShortageSemantics() || hasMultipleSemanticAlternatives(input, variantCache);
    }

    private boolean hasMultipleSemanticAlternatives(InputContent input, InputVariantCache variantCache) {
        return input.isEnumerable() && variantCache.count(input) > 1;
    }

    private long consumeDemandWithPolicy(
        PlannerContext context,
        InputContent input,
        long required,
        ResourceLedger ledger,
        PlanBuilder planBuilder,
        MutableList<RoutingTrace.TraceStage> traceStages,
        int inputIndex,
        InputVariantCache variantCache
    ) {
        if (!input.isEnumerable()) {
            return ledger.consumeDemand(input, required, planBuilder);
        }
        if (input instanceof ValueContent<?> value) {
            return required - ledger.consumeValueDemand(input, value.type(), required, planBuilder);
        }

        List<VariantCoverage> ordered = collectOrderedVariantCoverage(
            context,
            context.request().effectiveTagResolvePolicy(),
            input,
            required,
            ledger,
            null,
            variantCache
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
        List<CandidateScore> candidates,
        InputVariantCache variantCache
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
            candidates,
            variantCache
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
        List<CandidateScore> candidates,
        InputVariantCache variantCache
    ) {
        int[] bestCandidateRanks = candidates == null ? null : bestCandidateRanksByVariant(input, candidates, variantCache);
        List<VariantCoverage> variants = new ArrayList<>();
        int index = 0;
        for (ContentVariant variant : variantCache.variants(input)) {
            long stockAvail;
            long byproductAvail;
            if (variant instanceof ContentStack<?, ?> stack) {
                stockAvail = ledger.peekStockAvailableNormalized(stack);
                byproductAvail = ledger.peekByproductAvailableNormalized(stack);
            } else {
                stockAvail = ledger.peekStockAvailable(variant);
                byproductAvail = ledger.peekByproductAvailable(variant);
            }
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

    private int[] bestCandidateRanksByVariant(InputContent input, List<CandidateScore> candidates, InputVariantCache variantCache) {
        int variantCount = variantCache.count(input);
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

    private int resolveMatchedVariantIndex(InputContent input, OutputContent output, InputVariantCache variantCache) {
        if (!input.isEnumerable()) {
            return input.satisfy(output.asResource()) ? 0 : -1;
        }
        int index = 0;
        for (ContentVariant variant : variantCache.variants(input)) {
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

    private boolean candidateRequiresActiveDemand(
        RecipeGraph.Node node,
        ActiveDemandSnapshot activeDemandSnapshot,
        ActiveDemandDependencyScratch scratch
    ) {
        if (activeDemandSnapshot.demands().length == 0) {
            return false;
        }
        for (InputContent demand : activeDemandSnapshot.demands()) {
            for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
                InputContent candidateInput = node.inputAt(inputIndex);
                if (scratch.inputOverlapsDemand(this, candidateInput, demand)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean candidateHasUncoveredActiveDemandDependency(
        PlannerContext context,
        RecipeGraph.Node node,
        int craftTimes,
        ActiveDemandSnapshot activeDemandSnapshot,
        ActiveDemandDependencyScratch scratch,
        CandidateAvailabilityScratch availabilityScratch
    ) {
        if (activeDemandSnapshot.demands().length == 0) {
            return false;
        }
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            InputContent candidateInput = node.inputAt(inputIndex);
            if (candidateInput.isEmpty() || candidateInput.notConsumed()) {
                continue;
            }
            long required = requiredCandidateInputAmount(candidateInput, craftTimes);
            if (required <= 0L || availabilityScratch.peekAvailable(candidateInput) >= required) {
                continue;
            }
            if (scratch.inputCanReachActiveDemand(this, context, candidateInput, activeDemandSnapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean inputCanReachActiveDemand(
        PlannerContext context,
        InputContent rootInput,
        ActiveDemandSnapshot activeDemandSnapshot,
        ActiveDemandDependencyScratch scratch
    ) {
        scratch.reset();
        if (inputOverlapsAnyActiveDemand(rootInput, activeDemandSnapshot, scratch)) {
            return true;
        }
        pushProducerRefs(context, rootInput, scratch);
        while (!scratch.isEmpty()
            && scratch.visitedNodes < activeDemandDependencyNodeLimit
            && scratch.producerRefsVisited < activeDemandDependencyProducerLimit) {
            int recipeIndex = scratch.pop();
            if (!scratch.markVisited(recipeIndex)) {
                continue;
            }
            RecipeGraph.Node node = context.catalog().nodeAt(recipeIndex);
            scratch.visitedNodes++;
            for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
                InputContent input = node.inputAt(inputIndex);
                if (input.isEmpty() || input.notConsumed()) {
                    continue;
                }
                if (inputOverlapsAnyActiveDemand(input, activeDemandSnapshot, scratch)) {
                    return true;
                }
                pushProducerRefs(context, input, scratch);
            }
        }
        return false;
    }

    private void pushProducerRefs(
        PlannerContext context,
        InputContent input,
        ActiveDemandDependencyScratch scratch
    ) {
        ProducerRefIterator producerRefs = indexedProducerRefs(context.graph(), input, scratch.variantCache);
        while (producerRefs.next() && scratch.producerRefsVisited < activeDemandDependencyProducerLimit) {
            scratch.producerRefsVisited++;
            int recipeIndex = producerRefs.recipeIndex();
            if (!context.sourcePolicyDecision().allowedRecipes().get(recipeIndex)) {
                continue;
            }
            if (scratch.isUnseen(recipeIndex)) {
                scratch.push(recipeIndex);
            }
        }
    }

    private boolean inputOverlapsAnyActiveDemand(
        InputContent input,
        ActiveDemandSnapshot activeDemandSnapshot,
        ActiveDemandDependencyScratch scratch
    ) {
        for (InputContent demand : activeDemandSnapshot.demands()) {
            if (scratch.inputOverlapsDemand(this, input, demand)) {
                return true;
            }
        }
        return false;
    }

    private boolean inputOverlapsDemand(InputContent input, InputContent demand) {
        return inputOverlapsDemand(input, demand, null);
    }

    private boolean inputOverlapsDemand(InputContent input, InputContent demand, @Nullable InputVariantCache variantCache) {
        if (input.isEmpty() || input.notConsumed()) {
            return false;
        }
        if (input.mergeable(demand)) {
            return true;
        }
        if (input.isEnumerable()) {
            if (!demand.isEnumerable()) {
                ContentResource demandResource = directInputResource(demand);
                return demandResource != null && input.satisfy(demandResource);
            }
            for (ContentVariant demandVariant : demand.enumerate()) {
                if (variantSatisfiesInput(input, demandVariant, variantCache)) {
                    return true;
                }
            }
            return false;
        }
        if (demand.isEnumerable()) {
            for (ContentVariant demandVariant : demand.enumerate()) {
                if (variantSatisfiesInput(input, demandVariant, variantCache)) {
                    return true;
                }
                if (demandVariant instanceof ContentResource resource && input.satisfy(resource)) {
                    return true;
                }
            }
            return false;
        }
        ContentResource demandResource = directInputResource(demand);
        return demandResource != null && input.satisfy(demandResource);
    }

    private boolean variantSatisfiesInput(InputContent input, ContentVariant variant, @Nullable InputVariantCache variantCache) {
        if (variant instanceof ContentResource resource) {
            ContentResource direct = directInputResource(input);
            if (direct != null) {
                return direct.matches(resource, MatchStrategy.identityAndComponent)
                    || resource.matches(direct, MatchStrategy.identityAndComponent);
            }
            return input.satisfy(resource);
        }
        if (!input.isEnumerable()) {
            return false;
        }
        if (variantCache == null) {
            for (ContentVariant inputVariant : input.enumerate()) {
                if (variantsOverlap(inputVariant, variant)) {
                    return true;
                }
            }
        } else {
            for (ContentVariant inputVariant : variantCache.variants(input)) {
                if (variantsOverlap(inputVariant, variant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class ActiveDemandDependencyScratch {
        private static final byte stateUnknown = 0;
        private static final byte stateSeen = 1;

        private final InputVariantCache variantCache;
        private final Int2ByteOpenHashMap stateByRecipe = new Int2ByteOpenHashMap();
        private int[] stack = new int[32];
        private int stackSize;
        private int visitedNodes;
        private int producerRefsVisited;

        private ActiveDemandDependencyScratch(InputVariantCache variantCache) {
            this.variantCache = variantCache;
            stateByRecipe.defaultReturnValue(stateUnknown);
        }

        private boolean inputOverlapsDemand(StandardPlanner planner, InputContent input, InputContent demand) {
            return variantCache.overlapMemo.inputOverlapsDemand(planner, input, demand);
        }

        private ActiveDemandSnapshot snapshot(ArrayDeque<InputContent> activeDemands) {
            if (activeDemands.isEmpty()) {
                return emptyActiveDemandSnapshot;
            }
            InputContent[] demands = activeDemands.toArray(new InputContent[0]);
            int hash = 1;
            for (InputContent demand : demands) {
                hash = 31 * hash + System.identityHashCode(demand);
            }
            return new ActiveDemandSnapshot(demands, hash);
        }

        private boolean inputCanReachActiveDemand(
            StandardPlanner planner,
            PlannerContext context,
            InputContent input,
            ActiveDemandSnapshot activeDemandSnapshot
        ) {
            ActiveReachKey key = new ActiveReachKey(input, activeDemandSnapshot);
            HashMap<ActiveReachKey, Boolean> reachesActiveDemand = variantCache.reachesActiveDemand;
            Boolean cached = reachesActiveDemand.get(key);
            if (cached != null) {
                return cached;
            }
            boolean result = planner.inputCanReachActiveDemand(context, input, activeDemandSnapshot, this);
            reachesActiveDemand.put(key, result);
            return result;
        }

        private void reset() {
            stateByRecipe.clear();
            stackSize = 0;
            visitedNodes = 0;
            producerRefsVisited = 0;
        }

        private void push(int recipeIndex) {
            if (stackSize == stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[stackSize++] = recipeIndex;
        }

        private boolean isUnseen(int recipeIndex) {
            return stateByRecipe.get(recipeIndex) == stateUnknown;
        }

        private boolean markVisited(int recipeIndex) {
            if (stateByRecipe.get(recipeIndex) == stateSeen) {
                return false;
            }
            stateByRecipe.put(recipeIndex, stateSeen);
            return true;
        }

        private int pop() {
            return stack[--stackSize];
        }

        private boolean isEmpty() {
            return stackSize == 0;
        }
    }

    private record ActiveDemandSnapshot(
        InputContent[] demands,
        int identityHash
    ) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ActiveDemandSnapshot other)
                || identityHash != other.identityHash
                || demands.length != other.demands.length) {
                return false;
            }
            for (int i = 0; i < demands.length; i++) {
                if (demands[i] != other.demands[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return identityHash;
        }
    }

    private static final class ActiveReachKey {
        private final InputContent input;
        private final ActiveDemandSnapshot activeDemandSnapshot;
        private final int hash;

        private ActiveReachKey(InputContent input, ActiveDemandSnapshot activeDemandSnapshot) {
            this.input = input;
            this.activeDemandSnapshot = activeDemandSnapshot;
            this.hash = 31 * System.identityHashCode(input) + activeDemandSnapshot.identityHash();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ActiveReachKey other
                && input == other.input
                && activeDemandSnapshot.equals(other.activeDemandSnapshot);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class OverlapMemo {
        private final InputVariantCache variantCache;
        private final HashMap<OverlapKey, Boolean> overlaps = new HashMap<>();

        private OverlapMemo(InputVariantCache variantCache) {
            this.variantCache = variantCache;
        }

        private boolean inputOverlapsDemand(StandardPlanner planner, InputContent input, InputContent demand) {
            OverlapKey key = new OverlapKey(input, demand);
            Boolean cached = overlaps.get(key);
            if (cached != null) {
                return cached;
            }
            boolean result = planner.inputOverlapsDemand(input, demand, variantCache);
            overlaps.put(key, result);
            return result;
        }
    }

    private static final class OverlapKey {
        private final InputContent input;
        private final InputContent demand;
        private final int hash;

        private OverlapKey(InputContent input, InputContent demand) {
            this.input = input;
            this.demand = demand;
            this.hash = 31 * System.identityHashCode(input) + System.identityHashCode(demand);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OverlapKey other
                && input == other.input
                && demand == other.demand;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private boolean variantsOverlap(ContentVariant left, ContentVariant right) {
        if (left instanceof ContentResource leftResource && right instanceof ContentResource rightResource) {
            return leftResource.matches(rightResource, MatchStrategy.identityAndComponent)
                || rightResource.matches(leftResource, MatchStrategy.identityAndComponent);
        }
        if (left instanceof ContentKind<?> leftKind && right instanceof ContentKind<?> rightKind) {
            return leftKind.matches(rightKind);
        }
        if (left instanceof ContentKind<?> leftKind && right instanceof ContentStack<?, ?> rightStack) {
            return rightStack.matchesContent(leftKind);
        }
        if (right instanceof ContentKind<?> rightKind && left instanceof ContentStack<?, ?> leftStack) {
            return leftStack.matchesContent(rightKind);
        }
        if (left instanceof ContentValue<?> leftValue && right instanceof ContentValue<?> rightValue) {
            return leftValue.matches(rightValue);
        }
        return Objects.equals(RoutingVariantKeys.variantKey(left), RoutingVariantKeys.variantKey(right));
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
        for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
            OutputContent output = node.outputAt(outputIndex);
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
        if (System.nanoTime() >= counters.wallDeadlineNanos) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxWallTimeMs";
            return true;
        }
        if (rootInputBudgetExceeded(context, counters)) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxRootInputBudget";
            return true;
        }
        return false;
    }

    private boolean rootInputBudgetExceeded(PlannerContext context, PlannerContext.Counters counters) {
        if (counters.activeRootInputIndex < 0) {
            return false;
        }
        int maxExpanded = context.request().budget().maxRootInputExpandedNodes();
        if (maxExpanded > 0 && counters.expandedNodes - counters.rootInputExpandedStart >= maxExpanded) {
            return true;
        }
        int maxGenerated = context.request().budget().maxRootInputGeneratedCandidates();
        return maxGenerated > 0 && counters.generatedCandidates - counters.rootInputGeneratedStart >= maxGenerated;
    }

    private void recordBudgetCulprit(
        PlannerContext context,
        ArrayList<PlanningFrame> stack,
        PlanningFrame frame,
        Int2IntOpenHashMap activePath
    ) {
        PlannerContext.Counters counters = context.counters();
        if (!"none".equals(counters.budgetCulpritRecipe)) {
            return;
        }
        PlanningFrame parent = stack.size() >= 2 ? stack.get(stack.size() - 2) : null;
        InputContent input = parent == null ? frame.activeInput : parent.activeInput;
        long remaining = parent == null ? frame.remaining : parent.remaining;
        counters.recordBudgetCulprit(
            describeRecipe(frame.recipeNode.recipe()),
            input == null ? "none" : describeInput(input),
            frame.depth,
            Math.max(0L, remaining),
            isActive(activePath, frame.recipeIndex)
        );
    }

    private void recordBudgetCulprit(
        PlannerContext context,
        RecipeGraph.Node recipeNode,
        InputContent input,
        long remaining,
        int depth,
        boolean activeCycleCandidate
    ) {
        PlannerContext.Counters counters = context.counters();
        if (!"none".equals(counters.budgetCulpritRecipe)) {
            return;
        }
        counters.recordBudgetCulprit(
            describeRecipe(recipeNode.recipe()),
            describeInput(input),
            depth,
            Math.max(0L, remaining),
            activeCycleCandidate
        );
    }

    private RoutingIssuePayload budgetIssuePayload(PlannerContext.Counters counters) {
        return new RoutingIssuePayload.BudgetExhaustion(
            counters.budgetStopReason,
            counters.budgetCulpritRecipe,
            counters.budgetCulpritInput,
            counters.budgetCulpritDepth,
            counters.budgetCulpritRemaining,
            counters.budgetCulpritActiveCycle
        );
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
        parent.pendingCost += frame.pendingCost;
        return null;
    }

    private double estimateAmplificationRisk(boolean cycleCandidate, int depth) {
        double risk = 0.05 + Math.min(depth, 20) * 0.02;
        if (cycleCandidate) {
            risk += 0.45;
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
            context.counters().scannedRefs,
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
            context.counters().budgetCulpritRecipe,
            context.counters().budgetCulpritInput,
            context.counters().budgetCulpritDepth,
            context.counters().budgetCulpritRemaining,
            context.counters().budgetCulpritActiveCycle,
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
        @Nullable CandidateContext context,
        boolean cycleCandidate,
        double amplificationRisk,
        int matchedVariantIndex,
        RoutingRecipeCatalog.StableRecipeKey stableKey
    ) {
    }

    private record CandidateSeed(
        int recipeIndex,
        RecipeGraph.Node node,
        OutputContent matchedOutput,
        int craftTimes,
        int matchedVariantIndex,
        double[] score,
        RoutingRecipeCatalog.StableRecipeKey stableKey
    ) {
    }

    private record CandidateEvaluation(
        List<CandidateScore> candidates,
        boolean hasActiveCycleCandidate,
        boolean truncatedByBudget,
        int producersVisited
    ) {
    }

    private static final class ScoringScratch {
        private final double[] score;
        private final dev.vfyjxf.cirrus.api.recipe.routing.rule.ScoreAccumulator accumulator;
        private final FeatureVector features = new FeatureVector();

        private ScoringScratch(RuleProfile profile) {
            this.score = profile.newScoreVector();
            this.accumulator = profile.newAccumulator(score);
        }

        private double[] clear() {
            Arrays.fill(score, 0.0d);
            return score;
        }

        private dev.vfyjxf.cirrus.api.recipe.routing.rule.ScoreAccumulator accumulator() {
            return accumulator;
        }

        private FeatureVector features() {
            return features;
        }
    }

    private interface ProducerRefIterator {
        boolean next();

        int recipeIndex();

        int outputIndex();

        int matchedVariantIndex();
    }

    private enum EmptyProducerRefIterator implements ProducerRefIterator {
        instance;

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public int recipeIndex() {
            throw new NoSuchElementException();
        }

        @Override
        public int outputIndex() {
            throw new NoSuchElementException();
        }

        @Override
        public int matchedVariantIndex() {
            return -1;
        }
    }

    private static final class CursorProducerRefIterator implements ProducerRefIterator {
        private final RecipeGraph.OutputRefCursor cursor;
        private final int matchedVariantIndex;

        private CursorProducerRefIterator(RecipeGraph.OutputRefCursor cursor, int matchedVariantIndex) {
            this.cursor = cursor;
            this.matchedVariantIndex = matchedVariantIndex;
        }

        @Override
        public boolean next() {
            return cursor.next();
        }

        @Override
        public int recipeIndex() {
            return cursor.recipeIndex();
        }

        @Override
        public int outputIndex() {
            return cursor.outputIndex();
        }

        @Override
        public int matchedVariantIndex() {
            return matchedVariantIndex;
        }
    }

    private record ProducerRefSnapshot(
        long[] refs,
        int[] matchedVariantIndices
    ) {
        private static final ProducerRefSnapshot empty = new ProducerRefSnapshot(new long[0], new int[0]);

        private ProducerRefIterator iterator() {
            return refs.length == 0 ? EmptyProducerRefIterator.instance : new SnapshotProducerRefIterator(this);
        }
    }

    private static final class SnapshotProducerRefIterator implements ProducerRefIterator {
        private final ProducerRefSnapshot snapshot;
        private int index = -1;

        private SnapshotProducerRefIterator(ProducerRefSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public boolean next() {
            if (index + 1 >= snapshot.refs.length) {
                return false;
            }
            index++;
            return true;
        }

        @Override
        public int recipeIndex() {
            if (index < 0 || index >= snapshot.refs.length) {
                throw new NoSuchElementException();
            }
            return producerRecipeIndex(snapshot.refs[index]);
        }

        @Override
        public int outputIndex() {
            if (index < 0 || index >= snapshot.refs.length) {
                throw new NoSuchElementException();
            }
            return producerOutputIndex(snapshot.refs[index]);
        }

        @Override
        public int matchedVariantIndex() {
            if (index < 0 || index >= snapshot.matchedVariantIndices.length) {
                return -1;
            }
            return snapshot.matchedVariantIndices[index];
        }
    }

    private static final class InputVariantCache {
        private final IdentityHashMap<InputContent, ContentVariant[]> variantsByInput = new IdentityHashMap<>();
        private final IdentityHashMap<InputContent, ProducerRefSnapshot> producerRefsByInput = new IdentityHashMap<>();
        /**
         * Plan-scoped overlap memo. {@code inputOverlapsDemand} is a pure function
         * of the two input contents, so results stay valid for the whole plan run;
         * keeping the memo here (instead of inside the per-evaluation
         * {@link ActiveDemandDependencyScratch}) lets sibling and retried
         * evaluations reuse it — it is the dominant candidate-evaluation cost on
         * large filtered catalogs.
         */
        private final OverlapMemo overlapMemo = new OverlapMemo(this);
        /** Plan-scoped memo for {@code inputCanReachActiveDemand}; pure per
         * (input, active-demand-set) within a plan, see {@link #overlapMemo}. */
        private final HashMap<ActiveReachKey, Boolean> reachesActiveDemand = new HashMap<>();

        private ContentVariant[] variants(InputContent input) {
            if (!input.isEnumerable()) {
                return emptyVariants;
            }
            return variantsByInput.computeIfAbsent(input, this::copyVariants);
        }

        private int count(InputContent input) {
            return variants(input).length;
        }

        private ContentVariant[] copyVariants(InputContent input) {
            ArrayList<ContentVariant> variants = new ArrayList<>();
            for (ContentVariant variant : input.enumerate()) {
                if (variant instanceof ContentStack<?, ?> stack) {
                    variants.add(stack.normalize());
                } else {
                    variants.add(variant);
                }
            }
            return variants.isEmpty() ? emptyVariants : variants.toArray(new ContentVariant[0]);
        }

        private ProducerRefSnapshot producerRefs(RecipeGraph graph, InputContent input) {
            return producerRefsByInput.computeIfAbsent(input, key -> copyProducerRefs(graph, key, this));
        }
    }

    private static final class CandidateAvailabilityScratch {
        private final ResourceLedger ledger;
        private final InputVariantCache variantCache;
        private final IdentityHashMap<InputContent, Long> availableByInput = new IdentityHashMap<>();
        private final HashMap<ContentStack<?, ?>, Long> availableByDirectStack = new HashMap<>();
        private final IdentityHashMap<Object, Long> availableByValueType = new IdentityHashMap<>();

        private CandidateAvailabilityScratch(ResourceLedger ledger, InputVariantCache variantCache) {
            this.ledger = ledger;
            this.variantCache = variantCache;
        }

        private long peekAvailable(InputContent input) {
            Long cached = availableByInput.get(input);
            if (cached != null) {
                return cached;
            }
            Long semanticCached = semanticDirectAvailable(input);
            if (semanticCached != null) {
                availableByInput.put(input, semanticCached);
                return semanticCached;
            }
            long available = input.isEnumerable()
                ? ledger.peekAvailable(input, variantCache.variants(input))
                : ledger.peekAvailable(input);
            availableByInput.put(input, available);
            rememberSemanticDirectAvailable(input, available);
            return available;
        }

        private @Nullable Long semanticDirectAvailable(InputContent input) {
            if (input instanceof ValueContent<?> value) {
                return availableByValueType.get(value.type());
            }
            if (input instanceof StackContent<?, ?> stack) {
                return availableByDirectStack.get(stack.content().normalize());
            }
            return null;
        }

        private void rememberSemanticDirectAvailable(InputContent input, long available) {
            if (input instanceof ValueContent<?> value) {
                availableByValueType.put(value.type(), available);
            } else if (input instanceof StackContent<?, ?> stack) {
                availableByDirectStack.put(stack.content().normalize(), available);
            }
        }
    }

    private static final class CandidateShortlist {
        private final int limit;
        private final Comparator<CandidateScore> comparator;
        private final ArrayList<CandidateScore> candidates;
        private final @Nullable ArrayList<CandidateScore> bestByVariant;
        private int worstCandidateIndex = -1;
        private boolean worstCandidateDirty;
        private boolean hasActiveCycleCandidate;

        private CandidateShortlist(int limit, Comparator<CandidateScore> comparator, int variantCount) {
            this.limit = Math.max(1, limit);
            this.comparator = comparator;
            this.candidates = new ArrayList<>(this.limit);
            this.bestByVariant = variantCount > 0 ? new ArrayList<>(variantCount) : null;
            if (this.bestByVariant != null) {
                for (int i = 0; i < variantCount; i++) {
                    this.bestByVariant.add(null);
                }
            }
        }

        private void add(
            int recipeIndex,
            RecipeGraph.Node node,
            int craftTimes,
            double[] scratchScore,
            @Nullable CandidateContext context,
            boolean cycleCandidate,
            double amplificationRisk,
            int matchedVariantIndex,
            RoutingRecipeCatalog.StableRecipeKey stableKey
        ) {
            if (cycleCandidate) {
                hasActiveCycleCandidate = true;
            }
            CandidateScore candidate = null;
            boolean retained = candidates.size() < limit;
            int replaceIndex = -1;
            CandidateScore scratchCandidate = null;
            if (!retained && limit > 0) {
                int worstIndex = worstCandidateIndex();
                CandidateScore worst = candidates.get(worstIndex);
                scratchCandidate = new CandidateScore(
                    recipeIndex,
                    node,
                    craftTimes,
                    scratchScore,
                    context,
                    cycleCandidate,
                    amplificationRisk,
                    matchedVariantIndex,
                    stableKey
                );
                if (comparator.compare(scratchCandidate, worst) < 0) {
                    retained = true;
                    replaceIndex = worstIndex;
                }
            }
            boolean retainedByVariant = false;
            if (bestByVariant != null && matchedVariantIndex >= 0 && matchedVariantIndex < bestByVariant.size()) {
                CandidateScore existing = bestByVariant.get(matchedVariantIndex);
                if (existing == null) {
                    retainedByVariant = true;
                } else {
                    if (scratchCandidate == null) {
                        scratchCandidate = new CandidateScore(
                            recipeIndex,
                            node,
                            craftTimes,
                            scratchScore,
                            context,
                            cycleCandidate,
                            amplificationRisk,
                            matchedVariantIndex,
                            stableKey
                        );
                    }
                    retainedByVariant = comparator.compare(scratchCandidate, existing) < 0;
                }
            }
            if (retained || retainedByVariant) {
                candidate = new CandidateScore(
                    recipeIndex,
                    node,
                    craftTimes,
                    Arrays.copyOf(scratchScore, scratchScore.length),
                    context,
                    cycleCandidate,
                    amplificationRisk,
                    matchedVariantIndex,
                    stableKey
                );
            }
            if (candidate == null) {
                return;
            }
            if (bestByVariant != null && candidate.matchedVariantIndex() >= 0) {
                int variantIndex = candidate.matchedVariantIndex();
                if (variantIndex < bestByVariant.size()) {
                    CandidateScore existing = bestByVariant.get(variantIndex);
                    if (existing == null || comparator.compare(candidate, existing) < 0) {
                        bestByVariant.set(variantIndex, candidate);
                    }
                }
            }
            if (!retained) {
                return;
            }
            if (replaceIndex >= 0) {
                candidates.set(replaceIndex, candidate);
                worstCandidateDirty = true;
                return;
            }
            if (candidates.size() < limit) {
                candidates.add(candidate);
                worstCandidateDirty = true;
            }
        }

        private void markActiveCycleCandidate() {
            hasActiveCycleCandidate = true;
        }

        private CandidateEvaluation toEvaluation(boolean truncatedByBudget, int producersVisited) {
            if (bestByVariant != null) {
                for (CandidateScore candidate : bestByVariant) {
                    if (candidate != null) {
                        addRetainedVariantCandidate(candidate);
                    }
                }
            }
            if (candidates.size() > 1) {
                candidates.sort(comparator);
            }
            return new CandidateEvaluation(candidates, hasActiveCycleCandidate, truncatedByBudget, producersVisited);
        }

        private void addRetainedVariantCandidate(CandidateScore candidate) {
            for (CandidateScore existing : candidates) {
                if (existing == candidate) {
                    return;
                }
            }
            candidates.add(candidate);
        }

        private int worstCandidateIndex() {
            if (!worstCandidateDirty && worstCandidateIndex >= 0 && worstCandidateIndex < candidates.size()) {
                return worstCandidateIndex;
            }
            int worstIndex = 0;
            CandidateScore worst = candidates.getFirst();
            for (int i = 1; i < candidates.size(); i++) {
                CandidateScore current = candidates.get(i);
                if (comparator.compare(current, worst) > 0) {
                    worst = current;
                    worstIndex = i;
                }
            }
            worstCandidateIndex = worstIndex;
            worstCandidateDirty = false;
            return worstIndex;
        }
    }

    private static final class CandidatePreShortlist {
        private final int limit;
        private final RuleProfile profile;
        private final boolean defaultStableTieBreak;
        private final ArrayList<CandidateSeed> candidates;
        private final @Nullable ArrayList<CandidateSeed> bestByVariant;
        private int worstCandidateIndex = -1;
        private boolean worstCandidateDirty;

        private CandidatePreShortlist(int limit, RuleProfile profile, int variantCount) {
            this.limit = Math.max(1, limit);
            this.profile = profile;
            this.defaultStableTieBreak = DefaultRuleProfiles.usesStableRecipeTieBreak(profile);
            this.candidates = new ArrayList<>(this.limit);
            this.bestByVariant = variantCount > 0 && variantCount <= candidatePreShortlistVariantRetentionLimit
                ? new ArrayList<>(variantCount)
                : null;
            if (this.bestByVariant != null) {
                for (int i = 0; i < variantCount; i++) {
                    this.bestByVariant.add(null);
                }
            }
        }

        private void add(
            int recipeIndex,
            RecipeGraph.Node node,
            OutputContent matchedOutput,
            int craftTimes,
            int matchedVariantIndex,
            double[] scratchScore,
            RoutingRecipeCatalog.StableRecipeKey stableKey
        ) {
            CandidateSeed candidate = null;
            boolean retained = candidates.size() < limit;
            int replaceIndex = -1;
            if (!retained) {
                int worstIndex = worstCandidateIndex();
                CandidateSeed worst = candidates.get(worstIndex);
                if (compareScratchTo(recipeIndex, scratchScore, stableKey, worst) < 0) {
                    retained = true;
                    replaceIndex = worstIndex;
                }
            }

            boolean retainedByVariant = false;
            if (bestByVariant != null && matchedVariantIndex >= 0 && matchedVariantIndex < bestByVariant.size()) {
                CandidateSeed existing = bestByVariant.get(matchedVariantIndex);
                if (existing == null) {
                    retainedByVariant = true;
                } else {
                    retainedByVariant = compareScratchTo(recipeIndex, scratchScore, stableKey, existing) < 0;
                }
            }

            if (retained || retainedByVariant) {
                candidate = new CandidateSeed(
                    recipeIndex,
                    node,
                    matchedOutput,
                    craftTimes,
                    matchedVariantIndex,
                    Arrays.copyOf(scratchScore, scratchScore.length),
                    stableKey
                );
            }
            if (candidate == null) {
                return;
            }

            if (bestByVariant != null && matchedVariantIndex >= 0 && matchedVariantIndex < bestByVariant.size()) {
                CandidateSeed existing = bestByVariant.get(matchedVariantIndex);
                if (existing == null || compareCandidateTo(candidate, existing) < 0) {
                    bestByVariant.set(matchedVariantIndex, candidate);
                }
            }
            if (!retained) {
                return;
            }
            if (replaceIndex >= 0) {
                candidates.set(replaceIndex, candidate);
                worstCandidateDirty = true;
                return;
            }
            if (candidates.size() < limit) {
                candidates.add(candidate);
                worstCandidateDirty = true;
            }
        }

        private List<CandidateSeed> toCandidates() {
            if (bestByVariant != null) {
                for (CandidateSeed candidate : bestByVariant) {
                    if (candidate != null) {
                        addRetainedVariantCandidate(candidate);
                    }
                }
            }
            if (candidates.size() > 1) {
                candidates.sort(this::compareCandidateTo);
            }
            return candidates;
        }

        private void addRetainedVariantCandidate(CandidateSeed candidate) {
            for (CandidateSeed existing : candidates) {
                if (existing == candidate) {
                    return;
                }
            }
            candidates.add(candidate);
        }

        private int worstCandidateIndex() {
            if (!worstCandidateDirty && worstCandidateIndex >= 0 && worstCandidateIndex < candidates.size()) {
                return worstCandidateIndex;
            }
            int worstIndex = 0;
            CandidateSeed worst = candidates.getFirst();
            for (int i = 1; i < candidates.size(); i++) {
                CandidateSeed current = candidates.get(i);
                if (compareCandidateTo(current, worst) > 0) {
                    worst = current;
                    worstIndex = i;
                }
            }
            worstCandidateIndex = worstIndex;
            worstCandidateDirty = false;
            return worstIndex;
        }

        private int compareScratchTo(
            int recipeIndex,
            double[] scratchScore,
            RoutingRecipeCatalog.StableRecipeKey stableKey,
            CandidateSeed existing
        ) {
            int cmp = profile.rankPolicy().compare(scratchScore, existing.score);
            if (cmp != 0) {
                return cmp;
            }
            return defaultStableTieBreak
                ? stableKey.compareTo(existing.stableKey)
                : Integer.compare(recipeIndex, existing.recipeIndex);
        }

        private int compareCandidateTo(CandidateSeed left, CandidateSeed right) {
            int cmp = profile.rankPolicy().compare(left.score, right.score);
            if (cmp != 0) {
                return cmp;
            }
            return defaultStableTieBreak
                ? left.stableKey.compareTo(right.stableKey)
                : Integer.compare(left.recipeIndex, right.recipeIndex);
        }
    }

    private static final class PlannerDebugLog {
        private int count;
        private boolean omitted;

        private boolean enabled() {
            return logger.isDebugEnabled();
        }

        private void debug(String format, Supplier<?>... arguments) {
            if (!enabled()) {
                return;
            }
            if (!reserveSlot()) {
                return;
            }
            logger.debug(format, evaluate(arguments));
        }

        private boolean reserveSlot() {
            if (count < detailLogLimit) {
                count++;
                return true;
            }
            if (!omitted) {
                logger.debug("planner.detail.omitted after={} reason=detailLogLimit", detailLogLimit);
                omitted = true;
            }
            count++;
            return false;
        }

        private Object[] evaluate(Supplier<?>[] arguments) {
            Object[] values = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                values[i] = arguments[i].get();
            }
            return values;
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
        private boolean entered;
        private int inputIndex;
        private FrameState state = FrameState.entering;
        private @Nullable InputContent activeInput;
        private int activeInputIndex = -1;
        private long remaining;
        private List<CandidateScore> candidates = List.of();
        /** Candidates before the top-K attempt window trimmed the list; ranking-truncation signal. */
        private int fullCandidateCount;
        private int candidateAttemptLimit;
        private int candidateAttemptIndex;
        private boolean candidateSetHasActiveCycleCandidate;
        private boolean rootInput;
        private boolean rootInputBudgetCut;
        private MutableList<RoutingIssue> candidateIssues = Lists.mutable.empty();
        private @Nullable CandidateRollbackState rollback;
        private boolean lastChildSucceeded;
        /**
         * Accumulated estimated cost ({@code Σ amount × oracle.unitCost}) of pending
         * leaves produced by this subtree, including completed children. Drives the
         * soft-failure exploration in {@code awaitingChildResult}.
         */
        private double pendingCost;
        private int bestCandidateIndex = -1;
        private double bestPendingCost = Double.MAX_VALUE;
        private boolean recommittingBest;
        /** Candidates actually dispatched for the active input (unlike
         * {@code candidateAttemptIndex}, which is also force-set by budget cuts). */
        private int candidatesTried;

        private PlanningFrame(RecipeGraph.Node recipeNode, int recipeIndex, int times, int depth) {
            this.recipeNode = recipeNode;
            this.recipeIndex = recipeIndex;
            this.times = times;
            this.depth = depth;
        }

        private void beginCandidateResolution(
            InputContent input,
            int inputIndex,
            long remaining,
            List<CandidateScore> candidates,
            int candidateAttemptLimit,
            boolean candidateSetHasActiveCycleCandidate,
            boolean rootInput
        ) {
            this.activeInput = input;
            this.activeInputIndex = inputIndex;
            this.remaining = remaining;
            this.candidates = candidates;
            this.candidateAttemptLimit = Math.min(candidates.size(), Math.max(1, candidateAttemptLimit));
            this.candidateAttemptIndex = 0;
            this.candidateSetHasActiveCycleCandidate = candidateSetHasActiveCycleCandidate;
            this.rootInput = rootInput;
            this.rootInputBudgetCut = false;
            this.candidateIssues = Lists.mutable.empty();
            this.rollback = null;
            this.lastChildSucceeded = false;
            resetBestExploration();
        }

        private void clearCandidateResolution() {
            activeInput = null;
            activeInputIndex = -1;
            remaining = 0;
            candidates = List.of();
            fullCandidateCount = 0;
            candidateAttemptLimit = 0;
            candidateAttemptIndex = 0;
            candidateSetHasActiveCycleCandidate = false;
            rootInput = false;
            rootInputBudgetCut = false;
            candidateIssues = Lists.mutable.empty();
            rollback = null;
            lastChildSucceeded = false;
            resetBestExploration();
        }

        private void resetBestExploration() {
            bestCandidateIndex = -1;
            bestPendingCost = Double.MAX_VALUE;
            recommittingBest = false;
            candidatesTried = 0;
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

        private ImmutableList<PlanAction.InputBinding> buildInputBindings() {
            int inputCount = recipeNode.inputCount();
            if (inputCount == 0) {
                return Lists.immutable.empty();
            }
            var bindings = Lists.mutable.<PlanAction.InputBinding>ofInitialCapacity(inputCount);
            for (int inputIndex = 0; inputIndex < inputCount; inputIndex++) {
                InputContent input = recipeNode.inputAt(inputIndex);
                long required = input.amount() * times;
                if (required > 0L) {
                    bindings.add(new PlanAction.InputBinding(input, required));
                }
            }
            return bindings.toImmutable();
        }

        private ImmutableList<PlanAction.OutputBinding> buildOutputBindings() {
            int outputCount = recipeNode.outputCount();
            if (outputCount == 0) {
                return Lists.immutable.empty();
            }
            var bindings = Lists.mutable.<PlanAction.OutputBinding>ofInitialCapacity(outputCount);
            for (int outputIndex = 0; outputIndex < outputCount; outputIndex++) {
                OutputContent output = recipeNode.outputAt(outputIndex);
                long produced = output.amount() * times;
                if (produced > 0L) {
                    bindings.add(new PlanAction.OutputBinding(output.asResource(), produced));
                }
            }
            return bindings.toImmutable();
        }

        private void close(Int2IntOpenHashMap activePath, CycleGuard cycleGuard) {
            if (!entered) {
                return;
            }
            leaveActive(activePath, recipeIndex);
            cycleGuard.leave(recipeIndex);
        }
    }

    private record CandidateRollbackState(
        ResourceLedger.Mark ledgerMark,
        int planActionCount,
        int planNextActionId,
        int issueCount,
        int traceCount,
        double pendingCostAtMark
    ) {
        private void restore(
            ResourceLedger currentLedger,
            PlanBuilder currentPlan,
            MutableList<RoutingIssue> currentIssues,
            MutableList<RoutingTrace.TraceStage> currentTraceStages
        ) {
            currentLedger.rollback(ledgerMark);
            currentPlan.restore(planActionCount, planNextActionId);
            trimToSize(currentIssues, issueCount);
            trimToSize(currentTraceStages, traceCount);
        }

        private void commit(ResourceLedger currentLedger) {
            currentLedger.commit(ledgerMark);
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

