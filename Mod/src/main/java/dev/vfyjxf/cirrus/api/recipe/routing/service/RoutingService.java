package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.planner.AssuredPlanner;
import dev.vfyjxf.cirrus.api.recipe.routing.planner.PlannerContext;
import dev.vfyjxf.cirrus.api.recipe.routing.planner.RoutePlanner;
import dev.vfyjxf.cirrus.api.recipe.routing.planner.StandardPlanner;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyDecision;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagSourcePolicyRule;
import dev.vfyjxf.cirrus.api.recipe.routing.state.SccAnalysis;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagIndex;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.UserTagWorkspace;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Public routing orchestration service — the single runtime entry point for
 * recipe routing in the redesigned API.
 *
 * <p>{@code RoutingService} owns the complete routing pipeline:
 * <ol>
 *     <li><b>Request validation</b> — rejects malformed requests early.</li>
 *     <li><b>Source-policy evaluation</b> — applies tag-based rules to
 *         compute the allowed-recipes bitset via {@link SourcePolicyRule}.</li>
 *     <li><b>SCC analysis</b> — runs Tarjan’s algorithm on the filtered
 *         recipe graph to identify strongly connected components for
 *         cycle-aware planning.</li>
 *     <li><b>Planner dispatch</b> — selects the appropriate
 *         {@link RoutePlanner} based on the requested {@link PlannerMode}
 *         and delegates the actual planning work.</li>
 *     <li><b>Output-target adaptation</b> — for
 *         {@link RoutingRequest.OutputTarget} requests, searches for
 *         producer candidates and iterates them through the recipe-target
 *         planning path.</li>
 * </ol>
 *
 * <p>Thread-safety: this class is <b>not</b> thread-safe. Instances should
 * be used from a single thread or externally synchronized.
 *
 * @see RoutingRequest
 * @see RoutingOutcome
 * @see RoutePlanner
 */
public final class RoutingService {

    private final RecipeGraph graph;
    private final RoutingRecipeCatalog catalog;
    private final TagIndex tagIndex;
    private final UserTagWorkspace userWorkspace;
    private final SourcePolicyRule sourcePolicyRule = new TagSourcePolicyRule();
    private final MutableMap<Namespace, SourcePolicy> sourcePolicies = Maps.mutable.empty();
    private final MutableMap<Namespace, RuleProfile> ruleProfiles = Maps.mutable.empty();
    private final RoutePlanner standardPlanner = new StandardPlanner();
    private final RoutePlanner assuredPlanner = new AssuredPlanner();

    /**
     * Creates a new routing service backed by the given recipe graph.
     *
     * <p>Construction performs several initialization steps:
     * <ul>
     *     <li>Builds a {@link RoutingRecipeCatalog} for stable integer indexing.</li>
     *     <li>Creates a {@link TagIndex} reverse index for tag-to-recipe lookups.</li>
     *     <li>Creates a {@link UserTagWorkspace} for per-player tag overlays.</li>
     *     <li>Registers built-in default source policies and rule profiles.</li>
     * </ul>
     *
     * @param graph the immutable recipe dependency graph
     */
    public RoutingService(RecipeGraph graph) {
        this.graph = Checks.checkNotNull(graph, "graph");
        this.catalog = RoutingRecipeCatalog.fromGraph(graph);
        this.tagIndex = new TagIndex(catalog);
        this.userWorkspace = new UserTagWorkspace(tagIndex);
        registerSourcePolicy(SourcePolicy.defaultPolicy());
        registerRuleProfile(DefaultRuleProfiles.standardDefault());
        registerRuleProfile(DefaultRuleProfiles.assuredBoundedDefault());
        registerRuleProfile(DefaultRuleProfiles.assuredProvenCompat());
    }

