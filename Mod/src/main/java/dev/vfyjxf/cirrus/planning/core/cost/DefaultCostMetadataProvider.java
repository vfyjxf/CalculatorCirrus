package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.Map;
import java.util.Optional;

public final class DefaultCostMetadataProvider implements CostMetadataProvider {

    public static final DefaultCostMetadataProvider INSTANCE = new DefaultCostMetadataProvider();
    public static final String TIME_VALUE_KEY = "calculator_cirrus:time";

    public DefaultCostMetadataProvider() {
    }

    @Override
    public SystemCostTier tierOfSystem(String systemId) {
        return SystemCostTiers.tierOfSystem(systemId);
    }

    @Override
    public double estimateHumanSecondsPerBatch(String systemId, Optional<String> machineId, boolean automated) {
        if (automated) {
            return 0.5;
        }
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).humanSecondsPerBatch();
    }

    @Override
    public double estimatePassiveSecondsPerBatch(
            String systemId,
            Optional<String> machineId,
            Map<String, Double> recipeValues
    ) {
        if (recipeValues != null) {
            Double ticks = recipeValues.get(TIME_VALUE_KEY);
            if (ticks != null && Double.isFinite(ticks) && ticks >= 0.0) {
                return ticks / 20.0;
            }
        }
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).passiveSecondsPerBatch();
    }

    @Override
    public double estimateMachineSetupHumanSeconds(String machineId, String systemId) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).machineSetupHumanSeconds();
    }

    @Override
    public double estimateMachineSetupCapital(String machineId, String systemId) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).machineSetupCapital();
    }

    @Override
    public double estimateLineSetupHumanSeconds(String systemId, MachineCapabilityId capability) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).lineSetupHumanSeconds();
    }

    @Override
    public double estimateLineSetupCapital(String systemId, MachineCapabilityId capability) {
        return SystemCostTiers.defaultsFor(tierOfSystem(systemId)).lineSetupCapital();
    }
}
