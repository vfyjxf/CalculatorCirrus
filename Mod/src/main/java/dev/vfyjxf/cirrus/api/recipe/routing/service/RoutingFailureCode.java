package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Exhaustive classification of routing failure reasons.
 *
 * <p>Every {@link RoutingIssue} carries exactly one failure code, enabling
 * deterministic programmatic handling and user-facing message mapping.
 */
public enum RoutingFailureCode {
    /** The request is malformed or contains invalid parameters. */
    invalidRequest,
    /** No recipe could be found to produce the demanded input/output. */
    noFeasibleSource,
    /** A cyclic dependency was detected and the loop policy forbids it. */
    cycleBlocked,
    /** Cyclic re-entry would exceed the amplification risk threshold. */
    cycleAmplificationBlocked,
    /** The per-recipe, per-SCC, or global loop budget was exhausted. */
    cycleBudgetExhausted,
    /** The rule profile lacks required capability flags for the requested planner mode. */
    capabilityGateRejected,
    /** The overall search budget (expanded nodes or generated candidates) was exhausted. */
    budgetExhausted,
    /** An unexpected internal error occurred during planning. */
    internalError,
    /** Required stock items are not available in the inventory. */
    missingStock,
    /** A required catalyst could not be reserved. */
    missingCatalyst,
    /** An input demand could not be satisfied by any candidate or stock. */
    unsatisfiedInput
}

