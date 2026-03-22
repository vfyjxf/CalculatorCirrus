package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Individual capability flags that a {@link RuleProfile} may declare.
 *
 * <p>These flags inform the {@link dev.vfyjxf.cirrus.api.recipe.routing.planner.AssuredPlanner}
 * whether a profile meets the mathematical preconditions for certain
 * planning algorithms (e.g. A* search requires {@link #admissibleBound}
 * and {@link #monotonicScore}).
 */
public enum RuleCapabilityFlag {
    /** Score rules produce monotonically non-decreasing values along any path. */
    monotonicScore,
    /** The rank policy defines a total order (no incomparable pairs). */
    totalOrderComparator,
    /** The score heuristic is admissible (never overestimates true cost). */
    admissibleBound,
    /** All rules are pure functions with no side effects. */
    pureRulesOnly,
    /** The tiebreak policy is fully deterministic. */
    deterministicTiebreak
}

