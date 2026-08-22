package dev.vfyjxf.cirrus.recipe.routing.cost;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cirrus.recipe.routing.planner.PlannerRequestCaches;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Lazy, memoized estimate of the minimum crafting cost of a demanded resource:
 * {@code h(demand) = min over producers of (1 + Σ input.amount × h(input)) / outputAmount}.
 *
 * <p>The greedy planner ranks candidates without any lookahead, which lets
 * high-yield pseudo-recipes (giant-block conversions, uncrafting, living-matter
 * transmutations) win purely on craft count. This oracle gives scoring a cheap
 * "how expensive is the subtree below this candidate" signal: producers whose
 * inputs are rare or unsourced inherit a large cost and sink to the bottom of
 * the shortlist.
 *
 * <p>Producer sets come from the same request-scoped, scan-capped provider the
 * planner uses, so oracle work stays proportional to what the planner itself
 * would consider. Recursion is guarded three ways: a per-request node
 * evaluation cap (on exhaustion, unknown demands degrade to a flat mid-tier
 * cost so ties fall back to the other score axes), a cycle back-edge cost for
 * looping demand families, and a recursion depth cap against deep chains.
 *
 * <p>This is the heuristic mirror of the exact dynamic program in
 * {@code AcyclicPlanner}; it deliberately differs in two ways: it never aborts
 * (approximate costs are fine for scoring) and it iterates the capped,
 * pre-filtered producer lists instead of the full producer index.
 */
public final class UnitCostOracle {

    /** Per-unit cost charged when no considered producer can make the resource. */
    public static final double unsourcedCost = 1_000_000.0d;
    /**
     * Cost charged when resolving a demand loops back onto itself (dye/recolor
     * families) or when the evaluation budget is saturated: expensive enough to
     * lose against real chains, cheap enough to stay comparable.
     */
    public static final double loopBackedgeCost = 10_000.0d;

    private static final int maxNodeEvaluations = 30_000;
    private static final int maxRecursionDepth = 512;

    private final RoutingRecipeCatalog catalog;
    private final Function<InputContent, Iterable<PlannerRequestCaches.CachedProducer>> producerProvider;
    private final @Nullable java.util.function.IntToDoubleFunction recipePenalty;
    private final Map<String, Double> unitCostByDemand = new HashMap<>();
    // demand keys are rebuilt per call otherwise (registry id resolution +
    // string concat per input); graph nodes reuse their InputContent
    // instances, so an identity memo collapses that to one key build each
    private final Map<InputContent, String> demandKeys = new IdentityHashMap<>();
    private final Set<String> visitingDemands = new HashSet<>();
    // primitive per-node memo: the boxed Integer->Double map showed up in
    // profiles through ~78k lookups per request
    private double @Nullable [] nodeCosts;
    private boolean @Nullable [] nodeEvaluated;
    private int nodeEvaluations;
    private int recursionDepth;
    private boolean saturated;

    public UnitCostOracle(
        RoutingRecipeCatalog catalog,
        Function<InputContent, Iterable<PlannerRequestCaches.CachedProducer>> producerProvider
    ) {
        this(catalog, producerProvider, null);
    }

    /**
     * @param recipePenalty optional per-recipe cost multiplier implementing the
     *                      advisory screening semantics ("deprioritized during
     *                      planning"): generation recipes (free resources) must
     *                      lose against real chains even though their raw cost
     *                      is 1, so their cost is multiplied up; penalties
     *                      compound naturally along chains
     */
    public UnitCostOracle(
        RoutingRecipeCatalog catalog,
        Function<InputContent, Iterable<PlannerRequestCaches.CachedProducer>> producerProvider,
        @Nullable java.util.function.IntToDoubleFunction recipePenalty
    ) {
        this.catalog = catalog;
        this.producerProvider = producerProvider;
        this.recipePenalty = recipePenalty;
    }

    /**
     * Estimated total cost of crafting the demanded amount of one input through
     * the cheapest known chain, per unit.
     */
    public double unitCost(InputContent input) {
        String key = demandKeys.get(input);
        if (key == null) {
            key = ClassifyViewFactory.demandKey(input);
            if (key == null) {
                return unsourcedCost;
            }
            demandKeys.put(input, key);
        }
        Double memo = unitCostByDemand.get(key);
        if (memo != null) {
            return memo;
        }
        if (saturated || recursionDepth >= maxRecursionDepth) {
            return loopBackedgeCost;
        }
        if (!visitingDemands.add(key)) {
            return loopBackedgeCost;
        }
        recursionDepth++;
        double best = unsourcedCost;
        for (PlannerRequestCaches.CachedProducer producer : producerProvider.apply(input)) {
            long produced = producer.matchedOutput().amount();
            if (produced <= 0) {
                continue;
            }
            double candidate = nodeCost(producer.recipeIndex()) / produced;
            if (candidate < best) {
                best = candidate;
            }
        }
        recursionDepth--;
        visitingDemands.remove(key);
        unitCostByDemand.put(key, best);
        return best;
    }

