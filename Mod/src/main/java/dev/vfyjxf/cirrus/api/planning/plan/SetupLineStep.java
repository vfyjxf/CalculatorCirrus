package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.List;
import java.util.Objects;

public record SetupLineStep(
    String stepId,
    List<String> dependsOnStepIds,
    Namespace systemId,
    MachineEntry primaryMachine,
    ComponentStackKey targetOutput,
    CostVector stepCost
) implements PlanStep {

    public SetupLineStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(primaryMachine, "primaryMachine");
        Objects.requireNonNull(targetOutput, "targetOutput");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
