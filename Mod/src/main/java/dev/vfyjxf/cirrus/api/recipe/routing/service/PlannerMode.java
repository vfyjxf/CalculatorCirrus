package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Selects the planning algorithm and its quality guarantees.
 *
 */
public enum PlannerMode {
    /**
     * Topology-aware default: analyzes the request cone first, then dispatches
     * to an exact dynamic-programming planner when the cone is acyclic, or to
     * the bounded greedy planner when the cone contains resource loops.
     * Internally distinguished, but callers get one unified entry point.
     */
    auto,
    /** Responsiveness-first greedy DFS planner. Fastest, no optimality guarantees. */
    standard,
    /**
     * Quality-oriented planner with bounded search.
     * Uses a wider deterministic candidate window and rollback budget.
     */
    assuredBounded,
    /**
     * Quality-oriented planner requiring the rule profile to declare
     * all proven-quality capabilities (monotone score, admissible bound, etc.).
     * Falls back to {@link #assuredBounded} if capabilities are missing and
     * fallback is allowed.
     */
    assuredProven,
    /**
     * Cost-aware best-first branch-and-bound planner over demand states.
     * Selects routes by accumulated plan cost (production steps + optional
     * {@link dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeCostHeuristic}
     * quality penalty); the heuristic never affects feasibility.
     */
    costAware
}

