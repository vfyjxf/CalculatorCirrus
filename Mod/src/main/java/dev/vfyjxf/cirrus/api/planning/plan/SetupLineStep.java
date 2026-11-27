package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;

public record SetupLineStep(
    String stepId,
    List<String> dependsOnStepIds,
    String systemId,
    String primaryMachineId,
    ResourceKey targetOutput,
    CostVector stepCost
) implements PlanStep {

    public SetupLineStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(primaryMachineId, "primaryMachineId");
        Objects.requireNonNull(targetOutput, "targetOutput");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