    /**
     * Executes the full routing pipeline for the given request.
     *
     * <p>This is the primary public API. The method is synchronous and returns
     * a complete {@link RoutingOutcome} containing the plan (if successful),
     * issues, metrics, and optional diagnostic trace.
     *
     * @param request the fully resolved routing request
     * @return the routing outcome (never {@code null})
     */
    public RoutingOutcome route(RoutingRequest request) {
        Checks.checkNotNull(request, "request");
        RoutingOutcome invalid = validateRequest(request);
        if (invalid != null) {
            return invalid;
        }

        SourcePolicy sourcePolicy = sourcePolicies.getIfAbsent(request.sourcePolicyId(), SourcePolicy::defaultPolicy);
        RuleProfile profile = ruleProfiles.get(request.ruleProfileId());
        if (profile == null) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.unknown_rule_profile");
        }

        TagSnapshot snapshot = request.tagSnapshot() != null ? request.tagSnapshot() : userWorkspace.snapshot();
        SourcePolicyDecision sourceDecision = sourcePolicyRule.apply(snapshot, sourcePolicy);
        SccAnalysis sccAnalysis = SccAnalysis.build(graph, catalog, sourceDecision.allowedRecipes());

        if (request.target() instanceof RoutingRequest.RecipeTarget recipeTarget) {
            return routeRecipeTarget(
                request,
                recipeTarget,
                profile,
                sourceDecision,
                snapshot,
                sccAnalysis
            );
        }

