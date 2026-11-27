package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProvisionMachineStep(
    String stepId,
    List<String> dependsOnStepIds,
    String machineId,
    Optional<MachineCapabilityId> satisfiedCapability,
    int countToBuild,
    boolean craftedByPredecessorSteps,
    CostVector stepCost
) implements PlanStep {

    public ProvisionMachineStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(satisfiedCapability, "satisfiedCapability");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
