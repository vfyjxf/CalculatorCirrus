package dev.vfyjxf.cirrus.api.planning.service;

/**
 * Explicit Planning v3 status contract (M1):
 * strictly separates certified feasible plans, conditional drafts, budget exhaustion,
 * proven infeasibility (including human-rule conflicts), and pending async continuation.
 */
public enum PlanningStatus {
    feasible,
    conditional,
    noPlanWithinBudget,
    provenInfeasible,
    infeasibleByHumanRules,
    pending;

    public boolean hasExecutablePlan() {
        return this == feasible;
    }

    public boolean isInfeasible() {
        return this == provenInfeasible || this == infeasibleByHumanRules;
    }
}
