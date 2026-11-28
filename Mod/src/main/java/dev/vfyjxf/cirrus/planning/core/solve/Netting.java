package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public final class Netting {

    public static final String reasonNoSource = "NO_SOURCE";
    public static final String reasonHumanRule = "HUMAN_RULE";
    public static final String reasonResourceBanned = "RESOURCE_BANNED";
    public static final String reasonMachineUnbuildable = "MACHINE_UNBUILDABLE";
    public static final String reasonBudgetTruncated = "BUDGET_TRUNCATED";
    public static final String reasonExplosion = "EXPLOSION";

    private static final long maxTotalBatches = 1_000_000L;
    private static final int maxHeapRounds = 100_000;

    private Netting() {}

    public static ProductionVector settle(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        Map<Integer, Long> extraPresenceNeed
    ) {
        int n = cone.nodeCount;
        int rCount = cone.recipeCount;
        int totalIn = cone.inStart[rCount];
        int totalCat = cone.catStart[rCount];

        ProductionVector vec = new ProductionVector(n, rCount, totalIn, totalCat);
        Arrays.fill(vec.chosenSlotBind, -1);
        Arrays.fill(vec.chosenCatBind, -1);

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
        Set<MachineEntry> availableMachines = new HashSet<>(ownedMachines);
        Set<MachineCapabilityId> availableCaps = new HashSet<>(ownedCaps);

        for (int u = 0; u < n; u++) {
            vec.net[u] = cone.usableStock[u];
            vec.gross[u] = cone.usableStock[u];
        }

        // Seed targets and optional repair presence needs.
        for (TargetDemand td : request.targets()) {
            int u = cone.localRes(td.resource());
            if (u < 0) {
                vec.unmetReasons.put(td.resource(), cone.truncatedByBudget ? reasonBudgetTruncated : reasonNoSource);
                continue;
            }
            vec.need[u] = SafeMath.addSaturating(vec.need[u], td.amount());
        }
        if (extraPresenceNeed != null) {
            for (var e : extraPresenceNeed.entrySet()) {
                int u = e.getKey();
                if (u >= 0 && u < n && e.getValue() > 0L) {
                    vec.need[u] = SafeMath.addSaturating(vec.need[u], e.getValue());
                }
            }
        }

        // Track single-input conversion pairs to make round-trips structurally impossible.
        Set<Long> activeConversionPairs = new HashSet<>();
        Map<Integer, Set<String>> forbiddenMacros = new HashMap<>();
        Map<Integer, Set<Integer>> forbiddenRecipes = new HashMap<>();
        long[] totalBatchesUsed = new long[1];

        // Descending tier priority queue: resolve higher-tier demands first so co-products net against lower tiers.
        PriorityQueue<Integer> heap = new PriorityQueue<>((a, b) -> {
            int c = Integer.compare(cone.tier[b], cone.tier[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        boolean[] inHeap = new boolean[n];

        for (int u = 0; u < n; u++) {
            if (vec.need[u] > vec.net[u]) {
                heap.add(u);
                inHeap[u] = true;
            }
        }

        int rounds = 0;
        while (!heap.isEmpty()) {
            if (++rounds > maxHeapRounds || totalBatchesUsed[0] > maxTotalBatches) {
                vec.markExploded();
                break;
            }
            int u = heap.poll();
            inHeap[u] = false;
            long deficit = vec.need[u] - vec.net[u];
            if (deficit <= 0L) {
                continue;
            }
            if (cone.bannedRes[u]) {
                vec.unmetReasons.put(cone.resKeys[u], reasonResourceBanned);
                collectBlocking(cone, u, vec);
                continue;
            }

            // 1. Check explicit Stage 2b mixed-batch override for u.
            List<ChoiceTable.BatchSplit> mix = choices.batchMix.get(u);
            if (mix != null && !mix.isEmpty()) {
                long remaining = deficit;
                for (ChoiceTable.BatchSplit split : mix) {
                    if (split.batches() <= 0L) continue;
                    int r = split.localRecipe();
                    if (applyStandardRecipe(
                        cone,
                        request,
                        potentials,
                        choices,
                        vec,
                        u,
                        r,
                        remaining,
                        split.batches(),
                        availableMachines,
                        availableCaps,
                        ownedMachines,
                        ownedCaps,
                        activeConversionPairs,
                        forbiddenMacros,
                        forbiddenRecipes,
                        totalBatchesUsed,
                        heap,
                        inHeap
                    )) {
                        remaining = vec.need[u] - vec.net[u];
                    }
                }
                if (remaining <= 0L) {
                    continue;
                }
                deficit = remaining;
            }

            // 2. Try primary choice, then deterministic fallback chain.
            boolean resolved = tryResolve(
                cone,
                request,
                potentials,
                choices,
                vec,
                u,
                deficit,
                availableMachines,
                availableCaps,
                ownedMachines,
                ownedCaps,
                activeConversionPairs,
                forbiddenMacros,
                forbiddenRecipes,
                totalBatchesUsed,
                heap,
                inHeap,
                false
            );

            if (!resolved && vec.need[u] > vec.net[u]) {
                String reason = classifyUnmetReason(cone, u, guidance);
                vec.unmetReasons.put(cone.resKeys[u], reason);
                collectBlocking(cone, u, vec);
            }
        }

        if (!vec.exploded() && vec.unmetReasons.isEmpty()) {
            pruneRedundantBatches(cone, vec);
        }
        return vec;
    }

    private static boolean tryResolve(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        ProductionVector vec,
        int u,
        long deficit,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        Set<MachineEntry> ownedMachines,
        Set<MachineCapabilityId> ownedCaps,
        Set<Long> activeConvPairs,
        Map<Integer, Set<String>> forbiddenMacros,
        Map<Integer, Set<Integer>> forbiddenRecipes,
        long[] totalBatchesUsed,
        PriorityQueue<Integer> heap,
        boolean[] inHeap,
        boolean nonMacroOnly
    ) {
        // Build ordered candidate sources: primary choice first, then backups.
        Potentials.SourceChoice primary = choices.sourceByRes[u];
        if (primary != null && primary.kind() != Potentials.SourceKind.none) {
            if (applyChoice(
                cone,
                request,
                potentials,
                choices,
                vec,
                u,
                deficit,
                primary,
                availableMachines,
                availableCaps,
                ownedMachines,
                ownedCaps,
                activeConvPairs,
                forbiddenMacros,
                forbiddenRecipes,
                totalBatchesUsed,
                heap,
                inHeap,
                nonMacroOnly
            )) {
                if (vec.need[u] <= vec.net[u]) {
                    return true;
                }
                deficit = vec.need[u] - vec.net[u];
            }
        }

        // Fallback 1: automated line.
        if (cone.bestLine[u] != null) {
            vec.line[u] = SafeMath.addSaturating(vec.line[u], deficit);
            vec.net[u] = SafeMath.addSaturating(vec.net[u], deficit);
            vec.gross[u] = SafeMath.addSaturating(vec.gross[u], deficit);
            return true;
        }

        // Fallback 2: active producers of u in rank order (excluding already-tried primary).
        for (int pi = cone.prodStart[u]; pi < cone.prodStart[u + 1]; pi++) {
            int r = cone.prodRecipe[pi];
            if (primary != null && primary.localRecipe() == r) continue;
            if (cone.isConvertActive(r)) {
                if (applyConvert(cone, vec, u, r, deficit, activeConvPairs, totalBatchesUsed, heap, inHeap)) {
                    if (vec.need[u] <= vec.net[u]) return true;
                    deficit = vec.need[u] - vec.net[u];
                }
            }
            if (cone.isStandardActive(r)) {
                if (applyStandardRecipe(
                    cone,
                    request,
                    potentials,
                    choices,
                    vec,
                    u,
                    r,
                    deficit,
                    Long.MAX_VALUE,
                    availableMachines,
                    availableCaps,
                    ownedMachines,
                    ownedCaps,
                    activeConvPairs,
                    forbiddenMacros,
                    forbiddenRecipes,
                    totalBatchesUsed,
                    heap,
                    inHeap
                )) {
                    if (vec.need[u] <= vec.net[u]) return true;
                    deficit = vec.need[u] - vec.net[u];
                }
            }
        }

        // Fallback 3: active cycle macros (unless bootstrapping a seed).
        if (!nonMacroOnly) {
            List<CompiledCycleMacro> macros = cone.macrosByLocalOut.get(u);
            if (macros != null) {
                for (CompiledCycleMacro m : macros) {
                    if (primary != null && primary.macro() != null && primary.macro().macroKey().equals(m.macroKey())) {
                        continue;
                    }
                    if (applyMacro(
                        cone,
                        request,
                        potentials,
                        choices,
                        vec,
                        u,
                        m,
                        availableMachines,
                        availableCaps,
                        ownedMachines,
                        ownedCaps,
                        activeConvPairs,
                        forbiddenMacros,
                        forbiddenRecipes,
                        totalBatchesUsed,
                        heap,
                        inHeap
                    )) {
                        if (vec.need[u] <= vec.net[u]) return true;
                        deficit = vec.need[u] - vec.net[u];
                    }
                }
            }
        }

        // Fallback 4: external acquisition (respecting forbidFieldTrips & capacityLimit).
        if (tryAcquireExternal(cone, request, vec, u, deficit)) {
            if (vec.need[u] <= vec.net[u]) return true;
        }

        return vec.need[u] <= vec.net[u];
    }

    private static boolean tryAcquireExternal(
        ConeModel cone,
        PlanningRequest request,
        ProductionVector vec,
        int u,
        long deficit
    ) {
        if (deficit <= 0L) return true;
        AcquisitionOption opt = cone.acquireOption[u];
        if (opt == null && !cone.freeBase[u]) return false;
        if (opt != null && opt.requiresFieldTrip() && request.profile().modifiers().forbidFieldTrips()) {
            return false;
        }
        long allowed = deficit;
        if (opt != null && opt.guaranteedQuantity() < Long.MAX_VALUE) {
            long remCap = Math.max(0L, opt.guaranteedQuantity() - vec.acq[u]);
            if (remCap <= 0L) {
                return false;
            }
            allowed = Math.min(deficit, remCap);
        }
        vec.acq[u] = SafeMath.addSaturating(vec.acq[u], allowed);
        vec.net[u] = SafeMath.addSaturating(vec.net[u], allowed);
        vec.gross[u] = SafeMath.addSaturating(vec.gross[u], allowed);
        return true;
    }

    private static boolean applyChoice(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        ProductionVector vec,
        int u,
        long deficit,
        Potentials.SourceChoice sc,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        Set<MachineEntry> ownedMachines,
        Set<MachineCapabilityId> ownedCaps,
        Set<Long> activeConvPairs,
        Map<Integer, Set<String>> forbiddenMacros,
        Map<Integer, Set<Integer>> forbiddenRecipes,
        long[] totalBatchesUsed,
        PriorityQueue<Integer> heap,
        boolean[] inHeap,
        boolean nonMacroOnly
    ) {
        return switch (sc.kind()) {
            case line -> {
                if (cone.bestLine[u] == null) yield false;
                vec.line[u] = SafeMath.addSaturating(vec.line[u], deficit);
                vec.net[u] = SafeMath.addSaturating(vec.net[u], deficit);
                vec.gross[u] = SafeMath.addSaturating(vec.gross[u], deficit);
                yield true;
            }
            case acquire -> tryAcquireExternal(cone, request, vec, u, deficit);
            case convert ->
                applyConvert(cone, vec, u, sc.localRecipe(), deficit, activeConvPairs, totalBatchesUsed, heap, inHeap);
            case recipe -> applyStandardRecipe(
                cone,
                request,
                potentials,
                choices,
                vec,
                u,
                sc.localRecipe(),
                deficit,
                Long.MAX_VALUE,
                availableMachines,
                availableCaps,
                ownedMachines,
                ownedCaps,
                activeConvPairs,
                forbiddenMacros,
                forbiddenRecipes,
                totalBatchesUsed,
                heap,
                inHeap
            );
            case macro -> !nonMacroOnly
                    && applyMacro(
                        cone,
                        request,
                        potentials,
                        choices,
                        vec,
                        u,
                        Objects.requireNonNull(sc.macro()),
                        availableMachines,
                        availableCaps,
                        ownedMachines,
                        ownedCaps,
                        activeConvPairs,
                        forbiddenMacros,
                        forbiddenRecipes,
                        totalBatchesUsed,
                        heap,
                        inHeap
                    );
            case none -> false;
        };
    }

    private static boolean applyConvert(
        ConeModel cone,
        ProductionVector vec,
        int outU,
        int r,
        long deficit,
        Set<Long> activeConvPairs,
        long[] totalBatchesUsed,
        PriorityQueue<Integer> heap,
        boolean[] inHeap
    ) {
        if (r < 0 || !cone.isConvertActive(r)) return false;
        int inS = cone.inStart[r];
        int inE = cone.inStart[r + 1];
        if (inE - inS != 1) return false;
        int cs = cone.inClassStart[inS];
        int ce = cone.inClassStart[inS + 1];
        if (cs == ce) return false;

        // Pick a stocked or lined input candidate.
        int chosenIn = -1;
        for (int cid = cs; cid < ce; cid++) {
            int rep = cone.classRep[cid];
            if (vec.net[rep] >= cone.inAmount[inS] || cone.bestLine[rep] != null) {
                chosenIn = rep;
                break;
            }
        }
        if (chosenIn < 0 || chosenIn == outU) return false;
        if (wouldCreateRoundTrip(activeConvPairs, chosenIn, outU)) return false;

        long outPerBatch = outputAmountOf(cone, r, outU);
        long inPerBatch = cone.inAmount[inS];
        if (outPerBatch <= 0L || inPerBatch <= 0L) return false;

        long wantedBatches = SafeMath.ceilDiv(deficit, outPerBatch);
        long batchesToRun;
        if (cone.bestLine[chosenIn] != null) {
            batchesToRun = wantedBatches;
        } else {
            long maxFromStock = Math.max(0L, vec.net[chosenIn]) / inPerBatch;
            if (maxFromStock <= 0L) return false;
            batchesToRun = Math.min(wantedBatches, maxFromStock);
        }

        totalBatchesUsed[0] = SafeMath.addSaturating(totalBatchesUsed[0], batchesToRun);
        if (totalBatchesUsed[0] > maxTotalBatches) {
            vec.markExploded();
            return false;
        }
        vec.convertBatches[r] = SafeMath.addSaturating(vec.convertBatches[r], batchesToRun);
        vec.chosenSlotBind[inS] = chosenIn;
        activeConvPairs.add(pairKey(chosenIn, outU));

        long consume = SafeMath.mulSaturating(batchesToRun, inPerBatch);
        vec.net[chosenIn] = SafeMath.addSaturating(vec.net[chosenIn], -consume);
        pushIfDeficit(vec, chosenIn, heap, inHeap);
        for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
            int ov = cone.outRes[oi];
            long prod = SafeMath.mulSaturating(batchesToRun, cone.outAmount[oi]);
            vec.net[ov] = SafeMath.addSaturating(vec.net[ov], prod);
            vec.gross[ov] = SafeMath.addSaturating(vec.gross[ov], prod);
        }
        return true;
    }

    private static boolean applyStandardRecipe(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        ProductionVector vec,
        int targetOutU,
        int r,
        long deficit,
        long maxBatches,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        Set<MachineEntry> ownedMachines,
        Set<MachineCapabilityId> ownedCaps,
        Set<Long> activeConvPairs,
        Map<Integer, Set<String>> forbiddenMacros,
        Map<Integer, Set<Integer>> forbiddenRecipes,
        long[] totalBatchesUsed,
        PriorityQueue<Integer> heap,
        boolean[] inHeap
    ) {
        if (r < 0 || deficit <= 0L || maxBatches <= 0L || !cone.isStandardActive(r)) return false;
        long outPerBatch = outputAmountOf(cone, r, targetOutU);
        if (outPerBatch <= 0L) return false;
        for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
            Set<Integer> forb = forbiddenRecipes.get(cone.outRes[oi]);
            if (forb != null && forb.contains(r)) return false;
        }

        // Resolve input slot bindings.
        int inS = cone.inStart[r], inE = cone.inStart[r + 1];
        int[] binds = new int[inE - inS];
        for (int pos = inS; pos < inE; pos++) {
            int chosen = vec.chosenSlotBind[pos];
            if (chosen < 0) {
                chosen = choices.slotRepOverride[pos];
            }
            if (chosen < 0 || cone.bannedRes[chosen]) {
                int cs = cone.inClassStart[pos];
                int ce = cone.inClassStart[pos + 1];
                chosen = cs < ce ? cone.classRep[cs] : -1;
            }
            if (chosen < 0 || cone.bannedRes[chosen]) return false;
            binds[pos - inS] = chosen;
        }

        // Prevent round-trip single-input conversions.
        if (inE - inS == 1 && cone.outStart[r + 1] - cone.outStart[r] == 1) {
            int inU = binds[0];
            int outU = cone.outRes[cone.outStart[r]];
            if (inU != outU && wouldCreateRoundTrip(activeConvPairs, inU, outU)) {
                return false;
            }
        }

        // Resolve catalyst bindings.
        int catS = cone.catStart[r], catE = cone.catStart[r + 1];
        int[] catBinds = new int[catE - catS];
        for (int cpos = catS; cpos < catE; cpos++) {
            int chosen = vec.chosenCatBind[cpos];
            if (chosen < 0) {
                int cs = cone.catCandStart[cpos], ce = cone.catCandStart[cpos + 1];
                int bestCat = -1;
                for (int i = cs; i < ce; i++) {
                    int cu = cone.catCandRes[i];
                    if (cone.usableStock[cu] > 0L) {
                        bestCat = cu;
                        break;
                    }
                    if (bestCat < 0 || cone.tier[cu] < cone.tier[bestCat]) {
                        bestCat = cu;
                    }
                }
                chosen = bestCat;
            }
            if (chosen < 0 || cone.bannedRes[chosen]) return false;
            catBinds[cpos - catS] = chosen;
        }

        // Bootstrap self-amplifying catalysts externally before finalizing batch count.
        long estBatches = Math.min(maxBatches, SafeMath.ceilDiv(deficit, outPerBatch));
        for (int i = 0; i < catBinds.length; i++) {
            int cpos = catS + i;
            int catU = catBinds[i];
            if (outputAmountOf(cone, r, catU) <= 0L) {
                continue;
            }
            int dur = cone.catDurCost[cpos];
            int maxDur = cone.catMaxDur[cpos];
            long minNeeded = (dur > 0 && maxDur > 0)
                    ? SafeMath.ceilDiv(
                        SafeMath.mulSaturating(SafeMath.addSaturating(vec.batches[r], estBatches), dur),
                        maxDur
                    )
                    : 1L;
            if (minNeeded > vec.presenceNeed[catU]) {
                vec.presenceNeed[catU] = minNeeded;
            }
            long missing = vec.presenceNeed[catU] - vec.gross[catU];
            if (missing > 0L) {
                Set<Integer> forbSet = forbiddenRecipes.computeIfAbsent(catU, ignored -> new HashSet<>());
                boolean addedForb = forbSet.add(r);
                long oldNeed = vec.need[catU];
                vec.need[catU] = SafeMath.addSaturating(vec.net[catU], missing);
                boolean supplied;
                try {
                    supplied = tryResolve(
                        cone,
                        request,
                        potentials,
                        choices,
                        vec,
                        catU,
                        missing,
                        availableMachines,
                        availableCaps,
                        ownedMachines,
                        ownedCaps,
                        activeConvPairs,
                        forbiddenMacros,
                        forbiddenRecipes,
                        totalBatchesUsed,
                        heap,
                        inHeap,
                        true
                    );
                } finally {
                    vec.need[catU] = oldNeed;
                    if (addedForb) {
                        forbSet.remove(r);
                    }
                }
                if (!supplied || vec.gross[catU] < vec.presenceNeed[catU]) {
                    return false;
                }
            }
        }

        long remainingDeficit = vec.need[targetOutU] - vec.net[targetOutU];
        if (remainingDeficit <= 0L) {
            return true;
        }
        long batchesToAdd = Math.min(maxBatches, SafeMath.ceilDiv(remainingDeficit, outPerBatch));

        // Resolve machine & optional SetupLineStep.
        MachineEntry machine = resolveAndProvisionMachine(
            cone,
            request,
            potentials,
            choices,
            vec,
            r,
            availableMachines,
            availableCaps,
            ownedMachines,
            ownedCaps,
            heap,
            inHeap
        );
        if (!cone.recHandCraftable[r]
                && machine == null
                && (cone.recCapability[r] == null || !availableCaps.contains(cone.recCapability[r]))) {
            return false;
        }

        totalBatchesUsed[0] = SafeMath.addSaturating(totalBatchesUsed[0], batchesToAdd);
        if (totalBatchesUsed[0] > maxTotalBatches) {
            vec.markExploded();
            return false;
        }

        vec.batches[r] = SafeMath.addSaturating(vec.batches[r], batchesToAdd);
        vec.chosenMachine[r] = machine;
        if (choices.automatedRecipes.contains(r)) {
            vec.automatedLocalRecipes.add(r);
        }

        for (int i = 0; i < binds.length; i++) {
            int pos = inS + i;
            vec.chosenSlotBind[pos] = binds[i];
            long consume = SafeMath.mulSaturating(batchesToAdd, cone.inAmount[pos]);
            vec.net[binds[i]] = SafeMath.addSaturating(vec.net[binds[i]], -consume);
            pushIfDeficit(vec, binds[i], heap, inHeap);
        }
        if (binds.length == 1 && cone.outStart[r + 1] - cone.outStart[r] == 1) {
            int outU = cone.outRes[cone.outStart[r]];
            if (binds[0] != outU) {
                activeConvPairs.add(pairKey(binds[0], outU));
            }
        }

        for (int i = 0; i < catBinds.length; i++) {
            int cpos = catS + i;
            int catU = catBinds[i];
            vec.chosenCatBind[cpos] = catU;
            int dur = cone.catDurCost[cpos];
            int maxDur = cone.catMaxDur[cpos];
            long minNeeded = (dur > 0 && maxDur > 0)
                    ? SafeMath.ceilDiv(SafeMath.mulSaturating(vec.batches[r], dur), maxDur)
                    : 1L;
            if (minNeeded > vec.presenceNeed[catU]) {
                vec.presenceNeed[catU] = minNeeded;
            }
            if (vec.presenceNeed[catU] > vec.gross[catU]) {
                long needMore = vec.presenceNeed[catU] - vec.gross[catU];
                vec.need[catU] = SafeMath.addSaturating(vec.need[catU], needMore);
                pushIfDeficit(vec, catU, heap, inHeap);
            }
        }

        for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
            int ov = cone.outRes[oi];
            long prod = SafeMath.mulSaturating(batchesToAdd, cone.outAmount[oi]);
            vec.net[ov] = SafeMath.addSaturating(vec.net[ov], prod);
            vec.gross[ov] = SafeMath.addSaturating(vec.gross[ov], prod);
        }
        return true;
    }

    private static boolean applyMacro(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        ProductionVector vec,
        int outU,
        CompiledCycleMacro macro,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        Set<MachineEntry> ownedMachines,
        Set<MachineCapabilityId> ownedCaps,
        Set<Long> activeConvPairs,
        Map<Integer, Set<String>> forbiddenMacros,
        Map<Integer, Set<Integer>> forbiddenRecipes,
        long[] totalBatchesUsed,
        PriorityQueue<Integer> heap,
        boolean[] inHeap
    ) {
        if (macro == null) return false;
        String mid = macro.macroKey().macroId();
        Set<String> forb = forbiddenMacros.get(outU);
        if (forb != null && forb.contains(mid)) return false;

        long netGain = macro.netDeltaPerIter().getOrDefault(cone.resKeys[outU], 0L);
        if (netGain <= 0L) return false;

        // 1. Ensure working seeds are present using sources OUTSIDE this macro.
        for (var e : macro.workingSeeds().entrySet()) {
            int su = cone.localRes(e.getKey());
            if (su < 0) return false;
            long reqSeed = e.getValue();
            if (reqSeed > vec.presenceNeed[su]) {
                vec.presenceNeed[su] = reqSeed;
            }
            long missing = reqSeed - vec.gross[su];
            if (missing > 0L) {
                forbiddenMacros.computeIfAbsent(su, ignored -> new HashSet<>()).add(mid);
                for (CompiledRecipe inner : macro.innerRecipes()) {
                    int lr = cone.localRecipe(inner.compactId());
                    if (lr >= 0) {
                        forbiddenRecipes.computeIfAbsent(su, ignored -> new HashSet<>()).add(lr);
                    }
                }
                long oldNeed = vec.need[su];
                vec.need[su] = SafeMath.addSaturating(vec.net[su], missing);
                boolean supplied;
                try {
                    supplied = tryResolve(
                        cone,
                        request,
                        potentials,
                        choices,
                        vec,
                        su,
                        missing,
                        availableMachines,
                        availableCaps,
                        ownedMachines,
                        ownedCaps,
                        activeConvPairs,
                        forbiddenMacros,
                        forbiddenRecipes,
                        totalBatchesUsed,
                        heap,
                        inHeap,
                        true
                    );
                } finally {
                    vec.need[su] = oldNeed;
                }
                if (!supplied || vec.gross[su] < reqSeed) {
                    return false;
                }
            }
        }

        // 2. Ensure machines for all inner recipes of the macro.
        for (CompiledRecipe inner : macro.innerRecipes()) {
            int lr = cone.localRecipe(inner.compactId());
            if (lr >= 0) {
                MachineEntry mach = resolveAndProvisionMachine(
                    cone,
                    request,
                    potentials,
                    choices,
                    vec,
                    lr,
                    availableMachines,
                    availableCaps,
                    ownedMachines,
                    ownedCaps,
                    heap,
                    inHeap
                );
                if (!cone.recHandCraftable[lr]
                        && mach == null
                        && (cone.recCapability[lr] == null || !availableCaps.contains(cone.recCapability[lr]))) {
                    return false;
                }
                vec.chosenMachine[lr] = mach;
            }
        }

        // 3. Recompute deficit after seed bootstrap (a self-amplifying seed counts toward target!).
        long d2 = vec.need[outU] - vec.net[outU];
        if (d2 <= 0L) {
            return true;
        }
        long iters = SafeMath.ceilDiv(d2, netGain);
        totalBatchesUsed[0] = SafeMath.addSaturating(totalBatchesUsed[0], iters);
        if (totalBatchesUsed[0] > maxTotalBatches) {
            vec.markExploded();
            return false;
        }

        vec.usedMacros.put(macro.macroKey(), macro);
        vec.macroIters.merge(macro.macroKey(), iters, SafeMath::addSaturating);
        for (var e : macro.netDeltaPerIter().entrySet()) {
            int lu = cone.localRes(e.getKey());
            if (lu < 0) continue;
            long totalDelta = SafeMath.mulSaturating(iters, e.getValue());
            vec.net[lu] = SafeMath.addSaturating(vec.net[lu], totalDelta);
            if (totalDelta > 0L) {
                vec.gross[lu] = SafeMath.addSaturating(vec.gross[lu], totalDelta);
            } else if (totalDelta < 0L) {
                pushIfDeficit(vec, lu, heap, inHeap);
            }
        }
        return true;
    }

    private static @Nullable MachineEntry resolveAndProvisionMachine(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        ProductionVector vec,
        int r,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        Set<MachineEntry> ownedMachines,
        Set<MachineCapabilityId> ownedCaps,
        PriorityQueue<Integer> heap,
        boolean[] inHeap
    ) {
        CompiledRecipe rec = cone.recObjects[r];
        Namespace sysId = rec.key().systemId();
        Set<Namespace> sysTags = rec.systemTags();
        MachineCapabilityId cap = cone.recCapability[r];
        List<MachineEntry> compat = cone.recCompatMachines[r];
        HumanGuidanceView guidance = request.context().guidance();
        FacilitySnapshot fac = request.context().facilities();

        // Check SetupLineStep automation trigger.
        boolean wantAutoLine = choices.automatedRecipes.contains(r)
                && fac.automatedRecipe(rec.key()).isEmpty();
        if (wantAutoLine) {
            MachineEntry primary = pickMachine(choices.machineOverride.get(r), potentials.bestMachine[r], compat, guidance);
            if (primary != null && guidance.isWillingToBuild(primary)) {
                if (!vec.setupLines.containsKey(sysId)) {
                    ComponentStackKey primaryOut = cone.resKeys[cone.outRes[cone.outStart[r]]];
                    vec.setupLines.put(
                        sysId,
                        new ProductionVector.SetupLineMeta(
                            sysId,
                            sysTags,
                            primary,
                            primaryOut,
                            cap != null ? cap : MachineCapabilityGroups.capabilityOf(sysId)
                        )
                    );
                    availableMachines.add(primary);
                    if (cap != null) availableCaps.add(cap);
                }
                return vec.setupLines.get(sysId).primaryMachine();
            }
        }

        if (cone.recHandCraftable[r]) {
            for (MachineEntry m : compat) {
                if (availableMachines.contains(m) || fac.ownsMachineFor(m)) return m;
            }
            return null;
        }

        // Respect explicit machine override if willing.
        MachineEntry override = choices.machineOverride.get(r);
        if (override != null) {
            if (availableMachines.contains(override) || fac.ownsMachineFor(override)) {
                return override;
            }
            if (guidance.isWillingToBuild(override)) {
                provisionMachine(cone, vec, override, sysId, sysTags, cap, availableMachines, availableCaps, heap, inHeap);
                return override;
            }
        }

        // Prefer already-owned/already-provisioned machine or capability.
        for (MachineEntry m : compat) {
            if (availableMachines.contains(m) || fac.ownsMachineFor(m)) {
                return m;
            }
        }
        if (cap != null && availableCaps.contains(cap)) {
            for (OwnedMachineGroup g : fac.ownedMachines()) {
                if (g.count() > 0 && g.capability().equals(cap)) {
                    return g.machine();
                }
            }
            for (var pm : vec.provisionedMachines.values()) {
                if (cap.equals(pm.capability())) {
                    return pm.machine();
                }
            }
            return compat.isEmpty() ? null : compat.get(0);
        }

        // Provision a new machine.
        MachineEntry picked = pickMachine(null, potentials.bestMachine[r], compat, guidance);
        if (picked == null) {
            return null;
        }
        provisionMachine(cone, vec, picked, sysId, sysTags, cap, availableMachines, availableCaps, heap, inHeap);
        return picked;
    }

    private static @Nullable MachineEntry pickMachine(
        @Nullable MachineEntry override,
        @Nullable MachineEntry potPick,
        List<MachineEntry> compat,
        HumanGuidanceView guidance
    ) {
        if (override != null && guidance.isWillingToBuild(override)) return override;
        if (potPick != null && guidance.isWillingToBuild(potPick)) return potPick;
        for (MachineEntry m : compat) {
            if (guidance.isWillingToBuild(m)) return m;
        }
        return null;
    }

    private static void provisionMachine(
        ConeModel cone,
        ProductionVector vec,
        MachineEntry machine,
        Namespace systemId,
        Set<Namespace> systemTags,
        MachineCapabilityId cap,
        Set<MachineEntry> availableMachines,
        Set<MachineCapabilityId> availableCaps,
        PriorityQueue<Integer> heap,
        boolean[] inHeap
    ) {
        availableMachines.add(machine);
        if (cap != null) availableCaps.add(cap);
        if (vec.provisionedMachines.containsKey(machine)) {
            return;
        }
        vec.provisionedMachines.put(
            machine,
            new ProductionVector.ProvisionMeta(machine, systemId, systemTags, cap, 1)
        );
        int mu = machine.contentKey().map(cone::localRes).orElse(-1);
        if (mu >= 0 && cone.tier[mu] < ConeModel.infTier && !cone.bannedRes[mu]) {
            vec.need[mu] = SafeMath.addSaturating(vec.need[mu], 1L);
            pushIfDeficit(vec, mu, heap, inHeap);
        } else {
            vec.playerPreparedMachines.add(machine);
        }
    }

    private static void pruneRedundantBatches(ConeModel cone, ProductionVector vec) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 64) {
            changed = false;
            for (int r = 0; r < cone.recipeCount; r++) {
                long b = vec.batches[r];
                if (b <= 0L) continue;
                long removable = b;
                for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                    int ov = cone.outRes[oi];
                    long outAmt = cone.outAmount[oi];
                    long surplusNet = vec.net[ov] - vec.need[ov];
                    long surplusPresence = vec.gross[ov] - vec.presenceNeed[ov];
                    long maxDrop = Math.min(surplusNet, surplusPresence) / outAmt;
                    if (maxDrop < removable) removable = maxDrop;
                    if (removable <= 0L) break;
                }
                if (removable > 0L) {
                    vec.batches[r] -= removable;
                    for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                        int ov = cone.outRes[oi];
                        long drop = SafeMath.mulSaturating(removable, cone.outAmount[oi]);
                        vec.net[ov] -= drop;
                        vec.gross[ov] -= drop;
                    }
                    for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                        int inU = vec.chosenSlotBind[pos];
                        if (inU >= 0) {
                            vec.net[inU] = SafeMath
                                    .addSaturating(vec.net[inU], SafeMath.mulSaturating(removable, cone.inAmount[pos]));
                        }
                    }
                    changed = true;
                }
            }
            // Also trim redundant acquisitions if pruning created surplus.
            for (int u = 0; u < cone.nodeCount; u++) {
                if (vec.acq[u] > 0L) {
                    long extraNet = vec.net[u] - vec.need[u];
                    long extraPres = vec.gross[u] - vec.presenceNeed[u];
                    long trim = Math.min(vec.acq[u], Math.min(extraNet, extraPres));
                    if (trim > 0L) {
                        vec.acq[u] -= trim;
                        vec.net[u] -= trim;
                        vec.gross[u] -= trim;
                        changed = true;
                    }
                }
            }
        }
    }

    private static long outputAmountOf(ConeModel cone, int r, int outU) {
        for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
            if (cone.outRes[oi] == outU) return cone.outAmount[oi];
        }
        return 0L;
    }

    private static void pushIfDeficit(ProductionVector vec, int u, PriorityQueue<Integer> heap, boolean[] inHeap) {
        if (vec.need[u] > vec.net[u] && !inHeap[u]) {
            heap.add(u);
            inHeap[u] = true;
        }
    }

    private static long pairKey(int from, int to) {
        return (((long) from) << 32) | (to & 0xffffffffL);
    }

    private static boolean wouldCreateRoundTrip(Set<Long> pairs, int from, int to) {
        return pairs.contains(pairKey(to, from));
    }

    private static String classifyUnmetReason(ConeModel cone, int u, HumanGuidanceView guidance) {
        ComponentStackKey rk = cone.resKeys[u];
        if (cone.bannedRes[u]) return reasonResourceBanned;
        Set<String> rules = cone.blockingRuleIds.get(rk);
        if (rules != null && !rules.isEmpty()) return reasonHumanRule;
        if (cone.truncatedByBudget && !cone.graph.producersOf(rk).isEmpty()) return reasonBudgetTruncated;
        return reasonNoSource;
    }

    private static void collectBlocking(ConeModel cone, int u, ProductionVector vec) {
        Set<String> rules = cone.blockingRuleIds.get(cone.resKeys[u]);
        if (rules != null) {
            vec.hitRuleIds.addAll(rules);
        }
    }
}
