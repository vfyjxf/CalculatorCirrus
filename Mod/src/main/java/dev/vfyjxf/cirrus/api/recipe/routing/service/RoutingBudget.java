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
 * @param maxRootInputExpandedNodes maximum number of nodes one root input branch may expand;
 *                                {@code 0} disables the per-root branch limit
 * @param maxRootInputGeneratedCandidates maximum number of candidates one root
 *                                input branch may generate; {@code 0} disables
 *                                the per-root branch limit
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
    int maxRootInputExpandedNodes,
    int maxRootInputGeneratedCandidates,
    double explorationBudgetShare,
    LoopBudget loopBudget
) {
    public RoutingBudget {
        Checks.checkArgument(maxExpandedNodes > 0, "maxExpandedNodes must be positive");
        Checks.checkArgument(maxGeneratedCandidates > 0, "maxGeneratedCandidates must be positive");
        Checks.checkArgument(maxWallTimeMs > 0, "maxWallTimeMs must be positive");
        Checks.checkArgument(maxMemoryStates > 0, "maxMemoryStates must be positive");
        Checks.checkArgument(maxRootInputExpandedNodes >= 0, "maxRootInputExpandedNodes must be non-negative");
        Checks.checkArgument(maxRootInputGeneratedCandidates >= 0, "maxRootInputGeneratedCandidates must be non-negative");
        Checks.checkArgument(explorationBudgetShare >= 0 && explorationBudgetShare <= 1, "explorationBudgetShare must be in [0,1]");
        Checks.checkNotNull(loopBudget, "loopBudget");
    }

    public RoutingBudget(
        int maxExpandedNodes,
        int maxGeneratedCandidates,
        int maxWallTimeMs,
        int maxMemoryStates,
        double explorationBudgetShare,
        LoopBudget loopBudget
    ) {
        this(maxExpandedNodes, maxGeneratedCandidates, maxWallTimeMs, maxMemoryStates, 0, 0, explorationBudgetShare, loopBudget);
    }

    public RoutingBudget withRootInputLimits(int maxRootInputExpandedNodes, int maxRootInputGeneratedCandidates) {
        return new RoutingBudget(
            maxExpandedNodes,
            maxGeneratedCandidates,
            maxWallTimeMs,
            maxMemoryStates,
            maxRootInputExpandedNodes,
            maxRootInputGeneratedCandidates,
            explorationBudgetShare,
            loopBudget
        );
    }

    /** Default budget for {@link PlannerMode#standard} — moderate limits. */
    public static RoutingBudget standardDefault() {
        return new RoutingBudget(1_200, 6_000, 30, 2_000, 0, 0, 0.05, LoopBudget.standardDefault());
    }


    public static RoutingBudget assuredDefault() {
        return new RoutingBudget(12_000, 60_000, 120, 15_000, 0, 0, 0.20, LoopBudget.assuredDefault());
    }
}

