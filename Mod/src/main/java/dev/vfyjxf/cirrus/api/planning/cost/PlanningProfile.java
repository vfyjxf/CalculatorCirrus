package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;

public record PlanningProfile(
    String profileId,
    ObjectivePreset preset,
    CostWeights weights,
    PreferenceModifiers modifiers,
    double diversityThreshold,
    int maxMachineRecursionDepth
) {

    public PlanningProfile(
        String profileId,
        ObjectivePreset preset,
        CostWeights weights,
        double diversityThreshold,
        int maxMachineRecursionDepth
    ) {
        this(profileId, preset, weights, PreferenceModifiers.defaults, diversityThreshold, maxMachineRecursionDepth);
    }

    public PlanningProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(modifiers, "modifiers");
        if (!Double.isFinite(diversityThreshold) || diversityThreshold < 0.0 || diversityThreshold > 1.0) {
            throw new IllegalArgumentException("diversityThreshold must be in [0, 1], got " + diversityThreshold);
        }
        if (maxMachineRecursionDepth < 0) {
            throw new IllegalArgumentException("maxMachineRecursionDepth must be >= 0");
        }
    }

    public static PlanningProfile defaultAutoSmart() {
        return new PlanningProfile(
            "default_auto_smart",
            ObjectivePreset.autoSmart,
            CostWeights.forPreset(ObjectivePreset.autoSmart),
            PreferenceModifiers.defaults,
            0.25,
            3
        );
    }

    public static PlanningProfile defaultBalanced() {
        return defaultAutoSmart();
    }

    public static PlanningProfile forPreset(ObjectivePreset preset) {
        Objects.requireNonNull(preset, "preset");
        return new PlanningProfile(
            "preset_" + preset.name(),
            preset,
            CostWeights.forPreset(preset),
            PreferenceModifiers.defaults,
            0.25,
            3
        );
    }

    public PlanningProfile withModifiers(PreferenceModifiers newModifiers) {
        return new PlanningProfile(
            profileId,
            preset,
            weights,
            newModifiers,
            diversityThreshold,
            maxMachineRecursionDepth
        );
    }
}
