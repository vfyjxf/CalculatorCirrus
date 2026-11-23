package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;

public record PlanningProfile(
        String profileId,
        ObjectivePreset preset,
        CostWeights weights,
        double diversityThreshold,
        int maxMachineRecursionDepth
) {

    public PlanningProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(weights, "weights");
        if (!Double.isFinite(diversityThreshold) || diversityThreshold < 0.0 || diversityThreshold > 1.0) {
            throw new IllegalArgumentException("diversityThreshold must be in [0, 1], got " + diversityThreshold);
        }
        if (maxMachineRecursionDepth < 0) {
            throw new IllegalArgumentException("maxMachineRecursionDepth must be >= 0");
        }
    }

    public static PlanningProfile defaultBalanced() {
        return new PlanningProfile("default_balanced", ObjectivePreset.BALANCED,
                CostWeights.forPreset(ObjectivePreset.BALANCED), 0.25, 3);
    }
}