        RoutingRequest.OutputTarget outputTarget = (RoutingRequest.OutputTarget) request.target();
        return routeOutputTarget(
            request,
            outputTarget,
            profile,
            sourceDecision,
            snapshot,
            sccAnalysis
        );
    }

    /**
     * Registers a source policy that can be referenced by ID in routing requests.
     *
     * @param sourcePolicy the policy to register
     */
    public void registerSourcePolicy(SourcePolicy sourcePolicy) {
        sourcePolicies.put(sourcePolicy.policyId(), sourcePolicy);
    }

    /**
     * Registers a rule profile that can be referenced by ID in routing requests.
     *
     * @param ruleProfile the profile to register
     */
    public void registerRuleProfile(RuleProfile ruleProfile) {
        ruleProfiles.put(ruleProfile.profileId(), ruleProfile);
    }

    /** Returns the tag index used by this service. */
    public TagIndex tagIndex() {
        return tagIndex;
    }

    /** Returns the per-player tag workspace. */
    public UserTagWorkspace userWorkspace() {
        return userWorkspace;
    }

    /** Returns the underlying recipe graph. */
    public RecipeGraph graph() {
        return graph;
    }

    private RoutingOutcome routeRecipeTarget(
        RoutingRequest request,
        RoutingRequest.RecipeTarget target,
        RuleProfile profile,
        SourcePolicyDecision sourceDecision,
        TagSnapshot snapshot,
        SccAnalysis sccAnalysis
    ) {
        int targetIndex = catalog.indexOf(target.recipe());
        if (targetIndex < 0) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.target_not_in_graph");
        }
        if (!sourceDecision.allowedRecipes().get(targetIndex)) {
            return invalidFailure(request, RoutingFailureCode.noFeasibleSource, "cirrus.routing.target_filtered_out");
        }

        PlannerContext context = new PlannerContext(
            this,
            graph,
            catalog,
            request,
            profile,
            sourceDecision,
            snapshot,
            sccAnalysis,
            new PlannerContext.Counters()
        );
        RoutePlanner planner = choosePlanner(request.plannerMode());
        return planner.plan(context);
    }

    private RoutingOutcome routeOutputTarget(
        RoutingRequest request,
        RoutingRequest.OutputTarget outputTarget,
        RuleProfile profile,
        SourcePolicyDecision sourceDecision,
        TagSnapshot snapshot,
        SccAnalysis sccAnalysis
    ) {
        List<ProducerCandidate> producers = findProducers(outputTarget.output(), sourceDecision.allowedRecipes());
        if (producers.isEmpty()) {
            return invalidFailure(request, RoutingFailureCode.noFeasibleSource, "cirrus.routing.no_output_producer");
        }

        producers.sort(Comparator
            .comparingDouble((ProducerCandidate candidate) -> candidate.estimatedUnitCost)
            .thenComparingInt(candidate -> candidate.depthEstimate)
            .thenComparing(candidate -> candidate.stableId));

        int maxAttempts = Math.min(request.maxProducerCandidates(), producers.size());
        MutableList<String> triedProducerIds = Lists.mutable.empty();
        RoutingOutcome bestPartial = null;
        int totalExpanded = 0;
        int totalGenerated = 0;
        int totalAccepted = 0;
        long totalElapsedMs = 0;

        for (int i = 0; i < maxAttempts; i++) {
            ProducerCandidate candidate = producers.get(i);
            triedProducerIds.add(candidate.stableId);

            int times = (int) Math.ceil((double) outputTarget.amount() / candidate.producedPerCraft);
            RoutingRequest subRequest = new RoutingRequest(
                request.plannerMode(),
                request.ruleProfileId(),
                request.sourcePolicyId(),
                request.diagnosticLevel(),
                request.deterministic(),
                request.allowModeFallback(),
                request.budget(),
                request.stock(),
                snapshot,
                new RoutingRequest.RecipeTarget(candidate.node.recipe(), times),
                request.inventorySensitivityProfile(),
                request.tagResolvePolicy(),
                request.effectiveTagResolvePolicy(),
                request.adapterSelectionPolicy(),
                request.maxProducerCandidates(),
                request.loopPolicy(),
                request.loopBudget(),
                request.explorationEnabled(),
                request.explorationPolicyId(),
                request.explorationSeed(),
                request.explorationBudgetShare()
            );

            RoutingOutcome outcome = routeRecipeTarget(subRequest, (RoutingRequest.RecipeTarget) subRequest.target(), profile, sourceDecision, snapshot, sccAnalysis);
            totalElapsedMs += outcome.metrics().elapsedMs();
            totalExpanded += outcome.metrics().expandedNodes();
            totalGenerated += outcome.metrics().generatedCandidates();
            totalAccepted += outcome.metrics().acceptedCandidates();

            if (outcome.status() == RoutingStatus.success) {
                RoutingMetrics metrics = rewriteAdapterMetrics(
                    outcome.metrics(),
                    totalElapsedMs,
                    totalExpanded,
                    totalGenerated,
                    totalAccepted,
                    i + 1,
                    triedProducerIds.toImmutable(),
                    RoutingStatus.success
                );
                return new RoutingOutcome(RoutingStatus.success, outcome.plan(), outcome.issues(), metrics, outcome.trace());
            }

            if (outcome.status() == RoutingStatus.partial && bestPartial == null) {
                bestPartial = outcome;
                if (request.adapterSelectionPolicy() == OutputSelectionPolicy.bestEffortSingleRecipe) {
                    // Keep searching for success, but retain best partial.
                }
            }

            if (request.adapterSelectionPolicy() == OutputSelectionPolicy.strictSingleRecipe) {
                break;
            }
        }

        if (bestPartial != null) {
            RoutingMetrics metrics = rewriteAdapterMetrics(
                bestPartial.metrics(),
                totalElapsedMs,
                totalExpanded,
                totalGenerated,
                totalAccepted,
                triedProducerIds.size(),
                triedProducerIds.toImmutable(),
                RoutingStatus.partial
            );
            return new RoutingOutcome(RoutingStatus.partial, bestPartial.plan(), bestPartial.issues(), metrics, bestPartial.trace());
        }

        RoutingOutcome failed = invalidFailure(request, RoutingFailureCode.noFeasibleSource, "cirrus.routing.no_feasible_output_route");
        RoutingMetrics metrics = rewriteAdapterMetrics(
            failed.metrics(),
            totalElapsedMs,
            totalExpanded,
            totalGenerated,
            totalAccepted,
            triedProducerIds.size(),
            triedProducerIds.toImmutable(),
            RoutingStatus.failed
        );
        return new RoutingOutcome(RoutingStatus.failed, null, failed.issues(), metrics, failed.trace());
    }

    private RoutingMetrics rewriteAdapterMetrics(
        RoutingMetrics original,
        long elapsedMs,
        int expandedNodes,
        int generatedCandidates,
        int acceptedCandidates,
        int attemptCount,
        ImmutableList<String> triedProducers,
        RoutingStatus bestIntermediateStatus
    ) {
        return new RoutingMetrics(
            elapsedMs,
            expandedNodes,
            generatedCandidates,
            acceptedCandidates,
            original.effectivePlannerMode(),
            original.effectiveInventorySensitivityProfile(),
            original.effectiveTagResolvePolicy(),
            original.effectiveRuleProfileId(),
            original.effectiveSourcePolicyId(),
            original.tagSnapshotVersion(),
            attemptCount,
            triedProducers,
            bestIntermediateStatus,
            original.budgetLimit(),
            original.budgetUsed(),
            original.budgetExhausted(),
            original.capabilityGatePassed(),
            original.missingCapabilities(),
            original.budgetStopReason(),
            original.explorationBranchesExpanded(),
            original.explorationBudgetUsed(),
            original.effectiveExplorationSeed(),
            original.effectiveLoopPolicy()
        );
    }

    private List<ProducerCandidate> findProducers(ContentResource output, BitSet allowedRecipes) {
        List<ProducerCandidate> candidates = new ArrayList<>();
        for (int index = allowedRecipes.nextSetBit(0); index >= 0; index = allowedRecipes.nextSetBit(index + 1)) {
            RecipeGraph.Node node = catalog.nodeAt(index);
            long bestAmount = 0;
            for (OutputContent candidateOutput : node.mergedOutputs()) {
                if (candidateOutput.asResource().matchesResource(output)) {
                    bestAmount = Math.max(bestAmount, candidateOutput.amount());
                }
            }
            if (bestAmount <= 0) {
                continue;
            }
            String stableId = catalog.stableRecipeId(index);
            candidates.add(new ProducerCandidate(node, index, bestAmount, estimateUnitCost(node), estimateDepth(node), stableId));
        }
        return candidates;
    }

    private double estimateUnitCost(RecipeGraph.Node node) {
        // Quick deterministic estimate used only for producer ordering in adapter mode.
        return node.mergedInputs().size() + (node.mergedOutputs().size() * 0.1);
    }

    private int estimateDepth(RecipeGraph.Node node) {
        return node.mergedInputs().size();
    }

    private RoutePlanner choosePlanner(PlannerMode mode) {
        return switch (mode) {
            case standard -> standardPlanner;
            case assuredBounded, assuredProven -> assuredPlanner;
        };
    }

    private RoutingOutcome validateRequest(RoutingRequest request) {
        if (request.target() == null) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.missing_target");
        }
        if (request.loopPolicy() == LoopPolicy.allowUnbounded) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.loop_policy_unbounded_forbidden");
        }
        if (request.plannerMode() == PlannerMode.assuredBounded && request.budget().maxExpandedNodes() <= 0) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.invalid_budget");
        }
        return null;
    }

    private RoutingOutcome invalidFailure(RoutingRequest request, RoutingFailureCode code, String messageKey) {
        RoutingIssue issue = new RoutingIssue(
            "issue-0",
            code,
            RoutingIssue.Severity.error,
            true,
            messageKey,
            Maps.immutable.empty(),
            null,
            null,
            Maps.immutable.empty()
        );
        RoutingMetrics metrics = new RoutingMetrics(
            0,
            0,
            0,
            0,
            request.plannerMode(),
            request.inventorySensitivityProfile(),
            request.effectiveTagResolvePolicy(),
            request.ruleProfileId(),
            request.sourcePolicyId(),
            request.tagSnapshot() == null ? 0 : request.tagSnapshot().version(),
            0,
            Lists.immutable.empty(),
            null,
            request.budget().maxExpandedNodes(),
            0,
            false,
            true,
            Lists.immutable.empty(),
            "invalidRequest",
            0,
            0,
            request.explorationSeed() == null ? 0 : request.explorationSeed(),
            request.loopPolicy()
        );
        return new RoutingOutcome(RoutingStatus.failed, null, Lists.immutable.of(issue), metrics, null);
    }

    private record ProducerCandidate(
        RecipeGraph.Node node,
        int recipeIndex,
        long producedPerCraft,
        double estimatedUnitCost,
        int depthEstimate,
        String stableId
    ) {
    }
}
