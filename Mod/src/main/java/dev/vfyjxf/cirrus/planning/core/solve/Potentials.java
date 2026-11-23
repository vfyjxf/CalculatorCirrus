package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Potentials {

    public enum SourceKind {
        NONE,
        ACQUIRE,
        LINE,
        RECIPE,
        CONVERT,
        MACRO
    }

    public record SourceChoice(
            SourceKind kind,
            int localRecipe,
            CompiledCycleMacro macro,
            String chosenMachine
    ) {
        public static final SourceChoice NONE = new SourceChoice(SourceKind.NONE, -1, null, null);
        public static final SourceChoice ACQUIRE = new SourceChoice(SourceKind.ACQUIRE, -1, null, null);
        public static final SourceChoice LINE = new SourceChoice(SourceKind.LINE, -1, null, null);

        public static SourceChoice recipe(int localR, String machine) {
            return new SourceChoice(SourceKind.RECIPE, localR, null, machine);
        }

        public static SourceChoice convert(int localR) {
            return new SourceChoice(SourceKind.CONVERT, localR, null, null);
        }

        public static SourceChoice macro(CompiledCycleMacro m) {
            return new SourceChoice(SourceKind.MACRO, -1, m, null);
        }
    }

    public static final int INF_ADV = Integer.MAX_VALUE / 4;

    public final int[] potInAdv;
    public final double[] potInCost;
    public final int[] prodAdv;
    public final double[] prodCost;
    public final SourceChoice[] prodChoice;
    public final int[] bestSlotRep;
    public final String[] bestMachine;

    private Potentials(
            int[] potInAdv,
            double[] potInCost,
            int[] prodAdv,
            double[] prodCost,
            SourceChoice[] prodChoice,
            int[] bestSlotRep,
            String[] bestMachine
    ) {
        this.potInAdv = potInAdv;
        this.potInCost = potInCost;
        this.prodAdv = prodAdv;
        this.prodCost = prodCost;
        this.prodChoice = prodChoice;
        this.bestSlotRep = bestSlotRep;
        this.bestMachine = bestMachine;
    }

    public static Potentials solve(
            ConeModel cone,
            PlanningRequest request,
            CostWeights weights,
            CostMetadataProvider meta,
            Set<String> forcedOwnedMachines,
            Set<String> disallowedNewMachines
    ) {
        int n = cone.nodeCount;
        int rCount = cone.recipeCount;
        FacilitySnapshot fac = request.context().facilities();
        HumanGuidanceView guidance = request.context().guidance();

        long demandScale = 0L;
        for (TargetDemand td : request.targets()) {
            demandScale += td.amount();
        }
        demandScale = Math.max(1L, demandScale);

        Set<String> ownedMachines = new HashSet<>(forcedOwnedMachines == null ? Set.of() : forcedOwnedMachines);
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : fac.ownedMachines()) {
            if (g.count() > 0) {
                ownedMachines.add(g.machineId());
                ownedCaps.add(g.capability());
            }
        }
        Set<String> disallowed = disallowedNewMachines == null ? Set.of() : disallowedNewMachines;

        int[] potInAdv = new int[n];
        double[] potInCost = new double[n];
        int[] prodAdv = new int[n];
        double[] prodCost = new double[n];
        SourceChoice[] prodChoice = new SourceChoice[n];
        Arrays.fill(potInAdv, INF_ADV);
        Arrays.fill(potInCost, Double.POSITIVE_INFINITY);
        Arrays.fill(prodAdv, INF_ADV);
        Arrays.fill(prodCost, Double.POSITIVE_INFINITY);
        Arrays.fill(prodChoice, SourceChoice.NONE);

        // 1. Direct sources (lines, free base, acquire) + finite inventory guard for potIn.
        for (int u = 0; u < n; u++) {
            if (cone.bannedRes[u]) continue;
            double resSoft = guidance.resourceSoftMultiplier(cone.resKeys[u]);

            AutomatedLine line = cone.bestLine[u];
            if (line != null) {
                double c = weights.scalarize(StepCosts.linePullCost(line, demandScale)) / demandScale;
                if (isBetter(0, c, prodAdv[u], prodCost[u])) {
                    prodAdv[u] = 0;
                    prodCost[u] = c;
                    prodChoice[u] = SourceChoice.LINE;
                }
            }

            AcquisitionOption opt = cone.acquireOption[u];
            if (opt != null) {
                CostVector unitEst = StepCosts.acquireUnitEstimate(opt, demandScale, cone.freeBase[u]);
                double c = weights.scalarize(unitEst) * resSoft;
                if (isBetter(0, c, prodAdv[u], prodCost[u])) {
                    prodAdv[u] = 0;
                    prodCost[u] = c;
                    prodChoice[u] = SourceChoice.ACQUIRE;
                }
            }

            // Finite seed guard (spec §5.6-1): inventory gives low potIn only when usable >= demandScale.
            if (cone.usableStock[u] >= demandScale) {
                potInAdv[u] = 0;
                potInCost[u] = 1e-4;
            } else {
                potInAdv[u] = prodAdv[u];
                potInCost[u] = prodCost[u];
            }
        }

        // 2. Pre-sort active recipes once by (max slot min tier, compactId).
        int[] activeOrder = new int[rCount];
        int activeLen = 0;
        int[] recipeMaxTier = new int[rCount];
        for (int r = 0; r < rCount; r++) {
            if (!cone.isAnyActive(r)) continue;
            int mt = 0;
            for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                int minT = ConeModel.INF_TIER;
                for (int cid = cone.inClassStart[pos]; cid < cone.inClassStart[pos + 1]; cid++) {
                    int t = cone.tier[cone.classRep[cid]];
                    if (t < minT) minT = t;
                }
                if (minT > mt) mt = minT;
            }
            recipeMaxTier[r] = mt;
            activeOrder[activeLen++] = r;
        }
        sortByKey(activeOrder, activeLen, recipeMaxTier, cone.recCompactId);

        int totalInSlots = cone.inStart[rCount];
        int[] bestSlotRep = new int[totalInSlots];
        Arrays.fill(bestSlotRep, -1);
        String[] bestMachine = new String[rCount];

        // 3. Relax 3 passes in tier order.
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;

            for (int idx = 0; idx < activeLen; idx++) {
                int r = activeOrder[idx];
                CompiledRecipe rec = cone.recObjects[r];
                boolean pinned = cone.recPinned[r];

                // Select best machine if needed.
                String chosenM = selectBestMachine(
                        cone, r, rec, weights, meta, guidance, ownedMachines, ownedCaps, disallowed,
                        potInAdv, potInCost, demandScale);
                if (!cone.recHandCraftable[r] && chosenM == null
                        && (cone.recCapability[r] == null || !ownedCaps.contains(cone.recCapability[r]))
                        && !cone.isConvertActive(r)) {
                    continue;
                }
                bestMachine[r] = chosenM;

                // Select best equivalence-class representative per input slot.
                int slotAdvSum = pinned ? 0 : cone.recAdvisory[r].weight();
                double slotCostSum = 0.0;
                boolean feasible = true;
                boolean convertStockReady = cone.isConvertActive(r);

                for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                    int cs = cone.inClassStart[pos];
                    int ce = cone.inClassStart[pos + 1];
                    if (cs == ce) {
                        feasible = false;
                        break;
                    }
                    String tagId = cone.inTagId[pos];
                    ResourceKey prefMember = tagId != null ? guidance.preferredTagMember(tagId).orElse(null) : null;
                    double tagMult = tagId != null ? guidance.tagSoftMultiplier(tagId) : 1.0;

                    int pickRep = -1;
                    int pickAdv = INF_ADV;
                    double pickCost = Double.POSITIVE_INFINITY;

                    for (int cid = cs; cid < ce; cid++) {
                        int rep = cone.classRep[cid];
                        int a = potInAdv[rep];
                        double c = potInCost[rep];
                        if (!Double.isFinite(c)) continue;
                        if (prefMember != null && cone.resKeys[rep].equals(prefMember)) {
                            c *= tagMult;
                        }
                        if (isBetter(a, c, pickAdv, pickCost)) {
                            pickAdv = a;
                            pickCost = c;
                            pickRep = rep;
                        }
                    }
                    if (pickRep < 0) {
                        feasible = false;
                        break;
                    }
                    bestSlotRep[pos] = pickRep;
                    slotAdvSum = Math.min(INF_ADV - 1, slotAdvSum + pickAdv);
                    slotCostSum += cone.inAmount[pos] * pickCost;
                    if (cone.usableStock[pickRep] <= 0L && cone.bestLine[pickRep] == null) {
                        convertStockReady = false;
                    }
                }
                if (!feasible) continue;

                // Catalyst availability check.
                for (int cpos = cone.catStart[r]; cpos < cone.catEnd[r]; cpos++) {
                    int cs = cone.catCandStart[cpos];
                    int ce = cone.catCandStart[cpos + 1];
                    double minCat = Double.POSITIVE_INFINITY;
                    for (int i = cs; i < ce; i++) {
                        int cu = cone.catCandRes[i];
                        double c = cone.usableStock[cu] > 0L ? 1e-4 : potInCost[cu];
                        if (c < minCat) minCat = c;
                    }
                    if (!Double.isFinite(minCat)) {
                        feasible = false;
                        break;
                    }
                    slotCostSum += minCat / demandScale;
                }
                if (!feasible) continue;

                // Reversible convert from stock takes precedence when stocked.
                if (convertStockReady && cone.isConvertActive(r)) {
                    double convBatchCost = slotCostSum + weights.scalarize(StepCosts.CONVERT_COST);
                    for (int oi = cone.outStart[r]; oi < cone.outEnd[r]; oi++) {
                        int outU = cone.outRes[oi];
                        double unit = convBatchCost / cone.outAmount[oi];
                        if (isBetter(0, unit, prodAdv[outU], prodCost[outU])) {
                            prodAdv[outU] = 0;
                            prodCost[outU] = unit;
                            prodChoice[outU] = SourceChoice.convert(r);
                            if (cone.usableStock[outU] < demandScale && isBetter(0, unit, potInAdv[outU], potInCost[outU])) {
                                potInAdv[outU] = 0;
                                potInCost[outU] = unit;
                            }
                            changed = true;
                        }
                    }
                }

                if (!cone.isStandardActive(r)) {
                    continue;
                }

                boolean autoAvail = fac.automatedRecipe(rec.key()).isPresent();
                CostVector runVec = StepCosts.craftCost(rec, 1L, Optional.ofNullable(chosenM),
                        autoAvail, fac, meta, guidance);
                double execCost = weights.scalarize(runVec);

                double setupAmortized = 0.0;
                if (!cone.recHandCraftable[r] && !isMachineAlreadyOwned(cone, r, chosenM, ownedMachines, ownedCaps)) {
                    if (chosenM == null) continue;
                    double setupVecCost = weights.scalarize(
                            StepCosts.provisionMachineCost(chosenM, rec.key().systemId(), 1, meta));
                    int mLocal = cone.localRes(ResourceKey.item(chosenM));
                    double mItemCost = (mLocal >= 0 && Double.isFinite(potInCost[mLocal])) ? potInCost[mLocal] : 0.0;
                    setupAmortized = (setupVecCost + mItemCost) / demandScale;
                }

                double pinFactor = pinned ? 0.01 : 1.0;
                double totalBatchCost = (slotCostSum + execCost + setupAmortized) * pinFactor;

                for (int oi = cone.outStart[r]; oi < cone.outEnd[r]; oi++) {
                    int outU = cone.outRes[oi];
                    double unit = totalBatchCost / cone.outAmount[oi];
                    if (isBetter(slotAdvSum, unit, prodAdv[outU], prodCost[outU])) {
                        prodAdv[outU] = slotAdvSum;
                        prodCost[outU] = unit;
                        prodChoice[outU] = SourceChoice.recipe(r, chosenM);
                        if (cone.usableStock[outU] < demandScale
                                && isBetter(slotAdvSum, unit, potInAdv[outU], potInCost[outU])) {
                            potInAdv[outU] = slotAdvSum;
                            potInCost[outU] = unit;
                        }
                        changed = true;
                    }
                }
            }

            // Relax active cycle macros.
            for (CompiledCycleMacro macro : cone.activeMacros) {
                double reagentCost = 0.0;
                double seedCost = 0.0;
                int macroAdv = 0;
                boolean ok = true;

                for (var e : macro.workingSeeds().entrySet()) {
                    int lu = cone.localRes(e.getKey());
                    if (lu < 0) { ok = false; break; }
                    if (cone.usableStock[lu] < e.getValue()) {
                        if (!Double.isFinite(potInCost[lu])) { ok = false; break; }
                        seedCost += (e.getValue() - cone.usableStock[lu]) * potInCost[lu];
                        macroAdv = Math.min(INF_ADV - 1, macroAdv + potInAdv[lu]);
                    }
                }
                if (!ok) continue;

                for (var e : macro.netDeltaPerIter().entrySet()) {
                    if (e.getValue() < 0L) {
                        int lu = cone.localRes(e.getKey());
                        if (lu < 0 || !Double.isFinite(potInCost[lu])) { ok = false; break; }
                        reagentCost += (-e.getValue()) * potInCost[lu];
                        macroAdv = Math.min(INF_ADV - 1, macroAdv + potInAdv[lu]);
                    }
                }
                if (!ok) continue;

                double runIterCost = 0.0;
                for (int i = 0; i < macro.innerRecipes().size(); i++) {
                    CompiledRecipe inner = macro.innerRecipes().get(i);
                    long mult = macro.innerMultipliers().get(i);
                    Optional<String> mOpt = inner.machineRequirement().compatibleMachineIds().isEmpty()
                            ? Optional.empty()
                            : Optional.of(inner.machineRequirement().compatibleMachineIds().get(0));
                    runIterCost += weights.scalarize(StepCosts.craftCost(inner, mult, mOpt, false, fac, meta, guidance));
                    if (!inner.machineRequirement().handCraftable()) {
                        boolean owned = inner.machineRequirement().capabilityId().map(ownedCaps::contains).orElse(false);
                        if (!owned) {
                            for (String mid : inner.machineRequirement().compatibleMachineIds()) {
                                if (ownedMachines.contains(mid)) { owned = true; break; }
                            }
                        }
                        if (!owned && mOpt.isPresent()) {
                            if (!guidance.isWillingToBuild(mOpt.get()) || disallowed.contains(mOpt.get())) {
                                ok = false;
                                break;
                            }
                            runIterCost += weights.scalarize(
                                    StepCosts.provisionMachineCost(mOpt.get(), inner.key().systemId(), 1, meta))
                                    / demandScale;
                        }
                    }
                }
                if (!ok) continue;

                double iterTotal = reagentCost + runIterCost + seedCost / demandScale;
                for (var e : macro.netDeltaPerIter().entrySet()) {
                    if (e.getValue() <= 0L) continue;
                    int outU = cone.localRes(e.getKey());
                    if (outU < 0) continue;
                    double unit = iterTotal / e.getValue();
                    if (isBetter(macroAdv, unit, prodAdv[outU], prodCost[outU])) {
                        prodAdv[outU] = macroAdv;
                        prodCost[outU] = unit;
                        prodChoice[outU] = SourceChoice.macro(macro);
                        if (cone.usableStock[outU] < demandScale
                                && isBetter(macroAdv, unit, potInAdv[outU], potInCost[outU])) {
                            potInAdv[outU] = macroAdv;
                            potInCost[outU] = unit;
                        }
                        changed = true;
                    }
                }
            }

            if (!changed) break;
        }

        return new Potentials(potInAdv, potInCost, prodAdv, prodCost, prodChoice, bestSlotRep, bestMachine);
    }

    private static String selectBestMachine(
            ConeModel cone,
            int r,
            CompiledRecipe rec,
            CostWeights weights,
            CostMetadataProvider meta,
            HumanGuidanceView guidance,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps,
            Set<String> disallowed,
            int[] potInAdv,
            double[] potInCost,
            long demandScale
    ) {
        List<String> compat = cone.recCompatMachines[r];
        if (cone.recHandCraftable[r]) {
            for (String m : compat) {
                if (ownedMachines.contains(m)) return m;
            }
            return null;
        }
        // Prefer an already-owned compatible machine.
        for (String m : compat) {
            if (ownedMachines.contains(m)) {
                return m;
            }
        }
        if (cone.recCapability[r] != null && ownedCaps.contains(cone.recCapability[r])) {
            return compat.isEmpty() ? null : compat.get(0);
        }
        // Choose cheapest willing-to-build compatible machine (setup + item potential).
        String best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        String sysId = rec.key().systemId();
        for (String m : compat) {
            if (!guidance.isWillingToBuild(m) || disallowed.contains(m)) {
                continue;
            }
            double setup = weights.scalarize(StepCosts.provisionMachineCost(m, sysId, 1, meta));
            int mLocal = cone.localRes(ResourceKey.item(m));
            double itemPot = (mLocal >= 0 && Double.isFinite(potInCost[mLocal])) ? potInCost[mLocal] : 0.0;
            double soft = guidance.softCostMultiplier(rec.key(), Optional.of(m));
            double score = (setup + itemPot) * soft;
            if (score + 1e-12 < bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    private static boolean isMachineAlreadyOwned(
            ConeModel cone,
            int r,
            String chosenM,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps
    ) {
        if (chosenM != null && ownedMachines.contains(chosenM)) return true;
        return cone.recCapability[r] != null && ownedCaps.contains(cone.recCapability[r]);
    }

    public static boolean isBetter(int advA, double costA, int advB, double costB) {
        if (!Double.isFinite(costA)) return false;
        if (!Double.isFinite(costB)) return true;
        if (advA != advB) return advA < advB;
        return costA + 1e-9 < costB;
    }

    private static void sortByKey(int[] ids, int len, int[] primary, int[] secondary) {
        Integer[] boxed = new Integer[len];
        for (int i = 0; i < len; i++) boxed[i] = ids[i];
        Arrays.sort(boxed, (a, b) -> {
            int c = Integer.compare(primary[a], primary[b]);
            return c != 0 ? c : Integer.compare(secondary[a], secondary[b]);
        });
        for (int i = 0; i < len; i++) ids[i] = boxed[i];
    }
}
