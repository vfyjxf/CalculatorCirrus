package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.DiagnosticLevel;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingTrace;
import dev.vfyjxf.cirrus.recipe.routing.cost.UnitCostOracle;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Cost-aware planner: best-first branch-and-bound over demand states.
 *
 * <p>Each search state is the multiset of still-unsatisfied demands (exact
 * amounts, netted against the branch's byproduct surplus), the demand keys
 * opened along the derivation path (cycle context), and the accumulated cost
 * {@code g = Σ craftTimes × (1 + heuristic penalty)}. Remaining work is
 * estimated by the penalty-free {@link UnitCostOracle} relaxation ({@code h});
 * the heuristic only ever contributes to {@code g}, never to {@code h} and
 * never to feasibility — every recipe stays selectable, so any plan reachable
 * without the heuristic stays reachable with it.
 *
 * <p>Semantics kept exact: craft counts are {@code ceil(remaining/produced)},
 * excess outputs accumulate as surplus that later demands consume for free,
 * catalysts (chance-0 inputs) are reserved instead of demanded, and demand
 * recursion through the active set is bounded (amplification-style cycles
 * allowed up to {@value #MAX_ACTIVE_REPEATS} activations of a demand key on
 * one path; deeper recursion is rejected with a cycle diagnostic instead of
 * being cut off by a depth limit).
 *
 * <p>Optimality claim: none beyond the relaxation — {@code h} is a lower bound
 * on remaining production cost under the min-chain relaxation, so pruning at
 * {@code f ≥ incumbent} is safe with respect to that relaxation. The first
 * goal found under f-ordering is kept and search continues until the prune
 * condition closes the queue or the budget stops it. Complex (non-enumerable)
 * inputs and inputs without any allowed producer become explicit pending
 * leaves — a documented approximation matching the legacy planner's pending
 * semantics; chance-based outputs never feed the surplus pool.
 */
public final class CostAwarePlanner implements RoutePlanner {

    /** Demand-key activations allowed on one derivation path (bounded amplification). */
    private static final int MAX_ACTIVE_REPEATS = 3;

    // region state representation

    private record Demand(String key, InputContent content, long amount) {
    }

    private record Producer(int recipeIndex, int outputIndex, RecipeGraph.Node node, OutputContent matched) {
    }

    private static final int PENDING = -2;

    private static final class State {
        final @Nullable State parent;
        /** Chosen step's recipe index; {@link #PENDING} marks a pending leaf. The root carries the target recipe. */
        final int recipeIndex;
        final @Nullable RecipeGraph.Node node;
        final int craftTimes;
        /** Demand key this decision resolves. */
        final String resolvedKey;
        /** Pending demand materialized by this decision (pending leaves only). */
        final @Nullable Demand pendingDemand;
        /** Remaining demands, sorted by key. */
        final Demand[] remaining;
        /** Byproduct surplus: sorted resource ids / positive amounts. */
        final String[] surplusIds;
        final long[] surplusAmounts;
        /** Demand keys opened along the path (cycle context), with activation counts. */
        final Object2IntOpenHashMap<String> active;
        final double gProduction;
        final double gPenalty;
        final double h;
        final double f;
        final String signature;
        final long seq;

        State(@Nullable State parent, int recipeIndex, @Nullable RecipeGraph.Node node, int craftTimes,
              String resolvedKey, @Nullable Demand pendingDemand, Demand[] remaining,
              String[] surplusIds, long[] surplusAmounts, Object2IntOpenHashMap<String> active,
              double gProduction, double gPenalty, double h, String signature, long seq) {
            this.parent = parent;
            this.recipeIndex = recipeIndex;
            this.node = node;
            this.craftTimes = craftTimes;
            this.resolvedKey = resolvedKey;
            this.pendingDemand = pendingDemand;
            this.remaining = remaining;
            this.surplusIds = surplusIds;
            this.surplusAmounts = surplusAmounts;
            this.active = active;
            this.gProduction = gProduction;
            this.gPenalty = gPenalty;
            this.h = h;
            this.f = gProduction + gPenalty + h;
            this.signature = signature;
            this.seq = seq;
        }
    }

    // endregion

    // region statistics (exposed through the diagnostic trace stage)

    private static final class Stats {
        long expandedStates;
        long generatedStates;
        long queuePeak;
        long duplicatedStates;
        long memoHits;
        long decisions;
        long cycleRejects;
        long bnbPrunes;
        long pendingLeaves;
        long goals;

        Map<String, String> asMap(double incumbentProduction, double incumbentPenalty, String stopReason) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("expandedStates", Long.toString(expandedStates));
            out.put("generatedStates", Long.toString(generatedStates));
            out.put("queuePeak", Long.toString(queuePeak));
            out.put("duplicatedStates", Long.toString(duplicatedStates));
            out.put("memoHits", Long.toString(memoHits));
            out.put("decisions", Long.toString(decisions));
            out.put("cycleRejects", Long.toString(cycleRejects));
            out.put("bnbPrunes", Long.toString(bnbPrunes));
            out.put("pendingLeaves", Long.toString(pendingLeaves));
            out.put("goals", Long.toString(goals));
            out.put("incumbentProductionCost", Double.toString(incumbentProduction));
            out.put("incumbentPenalty", Double.toString(incumbentPenalty));
            out.put("stopReason", stopReason);
            return out;
        }
    }

    // endregion

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        long startNanos = System.nanoTime();
        PlannerContext.Counters counters = context.counters();
        counters.wallDeadlineNanos = context.request().budget().maxWallTimeMs() <= 0
            ? Long.MAX_VALUE
            : startNanos + context.request().budget().maxWallTimeMs() * 1_000_000L;

        RoutingRequest request = context.request();
        if (!(request.target() instanceof RoutingRequest.RecipeTarget(RecipeEntry<?> targetRecipe, int times))) {
            return failedOutcome(context, startNanos, "target_mode_mismatch");
        }
        int targetIndex = context.catalog().indexOf(targetRecipe);
        if (targetIndex < 0) {
            return failedOutcome(context, startNanos, "target_recipe_not_in_graph");
        }
        RecipeGraph.Node targetNode = context.catalog().nodeAt(targetIndex);
        RecipeCostHeuristic heuristic = context.ruleProfile().costHeuristic();
        Stats stats = new Stats();

        Search search = new Search(context, heuristic, stats);
        State goal = search.run(targetNode, targetIndex, times);

        RoutingStatus status;
        RoutingPlan plan = null;
        String stopReason = counters.budgetExhausted ? counters.budgetStopReason : "queueClosed";
        if (goal != null) {
            status = RoutingStatus.success;
            PlanBuilder builder = new PlanBuilder(
                System.currentTimeMillis(),
                context.tagSnapshot().version(),
                targetRecipe,
                times
            );
            rebuildPlan(goal, builder);
            plan = builder.build();
        } else {
            status = RoutingStatus.failed;
        }

        MutableList<RoutingTrace.TraceStage> stages = Lists.mutable.empty();
        if (request.diagnosticLevel() != DiagnosticLevel.none) {
            Map<String, String> values = stats.asMap(
                goal != null ? goal.gProduction : Double.NaN,
                goal != null ? goal.gPenalty : Double.NaN,
                stopReason);
            stages.add(new RoutingTrace.TraceStage(RoutingTrace.TraceStageKind.planAssembly,
                org.eclipse.collections.api.factory.Maps.immutable.ofAll(values)));
        }
        RoutingTrace trace = stages.isEmpty() ? null : new RoutingTrace(1, buildFingerprint(request), stages.toImmutable());

        RoutingMetrics metrics = buildMetrics(context, request, counters, startNanos);
        return new RoutingOutcome(status, plan, Lists.immutable.empty(), metrics, trace);
    }

    private static String buildFingerprint(RoutingRequest request) {
        return "costAware|" + request.target() + "|" + request.ruleProfileId() + "|" + request.sourcePolicyId();
    }

    private static RoutingMetrics buildMetrics(
        PlannerContext context, RoutingRequest request, PlannerContext.Counters counters, long startNanos
    ) {
        return new RoutingMetrics(
            (System.nanoTime() - startNanos) / 1_000_000L,
            counters.expandedNodes,
            counters.generatedCandidates,
            counters.scannedRefs,
            counters.acceptedCandidates,
            PlannerMode.costAware,
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
    }

    private static RoutingOutcome failedOutcome(PlannerContext context, long startNanos, String reason) {
        RoutingMetrics metrics = buildMetrics(context, context.request(), context.counters(), startNanos);
        context.counters().budgetStopReason = reason;
        return new RoutingOutcome(RoutingStatus.failed, null, Lists.immutable.empty(), metrics, null);
    }

    // endregion

    // region search

    private static final class Search {
        private final PlannerContext context;
        private final @Nullable RecipeCostHeuristic heuristic;
        private final Stats stats;
        private final java.util.BitSet allowed;
        private final KeyCache keys = new KeyCache();
        private final Map<String, List<Producer>> producersByKey = new HashMap<>();
        private final Map<String, Double> bestGBySignature = new HashMap<>();
        private final UnitCostOracle oracle;
        private final PriorityQueue<State> open = new PriorityQueue<>(
            Comparator.<State>comparingDouble((State s) -> s.f)
                .thenComparingDouble(s -> s.gProduction + s.gPenalty)
                .thenComparingLong(s -> s.seq));
        private long seqCounter;
        private State incumbent;

        private Search(PlannerContext context, @Nullable RecipeCostHeuristic heuristic, Stats stats) {
            this.context = context;
            this.heuristic = heuristic;
            this.stats = stats;
            this.allowed = context.sourcePolicyDecision().allowedRecipes();
            this.oracle = new UnitCostOracle(context.catalog(), this::oracleProducers, null);
        }

        @Nullable State run(RecipeGraph.Node targetNode, int targetIndex, int times) {
            PlannerContext.Counters counters = context.counters();
            // root state: the target execution itself. Its inputs become demands,
            // its outputs become the initial surplus, and the step already accrues
            // its own production + penalty cost.
            DemandAccumulator demands = new DemandAccumulator(keys);
            for (int i = 0; i < targetNode.inputCount(); i++) {
                InputContent input = targetNode.inputAt(i);
                if (input.notConsumed() || input.amount() <= 0) {
                    continue;
                }
                demands.add(input, input.amount() * times);
            }
            SurplusAccumulator surplus = new SurplusAccumulator();
            addOutputs(targetNode, times, surplus, -1, 0);
            double penalty = penalty(targetIndex);
            State root = materialize(null, targetIndex, targetNode, times, "target",
                null, demands, surplus, new Object2IntOpenHashMap<>(),
                times, times * penalty);

            // Greedy dive first: descend along each step's best child to obtain
            // an initial incumbent, so the branch-and-bound pass starts with a
            // bound to prune against. Without it, cyclic cones explode the queue
            // before any goal is found.
            State dove = dive(root);
            if (dove != null) {
                stats.goals++;
                incumbent = dove;
            }
            offer(root);

            while (!open.isEmpty()) {
                if (budgetExceeded(counters)) {
                    return incumbent;
                }
                State state = open.poll();
                if (incumbent != null && state.f >= total(incumbent)) {
                    stats.bnbPrunes++;
                    continue;
                }
                Double best = bestGBySignature.get(state.signature);
                if (best != null && best < total(state)) {
                    continue; // stale queue entry superseded by a cheaper equivalent
                }
                counters.expandedNodes++;
                counters.budgetUsed = counters.expandedNodes;
                stats.expandedStates++;

                if (state.remaining.length == 0) {
                    stats.goals++;
                    if (incumbent == null || total(state) < total(incumbent)) {
                        incumbent = state;
                    }
                    continue;
                }
                expand(state);
            }
            return incumbent;
        }

        private static double total(State state) {
            return state.gProduction + state.gPenalty;
        }

        private boolean budgetExceeded(PlannerContext.Counters counters) {
            RoutingRequest request = context.request();
            if (counters.expandedNodes >= request.budget().maxExpandedNodes()) {
                counters.budgetExhausted = true;
                if (counters.budgetStopReason.equals("none")) {
                    counters.budgetStopReason = "maxExpandedNodes";
                }
                return true;
            }
            if (System.nanoTime() >= counters.wallDeadlineNanos) {
                counters.budgetExhausted = true;
                if (counters.budgetStopReason.equals("none")) {
                    counters.budgetStopReason = "wallTime";
                }
                return true;
            }
            if (request.budget().maxGeneratedCandidates() > 0
                && counters.generatedCandidates >= request.budget().maxGeneratedCandidates()) {
                counters.budgetExhausted = true;
                if (counters.budgetStopReason.equals("none")) {
                    counters.budgetStopReason = "maxGeneratedCandidates";
                }
                return true;
            }
            return false;
        }

        /** Deterministic fail-fast choice: the remaining demand with the largest estimated cost share. */
        private Demand pickDemand(State state) {
            Demand demand = state.remaining[0];
            double bestShare = -1;
            for (Demand candidate : state.remaining) {
                double share = candidate.amount * oracle.unitCost(candidate.content);
                if (share >= bestShare) {
                    bestShare = share;
                    demand = candidate;
                }
            }
            return demand;
        }

        /** Builds the child state for one (demand, producer) decision, or null on cycle rejection. */
        @Nullable private State buildChild(State state, Demand demand, Producer producer) {
            long produced = producer.matched.amount();
            if (produced <= 0) {
                return null;
            }
            long craftTimes = (demand.amount + produced - 1) / produced;
            if (craftTimes > Integer.MAX_VALUE) {
                return null;
            }
            DemandAccumulator demands = new DemandAccumulator(state.remaining, demand, keys);
            SurplusAccumulator surplus = new SurplusAccumulator(state.surplusIds, state.surplusAmounts);
            for (int i = 0; i < producer.node.inputCount(); i++) {
                InputContent input = producer.node.inputAt(i);
                if (input.notConsumed() || input.amount() <= 0) {
                    continue;
                }
                demands.add(input, input.amount() * craftTimes);
            }
            // the matched output already paid for the demand: only its uncommitted
            // leftover enters the surplus (the full amount would double-count the
            // same items and let pure swap cycles "solve" themselves)
            addOutputs(producer.node, craftTimes, surplus, producer.outputIndex,
                craftTimes * produced - demand.amount);
            demands.netAgainst(surplus);
            // cycle context: a new input demanding a resource whose resolution is
            // already open on this path is recursion — bounded, not silently cut
            Object2IntOpenHashMap<String> nextActive = state.active.clone();
            nextActive.addTo(demand.key, 1);
            for (Demand d : demands.view()) {
                if (nextActive.getInt(d.key) >= MAX_ACTIVE_REPEATS) {
                    stats.cycleRejects++;
                    return null;
                }
            }
            stats.decisions++;
            context.counters().generatedCandidates++;
            return materialize(state, producer.recipeIndex, producer.node, (int) craftTimes,
                demand.key, null, demands, surplus, nextActive,
                craftTimes, craftTimes * penalty(producer.recipeIndex));
        }

        /** The pending-leaf child for a demand (explicit external requirement). */
        private State buildPendingChild(State state, Demand demand) {
            DemandAccumulator demands = new DemandAccumulator(state.remaining, demand, keys);
            SurplusAccumulator surplus = new SurplusAccumulator(state.surplusIds, state.surplusAmounts);
            Object2IntOpenHashMap<String> nextActive = state.active.clone();
            nextActive.addTo(demand.key, 1);
            stats.pendingLeaves++;
            context.counters().generatedCandidates++;
            return materialize(state, PENDING, null, 0, demand.key, demand, demands, surplus,
                nextActive, demand.amount * UnitCostOracle.unsourcedCost, 0);
        }

        /**
         * Greedy dive from the root: repeatedly resolve the largest demand with
         * its cheapest non-cycle producer (falling back to the pending leaf),
         * returning a goal state or null. Cheap linear-scan bound for the BNB.
         */
        @Nullable private State dive(State root) {
            State state = root;
            int guard = 0;
            while (state.remaining.length > 0 && guard++ < 10_000) {
                Demand demand = pickDemand(state);
                State next = null;
                for (Producer producer : producers(demand.content)) {
                    State child = buildChild(state, demand, producer);
                    if (child != null) {
                        next = child;
                        break;
                    }
                }
                if (next == null) {
                    next = buildPendingChild(state, demand);
                }
                if (next == null) {
                    return null;
                }
                state = next;
            }
            return state.remaining.length == 0 ? state : null;
        }

        private void expand(State state) {
            Demand demand = pickDemand(state);
            List<Producer> producers = producers(demand.content);
            boolean offeredAny = false;
            for (Producer producer : producers) {
                if (incumbent != null) {
                    long produced = producer.matched.amount();
                    long craftTimes = produced > 0 ? (demand.amount + produced - 1) / produced : Long.MAX_VALUE;
                    double optimistic = total(state) + Math.min(craftTimes, (long) Integer.MAX_VALUE) * (1 + penalty(producer.recipeIndex));
                    if (optimistic >= total(incumbent)) {
                        stats.bnbPrunes++;
                        continue;
                    }
                }
                State child = buildChild(state, demand, producer);
                if (child != null) {
                    offer(child);
                    offeredAny = true;
                }
            }
            // pending leaf branch: nothing produces this demand, or every producer
            // branch was cycle-rejected/failed — it becomes an explicit external
            // requirement priced at the unsourced cost. This escape hatch keeps
            // the planner complete in the legacy pending semantics.
            if (!offeredAny) {
                State child = buildPendingChild(state, demand);
                if (child != null) {
                    offer(child);
                }
            }
        }

        private void offer(State state) {
            stats.generatedStates++;
            if (incumbent != null && state.f >= total(incumbent)) {
                stats.bnbPrunes++;
                return;
            }
            Double best = bestGBySignature.get(state.signature);
            if (best != null && best <= total(state)) {
                stats.duplicatedStates++;
                return;
            }
            bestGBySignature.put(state.signature, total(state));
            open.add(state);
            stats.queuePeak = Math.max(stats.queuePeak, open.size());
        }

        /** Builds a state, computing the h estimate and the dedup signature. */
        @Nullable private State materialize(@Nullable State parent, int recipeIndex, @Nullable RecipeGraph.Node node,
                                           int craftTimes, String resolvedKey, @Nullable Demand pendingDemand,
                                           DemandAccumulator demands, SurplusAccumulator surplus,
                                           Object2IntOpenHashMap<String> active,
                                           double gProductionDelta, double gPenaltyDelta) {
            Demand[] remaining = demands.sorted();
            double h = 0;
            for (Demand demand : remaining) {
                h += demand.amount * oracle.unitCost(demand.content);
            }
            double gProduction = (parent == null ? 0 : parent.gProduction) + gProductionDelta;
            double gPenalty = (parent == null ? 0 : parent.gPenalty) + gPenaltyDelta;
            String[] surplusIds = surplus.idsView();
            long[] surplusAmounts = surplus.amountsView();
            return new State(parent, recipeIndex, node, craftTimes, resolvedKey, pendingDemand, remaining,
                surplusIds, surplusAmounts, active, gProduction, gPenalty, h,
                signature(remaining, surplusIds, surplusAmounts, active), seqCounter++);
        }

        private double penalty(int recipeIndex) {
            return heuristic == null ? 0.0 : Math.max(0.0, heuristic.penalty(recipeIndex));
        }

        /** Allowed producers for the demand, memoized per demand key. */
        private List<Producer> producers(InputContent input) {
            String key = keys.demandKeyOf(input);
            String memoKey = key != null ? key : "opaque";
            List<Producer> cached = producersByKey.get(memoKey);
            if (cached != null) {
                stats.memoHits++;
                return cached;
            }
            List<Producer> out = new ArrayList<>();
            for (RecipeGraph.OutputRef ref : context.graph().producersFor(input)) {
                if (!allowed.get(ref.recipeIndex())) {
                    continue;
                }
                OutputContent output = ref.output(context.graph());
                if (output == null || output.amount() <= 0) {
                    continue;
                }
                if (!input.satisfy(output.asResource())) {
                    continue;
                }
                out.add(new Producer(ref.recipeIndex(), ref.outputIndex(), ref.node(context.graph()), output));
            }
            out.sort(Comparator.<Producer>comparingDouble((Producer p) -> 1 + penalty(p.recipeIndex))
                .thenComparingInt(p -> p.recipeIndex));
            List<Producer> view = Collections.unmodifiableList(out);
            producersByKey.put(memoKey, view);
            return view;
        }

        private Iterable<PlannerRequestCaches.CachedProducer> oracleProducers(InputContent input) {
            List<PlannerRequestCaches.CachedProducer> out = new ArrayList<>();
            for (Producer producer : producers(input)) {
                out.add(new PlannerRequestCaches.CachedProducer(producer.recipeIndex, producer.node, producer.matched, 0));
            }
            return out;
        }

        private void addOutputs(RecipeGraph.Node node, long craftTimes, SurplusAccumulator surplus,
                                  int matchedOutputIndex, long matchedLeftover) {
            for (int o = 0; o < node.outputCount(); o++) {
                OutputContent output = node.outputAt(o);
                if (output.amount() <= 0 || output.chance() < RecipeContent.MAX_CHANCE) {
                    continue;
                }
                String id = keys.resourceIdOf(output);
                if (id == null) {
                    continue;
                }
                long amount = o == matchedOutputIndex ? matchedLeftover : output.amount() * craftTimes;
                if (amount > 0) {
                    surplus.add(id, amount);
                }
            }
        }
    }

    private static String signature(Demand[] remaining, String[] surplusIds, long[] surplusAmounts,
                                    Object2IntOpenHashMap<String> active) {
        StringBuilder sb = new StringBuilder(remaining.length * 24 + 16);
        for (Demand demand : remaining) {
            sb.append(demand.key).append('=').append(demand.amount).append(';');
        }
        sb.append('|');
        for (int i = 0; i < surplusIds.length; i++) {
            sb.append(surplusIds[i]).append('=').append(surplusAmounts[i]).append(';');
        }
        // the active set is part of the semantic state: two states with equal
        // demands/surplus but different open-demand context must not dedup
        sb.append('|');
        Object[] activeKeys = active.keySet().toArray();
        Arrays.sort(activeKeys);
        for (Object key : activeKeys) {
            sb.append(key).append('#').append(active.getInt((String) key)).append(';');
        }
        return sb.toString();
    }

    // reflection-free field setters are not available on the private State
    // holder below; the fill method uses direct field access instead

    // endregion

    // region accumulators

    /**
     * Per-request memo for content→key resolutions. Input/Output content
     * instances are immutable and reused by graph nodes, so identity-keyed
     * caching is safe and removes the dominant allocation source observed in
     * JFR profiles (demandKey/resourceIdOf string building and
     * ResourceLocation parsing on every expansion).
     */
    private static final class KeyCache {
        private static final String NULL_KEY = "\u0000null";
        private final java.util.IdentityHashMap<InputContent, String> demandKeys = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<OutputContent, String> resourceIds = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<InputContent, List<String>> candidateIds = new java.util.IdentityHashMap<>();

        String demandKeyOf(InputContent content) {
            String cached = demandKeys.get(content);
            if (cached != null) {
                return NULL_KEY.equals(cached) ? null : cached;
            }
            String key = ClassifyViewFactory.demandKey(content);
            demandKeys.put(content, key == null ? NULL_KEY : key);
            return key;
        }

        String resourceIdOf(OutputContent output) {
            String cached = resourceIds.get(output);
            if (cached != null) {
                return NULL_KEY.equals(cached) ? null : cached;
            }
            String id = ClassifyViewFactory.resourceIdOf(output);
            resourceIds.put(output, id == null ? NULL_KEY : id);
            return id;
        }

        List<String> candidateIdsOf(InputContent content) {
            List<String> cached = candidateIds.get(content);
            if (cached != null) {
                return cached;
            }
            List<String> ids = new ArrayList<>(2);
            if (content instanceof dev.vfyjxf.cirrus.api.recipe.content.StackContent<?, ?> stack) {
                Object value = stack.content().value();
                if (value != null) {
                    ids.add(value.toString());
                }
            } else if (content instanceof dev.vfyjxf.cirrus.api.recipe.content.TagStackContent<?> tag) {
                for (var member : tag.values()) {
                    ids.add(member.toString());
                }
            } else if (content instanceof dev.vfyjxf.cirrus.api.recipe.content.StackListContent list) {
                for (var entry : list.stacks()) {
                    Object value = entry.value();
                    if (value != null) {
                        ids.add(value.toString());
                    }
                }
            } else if (content instanceof dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent<?, ?> typed) {
                for (var entry : typed.stacks()) {
                    Object value = entry.value();
                    if (value != null) {
                        ids.add(value.toString());
                    }
                }
            }
            List<String> view = java.util.Collections.unmodifiableList(ids);
            candidateIds.put(content, view);
            return view;
        }
    }

    /** Mutable demand multiset keyed by demand key (merges amounts, keeps first content). */
    private static final class DemandAccumulator {
        private final LinkedHashMap<String, Demand> byKey = new LinkedHashMap<>();
        private final KeyCache keys;

        DemandAccumulator(KeyCache keys) {
            this.keys = keys;
        }

        /** Copy of a state's remaining demands minus the one being resolved. */
        DemandAccumulator(Demand[] remaining, Demand resolved, KeyCache keys) {
            this.keys = keys;
            for (Demand demand : remaining) {
                if (demand != resolved) {
                    byKey.put(demand.key, demand);
                }
            }
        }

        void add(InputContent content, long amount) {
            String key = keys.demandKeyOf(content);
            if (key == null) {
                // opaque content: keep as its own demand (pending-leaf material)
                key = "opaque#" + byKey.size() + "@" + System.identityHashCode(content);
                byKey.put(key, new Demand(key, content, amount));
                return;
            }
            Demand existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, new Demand(key, content, amount));
            } else {
                byKey.put(key, new Demand(key, existing.content, existing.amount + amount));
            }
        }

        /** Consumes available surplus against demands (exact id matches). */
        void netAgainst(SurplusAccumulator surplus) {
            List<String> removed = null;
            for (Map.Entry<String, Demand> entry : byKey.entrySet()) {
                Demand demand = entry.getValue();
                long taken = surplus.consume(keys.candidateIdsOf(demand.content), demand.amount);
                long rest = demand.amount - taken;
                if (rest <= 0) {
                    if (removed == null) {
                        removed = new ArrayList<>();
                    }
                    removed.add(entry.getKey());
                } else if (rest != demand.amount) {
                    entry.setValue(new Demand(demand.key, demand.content, rest));
                }
            }
            if (removed != null) {
                for (String key : removed) {
                    byKey.remove(key);
                }
            }
        }

        List<Demand> view() {
            return new ArrayList<>(byKey.values());
        }

        Demand[] sorted() {
            Demand[] out = byKey.values().toArray(new Demand[0]);
            Arrays.sort(out, Comparator.comparing(Demand::key));
            return out;
        }
    }

    /**
     * Mutable byproduct surplus held as parallel sorted arrays. Typical branch
     * state carries a handful of entries, so binary-search array ops beat the
     * tree-map node allocation of the earlier implementation (JFR: TreeMap
     * put/merge/fixAfterInsertion ~7% of CPU).
     */
    private static final class SurplusAccumulator {
        private static final String[] NO_IDS = new String[0];
        private static final long[] NO_AMOUNTS = new long[0];

        private String[] ids = new String[8];
        private long[] amounts = new long[8];
        private int size;

        SurplusAccumulator() {
        }

        SurplusAccumulator(String[] inIds, long[] inAmounts) {
            for (int i = 0; i < inIds.length; i++) {
                if (inAmounts[i] > 0) {
                    add(inIds[i], inAmounts[i]);
                }
            }
        }

        void add(String id, long amount) {
            if (amount <= 0) {
                return;
            }
            int pos = Arrays.binarySearch(ids, 0, size, id);
            if (pos >= 0) {
                amounts[pos] += amount;
                return;
            }
            int insert = -(pos + 1);
            ensureCapacity(size + 1);
            System.arraycopy(ids, insert, ids, insert + 1, size - insert);
            System.arraycopy(amounts, insert, amounts, insert + 1, size - insert);
            ids[insert] = id;
            amounts[insert] = amount;
            size++;
        }

        /** Consumes up to {@code amount} from the first candidate id present; returns how much was taken. */
        long consume(List<String> candidates, long amount) {
            for (String candidate : candidates) {
                int pos = Arrays.binarySearch(ids, 0, size, candidate);
                if (pos >= 0 && amounts[pos] > 0) {
                    long taken = Math.min(amounts[pos], amount);
                    amounts[pos] -= taken;
                    if (amounts[pos] <= 0) {
                        System.arraycopy(ids, pos + 1, ids, pos, size - pos - 1);
                        System.arraycopy(amounts, pos + 1, amounts, pos, size - pos - 1);
                        size--;
                        ids[size] = null;
                    }
                    return taken;
                }
            }
            return 0;
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= ids.length) {
                return;
            }
            int grown = Math.max(capacity, ids.length * 2);
            ids = Arrays.copyOf(ids, grown);
            amounts = Arrays.copyOf(amounts, grown);
        }

        String[] idsView() {
            return size == 0 ? NO_IDS : Arrays.copyOf(ids, size);
        }

        long[] amountsView() {
            return size == 0 ? NO_AMOUNTS : Arrays.copyOf(amounts, size);
        }
    }

    // endregion

    // region plan reconstruction

    private static void rebuildPlan(State goal, PlanBuilder builder) {
        List<State> steps = new ArrayList<>();
        for (State state = goal; state != null; state = state.parent) {
            steps.add(state);
        }
        Collections.reverse(steps);
        for (State step : steps) {
            if (step.recipeIndex == PENDING) {
                Demand pending = step.pendingDemand;
                if (pending != null) {
                    builder.addPendingInput(pending.content(), pending.amount(), 0, 0);
                }
                continue;
            }
            if (step.node == null || step.craftTimes <= 0) {
                continue;
            }
            List<PlanAction.InputBinding> inputs = new ArrayList<>();
            List<PlanAction.OutputBinding> outputs = new ArrayList<>();
            for (int i = 0; i < step.node.inputCount(); i++) {
                InputContent input = step.node.inputAt(i);
                if (input.notConsumed() || input.amount() <= 0) {
                    builder.addReserveCatalyst(input, Math.max(1, input.amount()) * step.craftTimes, step.node.recipe());
                    continue;
                }
                inputs.add(new PlanAction.InputBinding(input, input.amount() * step.craftTimes));
            }
            for (int o = 0; o < step.node.outputCount(); o++) {
                OutputContent output = step.node.outputAt(o);
                if (output.amount() > 0) {
                    outputs.add(new PlanAction.OutputBinding(output.asResource(), output.amount() * step.craftTimes));
                }
            }
            builder.addExecuteRecipe(
                step.node.recipe(),
                step.craftTimes,
                Lists.immutable.ofAll(inputs),
                Lists.immutable.ofAll(outputs)
            );
        }
    }

    // endregion
}
