package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Selects the planning algorithm and its quality guarantees.
 *
 * @see dev.vfyjxf.cirrus.api.recipe.routing.planner.StandardPlanner
 * @see dev.vfyjxf.cirrus.api.recipe.routing.planner.AssuredPlanner
 */
public enum PlannerMode {
    /** Responsiveness-first greedy DFS planner. Fastest, no optimality guarantees. */
    standard,
    /**
     * Quality-oriented planner with bounded search.
     * Delegates to the standard search core but rebrands the result.
     */
    assuredBounded,
    /**
     * Quality-oriented planner requiring the rule profile to declare
     * all proven-quality capabilities (monotonic score, admissible bound, etc.).
     * Falls back to {@link #assuredBounded} if capabilities are missing and
     * fallback is allowed.
     */
    assuredProven
}

