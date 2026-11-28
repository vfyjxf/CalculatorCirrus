package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;

import java.util.List;
import java.util.Objects;

public record AcquireStep(
    String stepId,
    List<String> dependsOnStepIds,
    ComponentStackKey resource,
    long amount,
    AcquisitionKind acquisitionKind,
    CostVector stepCost,
    String tripEpisodeId,
    double fixedTripHumanSeconds
) implements PlanStep {

    public AcquireStep(
        String stepId,
        List<String> dependsOnStepIds,
        ComponentStackKey resource,
        long amount,
        AcquisitionKind acquisitionKind,
        CostVector stepCost
    ) {
        this(stepId, dependsOnStepIds, resource, amount, acquisitionKind, stepCost, "", 0.0);
    }

    public AcquireStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(acquisitionKind, "acquisitionKind");
        Objects.requireNonNull(stepCost, "stepCost");
        Objects.requireNonNull(tripEpisodeId, "tripEpisodeId");
        if (!Double.isFinite(fixedTripHumanSeconds) || fixedTripHumanSeconds < 0.0) {
            throw new IllegalArgumentException("fixedTripHumanSeconds must be finite >= 0, got " + fixedTripHumanSeconds);
        }
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
    }
}
