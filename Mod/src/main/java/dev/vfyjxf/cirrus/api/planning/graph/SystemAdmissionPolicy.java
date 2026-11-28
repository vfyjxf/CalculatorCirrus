package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;

public interface SystemAdmissionPolicy {

    SystemAdmission classify(Namespace systemId, String sourceKind);

    MachineCapabilityId capabilityOf(Namespace systemId);

    List<MachineEntry> orderMachinesForCapability(Namespace systemId, List<MachineEntry> rawMachines);

    default SystemAdmission classifyRecipe(RawRecipeRecord record) {
        return classify(record.systemId(), record.sourceKind());
    }

    default RecipeAdvisory advisoryOf(RawRecipeRecord record) {
        return RecipeAdvisory.none;
    }
}
