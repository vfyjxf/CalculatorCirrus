package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;

import java.util.List;
import java.util.Objects;

public record LinePullStep(
    String stepId,
    List<String> dependsOnStepIds,
    String lineId,
    ComponentStackKey resource,
    long amount,
    CostVector stepCost
) implements PlanStep {

    public LinePullStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
