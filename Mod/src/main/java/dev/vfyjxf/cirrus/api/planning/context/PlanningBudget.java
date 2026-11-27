package dev.vfyjxf.cirrus.api.planning.context;

import java.time.Duration;
import java.util.Objects;

public record PlanningBudget(
    Duration softTimeLimit,
    Duration hardTimeLimit,
    int maxSubgraphNodes,
    int maxSubgraphRecipes,
    int maxReturnedPlans,
    int maxDecisionPrompts,
    boolean enableMachineSetCoverRefinement,
    boolean enableBatchCouplingRefinement,
    int maxProducersPerResource,
    long searchWorkBudget
) {

    public static final int defaultMaxProducersPerResource = 16;
    public static final long defaultSearchWorkBudget = 1200L;

    public PlanningBudget(
        Duration softTimeLimit,
        Duration hardTimeLimit,
        int maxSubgraphNodes,
        int maxSubgraphRecipes,
        int maxReturnedPlans,
        int maxDecisionPrompts,
        boolean enableMachineSetCoverRefinement,
        boolean enableBatchCouplingRefinement
    ) {
        this(
            softTimeLimit,
            hardTimeLimit,
            maxSubgraphNodes,
            maxSubgraphRecipes,
            maxReturnedPlans,
            maxDecisionPrompts,
            enableMachineSetCoverRefinement,
            enableBatchCouplingRefinement,
            defaultMaxProducersPerResource,
            defaultSearchWorkBudget
        );
    }

    public PlanningBudget {
        Objects.requireNonNull(softTimeLimit, "softTimeLimit");
        Objects.requireNonNull(hardTimeLimit, "hardTimeLimit");
        if (softTimeLimit.isNegative()) {
            throw new IllegalArgumentException("softTimeLimit must be >= 0");
        }
        if (hardTimeLimit.isNegative()) {
            throw new IllegalArgumentException("hardTimeLimit must be >= 0");
        }
        if (softTimeLimit.compareTo(hardTimeLimit) > 0) {
            throw new IllegalArgumentException("softTimeLimit must be <= hardTimeLimit");
        }
        if (maxSubgraphNodes <= 0) {
            throw new IllegalArgumentException("maxSubgraphNodes must be > 0");
        }
        if (maxSubgraphRecipes <= 0) {
            throw new IllegalArgumentException("maxSubgraphRecipes must be > 0");
        }
        if (maxReturnedPlans <= 0) {
            throw new IllegalArgumentException("maxReturnedPlans must be > 0");
        }
        if (maxDecisionPrompts < 0) {
            throw new IllegalArgumentException("maxDecisionPrompts must be >= 0");
        }
        if (maxProducersPerResource <= 0) {
            throw new IllegalArgumentException("maxProducersPerResource must be > 0");
        }
        if (searchWorkBudget < 0L) {
            throw new IllegalArgumentException("searchWorkBudget must be >= 0");
        }
    }

    public static PlanningBudget defaultInteractive() {
        return new PlanningBudget(
            Duration.ofMillis(80),
            Duration.ofMillis(250),
            2500,
            6000,
            3,
            3,
            true,
            true,
            defaultMaxProducersPerResource,
            defaultSearchWorkBudget
        );
    }
}
