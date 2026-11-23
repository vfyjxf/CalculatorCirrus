package dev.vfyjxf.cirrus.planning.core.verify;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanVerifier;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class DefaultPlanVerifier implements PlanVerifier {

    private static final double COST_TOLERANCE = 1e-5;

    private final CompiledRecipeGraph graph;
    private final CostMetadataProvider costMetadata;

    public DefaultPlanVerifier(CompiledRecipeGraph graph) {
        this(graph, DefaultCostMetadataProvider.INSTANCE);
    }

    public DefaultPlanVerifier(CompiledRecipeGraph graph, CostMetadataProvider costMetadata) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.costMetadata = Objects.requireNonNull(costMetadata, "costMetadata");
    }

    @Override
    public VerificationCertificate verify(ProductionPlan plan, PlanningRequest request) {
        if (plan == null || request == null) {
            return new VerificationCertificate(
                    false, false, false, false, false, false,
                    List.of(ErrorCodes.ERR_MALFORMED_PLAN + ":null_input")
            );
        }
        try {
            return verifyInternal(plan, request);
        } catch (RuntimeException ex) {
            return new VerificationCertificate(
                    false, false, false, false, false, false,
                    List.of(ErrorCodes.ERR_MALFORMED_PLAN + ":" + ex.getClass().getSimpleName())
            );
        }
    }

    private VerificationCertificate verifyInternal(ProductionPlan plan, PlanningRequest request) {
        List<String> violations = new ArrayList<>();
        boolean[] flags = new boolean[]{true, true, true, true, true};
        // flags: 0=dag, 1=mass, 2=machines/catalysts, 3=batches, 4=surplus

        // 1. Unique step IDs and topological order.
        Map<String, PlanStep> byId = new LinkedHashMap<>();
        for (PlanStep s : plan.steps()) {
            if (s == null || s.stepId().isEmpty()) {
                violations.add(ErrorCodes.ERR_MALFORMED_PLAN + ":empty_step");
                flags[0] = false;
                continue;
            }
            if (byId.put(s.stepId(), s) != null) {
                violations.add(ErrorCodes.ERR_DUPLICATE_STEP_ID + ":" + s.stepId());
                flags[0] = false;
            }
        }
        Set<String> seen = new HashSet<>(byId.size() * 2);
        for (PlanStep s : plan.steps()) {
            if (s == null) continue;
            for (String dep : s.dependsOnStepIds()) {
                if (!byId.containsKey(dep)) {
                    violations.add(ErrorCodes.ERR_MISSING_DEPENDENCY + ":" + s.stepId() + "->" + dep);
                    flags[0] = false;
                } else if (!seen.contains(dep)) {
                    violations.add(ErrorCodes.ERR_TOPOLOGICAL_ORDER_VIOLATION + ":" + s.stepId() + "->" + dep);
                    flags[0] = false;
                }
            }
            seen.add(s.stepId());
        }

        // 2. Initialize two-pool FIFO ledger and facility state.
        PlanningContext ctx = request.context();
        FacilitySnapshot facilities = ctx.facilities();
        HumanGuidanceView guidance = ctx.guidance();
        AcquisitionOracle oracle = ctx.acquisitionOracle();

        Map<ResourceKey, Long> initialBefore = new TreeMap<>(ctx.inventory().allUsable());
        TwoPoolLedger ledger = new TwoPoolLedger(initialBefore);

        Set<String> ownedMachines = new HashSet<>();
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : facilities.ownedMachines()) {
            if (g.count() > 0) {
                ownedMachines.add(g.machineId());
                ownedCaps.add(g.capability());
            }
        }
        Set<String> automatedSystems = new HashSet<>();
        Map<ResourceKey, Set<String>> dynamicLinesByOutput = new HashMap<>();

        Map<ResourceKey, Long> actualAcquired = new TreeMap<>();
        Map<String, Integer> actualProvisioned = new TreeMap<>();
        Set<String> actualLinesUsed = new TreeSet<>();
        Set<ResourceKey> actualCatalysts = new TreeSet<>();

        // Provenance: resource -> compiled recipe keys that contributed to it (for CycleBreakRule).
        Map<ResourceKey, Set<CompiledRecipeKey>> provenance = new HashMap<>();
        // Directed single-input conversions for plan-level round-trip detection.
        Set<ConversionPair> conversions = new HashSet<>();

        // 3. Step-by-step simulation.
        for (PlanStep step : plan.steps()) {
            if (step == null) continue;
            switch (step) {
                case AcquireStep a -> {
                    if (a.amount() <= 0L) {
                        violations.add(ErrorCodes.ERR_NON_POSITIVE_ACQUIRE + ":" + a.stepId());
                        flags[3] = false;
                        break;
                    }
                    if (guidance.isResourceBanned(a.resource())) {
                        violations.add(ErrorCodes.ERR_BANNED_RESOURCE + ":" + a.resource().shortString());
                        flags[1] = false;
                    }
                    boolean acquirable = guidance.isResourceTreatedAsBase(a.resource())
                            || guidance.overriddenUnitCost(a.resource()).isPresent()
                            || oracle.assess(a.resource(), a.amount(), ctx.progression()).isPresent();
                    if (!acquirable) {
                        violations.add(ErrorCodes.ERR_UNACQUIRABLE_RESOURCE + ":" + a.resource().shortString());
                        flags[1] = false;
                    }
                    if (SafeMath.willOverflowAdd(ledger.totalOf(a.resource()), a.amount())) {
                        violations.add(ErrorCodes.ERR_STOCK_OVERFLOW + ":" + a.stepId());
                        flags[1] = false;
                    }
                    ledger.produce(a.resource(), a.amount(), a.stepId());
                    actualAcquired.merge(a.resource(), a.amount(), SafeMath::addSaturating);
                }
                case LinePullStep lp -> {
                    if (lp.amount() <= 0L) {
                        violations.add(ErrorCodes.ERR_NON_POSITIVE_PULL + ":" + lp.stepId());
                        flags[3] = false;
                        break;
                    }
                    boolean existsInFacilities = false;
                    for (AutomatedLine line : facilities.automatedLinesFor(lp.resource())) {
                        if (line.lineId().equals(lp.lineId())) {
                            existsInFacilities = true;
                            break;
                        }
                    }
                    Set<String> dyn = dynamicLinesByOutput.get(lp.resource());
                    boolean existsDynamic = dyn != null && dyn.contains(lp.lineId());
                    if (!existsInFacilities && !existsDynamic) {
                        violations.add(ErrorCodes.ERR_UNKNOWN_LINE + ":" + lp.lineId());
                        flags[2] = false;
                    }
                    if (SafeMath.willOverflowAdd(ledger.totalOf(lp.resource()), lp.amount())) {
                        violations.add(ErrorCodes.ERR_STOCK_OVERFLOW + ":" + lp.stepId());
                        flags[1] = false;
                    }
                    ledger.produce(lp.resource(), lp.amount(), lp.stepId());
                    actualLinesUsed.add(lp.lineId());
                }
                case ProvisionMachineStep pm -> {
                    if (pm.countToBuild() <= 0) {
                        violations.add(ErrorCodes.ERR_NON_POSITIVE_MACHINE_COUNT + ":" + pm.stepId());
                        flags[3] = false;
                        break;
                    }
                    if (!guidance.isWillingToBuild(pm.machineId())) {
                        violations.add(ErrorCodes.ERR_BANNED_MACHINE_BUILD + ":" + pm.machineId());
                        flags[2] = false;
                    }
                    if (pm.craftedByPredecessorSteps()) {
                        ResourceKey mk = ResourceKey.item(pm.machineId());
                        if (!ledger.consume(mk, pm.countToBuild())) {
                            violations.add(ErrorCodes.ERR_INSUFFICIENT_MACHINE_ITEM + ":" + pm.machineId());
                            flags[1] = false;
                        }
                    }
                    ownedMachines.add(pm.machineId());
                    pm.satisfiedCapability().ifPresent(ownedCaps::add);
                    actualProvisioned.merge(pm.machineId(), pm.countToBuild(), Integer::sum);
                }
                case SetupLineStep sl -> {
                    if (!guidance.isWillingToBuild(sl.primaryMachineId())) {
                        violations.add(ErrorCodes.ERR_BANNED_MACHINE_BUILD + ":" + sl.primaryMachineId());
                        flags[2] = false;
                    }
                    ownedMachines.add(sl.primaryMachineId());
                    ownedCaps.add(MachineCapabilityGroups.capabilityOf(sl.systemId()));
                    automatedSystems.add(sl.systemId());
                    dynamicLinesByOutput.computeIfAbsent(sl.targetOutput(), ignored -> new HashSet<>())
                            .add("setup:" + sl.stepId());
                }
                case CraftStep cs -> {
                    verifyCraft(cs, ledger, ownedMachines, ownedCaps, automatedSystems,
                            facilities, guidance, violations, flags, actualCatalysts, provenance, conversions, true);
                }
                case ConvertStep conv -> {
                    verifyConvert(conv, ledger, facilities, guidance, violations, flags, provenance, conversions);
                }
                case CycleLoopStep loop -> {
                    verifyCycleLoop(loop, ledger, ownedMachines, ownedCaps, automatedSystems,
                            facilities, guidance, violations, flags, actualCatalysts, provenance);
                }
            }
        }

        // 4. Plan-level round-trip check.
        for (ConversionPair pair : conversions) {
            if (conversions.contains(new ConversionPair(pair.to, pair.from))) {
                violations.add(ErrorCodes.ERR_ROUND_TRIP + ":" + pair.from.shortString() + "<->" + pair.to.shortString());
                flags[1] = false;
                break;
            }
        }

        // 5. Goal satisfaction + CycleBreakRule provenance check.
        Map<ResourceKey, Long> mergedTargets = new TreeMap<>();
        for (TargetDemand td : request.targets()) {
            mergedTargets.merge(td.resource(), td.amount(), SafeMath::addSaturating);
        }
        for (var e : mergedTargets.entrySet()) {
            ResourceKey targetRes = e.getKey();
            long need = e.getValue();
            Set<CompiledRecipeKey> contributors = provenance.get(targetRes);
            if (contributors != null) {
                for (CompiledRecipeKey rk : contributors) {
                    if (guidance.isRecipeBannedForTarget(targetRes, rk)) {
                        violations.add(ErrorCodes.ERR_BANNED_RECIPE_FOR_TARGET + ":" + targetRes.shortString() + ":" + rk.shortString());
                        flags[1] = false;
                    }
                }
            }
            if (!ledger.consume(targetRes, need)) {
                violations.add(ErrorCodes.ERR_GOAL_NOT_MET + ":" + targetRes.shortString()
                        + " have=" + ledger.totalOf(targetRes) + " need=" + need);
                flags[1] = false;
            }
        }

        // 6. Strict two-pool FIFO surplus check.
        Map<ResourceKey, Long> actualSurplus = ledger.remainingProducedTotals();
        Map<ResourceKey, Long> declaredSurplus = new TreeMap<>();
        for (ByproductEntry bp : plan.surplusByproducts()) {
            if (!byId.containsKey(bp.producedByStepId())) {
                violations.add(ErrorCodes.ERR_SURPLUS_MISMATCH + ":unknown_producer:" + bp.producedByStepId());
                flags[4] = false;
            }
            declaredSurplus.merge(bp.resource(), bp.amount(), SafeMath::addSaturating);
        }
        if (!actualSurplus.equals(declaredSurplus)) {
            Set<ResourceKey> union = new TreeSet<>(actualSurplus.keySet());
            union.addAll(declaredSurplus.keySet());
            for (ResourceKey rk : union) {
                long act = actualSurplus.getOrDefault(rk, 0L);
                long dec = declaredSurplus.getOrDefault(rk, 0L);
                if (act != dec) {
                    violations.add(ErrorCodes.ERR_SURPLUS_MISMATCH + ":" + rk.shortString()
                            + " declared=" + dec + " actual=" + act);
                    flags[4] = false;
                }
            }
        }

        // 7. PreparationSummary consistency check.
        PreparationSummary prep = plan.preparation();
        Map<ResourceKey, Long> actualConsumedInv = ledger.consumedFromInitial(initialBefore);
        if (!actualConsumedInv.equals(prep.consumedFromInventory().entries())
                || !actualAcquired.equals(prep.toAcquireFromWorld().entries())
                || !actualProvisioned.equals(prep.machinesToProvision())) {
            violations.add(ErrorCodes.ERR_PREPARATION_MISMATCH);
            flags[4] = false;
        }

        // 8. Total cost aggregation consistency check.
        CostVector recomputedTotal = PlanCosts.aggregate(plan.steps());
        if (!costsClose(recomputedTotal, plan.totalCost())) {
            violations.add(ErrorCodes.ERR_COST_MISMATCH + ":total");
            flags[4] = false;
        }

        boolean valid = violations.isEmpty() && flags[0] && flags[1] && flags[2] && flags[3] && flags[4];
        return new VerificationCertificate(valid, flags[0], flags[1], flags[2], flags[3], flags[4], violations);
    }

    private boolean verifyCraft(
            CraftStep cs,
            TwoPoolLedger ledger,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps,
            Set<String> automatedSystems,
            FacilitySnapshot facilities,
            HumanGuidanceView guidance,
            List<String> violations,
            boolean[] flags,
            Set<ResourceKey> actualCatalysts,
            Map<ResourceKey, Set<CompiledRecipeKey>> provenance,
            Set<ConversionPair> conversions,
            boolean recordConversion
    ) {
        if (cs.batches() <= 0L) {
            violations.add(ErrorCodes.ERR_NON_POSITIVE_BATCH + ":" + cs.stepId());
            flags[3] = false;
            return false;
        }
        Optional<CompiledRecipe> recOpt = graph.recipeByKey(cs.recipeKey());
        if (recOpt.isEmpty()) {
            violations.add(ErrorCodes.ERR_UNKNOWN_RECIPE + ":" + cs.recipeKey().shortString());
            flags[1] = false;
            return false;
        }
        CompiledRecipe rec = recOpt.get();
        if (guidance.isRecipeBanned(cs.recipeKey())) {
            violations.add(ErrorCodes.ERR_BANNED_RECIPE + ":" + cs.recipeKey().shortString());
            flags[1] = false;
        }
        String sysId = rec.key().systemId();
        SystemAdmission adm = graph.admissionOf(sysId);
        boolean unlocked = facilities.unlockedSystems().contains(sysId);
        if (!guidance.isSystemEnabled(sysId, adm, unlocked)) {
            violations.add(ErrorCodes.ERR_BANNED_SYSTEM + ":" + sysId);
            flags[2] = false;
        }

        MachineRequirement req = rec.machineRequirement();
        if (!req.handCraftable()) {
            boolean hasM = cs.chosenMachineId().map(ownedMachines::contains).orElse(false);
            boolean hasC = req.capabilityId().map(ownedCaps::contains).orElse(false);
            if (!hasM && !hasC) {
                violations.add(ErrorCodes.ERR_MACHINE_NOT_AVAILABLE + ":" + cs.stepId());
                flags[2] = false;
            }
        }
        if (cs.executedByAutomation()) {
            boolean hasAuto = facilities.automatedRecipe(cs.recipeKey()).isPresent()
                    || automatedSystems.contains(sysId);
            if (!hasAuto) {
                violations.add(ErrorCodes.ERR_AUTOMATION_UNAVAILABLE + ":" + cs.stepId());
                flags[2] = false;
            }
        }

        for (CatalystSlot cat : rec.catalysts()) {
            ResourceKey bound = cs.catalystBindings().get(cat.slotIndex());
            long minNeeded = (cat.durabilityCostPerBatch() > 0 && cat.maxDurability() > 0)
                    ? SafeMath.ceilDiv(SafeMath.mulSaturating(cs.batches(), cat.durabilityCostPerBatch()), cat.maxDurability())
                    : 1L;
            if (bound == null || !cat.candidates().contains(bound) || ledger.totalOf(bound) < minNeeded) {
                violations.add(ErrorCodes.ERR_MISSING_CATALYST + ":" + cs.stepId() + ":" + cat.slotIndex());
                flags[2] = false;
            } else {
                actualCatalysts.add(bound);
            }
        }

        return applyRecipeIO(cs.stepId(), rec, cs.batches(), cs.slotBindings(),
                ledger, guidance, violations, flags, provenance, conversions, recordConversion);
    }

    private void verifyConvert(
            ConvertStep conv,
            TwoPoolLedger ledger,
            FacilitySnapshot facilities,
            HumanGuidanceView guidance,
            List<String> violations,
            boolean[] flags,
            Map<ResourceKey, Set<CompiledRecipeKey>> provenance,
            Set<ConversionPair> conversions
    ) {
        if (conv.batches() <= 0L) {
            violations.add(ErrorCodes.ERR_NON_POSITIVE_BATCH + ":" + conv.stepId());
            flags[3] = false;
            return;
        }
        Optional<CompiledRecipe> recOpt = graph.recipeByKey(conv.conversionRecipeKey());
        if (recOpt.isEmpty()) {
            violations.add(ErrorCodes.ERR_UNKNOWN_RECIPE + ":" + conv.conversionRecipeKey().shortString());
            flags[1] = false;
            return;
        }
        CompiledRecipe rec = recOpt.get();
        if (!rec.isReversibleConversion()) {
            violations.add(ErrorCodes.ERR_NOT_REVERSIBLE_CONVERSION + ":" + conv.conversionRecipeKey().shortString());
            flags[1] = false;
        }
        if (guidance.isRecipeBanned(conv.conversionRecipeKey())) {
            violations.add(ErrorCodes.ERR_BANNED_RECIPE + ":" + conv.conversionRecipeKey().shortString());
            flags[1] = false;
        }
        String sysId = rec.key().systemId();
        if (!guidance.isSystemEnabled(sysId, graph.admissionOf(sysId), facilities.unlockedSystems().contains(sysId))) {
            violations.add(ErrorCodes.ERR_BANNED_SYSTEM + ":" + sysId);
            flags[2] = false;
        }
        applyRecipeIO(conv.stepId(), rec, conv.batches(), conv.slotBindings(),
                ledger, guidance, violations, flags, provenance, conversions, true);
    }

    private boolean applyRecipeIO(
            String stepId,
            CompiledRecipe rec,
            long batches,
            Map<Integer, SlotBinding> bindings,
            TwoPoolLedger ledger,
            HumanGuidanceView guidance,
            List<String> violations,
            boolean[] flags,
            Map<ResourceKey, Set<CompiledRecipeKey>> provenance,
            Set<ConversionPair> conversions,
            boolean recordConversion
    ) {
        boolean ok = true;
        Set<CompiledRecipeKey> inheritedProv = new HashSet<>();
        inheritedProv.add(rec.key());
        ResourceKey singleInputChosen = null;

        for (InputSlot slot : rec.inputs()) {
            SlotBinding b = bindings.get(slot.slotIndex());
            if (b == null || !matchesSlot(slot, b.chosenResource())) {
                violations.add(ErrorCodes.ERR_INVALID_SLOT_BINDING + ":" + stepId + ":" + slot.slotIndex());
                flags[1] = false;
                ok = false;
                continue;
            }
            if (guidance.isResourceBanned(b.chosenResource())) {
                violations.add(ErrorCodes.ERR_BANNED_RESOURCE + ":" + b.chosenResource().shortString());
                flags[1] = false;
                ok = false;
            }
            if (SafeMath.willOverflowMul(batches, slot.amountPerBatch())) {
                violations.add(ErrorCodes.ERR_STOCK_OVERFLOW + ":" + stepId);
                flags[1] = false;
                ok = false;
                continue;
            }
            long need = batches * slot.amountPerBatch();
            if (!ledger.consume(b.chosenResource(), need)) {
                violations.add(ErrorCodes.ERR_NEGATIVE_STOCK + ":" + stepId + ":" + b.chosenResource().shortString());
                flags[1] = false;
                ok = false;
            }
            Set<CompiledRecipeKey> inProv = provenance.get(b.chosenResource());
            if (inProv != null) {
                inheritedProv.addAll(inProv);
            }
            if (rec.inputs().size() == 1) {
                singleInputChosen = b.chosenResource();
            }
        }

        for (OutputSlot out : rec.outputs()) {
            if (SafeMath.willOverflowMul(batches, out.amountPerBatch())
                    || SafeMath.willOverflowAdd(ledger.totalOf(out.resource()), batches * out.amountPerBatch())) {
                violations.add(ErrorCodes.ERR_STOCK_OVERFLOW + ":" + stepId);
                flags[1] = false;
                ok = false;
                continue;
            }
            long produced = batches * out.amountPerBatch();
            ledger.produce(out.resource(), produced, stepId);
            provenance.computeIfAbsent(out.resource(), ignored -> new HashSet<>()).addAll(inheritedProv);
            if (recordConversion && singleInputChosen != null && rec.outputs().size() == 1
                    && !singleInputChosen.equals(out.resource())) {
                conversions.add(new ConversionPair(singleInputChosen, out.resource()));
            }
        }
        return ok;
    }

    /** O(|singleIterationTemplate|) mathematical induction check with per-slot peakDraw sampling. */
    private void verifyCycleLoop(
            CycleLoopStep loop,
            TwoPoolLedger ledger,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps,
            Set<String> automatedSystems,
            FacilitySnapshot facilities,
            HumanGuidanceView guidance,
            List<String> violations,
            boolean[] flags,
            Set<ResourceKey> actualCatalysts,
            Map<ResourceKey, Set<CompiledRecipeKey>> provenance
    ) {
        if (loop.iterations() <= 0L || loop.singleIterationTemplate().isEmpty()) {
            violations.add(ErrorCodes.ERR_INVALID_CYCLE_ITER + ":" + loop.stepId());
            flags[3] = false;
            return;
        }
        if (!graph.allCycleMacros().isEmpty()) {
            Optional<CompiledCycleMacro> macroOpt = graph.cycleMacroByKey(loop.macroKey());
            if (macroOpt.isEmpty()) {
                violations.add(ErrorCodes.ERR_UNKNOWN_CYCLE_MACRO + ":" + loop.macroKey().macroId());
                flags[1] = false;
            } else {
                CompiledCycleMacro macro = macroOpt.get();
                if (macro.innerRecipes().size() != loop.singleIterationTemplate().size()) {
                    violations.add(ErrorCodes.ERR_UNKNOWN_CYCLE_MACRO + ":template_size:" + loop.macroKey().macroId());
                    flags[1] = false;
                } else {
                    for (int i = 0; i < macro.innerRecipes().size(); i++) {
                        CraftStep inner = loop.singleIterationTemplate().get(i);
                        if (!inner.recipeKey().equals(macro.innerRecipes().get(i).key())
                                || inner.batches() != macro.innerMultipliers().get(i)) {
                            violations.add(ErrorCodes.ERR_UNKNOWN_CYCLE_MACRO + ":template_mismatch:" + loop.macroKey().macroId());
                            flags[1] = false;
                            break;
                        }
                    }
                }
            }
        }

        for (var e : loop.workingSeedAmounts().entrySet()) {
            if (e.getValue() <= 0L || ledger.totalOf(e.getKey()) < e.getValue()) {
                violations.add(ErrorCodes.ERR_MISSING_CYCLE_SEED + ":" + loop.stepId() + ":" + e.getKey().shortString());
                flags[1] = false;
            }
        }

        // Sandbox simulation of 1 iteration sampling peakDraw after every input consumption.
        Map<ResourceKey, Long> beforeMap = ledger.snapshotTotals();
        Map<ResourceKey, Long> sim = new TreeMap<>(beforeMap);
        Map<ResourceKey, Long> peakDraw = new HashMap<>();
        Set<ResourceKey> touched = new TreeSet<>(loop.netDeltaPerIteration().keySet());
        Set<CompiledRecipeKey> loopProv = new HashSet<>();

        for (CraftStep inner : loop.singleIterationTemplate()) {
            loopProv.add(inner.recipeKey());
            if (inner.batches() <= 0L) {
                violations.add(ErrorCodes.ERR_NON_POSITIVE_BATCH + ":" + inner.stepId());
                flags[3] = false;
                continue;
            }
            Optional<CompiledRecipe> recOpt = graph.recipeByKey(inner.recipeKey());
            if (recOpt.isEmpty()) {
                violations.add(ErrorCodes.ERR_UNKNOWN_RECIPE + ":" + inner.recipeKey().shortString());
                flags[1] = false;
                continue;
            }
            CompiledRecipe rec = recOpt.get();
            if (guidance.isRecipeBanned(inner.recipeKey())) {
                violations.add(ErrorCodes.ERR_BANNED_RECIPE + ":" + inner.recipeKey().shortString());
                flags[1] = false;
            }
            String sysId = rec.key().systemId();
            if (!guidance.isSystemEnabled(sysId, graph.admissionOf(sysId), facilities.unlockedSystems().contains(sysId))) {
                violations.add(ErrorCodes.ERR_BANNED_SYSTEM + ":" + sysId);
                flags[2] = false;
            }
            MachineRequirement req = rec.machineRequirement();
            if (!req.handCraftable()) {
                boolean hasM = inner.chosenMachineId().map(ownedMachines::contains).orElse(false);
                boolean hasC = req.capabilityId().map(ownedCaps::contains).orElse(false);
                if (!hasM && !hasC) {
                    violations.add(ErrorCodes.ERR_MACHINE_NOT_AVAILABLE + ":" + inner.stepId());
                    flags[2] = false;
                }
            }
            for (CatalystSlot cat : rec.catalysts()) {
                ResourceKey bound = inner.catalystBindings().get(cat.slotIndex());
                long minNeeded = (cat.durabilityCostPerBatch() > 0 && cat.maxDurability() > 0)
                        ? SafeMath.ceilDiv(SafeMath.mulSaturating(inner.batches(), cat.durabilityCostPerBatch()), cat.maxDurability())
                        : 1L;
                if (bound == null || !cat.candidates().contains(bound) || sim.getOrDefault(bound, 0L) < minNeeded) {
                    violations.add(ErrorCodes.ERR_MISSING_CATALYST + ":" + inner.stepId() + ":" + cat.slotIndex());
                    flags[2] = false;
                } else {
                    actualCatalysts.add(bound);
                }
            }
            for (InputSlot slot : rec.inputs()) {
                SlotBinding b = inner.slotBindings().get(slot.slotIndex());
                if (b == null || !matchesSlot(slot, b.chosenResource())) {
                    violations.add(ErrorCodes.ERR_INVALID_SLOT_BINDING + ":" + inner.stepId() + ":" + slot.slotIndex());
                    flags[1] = false;
                    continue;
                }
                if (guidance.isResourceBanned(b.chosenResource())) {
                    violations.add(ErrorCodes.ERR_BANNED_RESOURCE + ":" + b.chosenResource().shortString());
                    flags[1] = false;
                }
                long need = SafeMath.mulSaturating(inner.batches(), slot.amountPerBatch());
                ResourceKey rk = b.chosenResource();
                touched.add(rk);
                long cur = sim.getOrDefault(rk, 0L);
                if (cur < need) {
                    violations.add(ErrorCodes.ERR_NEGATIVE_STOCK + ":" + inner.stepId() + ":" + rk.shortString());
                    flags[1] = false;
                }
                long after = cur - need;
                sim.put(rk, after);
                long draw = beforeMap.getOrDefault(rk, 0L) - after;
                if (draw > peakDraw.getOrDefault(rk, 0L)) {
                    peakDraw.put(rk, draw);
                }
            }
            for (OutputSlot out : rec.outputs()) {
                ResourceKey rk = out.resource();
                touched.add(rk);
                long prod = SafeMath.mulSaturating(inner.batches(), out.amountPerBatch());
                sim.put(rk, SafeMath.addSaturating(sim.getOrDefault(rk, 0L), prod));
            }
        }

        // Bidirectional A16 delta check + N-iteration induction.
        long iters = loop.iterations();
        for (ResourceKey rk : touched) {
            long before = beforeMap.getOrDefault(rk, 0L);
            long actualDelta = sim.getOrDefault(rk, 0L) - before;
            long declaredDelta = loop.netDeltaPerIteration().getOrDefault(rk, 0L);
            if (actualDelta != declaredDelta) {
                violations.add(ErrorCodes.ERR_CYCLE_DELTA_MISMATCH + ":" + loop.stepId() + ":" + rk.shortString()
                        + " actual=" + actualDelta + " declared=" + declaredDelta);
                flags[1] = false;
            }
            long peak = peakDraw.getOrDefault(rk, 0L);
            if (actualDelta >= 0L) {
                if (before < peak) {
                    violations.add(ErrorCodes.ERR_CYCLE_INSUFFICIENT_REAGENT + ":" + loop.stepId() + ":" + rk.shortString());
                    flags[1] = false;
                }
                if (actualDelta > 0L) {
                    if (SafeMath.willOverflowMul(iters, actualDelta)) {
                        violations.add(ErrorCodes.ERR_STOCK_OVERFLOW + ":" + loop.stepId());
                        flags[1] = false;
                    } else {
                        ledger.produce(rk, iters * actualDelta, loop.stepId());
                        provenance.computeIfAbsent(rk, ignored -> new HashSet<>()).addAll(loopProv);
                    }
                }
            } else {
                long negDelta = -actualDelta;
                long neededTotal = SafeMath.addSaturating(SafeMath.mulSaturating(iters - 1L, negDelta), peak);
                if (before < neededTotal) {
                    violations.add(ErrorCodes.ERR_CYCLE_INSUFFICIENT_REAGENT + ":" + loop.stepId() + ":" + rk.shortString());
                    flags[1] = false;
                }
                long totalConsume = SafeMath.mulSaturating(iters, negDelta);
                ledger.consume(rk, totalConsume);
            }
        }
    }

    private static boolean matchesSlot(InputSlot slot, ResourceKey chosen) {
        if (slot.matchMode() == SlotMatchMode.IDENTITY_AND_COMPONENT) {
            return slot.candidates().contains(chosen);
        }
        for (ResourceKey c : slot.candidates()) {
            if (c.sameBaseIdentity(chosen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean costsClose(CostVector a, CostVector b) {
        return Math.abs(a.humanActiveSeconds() - b.humanActiveSeconds()) <= COST_TOLERANCE
                && Math.abs(a.passiveWaitSeconds() - b.passiveWaitSeconds()) <= COST_TOLERANCE
                && Math.abs(a.machineSetupCapital() - b.machineSetupCapital()) <= COST_TOLERANCE
                && Math.abs(a.materialScarcity() - b.materialScarcity()) <= COST_TOLERANCE
                && Math.abs(a.cognitiveFriction() - b.cognitiveFriction()) <= COST_TOLERANCE;
    }

    private record ConversionPair(ResourceKey from, ResourceKey to) {
    }

    private static final class ProducedBatch {
        final String stepId;
        long amount;

        ProducedBatch(String stepId, long amount) {
            this.stepId = stepId;
            this.amount = amount;
        }
    }

    private static final class TwoPoolLedger {

        private final Map<ResourceKey, Long> initialPool;
        private final Map<ResourceKey, Deque<ProducedBatch>> producedPool = new TreeMap<>();
        private final Map<ResourceKey, Long> producedTotals = new TreeMap<>();

        TwoPoolLedger(Map<ResourceKey, Long> initial) {
            this.initialPool = new TreeMap<>(initial);
        }

        long totalOf(ResourceKey key) {
            return SafeMath.addSaturating(initialPool.getOrDefault(key, 0L), producedTotals.getOrDefault(key, 0L));
        }

        void produce(ResourceKey key, long amount, String stepId) {
            if (amount <= 0L) return;
            producedPool.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(new ProducedBatch(stepId, amount));
            producedTotals.merge(key, amount, SafeMath::addSaturating);
        }

        /** Consumes produced pool FIFO first, then initial stock. */
        boolean consume(ResourceKey key, long amount) {
            if (amount <= 0L) return true;
            long have = totalOf(key);
            if (have < amount) {
                return false;
            }
            long rem = amount;
            Deque<ProducedBatch> q = producedPool.get(key);
            if (q != null && !q.isEmpty()) {
                long takenFromProduced = 0L;
                while (rem > 0L && !q.isEmpty()) {
                    ProducedBatch head = q.peekFirst();
                    long take = Math.min(rem, head.amount);
                    head.amount -= take;
                    rem -= take;
                    takenFromProduced += take;
                    if (head.amount == 0L) {
                        q.pollFirst();
                    }
                }
                long newProdTotal = producedTotals.getOrDefault(key, 0L) - takenFromProduced;
                if (newProdTotal <= 0L) {
                    producedTotals.remove(key);
                } else {
                    producedTotals.put(key, newProdTotal);
                }
            }
            if (rem > 0L) {
                long init = initialPool.getOrDefault(key, 0L);
                long left = init - rem;
                if (left <= 0L) {
                    initialPool.remove(key);
                } else {
                    initialPool.put(key, left);
                }
            }
            return true;
        }

        Map<ResourceKey, Long> snapshotTotals() {
            TreeMap<ResourceKey, Long> snap = new TreeMap<>(initialPool);
            for (var e : producedTotals.entrySet()) {
                snap.merge(e.getKey(), e.getValue(), SafeMath::addSaturating);
            }
            return snap;
        }

        Map<ResourceKey, Long> remainingProducedTotals() {
            TreeMap<ResourceKey, Long> out = new TreeMap<>();
            for (var e : producedTotals.entrySet()) {
                if (e.getValue() > 0L) {
                    out.put(e.getKey(), e.getValue());
                }
            }
            return out;
        }

        Map<ResourceKey, Long> consumedFromInitial(Map<ResourceKey, Long> initialBefore) {
            TreeMap<ResourceKey, Long> consumed = new TreeMap<>();
            for (var e : initialBefore.entrySet()) {
                long rem = initialPool.getOrDefault(e.getKey(), 0L);
                long used = e.getValue() - rem;
                if (used > 0L) {
                    consumed.put(e.getKey(), used);
                }
            }
            return consumed;
        }
    }
}
