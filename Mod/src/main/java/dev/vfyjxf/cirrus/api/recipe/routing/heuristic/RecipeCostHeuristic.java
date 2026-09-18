package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import java.util.List;

/**
 * A per-recipe soft cost contribution for cost-aware planners.
 *
 * <p>Implementations wrap a trained junk-quality model (e.g. the Bernoulli
 * Naive Bayes over the builtin recipe features). The contract is strictly
 * <b>preference, never feasibility</b>: penalties only reorder candidates and
 * accumulate into the search cost; no penalty value may make a recipe
 * unselectable. Planners must therefore keep every recipe reachable
 * regardless of what this interface returns.
 */
public interface RecipeCostHeuristic {

    /**
     * Calibrated, non-negative quality penalty for one execution of the recipe
     * at the given catalog index. Zero when there is no evidence.
     */
    double penalty(int recipeIndex);

    /**
     * Per-feature evidence explaining {@link #penalty(int)} for the recipe
     * (which features fired and how they moved the score). May be empty.
     */
    default List<FeatureContribution> explain(int recipeIndex) {
        return List.of();
    }

    /** Whether this heuristic carries no information at all. */
    default boolean isEmpty() {
        return false;
    }

    /**
     * One piece of evidence in a heuristic score.
     *
     * @param featureId   the feature (rule) that fired
     * @param weight      signed contribution to the recipe's log-odds
     * @param junkEvidence whether the feature pushes towards junk (true) or useful (false)
     */
    record FeatureContribution(String featureId, double weight, boolean junkEvidence) {
    }
}
