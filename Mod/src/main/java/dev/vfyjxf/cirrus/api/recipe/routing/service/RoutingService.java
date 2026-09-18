package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.HeuristicPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.recipe.routing.planner.AssuredPlanner;
import dev.vfyjxf.cirrus.recipe.routing.planner.AcyclicPlanner;
import dev.vfyjxf.cirrus.recipe.routing.planner.PlannerContext;
import dev.vfyjxf.cirrus.recipe.routing.planner.PlannerRequestCaches;
import dev.vfyjxf.cirrus.recipe.routing.planner.RoutePlanner;
import dev.vfyjxf.cirrus.recipe.routing.planner.StandardPlanner;
import dev.vfyjxf.cirrus.recipe.routing.rule.BuiltInContextualRules;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import dev.vfyjxf.cirrus.recipe.routing.topology.GraphTopology;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.PolicyExplain;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollection;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyDecision;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagSourcePolicyRule;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Public routing orchestration service — the single runtime entry point for
 * recipe routing in the redesigned API.
 *
 * <p>{@code RoutingService} owns the complete routing pipeline:
 * <ol>
 *     <li><b>Request validation</b> — rejects malformed requests early.</li>
 *     <li><b>Source-policy evaluation</b> — applies tag-based rules to
 *         compute the allowed-recipes bitset via {@link SourcePolicyRule}.</li>
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
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");
    private static final int maxCachedPolicyAnalyses = 16;
    private static final Object policyAnalysisCacheLock = new Object();
    private static final Map<PolicyAnalysisCacheKey, PolicyAnalysis> policyAnalysisCache = new HashMap<>();
    private static final SourcePolicy defaultSourcePolicy = SourcePolicy.defaultPolicy();
    private static final AtomicLong nextGraphCacheId = new AtomicLong();

    private final RecipeGraph graph;
    private final RoutingRecipeCatalog catalog;
    private final long graphCacheId;
    private final TagIndex tagIndex;
    private final UserTagWorkspace userWorkspace;
    private final PolicyAnalysis allAllowedPolicyAnalysis;
    private final SourcePolicyRule sourcePolicyRule = new TagSourcePolicyRule();
    private final MutableMap<Namespace, SourcePolicy> sourcePolicies = Maps.mutable.empty();
    private final MutableMap<Namespace, RuleProfile> ruleProfiles = Maps.mutable.empty();
    private final RoutePlanner standardPlanner = new StandardPlanner();
    private final RoutePlanner assuredPlanner = new AssuredPlanner();
    private final RoutePlanner acyclicPlanner = new AcyclicPlanner(standardPlanner);
    private final RoutePlanner costAwarePlanner = new dev.vfyjxf.cirrus.recipe.routing.planner.CostAwarePlanner();
    private final Object coneCacheLock = new Object();
    private final Map<ConeCacheKey, ConeStats> coneCache = new LinkedHashMap<>(16, 0.75f, true);
    /**
     * Continuation sessions: request-scoped planner caches (static producer
     * shortlists, unit-cost oracle memos) kept alive across waves of the same
     * logical request so a continuation wave never re-scans. Access-ordered LRU;
     * sessions vanish on tag/version drift via fingerprint mismatch.
     */
    private final Object sessionLock = new Object();
    private final Map<String, PlannerRequestCaches> planningSessions = new LinkedHashMap<>(16, 0.75f, true);
    private static final int maxPlanningSessions = 8;
    private volatile @Nullable GraphTopology topology;

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
        this.graphCacheId = nextGraphCacheId.incrementAndGet();
        this.catalog = RoutingRecipeCatalog.fromGraph(graph);
        this.tagIndex = new TagIndex(catalog);
        this.userWorkspace = new UserTagWorkspace(tagIndex);
        this.allAllowedPolicyAnalysis = allAllowedPolicyAnalysis(catalog.size(), tagIndex.version());
        BuiltInContextualRules.register();
        registerSourcePolicy(defaultSourcePolicy);
        registerRuleCollection(DefaultRuleDefinitions.defaultCollection());
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
        return routeInternal(request, null);
    }

    /**
     * Continues a previous partial/budget-truncated route. The token identifies the
     * planning session whose request-scoped caches (producer shortlists, unit-cost
     * memos) are reused, so the follow-up wave only pays for the extra budget, not
     * for re-scanning. A stale or mismatched token transparently degrades to a
     * fresh {@link #route(RoutingRequest)}.
     *
     * @param continuationToken token from {@link RoutingOutcome#continuationToken()}
     * @param request           the same logical request, typically with a larger budget
     */
    public RoutingOutcome routeContinuation(String continuationToken, RoutingRequest request) {
        Checks.checkNotNull(continuationToken, "continuationToken");
        Checks.checkNotNull(request, "request");
        return routeInternal(request, continuationToken);
    }

    private RoutingOutcome routeInternal(RoutingRequest request, @Nullable String continuationToken) {
        Checks.checkNotNull(request, "request");
        long routeStartNanos = System.nanoTime();
        if (logger.isDebugEnabled()) {
            logger.debug(
                "routing.start target={} mode={} profile={} sourcePolicy={} budgetNodes={} budgetCandidates={} heuristic={} stockEmpty={}",
                describeTarget(request.target()),
                request.plannerMode(),
                request.ruleProfileId(),
                request.sourcePolicyId(),
                request.budget().maxExpandedNodes(),
                request.budget().maxGeneratedCandidates(),
                request.heuristicPolicy(),
                request.stock().isEmpty()
            );
        }
        RoutingOutcome invalid = validateRequest(request);
        if (invalid != null) {
            logRouteFinish(invalid, routeStartNanos);
            return invalid;
        }

        SourcePolicy sourcePolicy = sourcePolicies.getIfAbsent(request.sourcePolicyId(), SourcePolicy::defaultPolicy);
        RuleProfile profile = ruleProfiles.get(request.ruleProfileId());
        if (profile == null) {
            RoutingOutcome failed = invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.unknown_rule_profile");
            logRouteFinish(failed, routeStartNanos);
            return failed;
        }

        TagSnapshot snapshot = request.tagSnapshot() != null ? request.tagSnapshot() : userWorkspace.snapshot();
        if (logger.isDebugEnabled()) {
            logger.debug(
                "routing.analysis.start target={} graphNodes={} catalog={} snapshot={} tags={} sourcePolicyHash={}",
                describeTarget(request.target()),
                catalog.size(),
                catalog.size(),
                snapshot.version(),
                snapshot.tagToRecipes().size(),
                sourcePolicy.hashCode()
            );
        }
        PolicyAnalysis policyAnalysis = cachedPolicyAnalysis(snapshot, sourcePolicy);
        if (logger.isDebugEnabled()) {
            logger.debug(
                "routing.analysis.finish target={} elapsedMs={} allowed={}",
                describeTarget(request.target()),
                (System.nanoTime() - routeStartNanos) / 1_000_000L,
                policyAnalysis.sourceDecision().allowedRecipes().cardinality()
            );
        }
        SourcePolicyDecision sourceDecision = policyAnalysis.sourceDecision();

        RoutingOutcome outcome;
        if (request.target() instanceof RoutingRequest.RecipeTarget recipeTarget) {
            outcome = routeRecipeTarget(
                request,
                recipeTarget,
                profile,
                sourceDecision,
                snapshot,
                continuationToken
            );
        } else {
            RoutingRequest.OutputTarget outputTarget = (RoutingRequest.OutputTarget) request.target();
            outcome = routeOutputTarget(
                request,
                outputTarget,
                profile,
                sourceDecision,
                snapshot
            );
        }
        logRouteFinish(outcome, routeStartNanos);
        return outcome;
    }

    private PolicyAnalysis cachedPolicyAnalysis(TagSnapshot snapshot, SourcePolicy sourcePolicy) {
        if (sourcePolicy.equals(defaultSourcePolicy) && snapshot.tagToRecipes().isEmpty()) {
            return allAllowedPolicyAnalysis;
        }
        long startNanos = System.nanoTime();
        PolicyAnalysisCacheKey key = new PolicyAnalysisCacheKey(
            graphCacheId,
            catalog.size(),
            snapshot.version(),
            snapshot.tagToRecipes().hashCode(),
            sourcePolicy.hashCode()
        );
        synchronized (policyAnalysisCacheLock) {
            PolicyAnalysis cached = policyAnalysisCache.get(key);
            if (cached != null) {
                PolicyAnalysis touched = cached.touch();
                policyAnalysisCache.put(key, touched);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "routing.analysis.cacheHit catalog={} snapshot={} allowed={} elapsedMs={}",
                        catalog.size(),
                        snapshot.version(),
                        touched.sourceDecision().allowedRecipes().cardinality(),
                        (System.nanoTime() - startNanos) / 1_000_000L
                    );
                }
                return touched;
            }
        }

        long policyStartNanos = System.nanoTime();
        SourcePolicyDecision sourceDecision = sourcePolicyRule.apply(snapshot, sourcePolicy);
        if (logger.isDebugEnabled()) {
            logger.debug(
                "routing.analysis.policy catalog={} snapshot={} rules={} allowed={} policyMs={} totalMs={}",
                catalog.size(),
                snapshot.version(),
                sourcePolicy.rules().size(),
                sourceDecision.allowedRecipes().cardinality(),
                (System.nanoTime() - policyStartNanos) / 1_000_000L,
                (System.nanoTime() - startNanos) / 1_000_000L
            );
        }
        PolicyAnalysis analysis = new PolicyAnalysis(sourceDecision, System.nanoTime());
        synchronized (policyAnalysisCacheLock) {
            PolicyAnalysis existing = policyAnalysisCache.get(key);
            if (existing != null) {
                PolicyAnalysis touched = existing.touch();
                policyAnalysisCache.put(key, touched);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "routing.analysis.cacheRace catalog={} snapshot={} elapsedMs={}",
                        catalog.size(),
                        snapshot.version(),
                        (System.nanoTime() - startNanos) / 1_000_000L
                    );
                }
                return touched;
            }
            policyAnalysisCache.put(key, analysis);
            trimPolicyAnalysisCache();
            return analysis;
        }
    }

    private static void trimPolicyAnalysisCache() {
        while (policyAnalysisCache.size() > maxCachedPolicyAnalyses) {
            PolicyAnalysisCacheKey oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (var entry : policyAnalysisCache.entrySet()) {
                long lastAccess = entry.getValue().lastAccessNanos();
                if (lastAccess < oldestAccess) {
                    oldestAccess = lastAccess;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            policyAnalysisCache.remove(oldestKey);
        }
    }

    private static PolicyAnalysis allAllowedPolicyAnalysis(int recipeCount, long snapshotVersion) {
        BitSet allowed = new BitSet(recipeCount);
        allowed.set(0, recipeCount);
        PolicyExplain explain = new PolicyExplain(
            snapshotVersion,
            recipeCount,
            recipeCount,
            Lists.immutable.empty()
        );
        return new PolicyAnalysis(new SourcePolicyDecision(allowed, explain), System.nanoTime());
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

    /** The registered profile for the given id, or null when unregistered. */
    public @Nullable RuleProfile ruleProfile(Namespace profileId) {
        return ruleProfiles.get(profileId);
    }

    public void registerRuleDefinition(RuleDefinition ruleDefinition) {
        registerSourcePolicy(ruleDefinition.sourcePolicy());
        registerRuleProfile(ruleDefinition.toProfile());
    }

    public void registerRuleCollection(RuleCollection collection) {
        for (RuleDefinition rule : collection.rules()) {
            registerRuleDefinition(rule);
        }
    }

    /** Returns the tag index used by this service. */
    public TagIndex tagIndex() {
        return tagIndex;
    }

    /** Returns the stable recipe catalog used by this service. */
    public RoutingRecipeCatalog catalog() {
        return catalog;
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
        @Nullable String continuationToken
    ) {
        int targetIndex = catalog.indexOf(target.recipe());
        if (targetIndex < 0) {
            return invalidFailure(request, RoutingFailureCode.invalidRequest, "cirrus.routing.target_not_in_graph");
        }
        if (!sourceDecision.allowedRecipes().get(targetIndex)) {
            return invalidFailure(request, RoutingFailureCode.noFeasibleSource, "cirrus.routing.target_filtered_out");
        }

        String sessionFingerprint = sessionFingerprint(request, target, snapshot);
        PlannerRequestCaches caches = null;
        int wave = 1;
        if (continuationToken != null) {
            int waveMarker = continuationToken.lastIndexOf("#w");
            String tokenFingerprint = waveMarker > 0 ? continuationToken.substring(0, waveMarker) : continuationToken;
            if (waveMarker > 0) {
                try {
                    wave = Integer.parseInt(continuationToken.substring(waveMarker + 2)) + 1;
                } catch (NumberFormatException ignored) {
                    wave = 1;
                }
            }
            if (tokenFingerprint.equals(sessionFingerprint)) {
                synchronized (sessionLock) {
                    caches = planningSessions.get(sessionFingerprint);
                }
            }
        }

        PlannerContext context = new PlannerContext(
            this,
            graph,
            catalog,
            request,
            profile,
            sourceDecision,
            snapshot,
            caches != null ? caches : new PlannerRequestCaches(),
            new PlannerContext.Counters()
        );
        RoutePlanner planner = choosePlanner(request, targetIndex, sourceDecision.allowedRecipes());
        RoutingOutcome outcome = planner.plan(context);
        if (outcome.partial() || outcome.metrics().budgetExhausted()) {
            // Keep the request-scoped caches warm so a continuation wave with a
            // larger budget resumes without re-scanning producers.
            PlannerRequestCaches sessionCaches = context.caches();
            synchronized (sessionLock) {
                planningSessions.put(sessionFingerprint, sessionCaches);
                if (planningSessions.size() > maxPlanningSessions) {
                    var iterator = planningSessions.keySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
            }
            outcome = outcome.withContinuationToken(sessionFingerprint + "#w" + wave);
        }
        return outcome;
    }

    /**
     * Session fingerprint covering everything the request-scoped caches depend on:
     * the target, the static filter inputs (source policy, heuristic policy), the
     * scoring setup (mode, profile) and the tag snapshot version.
     */
    private String sessionFingerprint(RoutingRequest request, RoutingRequest.RecipeTarget target, TagSnapshot snapshot) {
        return String.valueOf(target.recipe().id())
            + "|" + target.times()
            + "|" + request.plannerMode()
            + "|" + request.ruleProfileId()
            + "|" + request.sourcePolicyId()
            + "|" + snapshot.version()
            + "|" + request.heuristicPolicy()
            + "|" + request.loopPolicy();
    }

    private RoutingOutcome routeOutputTarget(
        RoutingRequest request,
        RoutingRequest.OutputTarget outputTarget,
        RuleProfile profile,
        SourcePolicyDecision sourceDecision,
        TagSnapshot snapshot
    ) {
        List<ProducerCandidate> producers = findProducers(
            outputTarget.output(),
            outputTarget.amount(),
            sourceDecision.allowedRecipes(),
            request.stock(),
            request.heuristicPolicy()
        );
        if (logger.isDebugEnabled()) {
            logger.debug(
                "producer.search output={} amount={} producers={} allowedRecipes={} maxAttempts={} heuristic={}",
                outputTarget.output(),
                outputTarget.amount(),
                producers.size(),
                sourceDecision.allowedRecipes().cardinality(),
                request.maxProducerCandidates(),
                request.heuristicPolicy()
            );
        }
        if (producers.isEmpty()) {
            return invalidFailure(request, RoutingFailureCode.noFeasibleSource, "cirrus.routing.no_output_producer");
        }

        producers.sort(producerComparator(request.inventorySensitivityProfile()));

        int maxAttempts = Math.min(request.maxProducerCandidates(), producers.size());
        MutableList<String> triedProducerIds = Lists.mutable.empty();
        RoutingOutcome bestPartial = null;
        int totalExpanded = 0;
        int totalGenerated = 0;
        int totalAccepted = 0;
        long totalElapsedMs = 0;

        for (int i = 0; i < maxAttempts; i++) {
            ProducerCandidate candidate = producers.get(i);
            triedProducerIds.add(candidate.stableId());

            int times = (int) Math.ceil((double) outputTarget.amount() / candidate.producedPerCraft());
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "producer.try index={} recipe={} producedPerCraft={} craftTimes={} unitCost={} depth={} stockShortage={}",
                    i,
                    candidate.stableId(),
                    candidate.producedPerCraft(),
                    times,
                    candidate.estimatedUnitCost(),
                    candidate.depthEstimate(),
                    candidate.inventoryShortage()
                );
            }
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
                new RoutingRequest.RecipeTarget(candidate.node().recipe(), times),
                request.inventorySensitivityProfile(),
                request.tagResolvePolicy(),
                request.effectiveTagResolvePolicy(),
                request.preferredVariants(),
                request.adapterSelectionPolicy(),
                request.maxProducerCandidates(),
                request.loopPolicy(),
                request.loopBudget(),
                request.explorationEnabled(),
                request.explorationPolicyId(),
                request.explorationSeed(),
                request.explorationBudgetShare(),
                request.heuristicPolicy()
            );

            RoutingOutcome outcome = routeRecipeTarget(subRequest, (RoutingRequest.RecipeTarget) subRequest.target(), profile, sourceDecision, snapshot, null);
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "producer.result index={} recipe={} status={} planActions={} issues={} elapsedMs={} expanded={} generated={} accepted={}",
                    i,
                    candidate.stableId(),
                    outcome.status(),
                    outcome.plan() == null ? 0 : outcome.plan().actions().size(),
                    outcome.issues().size(),
                    outcome.metrics().elapsedMs(),
                    outcome.metrics().expandedNodes(),
                    outcome.metrics().generatedCandidates(),
                    outcome.metrics().acceptedCandidates()
                );
            }
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
            original.scannedRefs(),
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
            original.budgetCulpritRecipe(),
            original.budgetCulpritInput(),
            original.budgetCulpritDepth(),
            original.budgetCulpritRemaining(),
            original.budgetCulpritActiveCycle(),
            original.explorationBranchesExpanded(),
            original.explorationBudgetUsed(),
            original.effectiveExplorationSeed(),
            original.effectiveLoopPolicy(),
            original.candidateRollbacks()
        );
    }

    private List<ProducerCandidate> findProducers(
        ContentResource output,
        long requestedAmount,
        BitSet allowedRecipes,
        Stock stock,
        HeuristicPolicy heuristicPolicy
    ) {
        var indexedProducers = graph.producersFor(output);
        if (indexedProducers.isEmpty()) {
            return List.of();
        }

        List<ProducerCandidate> candidates = new ArrayList<>(indexedProducers.size());
        for (RecipeGraph.OutputRef ref : indexedProducers) {
            int index = ref.recipeIndex();
            RecipeGraph.Node node = catalog.nodeAt(index);
            OutputContent producedOutput = ref.output(graph);
            if (!allowedRecipes.get(index) || producedOutput.amount() <= 0) {
                continue;
            }
            if (!heuristicAllowsOutputProducer(node, heuristicPolicy)) {
                continue;
            }
            candidates.add(producerCandidate(node, index, producedOutput.amount(), requestedAmount, stock));
        }
        return candidates;
    }

    private boolean heuristicAllowsOutputProducer(RecipeGraph.Node node, HeuristicPolicy policy) {
        if (policy == HeuristicPolicy.ignoreHints) {
            return true;
        }
        return switch (node.routingHints().usePolicy()) {
            case normal, discouraged -> true;
            case lastResort -> policy == HeuristicPolicy.allowLastResort || policy == HeuristicPolicy.includeAll;
            case disabledByDefault, targetOnly -> policy == HeuristicPolicy.includeAll;
        };
    }

    private ProducerCandidate producerCandidate(
        RecipeGraph.Node node,
        int index,
        long producedPerCraft,
        long requestedAmount,
        Stock stock
    ) {
        String stableId = catalog.stableRecipeId(index);
        int craftTimes = (int) Math.ceil((double) requestedAmount / producedPerCraft);
        return new ProducerCandidate(
            node,
            index,
            producedPerCraft,
            estimateUnitCost(node) + heuristicUnitPenalty(node.routingHints()),
            estimateDepth(node),
            1.0d - estimateStockCoverage(node, stock, craftTimes),
            stableId
        );
    }

    private double heuristicUnitPenalty(RecipeRoutingHints hints) {
        double penalty = hints.penalty();
        if (hints.usePolicy() == RecipeUsePolicy.discouraged) {
            penalty += dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultScoreModels.HINT_DISCOURAGED_WEIGHT;
        } else if (hints.usePolicy() == RecipeUsePolicy.lastResort) {
            penalty += dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultScoreModels.HINT_LAST_RESORT_WEIGHT;
        }
        return penalty;
    }

    private Comparator<ProducerCandidate> producerComparator(InventorySensitivityProfile profile) {
        return switch (profile) {
            case high -> Comparator
                .comparingDouble(ProducerCandidate::inventoryShortage)
                .thenComparingDouble(ProducerCandidate::estimatedUnitCost)
                .thenComparingInt(ProducerCandidate::depthEstimate)
                .thenComparing(ProducerCandidate::stableId);
            case balanced -> Comparator
                .comparingDouble(ProducerCandidate::estimatedUnitCost)
                .thenComparingDouble(ProducerCandidate::inventoryShortage)
                .thenComparingInt(ProducerCandidate::depthEstimate)
                .thenComparing(ProducerCandidate::stableId);
            case low -> Comparator
                .comparingDouble(ProducerCandidate::estimatedUnitCost)
                .thenComparingDouble(ProducerCandidate::inventoryShortage)
                .thenComparingInt(ProducerCandidate::depthEstimate)
                .thenComparing(ProducerCandidate::stableId);
        };
    }

    private double estimateUnitCost(RecipeGraph.Node node) {
        // Quick deterministic estimate used only for producer ordering in adapter mode.
        return node.inputCount() + (node.outputCount() * 0.1);
    }

    private double estimateStockCoverage(RecipeGraph.Node node, Stock stock, int craftTimes) {
        double totalRequired = 0.0d;
        double totalCovered = 0.0d;
        for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
            InputContent input = node.inputAt(inputIndex);
            long required = input.notConsumed() ? input.amount() : input.amount() * craftTimes;
            if (required <= 0L) {
                continue;
            }
            long available = Math.min(required, stock.countMatchingInput(input));
            totalRequired += required;
            totalCovered += available;
        }
        if (totalRequired <= 0.0d) {
            return 1.0d;
        }
        return Math.min(1.0d, totalCovered / totalRequired);
    }

    private int estimateDepth(RecipeGraph.Node node) {
        return node.inputCount();
    }

    /**
     * Selects the planner for one request. {@link PlannerMode#auto} inspects
     * the request cone: when the cone reachable from the target over allowed
     * recipes contains no resource loop, the exact dynamic-programming planner
     * runs; otherwise the bounded greedy planner handles the cyclic cone.
     */
    private RoutePlanner choosePlanner(RoutingRequest request, int targetIndex, BitSet allowedRecipes) {
        return switch (request.plannerMode()) {
            case standard -> standardPlanner;
            case assuredBounded, assuredProven -> assuredPlanner;
            case costAware -> costAwarePlanner;
            case auto -> coneHasCycle(targetIndex, allowedRecipes) ? standardPlanner : acyclicPlanner;
        };
    }

    private static final int maxCachedConeAnalyses = 32;

    private boolean coneHasCycle(int targetIndex, BitSet allowedRecipes) {
        return coneStats(targetIndex, allowedRecipes).hasNontrivialCycle();
    }

    /**
     * Cone shape statistics for budget sizing and planner dispatch, cached per
     * (target, allowedRecipes) pair. The pass is a single backward reachability
     * walk, cheap enough to run once per calculator request.
     */
    public ConeStats coneStats(int targetIndex, BitSet allowedRecipes) {
        ConeCacheKey key = new ConeCacheKey(targetIndex, allowedRecipes);
        synchronized (coneCacheLock) {
            ConeStats cached = coneCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        ConeStats result = topology().coneStats(targetIndex, allowedRecipes);
        synchronized (coneCacheLock) {
            coneCache.put(key, result);
            if (coneCache.size() > maxCachedConeAnalyses) {
                var iterator = coneCache.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
        return result;
    }

    /**
     * The resource-level topology of this service's graph, computed lazily on
     * first use and shared by cone dispatch ({@link PlannerMode#auto}) and
     * loop-aware candidate scoring.
     */
    public GraphTopology topology() {
        GraphTopology current = topology;
        if (current == null) {
            current = GraphTopology.of(graph);
            topology = current;
        }
        return current;
    }

    /**
     * Cone-analysis cache key. {@code allowedRecipes} compares by value, so
     * sub-requests of one route (which share the same decision bitset) hit the
     * same entry, and identical decisions across routes reuse it too.
     */
    private record ConeCacheKey(int targetIndex, BitSet allowedRecipes) {
        private ConeCacheKey {
            allowedRecipes = (BitSet) allowedRecipes.clone();
        }
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
            new RoutingIssuePayload.Empty(),
            null,
            null,
            Maps.immutable.empty()
        );
        RoutingMetrics metrics = new RoutingMetrics(
            0,
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
            "none",
            "none",
            -1,
            0,
            false,
            0,
            0,
            request.explorationSeed() == null ? 0 : request.explorationSeed(),
            request.loopPolicy(),
            0
        );
        return new RoutingOutcome(RoutingStatus.failed, null, Lists.immutable.of(issue), metrics, null);
    }

    private static void logRouteFinish(RoutingOutcome outcome, long routeStartNanos) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        RoutingMetrics metrics = outcome.metrics();
        long routeElapsedMs = (System.nanoTime() - routeStartNanos) / 1_000_000L;
        logger.debug(
            "routing.finish status={} planActions={} issues={} routeElapsedMs={} plannerElapsedMs={} analysisAndDispatchMs={} expanded={} generated={} accepted={} budgetUsed={} budgetLimit={} budgetExhausted={} budgetStop={} budgetCulprit={} budgetInput={} budgetDepth={} budgetRemaining={} budgetCycle={} adapterAttempts={} adapterStatus={}",
            outcome.status(),
            outcome.plan() == null ? 0 : outcome.plan().actions().size(),
            outcome.issues().size(),
            routeElapsedMs,
            metrics.elapsedMs(),
            Math.max(0L, routeElapsedMs - metrics.elapsedMs()),
            metrics.expandedNodes(),
            metrics.generatedCandidates(),
            metrics.acceptedCandidates(),
            metrics.budgetUsed(),
            metrics.budgetLimit(),
            metrics.budgetExhausted(),
            metrics.budgetStopReason(),
            metrics.budgetCulpritRecipe(),
            metrics.budgetCulpritInput(),
            metrics.budgetCulpritDepth(),
            metrics.budgetCulpritRemaining(),
            metrics.budgetCulpritActiveCycle(),
            metrics.adapterAttemptCount(),
            metrics.adapterBestIntermediateStatus()
        );
        if (!outcome.issues().isEmpty()) {
            int limit = Math.min(8, outcome.issues().size());
            for (int index = 0; index < limit; index++) {
                RoutingIssue issue = outcome.issues().get(index);
                logger.debug(
                    "routing.issue index={} code={} severity={} blocking={} message={} payload={}",
                    index,
                    issue.code(),
                    issue.severity(),
                    issue.blocking(),
                    issue.messageKey(),
                    issue.payload()
                );
            }
            if (outcome.issues().size() > limit) {
                logger.debug("routing.issue omitted={}", outcome.issues().size() - limit);
            }
        }
    }

    private static String describeTarget(RoutingRequest.Target target) {
        return switch (target) {
            case null -> "<null>";
            case RoutingRequest.RecipeTarget recipe -> "recipe:" + describeRecipe(recipe.recipe()) + "x" + recipe.times();
            case RoutingRequest.OutputTarget output -> "output:" + output.output() + "x" + output.amount();
        };
    }

    private static String describeRecipe(dev.vfyjxf.cirrus.api.recipe.RecipeEntry<?> recipe) {
        net.minecraft.resources.ResourceLocation id = recipe.id();
        return id == null ? recipe.systemId() + "#" + recipe.hashCode() : id.toString();
    }

    private record ProducerCandidate(
        RecipeGraph.Node node,
        int recipeIndex,
        long producedPerCraft,
        double estimatedUnitCost,
        int depthEstimate,
        double inventoryShortage,
        String stableId
    ) {
    }

    private record PolicyAnalysisCacheKey(
        long graphCacheId,
        int recipeCount,
        long tagSnapshotVersion,
        int tagSnapshotHash,
        int sourcePolicyHash
    ) {
    }

    private record PolicyAnalysis(
        SourcePolicyDecision sourceDecision,
        long lastAccessNanos
    ) {
        private PolicyAnalysis touch() {
            return new PolicyAnalysis(sourceDecision, System.nanoTime());
        }
    }
}
