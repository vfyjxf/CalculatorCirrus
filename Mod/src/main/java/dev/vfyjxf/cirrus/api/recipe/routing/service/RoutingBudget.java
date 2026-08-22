package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
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
 * @param maxWallTimeMs           maximum wall-clock time in milliseconds; {@code 0} disables the
 *                                deadline — routing is bounded deterministically by the count budgets
 *                                instead of machine-load-dependent wall time
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
        Checks.checkArgument(maxWallTimeMs >= 0, "maxWallTimeMs must be non-negative");
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

    /** Copy with a different generated-candidate ceiling (deepen-wave clamping). */
    public RoutingBudget withMaxGeneratedCandidates(int clampedMaxGeneratedCandidates) {
        return new RoutingBudget(
            maxExpandedNodes,
            clampedMaxGeneratedCandidates,
            maxWallTimeMs,
            maxMemoryStates,
            maxRootInputExpandedNodes,
            maxRootInputGeneratedCandidates,
            explorationBudgetShare,
            loopBudget
        );
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
        return new RoutingBudget(1_200, 6_000, 500, 2_000, 0, 0, 0.05, LoopBudget.standardDefault());
    }


    public static RoutingBudget assuredDefault() {
        return new RoutingBudget(12_000, 60_000, 5_000, 15_000, 1_500, 7_500, 0.20, LoopBudget.assuredDefault());
    }

    /**
     * Sizes the budget from the actual request-cone shape instead of catalog-size
     * proxies: big recipes (deep cones, fat tag inputs, many looping families)
     * automatically get proportionally larger budgets, while small targets keep
     * the responsive defaults as a floor.
     *
     * <p>Per-root limits split the total budget evenly across the target's root
     * inputs so one greedy branch cannot starve its siblings.
     *
     * @param cone           cone statistics of the target over the routable graph
     * @param mode           planner mode (assured-quality modes get higher floors)
     * @param rootInputCount number of consumable root inputs on the target recipe
     */
    public static RoutingBudget fromConeStats(ConeStats cone, PlannerMode mode, int rootInputCount) {
        int roots = Math.max(1, rootInputCount);
        RoutingBudget floor = mode == PlannerMode.standard ? standardDefault() : assuredDefault();
        int expanded = clampBudget((int) Math.min(Integer.MAX_VALUE, cone.nodeCount() / 4L + 2_000), floor.maxExpandedNodes(), 96_000);
        int generated = clampBudget(
            (int) Math.min(Integer.MAX_VALUE, cone.fatInputCandidateEstimate() / 2L + cone.nodeCount() * 2L),
            floor.maxGeneratedCandidates(),
            480_000
        );
        // no wall-clock quota: count budgets (nodes/candidates/memory and the
        // per-root shares) are deterministic bounds; a time ceiling made results
        // drift with machine load and let time-hungry branches starve siblings
        int wall = 0;
        int memory = clampBudget(2_000 + cone.nodeCount(), floor.maxMemoryStates(), 40_000);
        int rootExpanded = clampBudget(Math.max(512, expanded / roots), 512, 24_000);
        int rootGenerated = clampBudget(Math.max(4_000, generated / roots), 4_000, 60_000);
        LoopBudget loops = cone.loopSccCount() >= 8
            ? new LoopBudget(8, 160)
            : floor.loopBudget();
        return new RoutingBudget(
            expanded,
            generated,
            wall,
            memory,
            rootExpanded,
            rootGenerated,
            floor.explorationBudgetShare(),
            loops
        );
    }

    private static int clampBudget(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

