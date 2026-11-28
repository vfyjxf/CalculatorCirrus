package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cirrus.planning.core.lang.WorkBudget;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ImprovementLoop {

    public record SearchOutcome(
        ChoiceTable bestChoices,
        ProductionPlan bestPlan,
        List<ProductionPlan> discoveredPlans,
        int coupledEvaluated
    ) {}

    private final BitmaskAndGreedySetCover setCoverSolver = new BitmaskAndGreedySetCover();
    private final BatchCouplingSolver batchSolver;

    public ImprovementLoop() {
        this(new BoundedBranchAndBoundBatchSolver());
    }

    public ImprovementLoop(BatchCouplingSolver batchSolver) {
        this.batchSolver = batchSolver;
    }

    public SearchOutcome refine(
        ConeModel cone,
        PlanningRequest request,
        ObjectivePreset preset,
        CostWeights weights,
        CostMetadataProvider meta,
        Potentials potentials,
        ChoiceTable initialChoices,
        ProductionPlan seedPlan,
        String strategyLabel,
        WorkBudget budget,
        long perspectiveWorkLimit
    ) {
        List<ProductionPlan> discovered = new ArrayList<>();
        discovered.add(seedPlan);
        int coupledEvaluated = 0;

        HumanGuidanceView guidance = request.context().guidance();
        ChoiceTable incumbent = initialChoices.copy();
        ProductionPlan incumbentPlan = seedPlan;
        int incAdv = advisoryWeight(cone, incumbentPlan);
        double incScalar = guidedScalar(incumbentPlan, weights, guidance);

        Set<Long> seenFingerprints = new HashSet<>();
        seenFingerprints.add(incumbent.fingerprint());
        long startWork = budget.workUnitsUsed();
        PlanningBudget pBudget = request.budget();

        // Move 1: Stage 2a Machine Set Cover (when unowned machines are present).
        if (pBudget.enableMachineSetCoverRefinement() && canContinue(budget, startWork, perspectiveWorkLimit)) {
            Set<MachineEntry> coverChoice = runMachineSetCover(cone, request, weights, meta, potentials, incumbentPlan);
            if (!coverChoice.isEmpty()) {
                coupledEvaluated++;
                ChoiceTable cand = incumbent.copy();
                applyMachineCover(cone, cand, incumbentPlan, coverChoice);
                if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                    ProductionPlan p = SchedulerAndFinalizer
                            .assembleWithRepair(cone, request, potentials, cand, meta, strategyLabel + "_setcover");
                    if (p != null) {
                        discovered.add(p);
                        int a = advisoryWeight(cone, p);
                        double s = guidedScalar(p, weights, guidance);
                        if (isStrictlyBetter(a, s, p.steps().size(), incAdv, incScalar, incumbentPlan.steps().size())) {
                            incumbent = cand;
                            incumbentPlan = p;
                            incAdv = a;
                            incScalar = s;
                        }
                    }
                }
            }
        }

        // Move 2: Stage 2b Full Net-Demand Mixed-Batch Refinement (A12: includes intermediates!).
        if (pBudget.enableBatchCouplingRefinement() && canContinue(budget, startWork, perspectiveWorkLimit)) {
            Map<Integer, Long> netDemands = extractProducedDemands(cone, incumbentPlan);
            for (var e : netDemands.entrySet()) {
                if (!canContinue(budget, startWork, perspectiveWorkLimit)) break;
                int u = e.getKey();
                long demand = e.getValue();
                if (demand <= 0L) continue;
                int ps = cone.prodStart[u], pe = cone.prodStart[u + 1];
                if (pe - ps < 2) continue;

                BatchCouplingComponent comp = buildBatchComponent(cone, request, weights, meta, potentials, u, demand);
                if (comp == null) continue;
                coupledEvaluated++;

                Optional<Map<CompiledRecipeKey, Long>> mixOpt = batchSolver
                        .refine(comp, request, budget.softDeadlineNanos());
                if (mixOpt.isEmpty() || mixOpt.get().size() < 2) continue;

                List<ChoiceTable.BatchSplit> splits = new ArrayList<>(mixOpt.get().size());
                for (var me : mixOpt.get().entrySet()) {
                    Optional<CompiledRecipe> recOpt = cone.graph.recipeByKey(me.getKey());
                    if (recOpt.isEmpty()) continue;
                    int lr = cone.localRecipe(recOpt.get().compactId());
                    if (lr >= 0) {
                        splits.add(new ChoiceTable.BatchSplit(lr, me.getValue()));
                    }
                }
                if (splits.size() < 2) continue;

                ChoiceTable cand = incumbent.copy();
                cand.batchMix.put(u, splits);
                if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                    ProductionPlan p = SchedulerAndFinalizer
                            .assembleWithRepair(cone, request, potentials, cand, meta, "mixed_batch_refined");
                    if (p != null) {
                        discovered.add(p);
                        int a = advisoryWeight(cone, p);
                        double s = guidedScalar(p, weights, guidance);
                        if (isStrictlyBetter(a, s, p.steps().size(), incAdv, incScalar, incumbentPlan.steps().size())) {
                            incumbent = cand;
                            incumbentPlan = p;
                            incAdv = a;
                            incScalar = s;
                        }
                    }
                }
            }
        }

        // Move 3: ConvertFromStock (check stocked reversible conversions for produced items).
        if (canContinue(budget, startWork, perspectiveWorkLimit)) {
            Map<Integer, Long> produced = extractProducedDemands(cone, incumbentPlan);
            for (int u : produced.keySet()) {
                if (!canContinue(budget, startWork, perspectiveWorkLimit)) break;
                for (int pi = cone.prodStart[u]; pi < cone.prodStart[u + 1]; pi++) {
                    int r = cone.prodRecipe[pi];
                    if (!cone.isConvertActive(r)) continue;
                    ChoiceTable cand = incumbent.copy();
                    cand.sourceByRes[u] = Potentials.SourceChoice.convert(r);
                    if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                        ProductionPlan p = SchedulerAndFinalizer
                                .assembleWithRepair(cone, request, potentials, cand, meta, strategyLabel);
                        if (p != null) {
                            discovered.add(p);
                            int a = advisoryWeight(cone, p);
                            double s = guidedScalar(p, weights, guidance);
                            if (isStrictlyBetter(
                                a,
                                s,
                                p.steps().size(),
                                incAdv,
                                incScalar,
                                incumbentPlan.steps().size()
                            )) {
                                incumbent = cand;
                                incumbentPlan = p;
                                incAdv = a;
                                incScalar = s;
                            }
                        }
                    }
                }
            }
        }

        // Move 4: ProducerSwap hill-climb (explores top alternative producers & co-production synergies).
        if (canContinue(budget, startWork, perspectiveWorkLimit)) {
            Map<Integer, Long> produced = extractProducedDemands(cone, incumbentPlan);
            List<Integer> resOrder = new ArrayList<>(produced.keySet());
            resOrder.sort((a, b) -> {
                int c = Integer.compare(cone.tier[b], cone.tier[a]);
                return c != 0 ? c : Integer.compare(a, b);
            });
            for (int u : resOrder) {
                if (!canContinue(budget, startWork, perspectiveWorkLimit)) break;
                Potentials.SourceChoice curSource = incumbent.sourceByRes[u];
                if (curSource != null
                        && curSource.kind() == Potentials.SourceKind.recipe
                        && curSource.localRecipe() >= 0
                        && cone.recPinned[curSource.localRecipe()]) {
                    continue;
                }
                int ps = cone.prodStart[u], pe = cone.prodStart[u + 1];
                int tried = 0;
                for (int pi = ps; pi < pe && tried < 6; pi++) {
                    if (!canContinue(budget, startWork, perspectiveWorkLimit)) break;
                    int r = cone.prodRecipe[pi];
                    if (!cone.isStandardActive(r)) continue;
                    Potentials.SourceChoice cur = incumbent.sourceByRes[u];
                    if (cur != null && cur.kind() == Potentials.SourceKind.recipe && cur.localRecipe() == r) {
                        continue;
                    }
                    tried++;
                    ChoiceTable cand = incumbent.copy();
                    cand.batchMix.remove(u);
                    cand.sourceByRes[u] = Potentials.SourceChoice.recipe(r, potentials.bestMachine[r]);
                    if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                        ProductionPlan p = SchedulerAndFinalizer
                                .assembleWithRepair(cone, request, potentials, cand, meta, strategyLabel);
                        if (p != null) {
                            discovered.add(p);
                            int a = advisoryWeight(cone, p);
                            double s = guidedScalar(p, weights, guidance);
                            if (isStrictlyBetter(
                                a,
                                s,
                                p.steps().size(),
                                incAdv,
                                incScalar,
                                incumbentPlan.steps().size()
                            )) {
                                incumbent = cand;
                                incumbentPlan = p;
                                incAdv = a;
                                incScalar = s;
                            }
                        }
                    }
                }
                // Also try direct acquire if acquirable (respecting forbidFieldTrips) and not currently acquiring.
                boolean canDirectAcquire = (cone.freeBase[u]
                        || (cone.acquireOption[u] != null
                                && !(cone.acquireOption[u].requiresFieldTrip()
                                        && request.profile().modifiers().forbidFieldTrips())));
                if (canDirectAcquire
                        && (incumbent.sourceByRes[u] == null
                                || incumbent.sourceByRes[u].kind() != Potentials.SourceKind.acquire)
                        && canContinue(budget, startWork, perspectiveWorkLimit)) {
                    ChoiceTable cand = incumbent.copy();
                    cand.batchMix.remove(u);
                    cand.sourceByRes[u] = Potentials.SourceChoice.acquire;
                    if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                        ProductionPlan p = SchedulerAndFinalizer
                                .assembleWithRepair(cone, request, potentials, cand, meta, strategyLabel);
                        if (p != null) {
                            discovered.add(p);
                            int a = advisoryWeight(cone, p);
                            double s = guidedScalar(p, weights, guidance);
                            if (isStrictlyBetter(
                                a,
                                s,
                                p.steps().size(),
                                incAdv,
                                incScalar,
                                incumbentPlan.steps().size()
                            )) {
                                incumbent = cand;
                                incumbentPlan = p;
                                incAdv = a;
                                incScalar = s;
                            }
                        }
                    }
                }
            }
        }

        // Move 5: A13 SetupLineStep automation branch under INVEST_AUTOMATION / MIN_HUMAN_TIME.
        if ((preset == ObjectivePreset.factoryScale || preset == ObjectivePreset.stayAtBase)
                && canContinue(budget, startWork, perspectiveWorkLimit)) {
            List<CraftStep> heavyCrafts = new ArrayList<>();
            for (PlanStep step : incumbentPlan.steps()) {
                if (step instanceof CraftStep cs && !cs.executedByAutomation()) {
                    Namespace sysId = cs.recipeKey().systemId();
                    Set<Namespace> sysTags = cone.graph.recipeByKey(cs.recipeKey())
                            .map(CompiledRecipe::systemTags)
                            .orElse(Set.of());
                    MachineCapabilityId cap = MachineCapabilityGroups.capabilityOf(sysId);
                    double setupH = meta.estimateLineSetupHumanSeconds(sysId, sysTags, cap);
                    if (cs.batches() >= 32L
                            || cs.stepCost().passiveWaitSeconds() >= 120.0
                            || cs.stepCost().totalActiveSeconds() > setupH * 0.25) {
                        heavyCrafts.add(cs);
                    }
                }
            }
            heavyCrafts
                    .sort(
                        Comparator.comparingDouble(
                            (CraftStep cs) -> cs.stepCost().passiveWaitSeconds()
                                    + cs.stepCost().totalActiveSeconds() * 4.0
                        ).reversed()
                    );
            int tries = Math.min(3, heavyCrafts.size());
            for (int i = 0; i < tries && canContinue(budget, startWork, perspectiveWorkLimit); i++) {
                CraftStep cs = heavyCrafts.get(i);
                Optional<CompiledRecipe> recOpt = cone.graph.recipeByKey(cs.recipeKey());
                if (recOpt.isEmpty()) continue;
                int lr = cone.localRecipe(recOpt.get().compactId());
                if (lr < 0 || cone.recCompatMachines[lr].isEmpty()) continue;
                ChoiceTable cand = incumbent.copy();
                cand.automatedRecipes.add(lr);
                if (seenFingerprints.add(cand.fingerprint()) && budget.consumeWork()) {
                    ProductionPlan p = SchedulerAndFinalizer
                            .assembleWithRepair(cone, request, potentials, cand, meta, "invest_automation_line");
                    if (p != null) {
                        discovered.add(p);
                        int a = advisoryWeight(cone, p);
                        double s = guidedScalar(p, weights, guidance);
                        if (isStrictlyBetter(a, s, p.steps().size(), incAdv, incScalar, incumbentPlan.steps().size())) {
                            incumbent = cand;
                            incumbentPlan = p;
                            incAdv = a;
                            incScalar = s;
                        }
                    }
                }
            }
        }

        return new SearchOutcome(incumbent, incumbentPlan, discovered, coupledEvaluated);
    }

    private Set<MachineEntry> runMachineSetCover(
        ConeModel cone,
        PlanningRequest request,
        CostWeights weights,
        CostMetadataProvider meta,
        Potentials potentials,
        ProductionPlan plan
    ) {
        FacilitySnapshot fac = request.context().facilities();
        HumanGuidanceView guidance = request.context().guidance();
        Set<MachineEntry> ownedMachines = new HashSet<>();
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : fac.ownedMachines()) {
            if (g.count() > 0) {
                ownedMachines.add(g.machine());
                ownedCaps.add(g.capability());
            }
        }

        List<CompiledRecipe> unownedSteps = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (!(step instanceof CraftStep cs)) continue;
            Optional<CompiledRecipe> recOpt = cone.graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            if (rec.machineRequirement().handCraftable()) continue;
            boolean owned = rec.machineRequirement().capabilityId().map(ownedCaps::contains).orElse(false);
            if (!owned) {
                for (MachineEntry m : rec.machineRequirement().compatibleMachines()) {
                    if (ownedMachines.contains(m) || fac.ownsMachineFor(m)) {
                        owned = true;
                        break;
                    }
                }
            }
            if (!owned && !rec.machineRequirement().compatibleMachines().isEmpty()) {
                unownedSteps.add(rec);
            }
        }
        if (unownedSteps.size() < 2) {
            return Set.of();
        }

        int k = unownedSteps.size();
        Map<MachineEntry, BitSet> coverByMachine = new TreeMap<>(MachineEntry.comparator);
        Map<MachineEntry, CompiledRecipe> sampleRecByMachine = new HashMap<>();
        for (int i = 0; i < k; i++) {
            CompiledRecipe rec = unownedSteps.get(i);
            for (MachineEntry m : rec.machineRequirement().compatibleMachines()) {
                if (!guidance.isWillingToBuild(m)) continue;
                coverByMachine.computeIfAbsent(m, ignored -> new BitSet(k)).set(i);
                sampleRecByMachine.putIfAbsent(m, rec);
            }
        }

        List<BitmaskAndGreedySetCover.CandidateMachine> cands = new ArrayList<>(coverByMachine.size());
        for (var e : coverByMachine.entrySet()) {
            MachineEntry m = e.getKey();
            CompiledRecipe sampleRec = Objects.requireNonNull(sampleRecByMachine.get(m));
            double setup = weights.scalarize(
                StepCosts.provisionMachineCost(m, sampleRec.key().systemId(), sampleRec.systemTags(), 1, meta)
            );
            int mLocal = m.contentKey().map(cone::localRes).orElse(-1);
            double itemPot = (mLocal >= 0 && Double.isFinite(potentials.potInCost[mLocal]))
                    ? potentials.potInCost[mLocal]
                    : setup * 2.0 + 50.0;
            cands.add(new BitmaskAndGreedySetCover.CandidateMachine(m, e.getValue(), setup + itemPot));
        }
        return setCoverSolver.solve(k, cands);
    }

    private static void applyMachineCover(
        ConeModel cone,
        ChoiceTable choices,
        ProductionPlan plan,
        Set<MachineEntry> chosen
    ) {
        for (PlanStep step : plan.steps()) {
            if (!(step instanceof CraftStep cs)) continue;
            Optional<CompiledRecipe> recOpt = cone.graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            int lr = cone.localRecipe(rec.compactId());
            if (lr < 0) continue;
            for (MachineEntry m : rec.machineRequirement().compatibleMachines()) {
                if (chosen.contains(m)) {
                    choices.machineOverride.put(lr, m);
                    break;
                }
            }
        }
    }

    private static Map<Integer, Long> extractProducedDemands(ConeModel cone, ProductionPlan plan) {
        Map<Integer, Long> demands = new LinkedHashMap<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                Optional<CompiledRecipe> recOpt = cone.graph.recipeByKey(cs.recipeKey());
                if (recOpt.isEmpty()) continue;
                for (OutputSlot out : recOpt.get().outputs()) {
                    int lu = cone.localRes(out.resource());
                    if (lu >= 0) {
                        demands.merge(
                            lu,
                            SafeMath.mulSaturating(cs.batches(), out.amountPerBatch()),
                            SafeMath::addSaturating
                        );
                    }
                }
            }
        }
        // Subtract any declared byproducts so net demand equals exact needed amount (e.g., 5 stone_bricks, not 8!).
        for (var bp : plan.surplusByproducts()) {
            int lu = cone.localRes(bp.resource());
            if (lu >= 0 && demands.containsKey(lu)) {
                long net = demands.get(lu) - bp.amount();
                if (net <= 0L) demands.remove(lu);
                else demands.put(lu, net);
            }
        }
        return demands;
    }

    private static @Nullable BatchCouplingComponent buildBatchComponent(
        ConeModel cone,
        PlanningRequest request,
        CostWeights weights,
        CostMetadataProvider meta,
        Potentials potentials,
        int u,
        long demand
    ) {
        FacilitySnapshot fac = request.context().facilities();
        HumanGuidanceView guidance = request.context().guidance();
        int ps = cone.prodStart[u], pe = cone.prodStart[u + 1];
        List<BatchCouplingComponent.CandidateRecipe> cands = new ArrayList<>();
        boolean hasBatchGt1OrMulti = false;
        double bestSingleCost = Double.POSITIVE_INFINITY;

        for (int pi = ps; pi < pe && cands.size() < 16; pi++) {
            int r = cone.prodRecipe[pi];
            if (!cone.isStandardActive(r)) continue;
            CompiledRecipe rec = cone.recObjects[r];
            if (rec.advisory() != RecipeAdvisory.none && !cone.recPinned[r]) {
                continue;
            }
            long outPerBatch = 0L;
            for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                if (cone.outRes[oi] == u) {
                    outPerBatch = cone.outAmount[oi];
                    break;
                }
            }
            if (outPerBatch <= 0L) continue;
            double inputPot = 0.0;
            boolean ok = true;
            for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                int rep = potentials.bestSlotRep[pos];
                if (rep < 0 || !Double.isFinite(potentials.potInCost[rep])) {
                    ok = false;
                    break;
                }
                inputPot += cone.inAmount[pos] * potentials.potInCost[rep];
            }
            if (!ok) continue;
            Optional<MachineEntry> mach = Optional.ofNullable(potentials.bestMachine[r]);
            boolean auto = fac.automatedRecipe(rec.key()).isPresent();
            double runCost = weights.scalarize(StepCosts.craftCost(rec, 1L, mach, auto, fac, meta));
            double softMult = guidance.softCostMultiplier(rec.key(), mach);
            double perBatchCost = (inputPot + runCost) * softMult;
            if (outPerBatch > 1L || rec.isMultiOutput()) {
                hasBatchGt1OrMulti = true;
            }
            long singleBatches = SafeMath.ceilDiv(demand, outPerBatch);
            bestSingleCost = Math.min(bestSingleCost, singleBatches * perBatchCost);
            cands.add(
                new BatchCouplingComponent.CandidateRecipe(rec.key(), outPerBatch, perBatchCost, rec.isMultiOutput())
            );
        }

        if (cands.size() < 2 || !hasBatchGt1OrMulti || !Double.isFinite(bestSingleCost)) {
            return null;
        }
        return new BatchCouplingComponent(cone.resKeys[u], demand, cands, bestSingleCost);
    }

    public static double guidedScalar(ProductionPlan plan, CostWeights weights, HumanGuidanceView guidance) {
        double base = weights.scalarize(plan.totalCost());
        if (guidance == null || guidance.allRules().isEmpty()) {
            return base;
        }
        double adj = 0.0;
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                if (guidance.isRecipePinned(cs.recipeKey())) {
                    adj -= Math.max(1000.0, weights.scalarize(cs.stepCost()) * 10.0);
                }
                double m = guidance.softCostMultiplier(cs.recipeKey(), cs.chosenMachine());
                if (m != 1.0) {
                    adj += Math.max(5.0, weights.scalarize(cs.stepCost())) * (m - 1.0);
                }
            } else if (step instanceof AcquireStep ac) {
                double m = guidance.resourceSoftMultiplier(ac.resource());
                if (m != 1.0) {
                    adj += Math.max(2.0, weights.scalarize(ac.stepCost())) * (m - 1.0);
                }
            } else if (step instanceof CycleLoopStep cl) {
                for (CraftStep inner : cl.singleIterationTemplate()) {
                    double m = guidance.softCostMultiplier(inner.recipeKey(), inner.chosenMachine());
                    if (m != 1.0) {
                        adj += Math.max(5.0, weights.scalarize(inner.stepCost())) * cl.iterations() * (m - 1.0);
                    }
                }
            }
        }
        return Math.max(0.0, base + adj);
    }

    private static int advisoryWeight(ConeModel cone, ProductionPlan plan) {
        int sum = 0;
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                Optional<CompiledRecipe> r = cone.graph.recipeByKey(cs.recipeKey());
                if (r.isPresent()) sum += r.get().advisory().weight();
            }
        }
        return sum;
    }

    private static boolean isStrictlyBetter(int advA, double sA, int stepsA, int advB, double sB, int stepsB) {
        if (advA != advB) return advA < advB;
        if (Math.abs(sA - sB) > 1e-9) return sA < sB;
        return stepsA < stepsB;
    }

    private static boolean canContinue(WorkBudget budget, long startWork, long limit) {
        return (budget.workUnitsUsed() - startWork) < limit
                && !budget.isSoftTimeExceeded()
                && !budget.isHardTimeExceeded();
    }
}
