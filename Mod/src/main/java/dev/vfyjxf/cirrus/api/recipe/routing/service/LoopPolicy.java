package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Determines how the planner handles cyclic recipe dependencies.
 *
 * @see dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard
 */
public enum LoopPolicy {
    /**
     * Strictly forbids cyclic re-entry. Any cycle encounter immediately
     * rejects the candidate with {@link RoutingFailureCode#cycleAmplificationBlocked}.
     */
    forbidAmplification,
    /**
     * Allows bounded cyclic expansion subject to {@link LoopBudget} limits.
     * The planner may re-enter a recipe if the amplification risk is below
     * the hard threshold and per-recipe/per-SCC repeat counts are within budget.
     */
    allowBoundedAmplification,
    /**
     * Allows unbounded cyclic expansion. <b>Forbidden at request validation</b> —
     * the routing service rejects requests with this policy to prevent infinite loops.
     */
    allowUnbounded
}

