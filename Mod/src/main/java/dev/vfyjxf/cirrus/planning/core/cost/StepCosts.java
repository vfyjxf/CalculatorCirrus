package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedRecipeCapability;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;

import java.util.List;
import java.util.Optional;

public final class StepCosts {

    public static final CostVector FREE_BASE_COST = new CostVector(0.0, 0.0, 0.0, 0.0, 0.1);
    public static final CostVector CONVERT_COST = new CostVector(1.0, 0.0, 0.0, 0.0, 0.5);

    private StepCosts() {
    }

    public static CostVector acquireCost(AcquisitionOption opt, long quantity, boolean treatedAsFreeBase) {
        if (treatedAsFreeBase || opt.kind() == AcquisitionKind.PLAYER_DECLARED_FREE) {
            return FREE_BASE_COST;
        }
        double q = Math.max(0L, quantity);
        double h = opt.fixedTripHumanSeconds() + q * opt.humanSecondsPerUnit();
        double p = q * opt.passiveSecondsPerUnit();
        double s = q * opt.scarcityPerUnit();
        return new CostVector(h, p, 0.0, s, 1.5);
    }

    public static CostVector acquireUnitEstimate(AcquisitionOption opt, long demandScale, boolean treatedAsFreeBase) {
        if (treatedAsFreeBase || opt.kind() == AcquisitionKind.PLAYER_DECLARED_FREE) {
            return FREE_BASE_COST;
        }
        double scale = Math.max(1L, demandScale);
        double h = opt.fixedTripHumanSeconds() / scale + opt.humanSecondsPerUnit();
        double p = opt.passiveSecondsPerUnit();
        double s = opt.scarcityPerUnit();
        return new CostVector(h, p, 0.0, s, 1.5 / scale);
    }

    public static CostVector linePullCost(AutomatedLine line, long quantity) {
        long unbuffered = Math.max(0L, quantity - line.bufferedStock());
        double ratePerSec = line.throughputPerMinute() / 60.0;
        double p = unbuffered > 0L && ratePerSec > 0.0 ? unbuffered / ratePerSec : 0.0;
        return new CostVector(line.humanAttentionSecondsPerPull(), p, 0.0, 0.0, 0.2);
    }

    public static CostVector craftCost(
            CompiledRecipe recipe,
            long batches,
            Optional<String> chosenMachineId,
            boolean executedByAutomation,
            FacilitySnapshot facilities,
            CostMetadataProvider meta,
            HumanGuidanceView guidance
    ) {
        String systemId = recipe.key().systemId();
        double softMult = guidance != null ? guidance.softCostMultiplier(recipe.key(), chosenMachineId) : 1.0;

        if (executedByAutomation) {
            Optional<AutomatedRecipeCapability> autoCap = facilities.automatedRecipe(recipe.key());
            double passivePerBatch = autoCap.map(AutomatedRecipeCapability::passiveSecondsPerBatch)
                    .orElseGet(() -> meta.estimatePassiveSecondsPerBatch(systemId, chosenMachineId, recipe.recipeValues()));
            int parallel = autoCap.map(AutomatedRecipeCapability::parallelCrafters).orElse(1);
            long waves = SafeMath.ceilDiv(batches, Math.max(1, parallel));
            double p = waves * passivePerBatch;
            return new CostVector(0.5 * softMult, p, 0.0, 0.0, 0.3 * softMult);
        }

        OwnedMachineGroup ownedGroup = resolveOwnedGroup(recipe, chosenMachineId, facilities);
        double hBase;
        double pBase;
        double passivePerBatch = meta.estimatePassiveSecondsPerBatch(systemId, chosenMachineId, recipe.recipeValues());
        if (ownedGroup != null && ownedGroup.automatedFeeding()) {
            hBase = 1.0;
        } else {
            double hPerBatch = meta.estimateHumanSecondsPerBatch(systemId, chosenMachineId, false);
            long effBatches = Math.min(batches, 64L);
            double factor = effBatches > 1L ? (1.0 + Math.log(effBatches)) / effBatches : 1.0;
            hBase = effBatches * hPerBatch * factor;
        }
        int machineCount = ownedGroup != null ? Math.max(1, ownedGroup.count()) : 1;
        double speed = ownedGroup != null ? ownedGroup.passiveSpeedMultiplier() : 1.0;
        long waves = SafeMath.ceilDiv(batches, machineCount);
        pBase = (waves * passivePerBatch) / speed;
        return new CostVector(hBase * softMult, pBase, 0.0, 0.0, 1.0 * softMult);
    }

    public static CostVector provisionMachineCost(
            String machineId,
            String systemId,
            int countToBuild,
            CostMetadataProvider meta
    ) {
        int c = Math.max(1, countToBuild);
        double h = c * meta.estimateMachineSetupHumanSeconds(machineId, systemId);
        double k = c * meta.estimateMachineSetupCapital(machineId, systemId);
        return new CostVector(h, 0.0, k, 0.0, 3.0 * c);
    }

    public static CostVector setupLineCost(
            String systemId,
            MachineCapabilityId capability,
            CostMetadataProvider meta
    ) {
        double h = meta.estimateLineSetupHumanSeconds(systemId, capability);
        double k = meta.estimateLineSetupCapital(systemId, capability);
        return new CostVector(h, 0.0, k, 0.0, 3.0);
    }

    public static CostVector cycleLoopCost(List<CraftStep> singleIterationTemplate, long iterations) {
        CostVector oneIter = CostVector.ZERO;
        for (CraftStep step : singleIterationTemplate) {
            oneIter = oneIter.addSerial(step.stepCost());
        }
        return oneIter.scaleLinear(Math.max(1L, iterations));
    }

    private static OwnedMachineGroup resolveOwnedGroup(
            CompiledRecipe recipe,
            Optional<String> chosenMachineId,
            FacilitySnapshot facilities
    ) {
        if (chosenMachineId.isPresent()) {
            String mid = chosenMachineId.get();
            for (OwnedMachineGroup g : facilities.ownedMachines()) {
                if (g.count() > 0 && g.machineId().equals(mid)) {
                    return g;
                }
            }
        }
        if (recipe.machineRequirement().capabilityId().isPresent()) {
            MachineCapabilityId cap = recipe.machineRequirement().capabilityId().get();
            OwnedMachineGroup best = null;
            for (OwnedMachineGroup g : facilities.ownedMachines()) {
                if (g.count() > 0 && g.capability().equals(cap)) {
                    if (best == null || g.passiveSpeedMultiplier() > best.passiveSpeedMultiplier()) {
                        best = g;
                    }
                }
            }
            return best;
        }
        return null;
    }
}
