package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.List;

public interface SystemAdmissionPolicy {

    SystemAdmission classify(String systemId, String sourceKind);

    MachineCapabilityId capabilityOf(String systemId);

    List<String> orderMachinesForCapability(String systemId, List<String> rawMachines);

    default SystemAdmission classifyRecipe(RawRecipeRecord record) {
        return classify(record.systemId(), record.sourceKind());
    }

    default RecipeAdvisory advisoryOf(RawRecipeRecord record) {
        return RecipeAdvisory.NONE;
    }
}
