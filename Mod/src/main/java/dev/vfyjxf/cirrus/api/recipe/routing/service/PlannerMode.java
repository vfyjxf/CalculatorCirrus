package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Selects the planning algorithm and its quality guarantees.
 *
 */
public enum PlannerMode {
    /** Responsiveness-first greedy DFS planner. Fastest, no optimality guarantees. */
    standard,
    /**
     * Quality-oriented planner with bounded search.
     * Uses a wider deterministic candidate window and rollback budget.
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

