package dev.vfyjxf.cirrus.api.planning.cost;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.Map;
import java.util.Optional;

public interface CostMetadataProvider {

    SystemCostTier tierOfSystem(String systemId);

    double estimateHumanSecondsPerBatch(String systemId, Optional<String> machineId, boolean automated);

    double estimatePassiveSecondsPerBatch(String systemId, Optional<String> machineId, Map<String, Double> recipeValues);

    double estimateMachineSetupHumanSeconds(String machineId, String systemId);

    double estimateMachineSetupCapital(String machineId, String systemId);

    double estimateLineSetupHumanSeconds(String systemId, MachineCapabilityId capability);

    double estimateLineSetupCapital(String systemId, MachineCapabilityId capability);
}
