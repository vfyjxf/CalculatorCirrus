package dev.vfyjxf.cirrus.api.planning.cost;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CostMetadataProvider {

    SystemCostTier tierOfSystem(Namespace systemId, Set<Namespace> systemTags);

    default SystemCostTier tierOfSystem(Namespace systemId) {
        return tierOfSystem(systemId, Set.of());
    }

    double estimateHumanSecondsPerBatch(
        Namespace systemId,
        Set<Namespace> systemTags,
        Optional<MachineEntry> machine,
        boolean automated
    );

    default double estimateHumanSecondsPerBatch(Namespace systemId, Optional<MachineEntry> machine, boolean automated) {
        return estimateHumanSecondsPerBatch(systemId, Set.of(), machine, automated);
    }

    double estimatePassiveSecondsPerBatch(
        Namespace systemId,
        Set<Namespace> systemTags,
        Optional<MachineEntry> machine,
        Map<Namespace, Double> recipeValues
    );

    default double estimatePassiveSecondsPerBatch(
        Namespace systemId,
        Optional<MachineEntry> machine,
        Map<Namespace, Double> recipeValues
    ) {
        return estimatePassiveSecondsPerBatch(systemId, Set.of(), machine, recipeValues);
    }

    double estimateMachineSetupHumanSeconds(MachineEntry machine, Namespace systemId, Set<Namespace> systemTags);

    default double estimateMachineSetupHumanSeconds(MachineEntry machine, Namespace systemId) {
        return estimateMachineSetupHumanSeconds(machine, systemId, Set.of());
    }

    double estimateMachineSetupCapital(MachineEntry machine, Namespace systemId, Set<Namespace> systemTags);

    default double estimateMachineSetupCapital(MachineEntry machine, Namespace systemId) {
        return estimateMachineSetupCapital(machine, systemId, Set.of());
    }

    double estimateLineSetupHumanSeconds(Namespace systemId, Set<Namespace> systemTags, MachineCapabilityId capability);

    default double estimateLineSetupHumanSeconds(Namespace systemId, MachineCapabilityId capability) {
        return estimateLineSetupHumanSeconds(systemId, Set.of(), capability);
    }

    double estimateLineSetupCapital(Namespace systemId, Set<Namespace> systemTags, MachineCapabilityId capability);

    default double estimateLineSetupCapital(Namespace systemId, MachineCapabilityId capability) {
        return estimateLineSetupCapital(systemId, Set.of(), capability);
    }
}
