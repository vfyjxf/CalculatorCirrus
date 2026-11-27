package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;

/**
 * 6-dimensional positive decision weights aligned with Planning v3 M1.3 / M5.
 * Includes frozen unit conversion factors (kC, kS, kF) mapping CU, SU, and steps into
 * base-equivalent seconds (s_e).
 */
public record CostWeights(
    double humanActiveWeight,
    double tripActiveWeight,
    double passiveWaitWeight,
    double machineSetupWeight,
    double materialScarcityWeight,
    double cognitiveFrictionWeight,
    double capitalToSeconds,
    double scarcityToSeconds,
    double stepToSeconds
) {

    public static final double defaultCapitalToSeconds = 1.0;
    public static final double defaultScarcityToSeconds = 1.0;
    public static final double defaultStepToSeconds = 2.0;

    public CostWeights(
        double humanActiveWeight,
        double tripActiveWeight,
        double passiveWaitWeight,
        double machineSetupWeight,
        double materialScarcityWeight,
        double cognitiveFrictionWeight
    ) {
        this(
            humanActiveWeight,
            tripActiveWeight,
            passiveWaitWeight,
            machineSetupWeight,
            materialScarcityWeight,
            cognitiveFrictionWeight,
            defaultCapitalToSeconds,
            defaultScarcityToSeconds,
            defaultStepToSeconds
        );
    }

    public CostWeights(
        double humanActiveWeight,
        double passiveWaitWeight,
        double machineSetupWeight,
        double materialScarcityWeight,
        double cognitiveFrictionWeight
    ) {
        this(
            humanActiveWeight,
            humanActiveWeight * 1.4,
            passiveWaitWeight,
            machineSetupWeight,
            materialScarcityWeight,
            cognitiveFrictionWeight,
            defaultCapitalToSeconds,
            defaultScarcityToSeconds,
            defaultStepToSeconds
        );
    }

    public CostWeights {
        requireNonNegFinite(humanActiveWeight, "humanActiveWeight");
        requireNonNegFinite(tripActiveWeight, "tripActiveWeight");
        requireNonNegFinite(passiveWaitWeight, "passiveWaitWeight");
        requireNonNegFinite(machineSetupWeight, "machineSetupWeight");
        requireNonNegFinite(materialScarcityWeight, "materialScarcityWeight");
        requireNonNegFinite(cognitiveFrictionWeight, "cognitiveFrictionWeight");
        requirePosFinite(capitalToSeconds, "capitalToSeconds");
        requirePosFinite(scarcityToSeconds, "scarcityToSeconds");
        requirePosFinite(stepToSeconds, "stepToSeconds");
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    private static void requirePosFinite(double v, String name) {
        if (!Double.isFinite(v) || v <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0, got " + v);
        }
    }

    /**
     * Converts a 6D physical CostVector into weighted base-equivalent decision seconds U(P).
     */
    public double scalarize(CostVector v) {
        return scalarizeWithAmortizedCapital(v, v.machineSetupCapital());
    }

    public double scalarizeWithAmortizedCapital(CostVector v, double effectiveCapitalUnits) {
        Objects.requireNonNull(v, "costVector");
        return humanActiveWeight * v.humanActiveSeconds()
                + tripActiveWeight * v.tripActiveSeconds()
                + passiveWaitWeight * v.passiveWaitSeconds()
                + machineSetupWeight * (effectiveCapitalUnits * capitalToSeconds)
                + materialScarcityWeight * (v.materialScarcity() * scarcityToSeconds)
                + cognitiveFrictionWeight * (v.cognitiveFriction() * stepToSeconds);
    }

    public CostWeights withEffectiveModifiers(
        ObjectivePreset preset,
        StageDistribution stage,
        PreferenceModifiers modifiers
    ) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(modifiers, "modifiers");

        double kStageMult = preset == ObjectivePreset.autoSmart
                ? stage.early() * 1.35 + stage.mid() * 1.00 + stage.late() * 0.75
                : 1.0;
        double wStageMult = preset == ObjectivePreset.autoSmart
                ? stage.early() * 1.20 + stage.mid() * 1.00 + stage.late() * 0.85
                : 1.0;

        double effTrip = tripActiveWeight * modifiers.fieldTripAversion();
        double effCap = (machineSetupWeight * kStageMult) / modifiers.newMachineTolerance();
        double effPassive = passiveWaitWeight * wStageMult;

        return new CostWeights(
            humanActiveWeight,
            effTrip,
            effPassive,
            effCap,
            materialScarcityWeight,
            cognitiveFrictionWeight,
            capitalToSeconds,
            scarcityToSeconds,
            stepToSeconds
        );
    }

    /** Frozen M5 preset weight matrix (base-equivalent seconds). */
    public static CostWeights forPreset(ObjectivePreset preset) {
        Objects.requireNonNull(preset, "preset");
        return switch (preset) {
            case autoSmart -> new CostWeights(1.00, 1.40, 0.25, 1.00, 0.80, 0.65);
            case stayAtBase -> new CostWeights(1.00, 4.00, 0.30, 0.90, 0.85, 0.65);
            case quickHandcraft -> new CostWeights(1.00, 2.00, 0.75, 1.80, 0.60, 0.80);
            case factoryScale -> new CostWeights(1.40, 1.40, 0.15, 0.55, 0.90, 0.45);
            case frugalMaterials -> new CostWeights(1.00, 1.25, 0.20, 0.95, 2.60, 0.60);
            case lowCognitive -> new CostWeights(1.00, 1.50, 0.25, 1.15, 0.80, 2.40);
        };
    }
}