    /**
     * Estimated total cost of producing the candidate's demanded amount:
     * {@code craftTimes × (1 + Σ input.amount × h(input))}. Multiplying by craft
     * times folds the old primary craft-count axis into the estimate, so yield
     * still matters but only through real subtree cost.
     */
    public double subtreeCost(RecipeGraph.Node node, int craftTimes) {
        return craftTimes * rawNodeCost(node);
    }

    private double nodeCost(int recipeIndex) {
        double[] costs = nodeCosts;
        boolean[] evaluated = nodeEvaluated;
        if (costs == null) {
            int nodes = Math.max(16, catalog.size());
            costs = new double[nodes];
            evaluated = new boolean[nodes];
            nodeCosts = costs;
            nodeEvaluated = evaluated;
        }
        if (recipeIndex >= evaluated.length) {
            // growable: derived indexes may exceed the catalog size, and the
            // memo must cover them exactly like the old boxed map did
            int capacity = Math.max(recipeIndex + 1, costs.length * 2);
            nodeCosts = costs = java.util.Arrays.copyOf(costs, capacity);
            nodeEvaluated = evaluated = java.util.Arrays.copyOf(evaluated, capacity);
        }
        if (recipeIndex >= 0 && evaluated[recipeIndex]) {
            return costs[recipeIndex];
        }
        if (saturated) {
            return loopBackedgeCost;
        }
        if (nodeEvaluations++ >= maxNodeEvaluations) {
            saturated = true;
            return loopBackedgeCost;
        }
        RecipeGraph.Node node = catalog.nodeAt(recipeIndex);
        double cost = rawNodeCost(node) * purityPenalty(node);
        java.util.function.IntToDoubleFunction penalty = recipePenalty;
        if (penalty != null && recipeIndex >= 0) {
            cost *= penalty.applyAsDouble(recipeIndex);
        }
        if (recipeIndex >= 0) {
            evaluated[recipeIndex] = true;
            costs[recipeIndex] = cost;
        }
        return cost;
    }

    /**
     * Purity penalty: recipes with many distinct guaranteed outputs are
     * usually not real crafts (loot-table displays, drop listings) — they
     * only ever appear as a producer for one of their many outputs and would
     * otherwise win on cost. Chance byproducts are normal machine behavior
     * and do not count; more than two guaranteed outputs scales the cost up
     * linearly (still usable as a last-resort source).
     */
    private static double purityPenalty(RecipeGraph.Node node) {
        int guaranteed = 0;
        for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
            if (node.outputAt(outputIndex).chance() >= dev.vfyjxf.cirrus.api.recipe.content.RecipeContent.MAX_CHANCE) {
                guaranteed++;
            }
        }
        return guaranteed <= 2 ? 1.0d : guaranteed - 1;
    }

    /**
     * Impurity factor for the candidate's own primary ranking axis: counts
     * only guaranteed outputs that are <b>not</b> the demanded one (true
     * byproducts), unlike {@link #purityPenalty} which counts every guaranteed
     * output and is only applied downstream in {@code nodeCost}. Chance
     * byproducts stay excluded (normal machine behavior). Capped so a long
     * chain through a modestly impure machine can still beat a worse pure chain.
     */
    public double impurityFactor(RecipeGraph.Node node, @Nullable dev.vfyjxf.cirrus.api.recipe.content.OutputContent matched) {
        if (matched == null) {
            return 1.0d;
        }
        String matchedId = ClassifyViewFactory.resourceIdOf(matched);
        if (matchedId == null) {
            return 1.0d;
        }
        int byproducts = 0;
        for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
            var output = node.outputAt(outputIndex);
            if (output.chance() < dev.vfyjxf.cirrus.api.recipe.content.RecipeContent.MAX_CHANCE) {
                continue;
            }
            String outputId = ClassifyViewFactory.resourceIdOf(output);
            if (outputId != null && !outputId.equals(matchedId)) {
                byproducts++;
            }
        }
        if (byproducts <= 0) {
            return 1.0d;
        }
        return Math.min(impurityCap, 1.0d + byproducts * impurityStep);
    }

    /** Per guaranteed byproduct step of {@link #impurityFactor}. */
    private static final double impurityStep = 1.5d;
    /** Upper bound of {@link #impurityFactor} — keeps bounded chains comparable. */
    private static final double impurityCap = 8.0d;

    private double rawNodeCost(RecipeGraph.Node node) {
        double cost = 1.0d;
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            InputContent input = node.inputAt(inputIndex);
            if (input.notConsumed() || input.amount() <= 0) {
                continue;
            }
            cost += input.amount() * unitCost(input);
        }
        return cost;
    }

    /** Whether the evaluation budget was exhausted; diagnostics only. */
    public boolean saturated() {
        return saturated;
    }

    /** Number of recipe nodes evaluated so far; diagnostics only. */
    public int nodeEvaluations() {
        return nodeEvaluations;
    }

    /** Exposes the memoized per-demand cost for diagnostics; {@code null} when unknown. */
    public @Nullable Double memoizedUnitCost(String demandKey) {
        return unitCostByDemand.get(demandKey);
    }

}
