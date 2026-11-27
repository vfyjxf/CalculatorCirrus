package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;

public record AcquireStep(
    String stepId,
    List<String> dependsOnStepIds,
    ResourceKey resource,
    long amount,
    AcquisitionKind acquisitionKind,
    CostVector stepCost
) implements PlanStep {

    public AcquireStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(acquisitionKind, "acquisitionKind");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
