package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import org.eclipse.collections.api.list.MutableList;

/**
 * Optional exploration hook that can perturb the ordering of near-tie
 * candidates to increase search diversity.
 *
 * <p>After candidates are ranked by {@link RankPolicy} and broken by
 * {@link TieBreakPolicy}, the explore policy may shuffle candidates
 * whose scores differ by less than {@code epsilon}. This prevents the
 * planner from always committing to the same first-ranked candidate and
 * allows discovery of alternative solutions.
 *
 * <p>The default implementation is a no-op, preserving deterministic behavior.
 *
 * @see RuleProfile#explorePolicy()
 */
public interface ExplorePolicy {

    /**
     * Optionally reorders candidates whose scores are within {@code epsilon}
     * of each other.
     *
     * @param candidates the mutable list of ranked candidates to reorder in-place
     * @param epsilon    the near-tie threshold; candidates within this delta are
     *                   considered "tied enough" to reorder
     * @param seed       a seed for deterministic pseudo-random shuffling
     */
    void reorderNearTies(MutableList<RankedCandidate> candidates, double epsilon, long seed);

    /**
     * A candidate together with its computed score vector.
     *
     * @param context the candidate's evaluation context
     * @param score   the score vector produced by score rules
     */
    record RankedCandidate(
        CandidateContext context,
        double[] score
    ) {
    }
}

