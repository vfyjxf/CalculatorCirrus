package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyDecision;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Frozen context passed into one planner run.
 *
 * <p>A {@code PlannerContext} bundles every piece of read-only state that a
 * {@link RoutePlanner} needs to execute: the recipe graph, request parameters,
 * rule profile, source-policy decision, tag snapshot, and a mutable
 * {@link Counters} instance for telemetry.
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
 * @param caches               request-scoped memoization for the candidate pipeline
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
    PlannerRequestCaches caches,
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
        Checks.checkNotNull(caches, "caches");
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
            caches,
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
        /**
         * Total number of candidate recipes that entered evaluation (passed the
         * static gates and were scored). Budget limits are measured against this.
         */
        public int generatedCandidates;
        /**
         * Raw producer refs visited during full producer scans (cache misses).
         * Diagnostic only — shows how much work the request-scoped producer
         * filter cache and the per-input scan cap avoided bounding.
         */
        public int scannedRefs;
        /** Number of candidates that passed all hard rules. */
        public int acceptedCandidates;
        /**
         * Candidate probes rolled back after a failed or soft child outcome —
         * the planner's backtracking count.
         */
        public int candidateRollbacks;
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
        /** Recipe most directly associated with the first budget exhaustion. */
        public String budgetCulpritRecipe = "none";
        /** Input most directly associated with the first budget exhaustion. */
        public String budgetCulpritInput = "none";
        /** Search depth where the first budget exhaustion was detected. */
        public int budgetCulpritDepth = -1;
        /** Remaining input amount when the first budget exhaustion was detected. */
        public long budgetCulpritRemaining;
        /** Whether the first budget exhaustion was on an active-path cycle candidate. */
        public boolean budgetCulpritActiveCycle;
        /** Expanded-node counter at the start of the root input currently being resolved. */
        public int rootInputExpandedStart;
        /** Generated-candidate counter at the start of the root input currently being resolved. */
        public int rootInputGeneratedStart;
        /** Index of the root input currently being resolved; {@code -1} outside root input resolution. */
        public int activeRootInputIndex = -1;
        /** Description of the root input currently being resolved. */
        public String activeRootInput = "none";
        /**
         * Wall-clock deadline for this run, computed from
         * {@code budget.maxWallTimeMs()} at plan start. {@code Long.MAX_VALUE}
         * disables the check (never set by the planner entry points).
         */
        public long wallDeadlineNanos = Long.MAX_VALUE;

        public void recordBudgetCulprit(
            String recipe,
            String input,
            int depth,
            long remaining,
            boolean activeCycleCandidate
        ) {
            if (!"none".equals(budgetCulpritRecipe)) {
                return;
            }
            budgetCulpritRecipe = activeRootInputIndex >= 0 ? recipe + "#rootInput" + activeRootInputIndex : recipe;
            budgetCulpritInput = activeRootInputIndex >= 0 ? activeRootInput : input;
            budgetCulpritDepth = depth;
            budgetCulpritRemaining = remaining;
            budgetCulpritActiveCycle = activeCycleCandidate;
        }

        public void beginRootInput(int inputIndex, String inputDescription) {
            activeRootInputIndex = inputIndex;
            activeRootInput = inputDescription;
            rootInputExpandedStart = expandedNodes;
            rootInputGeneratedStart = generatedCandidates;
        }

        public void clearRootInput() {
            activeRootInputIndex = -1;
            activeRootInput = "none";
        }
    }
}

