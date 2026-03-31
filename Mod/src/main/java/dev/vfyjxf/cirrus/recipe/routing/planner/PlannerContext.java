package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyDecision;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.api.recipe.routing.state.SccAnalysis;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Frozen context passed into one planner run.
 *
 * <p>A {@code PlannerContext} bundles every piece of read-only state that a
 * {@link RoutePlanner} needs to execute: the recipe graph, request parameters,
 * rule profile, source-policy decision, tag snapshot, SCC analysis, and a
 * mutable {@link Counters} instance for telemetry.
 *
 * <p>All record components except {@code counters} are immutable or effectively
 * immutable during the planning run.
 *
 * @param service              the routing service that created this context
 * @param graph                the immutable recipe dependency graph
 * @param catalog              stable integer-indexed recipe list for bitset operations
 * @param request              the fully resolved routing request
 * @param ruleProfile          the rule profile governing candidate evaluation
 * @param sourcePolicyDecision the pre-computed source-policy decision (allowed recipes bitset)
 * @param tagSnapshot          the frozen tag-to-recipe assignments
 * @param sccAnalysis          the SCC decomposition of the recipe graph
 * @param counters             mutable telemetry counters updated during planning
 * @see RoutePlanner
 */
public record PlannerContext(
    RoutingService service,
    RecipeGraph graph,
    RoutingRecipeCatalog catalog,
    RoutingRequest request,
    RuleProfile ruleProfile,
    SourcePolicyDecision sourcePolicyDecision,
    TagSnapshot tagSnapshot,
    SccAnalysis sccAnalysis,
    Counters counters
) {
    public PlannerContext {
        Checks.checkNotNull(service, "service");
        Checks.checkNotNull(graph, "graph");
        Checks.checkNotNull(catalog, "catalog");
        Checks.checkNotNull(request, "request");
        Checks.checkNotNull(ruleProfile, "ruleProfile");
        Checks.checkNotNull(sourcePolicyDecision, "sourcePolicyDecision");
        Checks.checkNotNull(tagSnapshot, "tagSnapshot");
        Checks.checkNotNull(sccAnalysis, "sccAnalysis");
        Checks.checkNotNull(counters, "counters");
    }

    public PlannerContext withRequest(RoutingRequest request) {
        return new PlannerContext(
            service,
            graph,
            catalog,
            request,
            ruleProfile,
            sourcePolicyDecision,
            tagSnapshot,
            sccAnalysis,
            counters
        );
    }

    /**
     * Mutable telemetry counters accumulated during one planning run.
     *
     * <p>These are written by the planner and later copied into the
     * {@link dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics} of the outcome.
     */
    public static final class Counters {
        /** Number of recipe nodes expanded (visited) during search. */
        public int expandedNodes;
        /** Total number of candidate recipes generated for evaluation. */
        public int generatedCandidates;
        /** Number of candidates that passed all hard rules. */
        public int acceptedCandidates;
        /** Number of output-target adapter attempts (producer candidates tried). */
        public int adapterAttemptCount;
        /** Number of branches expanded during optional exploration. */
        public int explorationBranchesExpanded;
        /** Exploration-specific budget units consumed. */
        public int explorationBudgetUsed;
        /** Overall budget units consumed (typically equals {@code expandedNodes}). */
        public int budgetUsed;
        /** Reason the budget was exhausted; {@code "none"} if not exhausted. */
        public String budgetStopReason = "none";
        /** Whether the budget was fully exhausted before planning completed. */
        public boolean budgetExhausted;
    }
}

