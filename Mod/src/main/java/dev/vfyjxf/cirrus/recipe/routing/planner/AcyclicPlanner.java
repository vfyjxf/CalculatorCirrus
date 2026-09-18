package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.state.ResourceLedger;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exact planner for acyclic request cones, selected by
 * {@link PlannerMode#auto} dispatch when the cone reachable from the target
 * contains no resource loop (see
 * {@link dev.vfyjxf.cirrus.recipe.routing.topology.GraphTopology}).
 *
 * <p>On a DAG the cost-minimizing recipe choice is well-defined, so this
 * planner computes it directly instead of searching: a memoized dynamic
 * program over recipe nodes computes, for every demanded resource, the
 * producer with the lowest total craft cost
 * ({@code nodeCost = 1 + Σ input.amount × unitCost(input)}), then a build
 * pass unfolds the chosen recipes top-down, consuming stock first and
 * sharing overproduction through the {@link ResourceLedger} exactly like the
 * greedy planner does.
 *
 * <p>Resources with no producer are charged a large but finite penalty in
 * the DP, so the planner still picks paths that need external supply when no
 * fully craftable path exists; the build pass records them as pending
 * inputs.
 *
 * <p>Safety valves: if the DP meets a cycle (the topology projection is an
 * over-approximation, so a real cycle can slip through tag/kind matching),
 * or the expansion budget is exceeded, planning aborts and delegates to the
 * fallback (greedy) planner — global optimum when provably possible, bounded
 * greedy otherwise.
 */
public final class AcyclicPlanner implements RoutePlanner {

    /** Fixed cost of executing one recipe once; the DP minimizes total crafts. */
    private static final double recipeBaseCost = 1.0d;
    /** Per-unit cost charged for resources no recipe can produce (external supply). */
    private static final double unsourcedUnitPenalty = 1_000_000.0d;
    /** Recursion depth cap for the build pass; deeper cones fall back. */
    private static final int buildDepthLimit = 2048;

    private final RoutePlanner fallback;

    public AcyclicPlanner(RoutePlanner fallback) {
        this.fallback = fallback;
    }

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        long startNanos = System.nanoTime();
        RoutingRequest request = context.request();
        if (!(request.target() instanceof RoutingRequest.RecipeTarget(RecipeEntry<?> recipe, int times))) {
            return fallback.plan(context);
        }
        int targetIndex = context.catalog().indexOf(recipe);
        if (targetIndex < 0) {
            return fallback.plan(context);
        }

        PlannerContext.Counters counters = context.counters();
        DynamicProgram dp = new DynamicProgram(context, counters);
        if (!dp.solve(targetIndex)) {
            counters.budgetStopReason = dp.abortReason;
            return fallback.plan(context);
        }

        PlanBuilder planBuilder = new PlanBuilder(
            System.currentTimeMillis(),
            context.tagSnapshot().version(),
            recipe,
            times
        );
        ResourceLedger ledger = new ResourceLedger(request.stock().copy());
        MutableList<RoutingIssue> issues = Lists.mutable.empty();
        AtomicInteger issueSeq = new AtomicInteger();
        BuildPass build = new BuildPass(context, dp, ledger, planBuilder, issues, issueSeq);
        boolean completed = build.resolve(targetIndex, times, 0);
        if (!completed) {
            counters.budgetStopReason = build.abortReason;
            return fallback.plan(context);
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        RoutingPlan plan = planBuilder.build();
        RoutingMetrics metrics = new RoutingMetrics(
            elapsedMs,
            counters.expandedNodes,
            counters.generatedCandidates,
            counters.scannedRefs,
            counters.acceptedCandidates,
            request.plannerMode(),
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
            request.loopPolicy(),
            counters.candidateRollbacks
        );
        RoutingStatus status = planBuilder.hasActions() ? RoutingStatus.success : RoutingStatus.failed;
        return new RoutingOutcome(status, plan, issues.toImmutable(), metrics, null);
    }

    /**
     * The memoized min-cost computation over the acyclic cone. All state is
     * keyed by demand key (see {@link ClassifyViewFactory#demandKey}), so
     * resources shared between branches are solved once — this is what makes
     * the result globally optimal rather than locally greedy.
     */
    private static final class DynamicProgram {
        private final PlannerContext context;
        private final PlannerContext.Counters counters;
        private final BitSet allowed;
        private final Map<String, Double> unitCostByDemand = new HashMap<>();
        private final Map<String, Choice> choiceByDemand = new HashMap<>();
        private final Map<Integer, Double> nodeCostMemo = new HashMap<>();
        private final Set<Integer> visitingNodes = new HashSet<>();
        private final Set<String> visitingDemands = new HashSet<>();
        private boolean aborted;
        private String abortReason = "none";

        private DynamicProgram(PlannerContext context, PlannerContext.Counters counters) {
            this.context = context;
            this.counters = counters;
            this.allowed = context.sourcePolicyDecision().allowedRecipes();
        }

        private boolean solve(int targetIndex) {
            nodeCost(targetIndex);
            return !aborted;
        }

        private double nodeCost(int nodeIndex) {
            if (aborted) {
                return unsourcedUnitPenalty;
            }
            Double memo = nodeCostMemo.get(nodeIndex);
            if (memo != null) {
                return memo;
            }
            if (!visitingNodes.add(nodeIndex)) {
                aborted = true;
                abortReason = "dpCycleDetected";
                return unsourcedUnitPenalty;
            }
            counters.expandedNodes++;
            if (counters.expandedNodes > context.request().budget().maxExpandedNodes()) {
                aborted = true;
                abortReason = "maxExpandedNodes";
                counters.budgetExhausted = true;
                visitingNodes.remove(nodeIndex);
                return unsourcedUnitPenalty;
            }
            RecipeGraph.Node node = context.catalog().nodeAt(nodeIndex);
            double cost = recipeBaseCost;
            for (int i = 0; i < node.inputCount(); i++) {
                InputContent input = node.inputAt(i);
                if (input.notConsumed() || input.amount() <= 0) {
                    continue;
                }
                cost += input.amount() * unitCost(input);
                if (aborted) {
                    visitingNodes.remove(nodeIndex);
                    return unsourcedUnitPenalty;
                }
            }
            visitingNodes.remove(nodeIndex);
            nodeCostMemo.put(nodeIndex, cost);
            return cost;
        }

        private double unitCost(InputContent input) {
            String key = ClassifyViewFactory.demandKey(input);
            if (key == null) {
                return unsourcedUnitPenalty;
            }
            Double memo = unitCostByDemand.get(key);
            if (memo != null) {
                return memo;
            }
            if (!visitingDemands.add(key)) {
                aborted = true;
                abortReason = "dpCycleDetected";
                return unsourcedUnitPenalty;
            }
            double best = Double.POSITIVE_INFINITY;
            Choice bestChoice = null;
            for (RecipeGraph.OutputRef ref : context.graph().producersFor(input)) {
                counters.generatedCandidates++;
                if (!allowed.get(ref.recipeIndex())) {
                    continue;
                }
                RecipeGraph.Node producer = context.catalog().nodeAt(ref.recipeIndex());
                OutputContent output = producer.outputAt(ref.outputIndex());
                long produced = output.amount();
                if (produced <= 0) {
                    continue;
                }
                double candidate = nodeCost(ref.recipeIndex()) / produced;
                if (aborted) {
                    visitingDemands.remove(key);
                    return unsourcedUnitPenalty;
                }
                if (candidate < best || (candidate == best && bestChoice != null && ref.recipeIndex() < bestChoice.nodeIndex())) {
                    best = candidate;
                    bestChoice = new Choice(ref.recipeIndex(), ref.outputIndex(), produced);
                }
            }
            visitingDemands.remove(key);
            if (bestChoice == null) {
                unitCostByDemand.put(key, unsourcedUnitPenalty);
                return unsourcedUnitPenalty;
            }
            counters.acceptedCandidates++;
            unitCostByDemand.put(key, best);
            choiceByDemand.put(key, bestChoice);
            return best;
        }

        private @Nullable Choice choiceFor(InputContent input) {
            String key = ClassifyViewFactory.demandKey(input);
            return key == null ? null : choiceByDemand.get(key);
        }
    }

    private record Choice(int nodeIndex, int outputIndex, long producedPerCraft) {
    }

    /**
     * Top-down unfold of the DP choices into plan actions. Stock is consumed
     * first (via the ledger, which also reuses byproduct overproduction from
     * earlier expansions); any remainder is crafted with the DP-chosen
     * producer, or recorded as a pending input when nothing produces it.
     */
    private static final class BuildPass {
        private final PlannerContext context;
        private final DynamicProgram dp;
        private final ResourceLedger ledger;
        private final PlanBuilder planBuilder;
        private final MutableList<RoutingIssue> issues;
        private final AtomicInteger issueSeq;
        private final BitSet allowed;
        private String abortReason = "none";

        private BuildPass(
            PlannerContext context,
            DynamicProgram dp,
            ResourceLedger ledger,
            PlanBuilder planBuilder,
            MutableList<RoutingIssue> issues,
            AtomicInteger issueSeq
        ) {
            this.context = context;
            this.dp = dp;
            this.ledger = ledger;
            this.planBuilder = planBuilder;
            this.issues = issues;
            this.issueSeq = issueSeq;
            this.allowed = context.sourcePolicyDecision().allowedRecipes();
        }

        private boolean resolve(int nodeIndex, int times, int depth) {
            if (depth > buildDepthLimit) {
                abortReason = "buildDepthLimit";
                return false;
            }
            RecipeGraph.Node node = context.catalog().nodeAt(nodeIndex);
            for (int i = 0; i < node.inputCount(); i++) {
                InputContent input = node.inputAt(i);
                long required = input.amount() * (long) times;
                if (required <= 0) {
                    continue;
                }
                if (input.notConsumed()) {
                    planBuilder.addReserveCatalyst(input, input.amount(), node.recipe());
                    continue;
                }
                long remaining = ledger.consumeDemand(input, required, planBuilder);
                if (remaining <= 0) {
                    continue;
                }
                Choice choice = validatedChoice(input);
                if (choice == null) {
                    ledger.recordPendingInput(input, remaining, planBuilder);
                    issues.add(new RoutingIssue(
                        "issue-" + issueSeq.getAndIncrement(),
                        RoutingFailureCode.noFeasibleSource,
                        RoutingIssue.Severity.warning,
                        false,
                        "cirrus.routing.no_source",
                        Maps.immutable.empty(),
                        new RoutingIssuePayload.Empty(),
                        null,
                        null,
                        Maps.immutable.empty()
                    ));
                    continue;
                }
                long childTimes = (remaining + choice.producedPerCraft() - 1) / choice.producedPerCraft();
                if (childTimes > Integer.MAX_VALUE) {
                    abortReason = "craftTimesOverflow";
                    return false;
                }
                if (!resolve(choice.nodeIndex(), (int) childTimes, depth + 1)) {
                    return false;
                }
            }
            planBuilder.addExecuteRecipe(node.recipe(), times, inputBindings(node, times), outputBindings(node, times));
            for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
                OutputContent output = node.outputAt(outputIndex);
                long produced = output.amount() * (long) times;
                if (produced > 0) {
                    ledger.addByproduct(output.asResource(), produced);
                }
            }
            return true;
        }

        /**
         * The DP choice for this input, re-validated against the actual
         * producer set: inputs that share a demand key but match differently
         * (components, kind semantics) fall back to the cheapest valid
         * producer for this concrete input.
         */
        private @Nullable Choice validatedChoice(InputContent input) {
            Choice choice = dp.choiceFor(input);
            if (choice != null && allowed.get(choice.nodeIndex()) && produces(context.graph(), input, choice)) {
                return choice;
            }
            Choice best = null;
            double bestCost = Double.POSITIVE_INFINITY;
            for (RecipeGraph.OutputRef ref : context.graph().producersFor(input)) {
                if (!allowed.get(ref.recipeIndex())) {
                    continue;
                }
                RecipeGraph.Node producer = context.catalog().nodeAt(ref.recipeIndex());
                OutputContent output = producer.outputAt(ref.outputIndex());
                if (output.amount() <= 0) {
                    continue;
                }
                Double nodeCost = dp.nodeCostMemo.get(ref.recipeIndex());
                double cost = nodeCost == null ? unsourcedUnitPenalty : nodeCost / output.amount();
                if (best == null || cost < bestCost || (cost == bestCost && ref.recipeIndex() < best.nodeIndex())) {
                    best = new Choice(ref.recipeIndex(), ref.outputIndex(), output.amount());
                    bestCost = cost;
                }
            }
            return best;
        }

        private static boolean produces(RecipeGraph graph, InputContent input, Choice choice) {
            for (RecipeGraph.OutputRef ref : graph.producersFor(input)) {
                if (ref.recipeIndex() == choice.nodeIndex() && ref.outputIndex() == choice.outputIndex()) {
                    return true;
                }
            }
            return false;
        }

        private static ImmutableList<PlanAction.InputBinding> inputBindings(RecipeGraph.Node node, int times) {
            if (node.inputCount() == 0) {
                return Lists.immutable.empty();
            }
            var bindings = Lists.mutable.<PlanAction.InputBinding>ofInitialCapacity(node.inputCount());
            for (int i = 0; i < node.inputCount(); i++) {
                InputContent input = node.inputAt(i);
                long required = input.amount() * (long) times;
                if (required > 0) {
                    bindings.add(new PlanAction.InputBinding(input, required));
                }
            }
            return bindings.toImmutable();
        }

        private static ImmutableList<PlanAction.OutputBinding> outputBindings(RecipeGraph.Node node, int times) {
            if (node.outputCount() == 0) {
                return Lists.immutable.empty();
            }
            var bindings = Lists.mutable.<PlanAction.OutputBinding>ofInitialCapacity(node.outputCount());
            for (int i = 0; i < node.outputCount(); i++) {
                OutputContent output = node.outputAt(i);
                long produced = output.amount() * (long) times;
                if (produced > 0) {
                    bindings.add(new PlanAction.OutputBinding(output.asResource(), produced));
                }
            }
            return bindings.toImmutable();
        }
    }
}
