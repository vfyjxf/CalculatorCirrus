package dev.vfyjxf.cirrus.api.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ExplorePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRuleResult;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.service.TagResolvePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace;
import dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard;
import dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 *     <li>Recursively resolving the best candidate's inputs.</li>
 * </ol>
 *
 * <p>Key design choices:
 * <ul>
 *     <li><b>Deterministic heuristic selection</b> — candidates are ranked by a
 *         multi-dimensional score vector with lexicographic comparison, then by
 *         a stable tie-break policy based on recipe identity.</li>
 *     <li><b>Small rollback width</b> — when the best candidate fails, the planner
 *         tries up to {@link RuleProfile#shortlistK()} alternatives before
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

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        long startNanos = System.nanoTime();
        PlannerContext.Counters counters = context.counters();

        RoutingRequest request = context.request();
        if (!(request.target() instanceof RoutingRequest.RecipeTarget target)) {
            return invalidRequestOutcome(context, startNanos, "target_mode_mismatch");
        }

        int targetIndex = context.catalog().indexOf(target.recipe());
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
            target.recipe(),
            target.times()
        );
        ResourceLedger ledger = new ResourceLedger(request.stock().copy());
        CycleGuard cycleGuard = new CycleGuard(request.loopPolicy(), request.loopBudget());
        IntOpenHashSet activePath = new IntOpenHashSet();
        MutableList<RoutingIssue> issues = Lists.mutable.empty();
        AtomicInteger issueSeq = new AtomicInteger();
        MutableList<RoutingTrace.TraceStage> traceStages = Lists.mutable.empty();

        boolean success = resolveRecipe(
            context,
            targetNode,
            targetIndex,
            target.times(),
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
        IntOpenHashSet activePath,
        PlanBuilder planBuilder,
        MutableList<RoutingTrace.TraceStage> traceStages,
        MutableList<RoutingIssue> issues,
        AtomicInteger issueSeq
    ) {
        PlannerContext.Counters counters = context.counters();
        if (isBudgetExceeded(context, counters)) {
            addIssue(issues, issueSeq, RoutingFailureCode.budgetExhausted, RoutingIssue.Severity.error, true, "cirrus.routing.budget.exhausted");
            return false;
        }

        int sccId = context.sccAnalysis().sccOf(recipeIndex);
        double amplificationRisk = estimateAmplificationRisk(context, activePath.contains(recipeIndex), sccId, depth);
        CycleGuard.Decision decision = cycleGuard.enter(
            recipeIndex,
            sccId,
            amplificationRisk,
            context.ruleProfile().amplificationHardThreshold()
        );
        if (!decision.allowed()) {
            addIssue(issues, issueSeq, decision.failureCode(), RoutingIssue.Severity.error, true, "cirrus.routing." + decision.reason());
            return false;
        }

        activePath.add(recipeIndex);
        counters.expandedNodes++;

        List<PlanAction.InputBinding> inputBindings = new ArrayList<>();
        List<PlanAction.OutputBinding> outputBindings = new ArrayList<>();
        var mergedInputs = recipeNode.mergedInputs().toList();

        try {
            for (int inputIndex = 0; inputIndex < mergedInputs.size(); inputIndex++) {
                InputContent input = mergedInputs.get(inputIndex);
                long required = input.amount() * times;
                if (required <= 0) {
                    continue;
                }
                inputBindings.add(new PlanAction.InputBinding(input, required));
                if (input.notConsumed()) {
                    planBuilder.addReserveCatalyst(input, input.amount(), recipeNode.recipe());
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
                    continue;
                }

                List<CandidateScore> candidates = evaluateCandidates(
                    context,
                    input,
                    remaining,
                    depth + 1,
                    activePath
                );
                if (candidates.isEmpty()) {
                    addIssue(issues, issueSeq, RoutingFailureCode.noFeasibleSource, RoutingIssue.Severity.error, true, "cirrus.routing.no_source");
                    return false;
                }

                candidates.sort(candidateComparator(context.ruleProfile()));
                reorderCandidatesByTagPolicy(context, input, remaining, ledger, candidates);
                if (context.request().explorationEnabled()) {
                    context.ruleProfile().explorePolicy().reorderNearTies(
                        toRankedCandidates(candidates),
                        context.ruleProfile().nearTieEpsilon(),
                        context.request().explorationSeed() == null ? 0 : context.request().explorationSeed()
                    );
                }

                int attempts = Math.min(candidates.size(), context.ruleProfile().shortlistK());
                boolean resolved = false;
                for (int i = 0; i < attempts; i++) {
                    CandidateScore candidate = candidates.get(i);
                    if (candidate.amplificationRisk >= context.ruleProfile().amplificationHardThreshold()
                        && context.request().loopPolicy() == LoopPolicy.forbidAmplification) {
                        addIssue(
                            issues,
                            issueSeq,
                            RoutingFailureCode.cycleAmplificationBlocked,
                            RoutingIssue.Severity.error,
                            true,
                            "cirrus.routing.cycle_amplification_blocked"
                        );
                        continue;
                    }

                    if (!resolveRecipe(
                        context,
                        candidate.node,
                        candidate.recipeIndex,
                        candidate.craftTimes,
                        depth + 1,
                        ledger,
                        cycleGuard,
                        activePath,
                        planBuilder,
                        traceStages,
                        issues,
                        issueSeq
                    )) {
                        continue;
                    }

                    remaining = consumeDemandWithPolicy(
                        context,
                        input,
                        remaining,
                        ledger,
                        planBuilder,
                        traceStages,
                        inputIndex
                    );
                    if (remaining <= 0) {
                        resolved = true;
                        break;
                    }
                }

                if (!resolved) {
                    addIssue(issues, issueSeq, RoutingFailureCode.unsatisfiedInput, RoutingIssue.Severity.error, true, "cirrus.routing.unsatisfied_input");
                    return false;
                }
            }

            for (OutputContent output : recipeNode.mergedOutputs()) {
                long produced = output.amount() * times;
                if (produced <= 0) {
                    continue;
                }
                outputBindings.add(new PlanAction.OutputBinding(output.asResource(), produced));
            }

            planBuilder.addExecuteRecipe(
                recipeNode.recipe(),
                times,
                Lists.immutable.ofAll(inputBindings),
                Lists.immutable.ofAll(outputBindings)
            );
            for (PlanAction.OutputBinding binding : outputBindings) {
                ledger.addByproduct(binding.resource(), binding.amount());
            }
            return true;
        } finally {
            activePath.remove(recipeIndex);
            cycleGuard.leave(recipeIndex, sccId);
        }
    }

    private List<CandidateScore> evaluateCandidates(
        PlannerContext context,
        InputContent input,
        long remaining,
        int depth,
        IntOpenHashSet activePath
    ) {
        BitSet allowed = context.sourcePolicyDecision().allowedRecipes();
        RuleProfile profile = context.ruleProfile();
        List<CandidateScore> candidates = new ArrayList<>();
        for (int recipeIndex = allowed.nextSetBit(0); recipeIndex >= 0; recipeIndex = allowed.nextSetBit(recipeIndex + 1)) {
            RecipeGraph.Node node = context.catalog().nodeAt(recipeIndex);
            OutputContent matchedOutput = findMatchedOutput(node, input);
            if (matchedOutput == null || matchedOutput.amount() <= 0) {
                continue;
            }
            int matchedVariantIndex = resolveMatchedVariantIndex(input, matchedOutput);
            if (matchedVariantIndex < 0) {
                continue;
            }
            context.counters().generatedCandidates++;

            int craftTimes = (int) Math.ceil((double) remaining / matchedOutput.amount());
            boolean cycleCandidate = activePath.contains(recipeIndex);
            int sccId = context.sccAnalysis().sccOf(recipeIndex);
            double amplificationRisk = estimateAmplificationRisk(context, cycleCandidate, sccId, depth);
            CandidateContext candidateContext = new CandidateContext(
                node,
                input,
                remaining,
                craftTimes,
                depth,
                cycleCandidate,
                amplificationRisk
            );

            HardRuleResult hardResult = evaluateHardRules(profile.hardRules(), candidateContext);
            if (!hardResult.accepted()) {
                continue;
            }
            context.counters().acceptedCandidates++;
            double[] score = profile.newScoreVector();
            var accumulator = profile.newAccumulator(score);
            for (var rule : profile.scoreRules()) {
                rule.contribute(candidateContext, accumulator);
            }
            candidates.add(new CandidateScore(recipeIndex, node, craftTimes, score, candidateContext, amplificationRisk, matchedVariantIndex));
        }
        return candidates;
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
            long consumed = ledger.consumeVariantDemand(coverage.variant(), remaining, planBuilder);
            remaining -= consumed;
        }

        if (context.request().diagnosticLevel() != DiagnosticLevel.none && !ordered.isEmpty()) {
            VariantCoverage first = ordered.get(0);
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
            context.request().effectiveTagResolvePolicy(),
            input,
            required,
            ledger,
            candidates
        );
        if (ordered.size() <= 1) {
            return;
        }

        Map<Integer, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            order.put(ordered.get(i).variantIndex(), i);
        }
        candidates.sort((left, right) -> {
            int leftOrder = order.getOrDefault(left.matchedVariantIndex(), Integer.MAX_VALUE);
            int rightOrder = order.getOrDefault(right.matchedVariantIndex(), Integer.MAX_VALUE);
            int cmp = Integer.compare(leftOrder, rightOrder);
            if (cmp != 0) {
                return cmp;
            }
            return 0;
        });
    }

    private List<VariantCoverage> collectOrderedVariantCoverage(
        TagResolvePolicy policy,
        InputContent input,
        long required,
        ResourceLedger ledger,
        List<CandidateScore> candidates
    ) {
        List<VariantCoverage> variants = new ArrayList<>();
        int index = 0;
        for (ContentVariant variant : input.enumerate()) {
            long stockAvail = ledger.peekStockAvailable(variant);
            long byproductAvail = ledger.peekByproductAvailable(variant);
            long totalAvail = stockAvail + byproductAvail;
            double coverage = required <= 0 ? 0 : Math.min(required, totalAvail) / (double) required;
            int bestCandidateRank = Integer.MAX_VALUE;
            if (candidates != null) {
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).matchedVariantIndex() == index) {
                        bestCandidateRank = i;
                        break;
                    }
                }
            }
            variants.add(new VariantCoverage(index, variant, stockAvail, byproductAvail, coverage, bestCandidateRank, variantStableKey(variant)));
            index++;
        }

        variants.sort((left, right) -> compareVariantCoverage(policy, left, right));
        return variants;
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
        return Objects.toString(variant, "null");
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
        if (counters.generatedCandidates >= context.request().budget().maxGeneratedCandidates()) {
            counters.budgetExhausted = true;
            counters.budgetStopReason = "maxGeneratedCandidates";
            return true;
        }
        return false;
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
        issues.add(new RoutingIssue(
            "issue-" + issueSeq.getAndIncrement(),
            code,
            severity,
            blocking,
            messageKey,
            Maps.immutable.empty(),
            null,
            null,
            Maps.immutable.empty()
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
}
