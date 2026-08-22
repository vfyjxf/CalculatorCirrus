package dev.vfyjxf.cirrus.recipe.routing.topology;

/**
 * Aggregate statistics of the request cone reachable from a target recipe,
 * collected by a single backward-reachability pass over allowed recipes.
 *
 * <p>Used to size planner budgets from the actual shape of the demand cone
 * instead of coarse catalog-size proxies.
 *
 * @param nodeCount                 recipe nodes reachable from the target
 * @param resourceCount             distinct demanded resources in the cone
 * @param loopSccCount              non-trivial (looping) SCCs touched by the cone;
 *                                  {@code > 0} iff the cone can cycle
 * @param fatInputCandidateEstimate candidate-volume estimate: for every demanded
 *                                  resource with at least one allowed producer, the
 *                                  allowed producer count capped per resource (so a few
 *                                  giant tag families cannot dominate the sum)
 */
public record ConeStats(
    int nodeCount,
    int resourceCount,
    int loopSccCount,
    long fatInputCandidateEstimate
) {
    /** Empty cone (invalid target index). */
    public static final ConeStats empty = new ConeStats(0, 0, 0, 0);

    /** Whether the cone may cycle — matches {@code coneHasNontrivialCycle} semantics. */
    public boolean hasNontrivialCycle() {
        return loopSccCount > 0;
    }
}
