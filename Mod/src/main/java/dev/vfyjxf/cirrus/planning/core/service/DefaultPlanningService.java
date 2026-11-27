package dev.vfyjxf.cirrus.planning.core.service;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.MachineReadiness;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;
import dev.vfyjxf.cirrus.api.planning.cost.StageDistribution;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.human.PromptSuppressionLedger;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.RegimeSignature;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningService;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.core.cone.ConeExtractor;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.cost.MachineReadinessClassifier;
import dev.vfyjxf.cirrus.planning.core.cost.StageAndCapitalModel;
import dev.vfyjxf.cirrus.planning.core.human.ConflictMinimalCutDiagnoser;
import dev.vfyjxf.cirrus.planning.core.human.DecisionPointDetector;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.lang.WorkBudget;
import dev.vfyjxf.cirrus.planning.core.select.ConditionedFrontierSelector;
import dev.vfyjxf.cirrus.planning.core.select.DiverseParetoSelector;
import dev.vfyjxf.cirrus.planning.core.select.TechBranchClusterer;
import dev.vfyjxf.cirrus.planning.core.solve.ChoiceTable;
import dev.vfyjxf.cirrus.planning.core.solve.ImprovementLoop;
import dev.vfyjxf.cirrus.planning.core.solve.Netting;
import dev.vfyjxf.cirrus.planning.core.solve.PassABaselinePotentials;
import dev.vfyjxf.cirrus.planning.core.solve.Potentials;
import dev.vfyjxf.cirrus.planning.core.solve.ProductionVector;
import dev.vfyjxf.cirrus.planning.core.solve.SchedulerAndFinalizer;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DefaultPlanningService implements PlanningService {

    private static final int coneCacheCapacity = 16;

    private static final ObjectivePreset[] portfolioPresets = {
        ObjectivePreset.autoSmart,
        ObjectivePreset.stayAtBase,
        ObjectivePreset.quickHandcraft,
        ObjectivePreset.factoryScale,
        ObjectivePreset.frugalMaterials,
        ObjectivePreset.lowCognitive
    };

    private final CompiledRecipeGraph graph;
    private final CostMetadataProvider costMetadata;
    private final HumanGuidanceStore guidanceStore;
    private final @Nullable Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener;
    private final ConeExtractor coneExtractor;
    private final ImprovementLoop improvementLoop;
    private final DefaultPlanVerifier verifier;
    private final DiverseParetoSelector paretoSelector;
    private final DecisionPointDetector promptDetector;
    private final ConflictMinimalCutDiagnoser conflictDiagnoser;
    private final TechBranchClusterer.ClusterAssignment techClusters;
    private final PromptSuppressionLedger suppressionLedger = new PromptSuppressionLedger();
    private final AtomicLong queryCounter = new AtomicLong(0L);

    private final LinkedHashMap<ConeCacheKey, ConeModel> coneCache = new LinkedHashMap<>(
        coneCacheCapacity + 2,
        0.75f,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ConeCacheKey, ConeModel> eldest) {
            return size() > coneCacheCapacity;
        }
    };

    public DefaultPlanningService(CompiledRecipeGraph graph) {
        this(graph, DefaultCostMetadataProvider.instance, new LayeredHumanGuidanceStore(), null);
    }

    public DefaultPlanningService(CompiledRecipeGraph graph, CostMetadataProvider costMetadata) {
        this(graph, costMetadata, new LayeredHumanGuidanceStore(), null);
    }

    public DefaultPlanningService(
        CompiledRecipeGraph graph,
        CostMetadataProvider costMetadata,
        HumanGuidanceStore guidanceStore,
        @Nullable Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener
    ) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.costMetadata = Objects.requireNonNull(costMetadata, "costMetadata");
        this.guidanceStore = guidanceStore == null ? new LayeredHumanGuidanceStore() : guidanceStore;
        this.machinePersistenceListener = machinePersistenceListener;
        this.coneExtractor = new ConeExtractor(graph);
        this.improvementLoop = new ImprovementLoop();
        this.verifier = new DefaultPlanVerifier(graph, costMetadata);
        this.paretoSelector = new DiverseParetoSelector(verifier, graph);
        this.promptDetector = new DecisionPointDetector(costMetadata);
        this.conflictDiagnoser = new ConflictMinimalCutDiagnoser(graph, coneExtractor, costMetadata);
        this.techClusters = TechBranchClusterer.cluster(graph);
    }

    public CompiledRecipeGraph graph() {
        return graph;
    }

    public CostMetadataProvider costMetadata() {
        return costMetadata;
    }

    public HumanGuidanceStore guidanceStore() {
        return guidanceStore;
    }

    public PromptSuppressionLedger suppressionLedger() {
        return suppressionLedger;
    }

    @Override
    public PlanningResult planOnce(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        return planOnceInternal(request, Set.of());
    }

    @Override
    public PlanningSession openSession(PlanningRequest initialRequest) {
        Objects.requireNonNull(initialRequest, "initialRequest");
        return new PlanningSessionImpl(this, initialRequest, guidanceStore, machinePersistenceListener);
    }

    PlanningResult planOnceInternal(PlanningRequest request, Set<String> dismissedPromptIds) {
        long startNanos = System.nanoTime();
        long querySeq = queryCounter.incrementAndGet();
        PlanningBudget pBudget = request.budget();
        WorkBudget workBudget = new WorkBudget(
            startNanos,
            pBudget.softTimeLimit().toNanos(),
            pBudget.hardTimeLimit().toNanos(),
            pBudget.searchWorkBudget()
        );

        // Compute M5 stage distribution and effective 6D weights with orthogonal modifiers.
        long totalOrderQ = 0L;
        for (TargetDemand td : request.targets()) {
            totalOrderQ = Math.addExact(totalOrderQ, td.amount());
        }
        totalOrderQ = Math.max(1L, totalOrderQ);

        double facCov = Math.min(1.0, request.context().facilities().ownedMachines().size() / 12.0);
        double netCov = request.context().inventory().hasNetworkStorage() ? 1.0 : 0.0;
        StageDistribution stage = StageAndCapitalModel.inferStage(
            facCov,
            netCov,
            totalOrderQ,
            request.profile().modifiers()
        );
        CostWeights primaryEffectiveWeights = request.profile().weights()
                .withEffectiveModifiers(request.profile().preset(), stage, request.profile().modifiers());

        // Stage 0: Cached query-cone extraction.
        ConeModel cone = getOrExtractCone(request, workBudget);

        List<ProductionPlan> rawCandidates = new ArrayList<>();
        int coupledEvaluated = 0;
        Map<ResourceKey, String> unmetReasons = new TreeMap<>();
        boolean primarySeedFound = false;
        boolean refinedFully = true;

        boolean targetsReachable = areAllTargetsReachable(cone, request);
        if (targetsReachable) {
            ObjectivePreset primaryPreset = request.profile().preset();
            String primaryLabel = labelForPreset(primaryPreset);

            Potentials primaryPot = Potentials.solve(cone, request, primaryEffectiveWeights, costMetadata, Set.of(), Set.of());
            ChoiceTable primaryChoices = new ChoiceTable(primaryPot);
            ProductionPlan primarySeed = SchedulerAndFinalizer
                    .assembleWithRepair(cone, request, primaryPot, primaryChoices, costMetadata, primaryLabel);

            if (primarySeed != null) {
                primarySeedFound = true;
                rawCandidates.add(primarySeed);
                long primaryWorkShare = Math.max(1L, pBudget.searchWorkBudget() * 2L / 5L);
                if (!workBudget.isSoftTimeExceeded() && pBudget.searchWorkBudget() > 0L) {
                    ImprovementLoop.SearchOutcome out = improvementLoop.refine(
                        cone,
                        request,
                        primaryPreset,
                        primaryEffectiveWeights,
                        costMetadata,
                        primaryPot,
                        primaryChoices,
                        primarySeed,
                        primaryLabel,
                        workBudget,
                        primaryWorkShare
                    );
                    rawCandidates.addAll(out.discoveredPlans());
                    coupledEvaluated += out.coupledEvaluated();
                } else {
                    refinedFully = false;
                }
            } else {
                ProductionVector diagVec = Netting.settle(cone, request, primaryPot, primaryChoices, Map.of());
                unmetReasons.putAll(diagVec.unmetReasons);
            }

            // Multi-regime portfolio channels (including stayAtBase, quickHandcraft, factoryScale, etc.).
            long perPerspectiveWork = Math.max(1L, pBudget.searchWorkBudget() / 6L);
            for (ObjectivePreset preset : portfolioPresets) {
                if (preset == primaryPreset) continue;
                if (primarySeedFound && (workBudget.isSoftTimeExceeded() || workBudget.workCapHit())) {
                    refinedFully = false;
                    break;
                }
                if (workBudget.isHardTimeExceeded()) {
                    refinedFully = false;
                    break;
                }
                CostWeights w = CostWeights.forPreset(preset)
                        .withEffectiveModifiers(preset, stage, request.profile().modifiers());
                String label = labelForPreset(preset);
                Potentials pot = Potentials.solve(cone, request, w, costMetadata, Set.of(), Set.of());
                ChoiceTable choices = new ChoiceTable(pot);
                ProductionPlan seed = SchedulerAndFinalizer
                        .assembleWithRepair(cone, request, pot, choices, costMetadata, label);
                if (seed != null) {
                    primarySeedFound = true;
                    rawCandidates.add(seed);
                    if (!workBudget.isSoftTimeExceeded()
                            && !workBudget.workCapHit()
                            && pBudget.searchWorkBudget() > 0L) {
                        ImprovementLoop.SearchOutcome out = improvementLoop.refine(
                            cone,
                            request,
                            preset,
                            w,
                            costMetadata,
                            pot,
                            choices,
                            seed,
                            label,
                            workBudget,
                            perPerspectiveWork
                        );
                        rawCandidates.addAll(out.discoveredPlans());
                        coupledEvaluated += out.coupledEvaluated();
                    } else {
                        refinedFully = false;
                    }
                }
            }

            // Tech-branch alternative route probe (M6 Channel 1/2): mask primary plan's exclusive tech cluster.
            if (pBudget.maxReturnedPlans() > 1
                    && !rawCandidates.isEmpty()
                    && !workBudget.isSoftTimeExceeded()
                    && !workBudget.workCapHit()) {
                probeAlternativeRoute(cone, request, primaryEffectiveWeights, rawCandidates, workBudget);
            }
        } else {
            collectUnreachableReasons(cone, request, unmetReasons);
        }

        // Compute query scale A & annotate verified candidates with M1 ScoreBreakdown + M6 RegimeSignature.
        double baselineScaleA = StageAndCapitalModel.queryScaleA(
            rawCandidates.isEmpty() ? 10.0 : primaryEffectiveWeights.scalarize(rawCandidates.get(0).totalCost())
        );

        Set<String> allCandidateMachines = new TreeSet<>();
        for (ProductionPlan p : rawCandidates) {
            for (PlanStep s : p.steps()) {
                if (s instanceof ProvisionMachineStep pm) {
                    allCandidateMachines.add(pm.machineId());
                }
            }
        }
        Map<String, MachineReadiness.Classification> machineClasses = allCandidateMachines.isEmpty()
                ? Map.of()
                : MachineReadinessClassifier.classifyAll(
                    graph,
                    request.context().facilities(),
                    Map.of(),
                    baselineScaleA,
                    allCandidateMachines
                );

        List<ProductionPlan> feasibleAnnotated = new ArrayList<>();
        List<ProductionPlan> conditionalDrafts = new ArrayList<>();

        for (ProductionPlan raw : rawCandidates) {
            var cert = verifier.verify(raw, request);
            if (!cert.valid()) continue;

            double amortCap = StageAndCapitalModel.amortizedCapital(
                raw.totalCost().machineSetupCapital(),
                totalOrderQ,
                stage,
                0L
            );
            double trustRaw = computeTrustRiskSeconds(raw);
            ScoreBreakdown breakdown = ScoreBreakdown.compute(
                raw.totalCost(),
                primaryEffectiveWeights,
                amortCap,
                baselineScaleA,
                0.0,
                0.0,
                trustRaw,
                0.0,
                0.0
            );
            RegimeSignature regime = buildRegimeSignature(raw, machineClasses);
            List<String> missingEvidence = collectMissingEvidence(raw);
            ProductionPlan stamped = new ProductionPlan(
                raw.planId(),
                raw.strategyLabelKey(),
                raw.steps(),
                raw.preparation(),
                raw.surplusByproducts(),
                raw.totalCost(),
                cert,
                breakdown,
                regime,
                !missingEvidence.isEmpty(),
                missingEvidence
            );
            if (missingEvidence.isEmpty()) {
                feasibleAnnotated.add(stamped);
            } else {
                conditionalDrafts.add(stamped);
            }
        }

        // Strict M8 eligibility rule: certified feasible plans are selected independently from conditional drafts.
        List<ProductionPlan> selectedFeasible = feasibleAnnotated.isEmpty()
                ? List.of()
                : selectWithConditionedFrontier(feasibleAnnotated, baselineScaleA, request);

        List<ProductionPlan> selectedConditional = conditionalDrafts.isEmpty()
                ? List.of()
                : selectWithConditionedFrontier(conditionalDrafts, baselineScaleA, request);

        if (selectedFeasible.isEmpty() && selectedConditional.isEmpty()) {
            if (unmetReasons.isEmpty()) {
                collectUnreachableReasons(cone, request, unmetReasons);
            }
            Optional<ConflictDiagnosis> conflict = conflictDiagnoser.diagnose(request, cone, unmetReasons, workBudget);
            PlanningStatus status;
            if (conflict.isPresent()) {
                status = PlanningStatus.infeasibleByHumanRules;
            } else if (cone.truncatedByBudget || workBudget.anyTimeHit() || workBudget.workCapHit()) {
                status = PlanningStatus.noPlanWithinBudget;
            } else {
                status = PlanningStatus.provenInfeasible;
            }
            long elapsedMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            PlanningDiagnostics diag = new PlanningDiagnostics(
                elapsedMs,
                cone.nodeCount,
                cone.recipeCount,
                cone.collapsedOrCandidates,
                coupledEvaluated,
                cone.truncatedByBudget,
                conflict,
                unmetReasons
            );
            return new PlanningResult(status, List.of(), List.of(), List.of(), diag);
        }

        List<ProductionPlan> promptBasis = !selectedFeasible.isEmpty() ? selectedFeasible : selectedConditional;
        List<DecisionPrompt> prompts = promptDetector.detect(
            request,
            promptBasis,
            cone.toQuerySubgraph(),
            graph,
            dismissedPromptIds,
            suppressionLedger,
            querySeq,
            machineClasses
        );

        PlanningStatus status = !selectedFeasible.isEmpty()
                ? PlanningStatus.feasible
                : PlanningStatus.conditional;

        long elapsedMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        PlanningDiagnostics diag = new PlanningDiagnostics(
            elapsedMs,
            cone.nodeCount,
            cone.recipeCount,
            cone.collapsedOrCandidates,
            coupledEvaluated,
            cone.truncatedByBudget || !refinedFully,
            Optional.empty(),
            Map.of()
        );
        return new PlanningResult(status, selectedFeasible, selectedConditional, prompts, diag);
    }

    private List<ProductionPlan> selectWithConditionedFrontier(
        List<ProductionPlan> pool,
        double scaleA,
        PlanningRequest request
    ) {
        double[] refScales = {scaleA, scaleA, scaleA, scaleA, scaleA, 2.0};
        var sel = ConditionedFrontierSelector.select(
            pool,
            refScales,
            ConditionedFrontierSelector.defaultBoxEpsilon,
            request.budget().maxReturnedPlans()
        );
        return sel.selectedTopK().isEmpty() ? paretoSelector.select(pool, request) : sel.selectedTopK();
    }

    private double computeTrustRiskSeconds(ProductionPlan plan) {
        double risk = 0.0;
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                Optional<CompiledRecipe> rec = graph.recipeByKey(cs.recipeKey());
                if (rec.isPresent()) {
                    double deficit = Math.max(0.0, 1.0 - rec.get().structuralFidelity());
                    if (deficit > 0.0) {
                        risk += deficit * 15.0 * Math.min(10L, cs.batches());
                    }
                }
            }
        }
        return risk;
    }

    private List<String> collectMissingEvidence(ProductionPlan plan) {
        List<String> missing = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                Optional<CompiledRecipe> rec = graph.recipeByKey(cs.recipeKey());
                if (rec.isPresent() && rec.get().isSuspiciousJei()) {
                    missing.add("VERIFY_RECIPE:" + cs.recipeKey().shortString());
                }
            }
        }
        return missing;
    }

    private RegimeSignature buildRegimeSignature(
        ProductionPlan plan,
        Map<String, MachineReadiness.Classification> machineClasses
    ) {
        Set<String> heavyMach = new LinkedHashSet<>();
        Set<String> clusters = new LinkedHashSet<>();
        Set<String> premises = new LinkedHashSet<>();
        boolean requiresTrip = plan.totalCost().tripActiveSeconds() > 1e-6;

        for (PlanStep step : plan.steps()) {
            if (step instanceof ProvisionMachineStep pm) {
                MachineReadiness.Classification cls = machineClasses.get(pm.machineId());
                if (cls == null || cls.readiness() == MachineReadiness.heavyInvestment) {
                    heavyMach.add(pm.machineId());
                    premises.add("build_machine:" + pm.machineId());
                }
            } else if (step instanceof CraftStep cs) {
                Optional<CompiledRecipe> rec = graph.recipeByKey(cs.recipeKey());
                if (rec.isPresent()) {
                    String clus = techClusters.clusterOf(rec.get().compactId());
                    if (!TechBranchClusterer.sharedCommonCluster.equals(clus)) {
                        clusters.add(clus);
                    }
                }
            }
        }
        return new RegimeSignature(heavyMach, clusters, requiresTrip, premises);
    }

    private void probeAlternativeRoute(
        ConeModel cone,
        PlanningRequest request,
        CostWeights effectiveWeights,
        List<ProductionPlan> rawCandidates,
        WorkBudget workBudget
    ) {
        ProductionPlan anchor = rawCandidates.get(0);
        int probed = 0;
        for (int i = anchor.steps().size() - 1; i >= 0 && probed < 2; i--) {
            if (workBudget.isSoftTimeExceeded() || !workBudget.consumeWork()) {
                return;
            }
            PlanStep step = anchor.steps().get(i);
            if (!(step instanceof CraftStep cs)) continue;
            Optional<CompiledRecipe> optRec = graph.recipeByKey(cs.recipeKey());
            if (optRec.isEmpty()) continue;
            int compactId = optRec.get().compactId();
            int localR = cone.localRecipe(compactId);
            if (localR < 0) continue;

            boolean hasAlt = false;
            for (int oi = cone.outStart[localR]; oi < cone.outStart[localR + 1]; oi++) {
                int outU = cone.outRes[oi];
                if (cone.prodStart[outU + 1] - cone.prodStart[outU] >= 2) {
                    hasAlt = true;
                    break;
                }
            }
            if (!hasAlt) continue;
            probed++;
            ConeModel altCone = coneExtractor.extract(request, Set.of(compactId), workBudget);
            if (!areAllTargetsReachable(altCone, request)) continue;
            Potentials pot = Potentials.solve(altCone, request, effectiveWeights, costMetadata, Set.of(), Set.of());
            ChoiceTable choices = new ChoiceTable(pot);
            ProductionPlan altPlan = SchedulerAndFinalizer
                    .assembleWithRepair(altCone, request, pot, choices, costMetadata, "cirrus.strategy.alternative_branch");
            if (altPlan != null) {
                rawCandidates.add(altPlan);
            }
        }
    }

    private ConeModel getOrExtractCone(PlanningRequest request, WorkBudget workBudget) {
        if (!(request.context().acquisitionOracle() instanceof DefaultAcquisitionOracle dao)) {
            return coneExtractor.extract(request, Set.of(), workBudget);
        }
        ConeCacheKey key = new ConeCacheKey(
            graph.catalogVersion(),
            request.targets(),
            request.context().inventory().allUsable(),
            request.context().facilities(),
            request.context().guidance().allRules(),
            request.context().progression(),
            request.budget().maxSubgraphNodes(),
            request.budget().maxSubgraphRecipes(),
            request.budget().maxProducersPerResource(),
            dao
        );
        synchronized (coneCache) {
            ConeModel cached = coneCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        ConeModel extracted = coneExtractor.extract(request, Set.of(), workBudget);
        if (!extracted.truncatedByBudget) {
            synchronized (coneCache) {
                coneCache.put(key, extracted);
            }
        }
        return extracted;
    }

    private static boolean areAllTargetsReachable(ConeModel cone, PlanningRequest request) {
        for (TargetDemand td : request.targets()) {
            int u = cone.localRes(td.resource());
            if (u < 0 || cone.tier[u] >= ConeModel.infTier) {
                return false;
            }
        }
        return true;
    }

    private static void collectUnreachableReasons(
        ConeModel cone,
        PlanningRequest request,
        Map<ResourceKey, String> unmetReasons
    ) {
        for (TargetDemand td : request.targets()) {
            int u = cone.localRes(td.resource());
            if (u < 0) {
                unmetReasons.putIfAbsent(
                    td.resource(),
                    cone.truncatedByBudget ? Netting.reasonBudgetTruncated : Netting.reasonNoSource
                );
            } else if (cone.tier[u] >= ConeModel.infTier) {
                if (cone.bannedRes[u]) {
                    unmetReasons.putIfAbsent(td.resource(), Netting.reasonResourceBanned);
                } else if (cone.blockingRuleIds.containsKey(td.resource())) {
                    unmetReasons.putIfAbsent(td.resource(), Netting.reasonHumanRule);
                } else {
                    unmetReasons.putIfAbsent(
                        td.resource(),
                        cone.truncatedByBudget ? Netting.reasonBudgetTruncated : Netting.reasonNoSource
                    );
                }
            }
        }
    }

    private static String labelForPreset(ObjectivePreset preset) {
        return switch (preset) {
            case autoSmart -> "cirrus.strategy.auto_smart";
            case stayAtBase -> "cirrus.strategy.stay_at_base";
            case quickHandcraft -> "cirrus.strategy.quick_handcraft";
            case factoryScale -> "cirrus.strategy.factory_scale";
            case frugalMaterials -> "cirrus.strategy.frugal_materials";
            case lowCognitive -> "cirrus.strategy.low_cognitive";
        };
    }

    private record ConeCacheKey(
        long graphVersion,
        List<TargetDemand> targets,
        Map<ResourceKey, Long> usableStock,
        FacilitySnapshot facilities,
        List<GuidanceRule> rules,
        ProgressionState progression,
        int maxSubgraphNodes,
        int maxSubgraphRecipes,
        int maxProducersPerResource,
        DefaultAcquisitionOracle oracle
    ) {}
}
