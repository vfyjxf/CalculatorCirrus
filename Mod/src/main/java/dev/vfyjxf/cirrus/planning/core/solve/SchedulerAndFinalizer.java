package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PreferenceModifiers;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.lang.Fnv1a64;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cirrus.planning.core.select.DiverseParetoSelector;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SchedulerAndFinalizer {

    private static final VerificationCertificate pendingCert = new VerificationCertificate(
        false,
        false,
        false,
        false,
        false,
        false,
        List.of("PENDING_VERIFICATION")
    );

    public record AssembleOutcome(
        @Nullable ProductionPlan plan,
        int repairResLocalId,
        long repairExtraAmount,
        @Nullable MachineEntry cycleMachineToDetach
    ) {
        public static AssembleOutcome ok(ProductionPlan plan) {
            return new AssembleOutcome(plan, -1, 0L, null);
        }

        public static AssembleOutcome repair(int localRes, long extra) {
            return new AssembleOutcome(null, localRes, extra, null);
        }

        public static AssembleOutcome detachMachine(MachineEntry machine) {
            return new AssembleOutcome(null, -1, 0L, machine);
        }

        public static AssembleOutcome fail() {
            return new AssembleOutcome(null, -1, 0L, null);
        }
    }

    private enum NodeKind {
        acquire(0), linePull(0), setupLine(1), provision(1), convert(2), craft(2), cycleLoop(2);

        final int rank;

        NodeKind(int rank) {
            this.rank = rank;
        }
    }

    private record StepNode(
        NodeKind kind,
        int localId,
        @Nullable MachineEntry machine,
        @Nullable Namespace systemId,
        @Nullable CompiledCycleMacroKey macroKey,
        int psi,
        String tieKey
    ) {}

    private SchedulerAndFinalizer() {}

    public static @Nullable ProductionPlan assembleWithRepair(
        ConeModel cone,
        PlanningRequest request,
        Potentials potentials,
        ChoiceTable choices,
        CostMetadataProvider meta,
        String strategyLabel
    ) {
        Map<Integer, Long> extraPresence = new HashMap<>();
        Set<MachineEntry> forcedPlayerPrepared = new HashSet<>();

        for (int attempt = 0; attempt < 4; attempt++) {
            ProductionVector vec = Netting.settle(cone, request, potentials, choices, extraPresence);
            if (!vec.isFeasible()) {
                return null;
            }
            for (MachineEntry detached : forcedPlayerPrepared) {
                if (vec.provisionedMachines.containsKey(detached)) {
                    vec.playerPreparedMachines.add(detached);
                    int mu = detached.contentKey().map(cone::localRes).orElse(-1);
                    if (mu >= 0 && vec.need[mu] > 0L) {
                        vec.need[mu] = Math.max(0L, vec.need[mu] - 1L);
                    }
                }
            }
            AssembleOutcome out = scheduleAndFinalize(cone, request, vec, meta, strategyLabel);
            if (out.plan() != null) {
                return out.plan();
            }
            if (out.cycleMachineToDetach() != null && forcedPlayerPrepared.add(out.cycleMachineToDetach())) {
                continue;
            }
            if (out.repairResLocalId() >= 0 && out.repairExtraAmount() > 0L) {
                extraPresence.merge(out.repairResLocalId(), out.repairExtraAmount(), SafeMath::addSaturating);
                continue;
            }
            return null;
        }
        return null;
    }

    public static AssembleOutcome scheduleAndFinalize(
        ConeModel cone,
        PlanningRequest request,
        ProductionVector vec,
        CostMetadataProvider meta,
        String strategyLabel
    ) {
        FacilitySnapshot fac = request.context().facilities();
        HumanGuidanceView guidance = request.context().guidance();
        boolean ignoreStock = request.profile().modifiers().stockMode() == PreferenceModifiers.StockReferenceMode.ignore;

        // 1. Collect all step nodes deterministically.
        List<StepNode> nodes = new ArrayList<>();
        for (int u = 0; u < cone.nodeCount; u++) {
            if (vec.acq[u] > 0L) {
                nodes.add(new StepNode(NodeKind.acquire, u, null, null, null, 0, "A:" + cone.resKeys[u].canonicalKey()));
            }
            if (vec.line[u] > 0L) {
                nodes.add(new StepNode(NodeKind.linePull, u, null, null, null, 0, "L:" + cone.resKeys[u].canonicalKey()));
            }
        }
        for (var e : vec.setupLines.entrySet()) {
            nodes.add(new StepNode(NodeKind.setupLine, -1, null, e.getKey(), null, 0, "S:" + e.getKey()));
        }
        for (var e : vec.provisionedMachines.entrySet()) {
            MachineEntry m = e.getKey();
            int mu = m.contentKey().map(cone::localRes).orElse(-1);
            int psi = (!vec.playerPreparedMachines.contains(m) && mu >= 0) ? cone.tier[mu] : 0;
            nodes.add(new StepNode(NodeKind.provision, mu, m, null, null, psi, "P:" + m.canonicalKey()));
        }
        for (int r = 0; r < cone.recipeCount; r++) {
            if (vec.convertBatches[r] > 0L) {
                nodes.add(
                    new StepNode(
                        NodeKind.convert,
                        r,
                        null,
                        null,
                        null,
                        Math.max(1, cone.recPsi[r]),
                        "V:" + cone.recObjects[r].key().shortString()
                    )
                );
            }
            if (vec.batches[r] > 0L) {
                nodes.add(
                    new StepNode(
                        NodeKind.craft,
                        r,
                        null,
                        null,
                        null,
                        Math.max(1, cone.recPsi[r]),
                        "C:" + cone.recObjects[r].key().shortString()
                    )
                );
            }
        }
        for (var e : vec.macroIters.entrySet()) {
            if (e.getValue() > 0L) {
                CompiledCycleMacro m = Objects.requireNonNull(vec.usedMacros.get(e.getKey()));
                int psi = 1;
                for (var d : m.netDeltaPerIter().entrySet()) {
                    if (d.getValue() > 0L) {
                        int ou = cone.localRes(d.getKey());
                        if (ou >= 0 && cone.tier[ou] < ConeModel.infTier) {
                            psi = Math.max(psi, cone.tier[ou]);
                        }
                    }
                }
                nodes.add(new StepNode(NodeKind.cycleLoop, -1, null, null, e.getKey(), psi, "Y:" + e.getKey().macroId()));
            }
        }

        int mCount = nodes.size();
        Map<Integer, List<Integer>> producersOf = new HashMap<>();
        Map<Integer, List<Integer>> consumersOf = new HashMap<>();
        Map<Integer, List<Integer>> presenceNeedsOf = new HashMap<>();
        Map<MachineEntry, Integer> machineProvider = new HashMap<>();
        Map<Namespace, Integer> systemAutoProvider = new HashMap<>();
        Map<MachineEntry, List<Integer>> machineWaiters = new HashMap<>();
        Map<Namespace, List<Integer>> systemAutoWaiters = new HashMap<>();

        for (int i = 0; i < mCount; i++) {
            StepNode nd = nodes.get(i);
            switch (nd.kind) {
                case acquire, linePull -> producersOf.computeIfAbsent(nd.localId, ignored -> new ArrayList<>(2)).add(i);
                case setupLine -> {
                    ProductionVector.SetupLineMeta sm = Objects.requireNonNull(vec.setupLines.get(nd.systemId));
                    machineProvider.put(sm.primaryMachine(), i);
                    systemAutoProvider.put(sm.systemId(), i);
                }
                case provision -> {
                    MachineEntry m = Objects.requireNonNull(nd.machine);
                    machineProvider.putIfAbsent(m, i);
                    if (!vec.playerPreparedMachines.contains(m) && nd.localId >= 0) {
                        consumersOf.computeIfAbsent(nd.localId, ignored -> new ArrayList<>(2)).add(i);
                    }
                }
                case convert -> {
                    int r = nd.localId;
                    for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                        int inU = vec.chosenSlotBind[pos];
                        if (inU >= 0) consumersOf.computeIfAbsent(inU, ignored -> new ArrayList<>(2)).add(i);
                    }
                    for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                        producersOf.computeIfAbsent(cone.outRes[oi], ignored -> new ArrayList<>(2)).add(i);
                    }
                }
                case craft -> {
                    int r = nd.localId;
                    for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                        int inU = vec.chosenSlotBind[pos];
                        if (inU >= 0) consumersOf.computeIfAbsent(inU, ignored -> new ArrayList<>(2)).add(i);
                    }
                    for (int cpos = cone.catStart[r]; cpos < cone.catStart[r + 1]; cpos++) {
                        int catU = vec.chosenCatBind[cpos];
                        if (catU >= 0) {
                            consumersOf.computeIfAbsent(catU, ignored -> new ArrayList<>(2)).add(i);
                            presenceNeedsOf.computeIfAbsent(catU, ignored -> new ArrayList<>(2)).add(i);
                        }
                    }
                    for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                        producersOf.computeIfAbsent(cone.outRes[oi], ignored -> new ArrayList<>(2)).add(i);
                    }
                    MachineEntry mach = vec.chosenMachine[r];
                    if (mach != null) {
                        machineWaiters.computeIfAbsent(mach, ignored -> new ArrayList<>(2)).add(i);
                    }
                    if (vec.automatedLocalRecipes.contains(r)) {
                        systemAutoWaiters
                                .computeIfAbsent(cone.recObjects[r].key().systemId(), ignored -> new ArrayList<>(2))
                                .add(i);
                    }
                }
                case cycleLoop -> {
                    CompiledCycleMacro macro = Objects.requireNonNull(vec.usedMacros.get(nd.macroKey));
                    for (var e : macro.netDeltaPerIter().entrySet()) {
                        int lu = cone.localRes(e.getKey());
                        if (lu < 0) continue;
                        if (e.getValue() > 0L) {
                            producersOf.computeIfAbsent(lu, ignored -> new ArrayList<>(2)).add(i);
                        } else if (e.getValue() < 0L) {
                            consumersOf.computeIfAbsent(lu, ignored -> new ArrayList<>(2)).add(i);
                        }
                    }
                    for (ComponentStackKey sk : macro.workingSeeds().keySet()) {
                        int su = cone.localRes(sk);
                        if (su >= 0) {
                            consumersOf.computeIfAbsent(su, ignored -> new ArrayList<>(2)).add(i);
                            presenceNeedsOf.computeIfAbsent(su, ignored -> new ArrayList<>(2)).add(i);
                        }
                    }
                    for (CompiledRecipe inner : macro.innerRecipes()) {
                        int lr = cone.localRecipe(inner.compactId());
                        MachineEntry mach = (lr >= 0 && vec.chosenMachine[lr] != null)
                                ? vec.chosenMachine[lr]
                                : (inner.machineRequirement().compatibleMachines().isEmpty()
                                        ? null
                                        : inner.machineRequirement().compatibleMachines().get(0));
                        if (mach != null) {
                            machineWaiters.computeIfAbsent(mach, ignored -> new ArrayList<>(2)).add(i);
                        }
                    }
                }
            }
        }

        // 2. Build DAG adjacency and in-degrees.
        @SuppressWarnings("unchecked")
        Set<Integer>[] succ = (Set<Integer>[]) new Set<?>[mCount];
        @SuppressWarnings("unchecked")
        Set<Integer>[] pred = (Set<Integer>[]) new Set<?>[mCount];
        for (int i = 0; i < mCount; i++) {
            succ[i] = new TreeSet<>();
            pred[i] = new TreeSet<>();
        }
        int[] indeg = new int[mCount];

        for (var e : producersOf.entrySet()) {
            List<Integer> cons = consumersOf.get(e.getKey());
            if (cons == null) continue;
            for (int p : e.getValue()) {
                for (int c : cons) {
                    if (p != c && succ[p].add(c)) {
                        pred[c].add(p);
                        indeg[c]++;
                    }
                }
            }
        }
        for (var e : machineWaiters.entrySet()) {
            Integer prov = machineProvider.get(e.getKey());
            if (prov == null) continue;
            for (int c : e.getValue()) {
                if (prov != c && succ[prov].add(c)) {
                    pred[c].add(prov);
                    indeg[c]++;
                }
            }
        }
        for (var e : systemAutoWaiters.entrySet()) {
            Integer prov = systemAutoProvider.get(e.getKey());
            if (prov == null) continue;
            for (int c : e.getValue()) {
                if (prov != c && succ[prov].add(c)) {
                    pred[c].add(prov);
                    indeg[c]++;
                }
            }
        }

        // 3. Presence-aware priority Kahn sort.
        PriorityQueue<Integer> ready = new PriorityQueue<>((a, b) -> compareNodes(nodes.get(a), nodes.get(b)));
        for (int i = 0; i < mCount; i++) {
            if (indeg[i] == 0) ready.add(i);
        }
        long[] simStock = cone.usableStock.clone();
        boolean[] scheduled = new boolean[mCount];
        List<Integer> order = new ArrayList<>(mCount);
        List<Integer> deferred = new ArrayList<>();

        while (!ready.isEmpty() || !deferred.isEmpty()) {
            int idx;
            boolean forced = false;
            if (!ready.isEmpty()) {
                idx = ready.poll();
            } else {
                deferred.sort((a, b) -> compareNodes(nodes.get(a), nodes.get(b)));
                idx = deferred.remove(0);
                forced = true;
            }
            if (!forced
                    && wouldStarvePendingPresence(
                        cone,
                        vec,
                        nodes.get(idx),
                        idx,
                        simStock,
                        presenceNeedsOf,
                        scheduled
                    )) {
                deferred.add(idx);
                continue;
            }
            scheduled[idx] = true;
            order.add(idx);
            advanceSimStock(cone, vec, nodes.get(idx), simStock);
            for (int nxt : succ[idx]) {
                if (--indeg[nxt] == 0) {
                    ready.add(nxt);
                }
            }
        }

        // Detect cycle (e.g., self-dependent machine crafting): detach a cyclic ProvisionMachine node.
        if (order.size() != mCount) {
            for (int i = 0; i < mCount; i++) {
                if (!scheduled[i]
                        && nodes.get(i).kind == NodeKind.provision
                        && !vec.playerPreparedMachines.contains(nodes.get(i).machine)) {
                    return AssembleOutcome.detachMachine(Objects.requireNonNull(nodes.get(i).machine));
                }
            }
            return AssembleOutcome.fail();
        }

        // 4. Finalize steps with contiguous IDs s001..sNNN and two-pool FIFO replay.
        String[] stepIds = new String[mCount];
        for (int pos = 0; pos < mCount; pos++) {
            stepIds[order.get(pos)] = String.format("s%03d", pos + 1);
        }

        Map<ComponentStackKey, Long> initialBefore = ignoreStock
                ? new TreeMap<>()
                : new TreeMap<>(request.context().inventory().allUsable());
        Map<ComponentStackKey, Long> initialPool = new TreeMap<>(initialBefore);
        Map<ComponentStackKey, Deque<FinalBatch>> producedPool = new TreeMap<>();

        List<PlanStep> planSteps = new ArrayList<>(mCount);
        Map<ComponentStackKey, Long> totalAcquired = new TreeMap<>();
        Map<MachineEntry, Integer> totalMachinesProv = new TreeMap<>(MachineEntry.comparator);
        Set<String> linesUsed = new TreeSet<>();
        Set<ComponentStackKey> catalystsReq = new TreeSet<>();

        for (int pos = 0; pos < mCount; pos++) {
            int ni = order.get(pos);
            StepNode nd = nodes.get(ni);
            String sid = stepIds[ni];
            List<String> deps = new ArrayList<>(pred[ni].size());
            for (int p : pred[ni]) {
                deps.add(stepIds[p]);
            }
            deps.sort(String::compareTo);

            switch (nd.kind) {
                case acquire -> {
                    int u = nd.localId;
                    ComponentStackKey rk = cone.resKeys[u];
                    long amt = vec.acq[u];
                    AcquisitionOption opt = cone.acquireOption[u];
                    AcquisitionKind kind = (cone.freeBase[u] || opt == null)
                            ? AcquisitionKind.baseSupply
                            : opt.kind();
                    CostVector sc = opt != null
                            ? StepCosts.acquireCost(opt, amt, cone.freeBase[u])
                            : StepCosts.freeBaseCost;
                    String tripEp = (opt != null && !cone.freeBase[u]) ? opt.tripEpisodeId() : "";
                    double fixedTrip = (opt != null && !cone.freeBase[u] && kind == AcquisitionKind.manualGather)
                            ? opt.fixedTripHumanSeconds()
                            : 0.0;
                    planSteps.add(new AcquireStep(sid, deps, rk, amt, kind, sc, tripEp, fixedTrip));
                    produceFifo(producedPool, rk, amt, sid);
                    totalAcquired.merge(rk, amt, SafeMath::addSaturating);
                }
                case linePull -> {
                    int u = nd.localId;
                    ComponentStackKey rk = cone.resKeys[u];
                    long amt = vec.line[u];
                    AutomatedLine line = cone.bestLine[u];
                    CostVector sc = StepCosts.linePullCost(line, amt);
                    planSteps.add(new LinePullStep(sid, deps, line.lineId(), rk, amt, sc));
                    produceFifo(producedPool, rk, amt, sid);
                    linesUsed.add(line.lineId());
                }
                case setupLine -> {
                    ProductionVector.SetupLineMeta sm = Objects.requireNonNull(vec.setupLines.get(nd.systemId));
                    CostVector sc = StepCosts.setupLineCost(sm.systemId(), sm.systemTags(), sm.capability(), meta);
                    planSteps.add(
                        new SetupLineStep(sid, deps, sm.systemId(), sm.primaryMachine(), sm.targetOutput(), sc)
                    );
                }
                case provision -> {
                    MachineEntry m = Objects.requireNonNull(nd.machine);
                    ProductionVector.ProvisionMeta pm = Objects.requireNonNull(vec.provisionedMachines.get(m));
                    boolean craftedFromStock = !vec.playerPreparedMachines.contains(m);
                    if (craftedFromStock) {
                        Optional<ComponentStackKey> mkOpt = m.contentKey();
                        if (mkOpt.isEmpty() || !consumeFifo(producedPool, initialPool, mkOpt.get(), pm.count())) {
                            return AssembleOutcome.detachMachine(m);
                        }
                    }
                    CostVector sc = StepCosts.provisionMachineCost(m, pm.systemId(), pm.systemTags(), pm.count(), meta);
                    planSteps.add(
                        new ProvisionMachineStep(
                            sid,
                            deps,
                            m,
                            Optional.ofNullable(pm.capability()),
                            pm.count(),
                            craftedFromStock,
                            sc
                        )
                    );
                    totalMachinesProv.merge(m, pm.count(), Integer::sum);
                }
                case convert -> {
                    int r = nd.localId;
                    CompiledRecipe rec = cone.recObjects[r];
                    long b = vec.convertBatches[r];
                    Map<Integer, SlotBinding> bindings = buildSlotBindings(cone, vec, r);
                    for (InputSlot slot : rec.inputs()) {
                        ComponentStackKey chosen = Objects.requireNonNull(bindings.get(slot.slotIndex())).chosenResource();
                        long need = SafeMath.mulSaturating(b, slot.amountPerBatch());
                        if (!consumeFifo(producedPool, initialPool, chosen, need)) {
                            int lu = cone.localRes(chosen);
                            return lu >= 0 ? AssembleOutcome.repair(lu, need) : AssembleOutcome.fail();
                        }
                    }
                    for (OutputSlot out : rec.outputs()) {
                        produceFifo(producedPool, out.resource(), SafeMath.mulSaturating(b, out.amountPerBatch()), sid);
                    }
                    planSteps.add(new ConvertStep(sid, deps, rec.key(), b, bindings, StepCosts.convertCost));
                }
                case craft -> {
                    int r = nd.localId;
                    CompiledRecipe rec = cone.recObjects[r];
                    long b = vec.batches[r];
                    Map<Integer, SlotBinding> bindings = buildSlotBindings(cone, vec, r);
                    Map<Integer, ComponentStackKey> catBindings = buildCatBindings(cone, vec, r);
                    for (CatalystSlot cat : rec.catalysts()) {
                        ComponentStackKey ck = Objects.requireNonNull(catBindings.get(cat.slotIndex()));
                        long minNeed = (cat.durabilityCostPerBatch() > 0 && cat.maxDurability() > 0)
                                ? SafeMath.ceilDiv(
                                    SafeMath.mulSaturating(b, cat.durabilityCostPerBatch()),
                                    cat.maxDurability()
                                )
                                : 1L;
                        if (totalFifo(producedPool, initialPool, ck) < minNeed) {
                            int lu = cone.localRes(ck);
                            return lu >= 0 ? AssembleOutcome.repair(lu, minNeed) : AssembleOutcome.fail();
                        }
                        catalystsReq.add(ck);
                    }
                    for (InputSlot slot : rec.inputs()) {
                        ComponentStackKey chosen = Objects.requireNonNull(bindings.get(slot.slotIndex())).chosenResource();
                        long need = SafeMath.mulSaturating(b, slot.amountPerBatch());
                        if (!consumeFifo(producedPool, initialPool, chosen, need)) {
                            int lu = cone.localRes(chosen);
                            return lu >= 0 ? AssembleOutcome.repair(lu, need) : AssembleOutcome.fail();
                        }
                    }
                    for (OutputSlot out : rec.outputs()) {
                        produceFifo(producedPool, out.resource(), SafeMath.mulSaturating(b, out.amountPerBatch()), sid);
                    }
                    Optional<MachineEntry> chosenM = Optional.ofNullable(vec.chosenMachine[r]);
                    boolean auto = fac.automatedRecipe(rec.key()).isPresent() || vec.automatedLocalRecipes.contains(r);
                    CostVector sc = StepCosts.craftCost(rec, b, chosenM, auto, fac, meta);
                    planSteps.add(new CraftStep(sid, deps, rec.key(), b, chosenM, auto, bindings, catBindings, sc));
                }
                case cycleLoop -> {
                    CompiledCycleMacro macro = Objects.requireNonNull(vec.usedMacros.get(nd.macroKey));
                    long iters = Objects.requireNonNull(vec.macroIters.get(nd.macroKey));
                    for (var se : macro.workingSeeds().entrySet()) {
                        if (totalFifo(producedPool, initialPool, se.getKey()) < se.getValue()) {
                            int lu = cone.localRes(se.getKey());
                            return lu >= 0 ? AssembleOutcome.repair(lu, se.getValue()) : AssembleOutcome.fail();
                        }
                    }
                    List<CraftStep> template = new ArrayList<>(macro.innerRecipes().size());
                    for (int k = 0; k < macro.innerRecipes().size(); k++) {
                        CompiledRecipe inner = macro.innerRecipes().get(k);
                        long mult = macro.innerMultipliers().get(k);
                        Map<Integer, SlotBinding> ib = new TreeMap<>();
                        for (InputSlot slot : inner.inputs()) {
                            ComponentStackKey c = slot.candidates().get(0);
                            ib.put(slot.slotIndex(), new SlotBinding(c, List.of()));
                        }
                        Map<Integer, ComponentStackKey> icb = new TreeMap<>();
                        for (CatalystSlot cat : inner.catalysts()) {
                            ComponentStackKey c = cat.candidates().get(0);
                            icb.put(cat.slotIndex(), c);
                            catalystsReq.add(c);
                        }
                        int lr = cone.localRecipe(inner.compactId());
                        MachineEntry mach = (lr >= 0 && vec.chosenMachine[lr] != null)
                                ? vec.chosenMachine[lr]
                                : (inner.machineRequirement().compatibleMachines().isEmpty()
                                        ? null
                                        : inner.machineRequirement().compatibleMachines().get(0));
                        Optional<MachineEntry> machOpt = Optional.ofNullable(mach);
                        CostVector icost = StepCosts.craftCost(inner, mult, machOpt, false, fac, meta);
                        template.add(
                            new CraftStep(
                                sid + "_inner_" + k,
                                List.of(),
                                inner.key(),
                                mult,
                                machOpt,
                                false,
                                ib,
                                icb,
                                icost
                            )
                        );
                    }
                    for (var de : macro.netDeltaPerIter().entrySet()) {
                        long d = de.getValue();
                        if (d < 0L) {
                            long need = SafeMath.mulSaturating(iters, -d);
                            if (!consumeFifo(producedPool, initialPool, de.getKey(), need)) {
                                int lu = cone.localRes(de.getKey());
                                return lu >= 0 ? AssembleOutcome.repair(lu, need) : AssembleOutcome.fail();
                            }
                        } else if (d > 0L) {
                            produceFifo(producedPool, de.getKey(), SafeMath.mulSaturating(iters, d), sid);
                        }
                    }
                    CostVector loopCost = StepCosts.cycleLoopCost(template, iters);
                    planSteps.add(
                        new CycleLoopStep(
                            sid,
                            deps,
                            macro.macroKey(),
                            iters,
                            macro.workingSeeds(),
                            template,
                            macro.netDeltaPerIter(),
                            loopCost
                        )
                    );
                }
            }
        }

        // Consume targets from produced pool first, then initial pool.
        for (TargetDemand td : request.targets()) {
            if (!consumeFifo(producedPool, initialPool, td.resource(), td.amount())) {
                return AssembleOutcome.fail();
            }
        }

        // Build exact surplusByproducts from remaining produced pool batches.
        List<ByproductEntry> surplus = new ArrayList<>();
        for (var e : producedPool.entrySet()) {
            for (FinalBatch batch : e.getValue()) {
                if (batch.amount > 0L) {
                    surplus.add(new ByproductEntry(e.getKey(), batch.amount, batch.stepId));
                }
            }
        }

        Map<ComponentStackKey, Long> consumedInv = new TreeMap<>();
        for (var e : initialBefore.entrySet()) {
            long rem = initialPool.getOrDefault(e.getKey(), 0L);
            long used = e.getValue() - rem;
            if (used > 0L) {
                consumedInv.put(e.getKey(), used);
            }
        }

        PreparationSummary prep = new PreparationSummary(
            new StockLedger(consumedInv),
            new StockLedger(totalAcquired),
            totalMachinesProv,
            List.copyOf(linesUsed),
            List.copyOf(catalystsReq)
        );
        CostVector totalCost = PlanCosts.aggregate(planSteps);
        ProductionPlan temp = new ProductionPlan(
            "tmp",
            strategyLabel,
            planSteps,
            prep,
            surplus,
            totalCost,
            pendingCert
        );
        String planId = "plan-" + strategyLabel + "-"
                + Long.toHexString(Fnv1a64.hashUtf8(DiverseParetoSelector.structuralKey(temp)));
        return AssembleOutcome
                .ok(new ProductionPlan(planId, strategyLabel, planSteps, prep, surplus, totalCost, pendingCert));
    }

    private static Map<Integer, SlotBinding> buildSlotBindings(ConeModel cone, ProductionVector vec, int r) {
        Map<Integer, SlotBinding> out = new TreeMap<>();
        for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
            int chosen = vec.chosenSlotBind[pos];
            ComponentStackKey chosenKey = cone.resKeys[chosen];
            List<ComponentStackKey> alts = List.of();
            for (int cid = cone.inClassStart[pos]; cid < cone.inClassStart[pos + 1]; cid++) {
                if (cone.classRep[cid] == chosen) {
                    int ms = cone.classMemStart[cid];
                    int me = cone.classMemStart[cid + 1];
                    if (me - ms > 1) {
                        List<ComponentStackKey> a = new ArrayList<>(me - ms - 1);
                        for (int j = ms; j < me; j++) {
                            if (cone.classMemRes[j] != chosen) {
                                a.add(cone.resKeys[cone.classMemRes[j]]);
                            }
                        }
                        alts = List.copyOf(a);
                    }
                    break;
                }
            }
            out.put(cone.inSlotIndex[pos], new SlotBinding(chosenKey, alts));
        }
        return out;
    }

    private static Map<Integer, ComponentStackKey> buildCatBindings(ConeModel cone, ProductionVector vec, int r) {
        Map<Integer, ComponentStackKey> out = new TreeMap<>();
        for (int cpos = cone.catStart[r]; cpos < cone.catStart[r + 1]; cpos++) {
            out.put(cone.catSlotIndex[cpos], cone.resKeys[vec.chosenCatBind[cpos]]);
        }
        return out;
    }

    private static boolean wouldStarvePendingPresence(
        ConeModel cone,
        ProductionVector vec,
        StepNode nd,
        int nodeIdx,
        long[] simStock,
        Map<Integer, List<Integer>> presenceNeedsOf,
        boolean[] scheduled
    ) {
        if (presenceNeedsOf.isEmpty()) return false;
        return switch (nd.kind) {
            case provision -> {
                if (vec.playerPreparedMachines.contains(nd.machine) || nd.localId < 0) yield false;
                yield starves(nd.localId, 1L, nodeIdx, simStock, presenceNeedsOf, scheduled);
            }
            case convert -> {
                int r = nd.localId;
                long b = vec.convertBatches[r];
                for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                    int u = vec.chosenSlotBind[pos];
                    if (u >= 0
                            && starves(
                                u,
                                SafeMath.mulSaturating(b, cone.inAmount[pos]),
                                nodeIdx,
                                simStock,
                                presenceNeedsOf,
                                scheduled
                            )) {
                        yield true;
                    }
                }
                yield false;
            }
            case craft -> {
                int r = nd.localId;
                long b = vec.batches[r];
                for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                    int u = vec.chosenSlotBind[pos];
                    if (u >= 0
                            && starves(
                                u,
                                SafeMath.mulSaturating(b, cone.inAmount[pos]),
                                nodeIdx,
                                simStock,
                                presenceNeedsOf,
                                scheduled
                            )) {
                        yield true;
                    }
                }
                yield false;
            }
            case cycleLoop -> {
                CompiledCycleMacro m = Objects.requireNonNull(vec.usedMacros.get(nd.macroKey));
                long iters = Objects.requireNonNull(vec.macroIters.get(nd.macroKey));
                for (var e : m.netDeltaPerIter().entrySet()) {
                    if (e.getValue() < 0L) {
                        int u = cone.localRes(e.getKey());
                        if (u >= 0
                                && starves(
                                    u,
                                    SafeMath.mulSaturating(iters, -e.getValue()),
                                    nodeIdx,
                                    simStock,
                                    presenceNeedsOf,
                                    scheduled
                                )) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean starves(
        int u,
        long taken,
        int selfIdx,
        long[] simStock,
        Map<Integer, List<Integer>> presenceNeedsOf,
        boolean[] scheduled
    ) {
        List<Integer> waiters = presenceNeedsOf.get(u);
        if (waiters == null) return false;
        if (simStock[u] - taken >= 1L) return false;
        for (int w : waiters) {
            if (w != selfIdx && !scheduled[w]) {
                return true;
            }
        }
        return false;
    }

    private static void advanceSimStock(ConeModel cone, ProductionVector vec, StepNode nd, long[] simStock) {
        switch (nd.kind) {
            case acquire -> simStock[nd.localId] = SafeMath.addSaturating(simStock[nd.localId], vec.acq[nd.localId]);
            case linePull -> simStock[nd.localId] = SafeMath.addSaturating(simStock[nd.localId], vec.line[nd.localId]);
            case provision -> {
                if (!vec.playerPreparedMachines.contains(nd.machine) && nd.localId >= 0) {
                    simStock[nd.localId] -= 1L;
                }
            }
            case convert -> {
                int r = nd.localId;
                long b = vec.convertBatches[r];
                for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                    int u = vec.chosenSlotBind[pos];
                    if (u >= 0) simStock[u] -= SafeMath.mulSaturating(b, cone.inAmount[pos]);
                }
                for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                    simStock[cone.outRes[oi]] = SafeMath
                            .addSaturating(simStock[cone.outRes[oi]], SafeMath.mulSaturating(b, cone.outAmount[oi]));
                }
            }
            case craft -> {
                int r = nd.localId;
                long b = vec.batches[r];
                for (int pos = cone.inStart[r]; pos < cone.inStart[r + 1]; pos++) {
                    int u = vec.chosenSlotBind[pos];
                    if (u >= 0) simStock[u] -= SafeMath.mulSaturating(b, cone.inAmount[pos]);
                }
                for (int oi = cone.outStart[r]; oi < cone.outStart[r + 1]; oi++) {
                    simStock[cone.outRes[oi]] = SafeMath
                            .addSaturating(simStock[cone.outRes[oi]], SafeMath.mulSaturating(b, cone.outAmount[oi]));
                }
            }
            case cycleLoop -> {
                CompiledCycleMacro m = Objects.requireNonNull(vec.usedMacros.get(nd.macroKey));
                long iters = Objects.requireNonNull(vec.macroIters.get(nd.macroKey));
                for (var e : m.netDeltaPerIter().entrySet()) {
                    int u = cone.localRes(e.getKey());
                    if (u >= 0)
                        simStock[u] = SafeMath.addSaturating(simStock[u], SafeMath.mulSaturating(iters, e.getValue()));
                }
            }
            default -> {}
        }
    }

    private static int compareNodes(StepNode a, StepNode b) {
        int c = Integer.compare(a.psi, b.psi);
        if (c != 0) return c;
        c = Integer.compare(a.kind.rank, b.kind.rank);
        if (c != 0) return c;
        return a.tieKey.compareTo(b.tieKey);
    }

    private static void produceFifo(Map<ComponentStackKey, Deque<FinalBatch>> pool, ComponentStackKey rk, long amt, String stepId) {
        if (amt <= 0L) return;
        pool.computeIfAbsent(rk, ignored -> new ArrayDeque<>()).addLast(new FinalBatch(stepId, amt));
    }

    private static long totalFifo(
        Map<ComponentStackKey, Deque<FinalBatch>> prod,
        Map<ComponentStackKey, Long> init,
        ComponentStackKey rk
    ) {
        long sum = init.getOrDefault(rk, 0L);
        Deque<FinalBatch> q = prod.get(rk);
        if (q != null) {
            for (FinalBatch b : q) sum = SafeMath.addSaturating(sum, b.amount);
        }
        return sum;
    }

    private static boolean consumeFifo(
        Map<ComponentStackKey, Deque<FinalBatch>> prod,
        Map<ComponentStackKey, Long> init,
        ComponentStackKey rk,
        long need
    ) {
        if (need <= 0L) return true;
        if (totalFifo(prod, init, rk) < need) return false;
        long rem = need;
        Deque<FinalBatch> q = prod.get(rk);
        if (q != null) {
            while (rem > 0L && !q.isEmpty()) {
                FinalBatch head = q.peekFirst();
                long take = Math.min(rem, head.amount);
                head.amount -= take;
                rem -= take;
                if (head.amount == 0L) q.pollFirst();
            }
        }
        if (rem > 0L) {
            long have = init.getOrDefault(rk, 0L);
            if (have <= rem) init.remove(rk);
            else init.put(rk, have - rem);
        }
        return true;
    }

    private static final class FinalBatch {
        final String stepId;
        long amount;

        FinalBatch(String stepId, long amount) {
            this.stepId = stepId;
            this.amount = amount;
        }
    }
}
