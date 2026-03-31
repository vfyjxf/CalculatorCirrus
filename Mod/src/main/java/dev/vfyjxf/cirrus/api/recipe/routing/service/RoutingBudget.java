package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Unified runtime budget configuration for a routing run.
 *
 * <p>Budgets act as safety limits preventing the planner from consuming
 * excessive CPU or memory. When any limit is exceeded, the planner stops
 * and reports the result achieved so far (possibly partial or failed).
 *
 * @param maxExpandedNodes        maximum number of recipe nodes the planner may visit
 * @param maxGeneratedCandidates  maximum number of candidate recipes that may be
 *                                generated across all demand resolution steps
 * @param maxWallTimeMs           maximum wall-clock time in milliseconds (advisory)
 * @param maxMemoryStates         maximum number of intermediate memory states
 * @param explorationBudgetShare  fraction of the total budget reserved for
 *                                exploration (non-deterministic diversity);
 *                                must be in [0, 1]
 * @param loopBudget              sub-budget governing cyclic expansion
 * @see LoopBudget
 */
public record RoutingBudget(
    int maxExpandedNodes,
    int maxGeneratedCandidates,
    int maxWallTimeMs,
    int maxMemoryStates,
    double explorationBudgetShare,
    LoopBudget loopBudget
) {
    public RoutingBudget {
        Checks.checkArgument(maxExpandedNodes > 0, "maxExpandedNodes must be positive");
        Checks.checkArgument(maxGeneratedCandidates > 0, "maxGeneratedCandidates must be positive");
        Checks.checkArgument(maxWallTimeMs > 0, "maxWallTimeMs must be positive");
        Checks.checkArgument(maxMemoryStates > 0, "maxMemoryStates must be positive");
        Checks.checkArgument(explorationBudgetShare >= 0 && explorationBudgetShare <= 1, "explorationBudgetShare must be in [0,1]");
        Checks.checkNotNull(loopBudget, "loopBudget");
    }

    /** Default budget for {@link PlannerMode#standard} — moderate limits. */
    public static RoutingBudget standardDefault() {
        return new RoutingBudget(1_200, 6_000, 30, 2_000, 0.05, LoopBudget.standardDefault());
    }


    public static RoutingBudget assuredDefault() {
        return new RoutingBudget(12_000, 60_000, 120, 15_000, 0.20, LoopBudget.assuredDefault());
    }
}

