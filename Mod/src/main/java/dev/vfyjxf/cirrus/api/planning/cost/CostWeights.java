package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;

public record CostWeights(
        double humanActiveWeight,
        double passiveWaitWeight,
        double machineSetupWeight,
        double materialScarcityWeight,
        double cognitiveFrictionWeight
) {

    public CostWeights {
        requireNonNegFinite(humanActiveWeight, "humanActiveWeight");
        requireNonNegFinite(passiveWaitWeight, "passiveWaitWeight");
        requireNonNegFinite(machineSetupWeight, "machineSetupWeight");
        requireNonNegFinite(materialScarcityWeight, "materialScarcityWeight");
        requireNonNegFinite(cognitiveFrictionWeight, "cognitiveFrictionWeight");
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    public double scalarize(CostVector v) {
        Objects.requireNonNull(v, "costVector");
        return humanActiveWeight * v.humanActiveSeconds()
                + passiveWaitWeight * v.passiveWaitSeconds()
                + machineSetupWeight * v.machineSetupCapital()
                + materialScarcityWeight * v.materialScarcity()
                + cognitiveFrictionWeight * v.cognitiveFriction();
    }

    public static CostWeights forPreset(ObjectivePreset preset) {
        Objects.requireNonNull(preset, "preset");
        return switch (preset) {
            case BALANCED -> new CostWeights(1.0, 0.15, 1.2, 1.0, 2.0);
            case MIN_HUMAN_TIME -> new CostWeights(3.0, 0.05, 0.6, 0.5, 1.5);
            case NO_NEW_MACHINES -> new CostWeights(1.0, 0.15, 12.0, 1.0, 3.0);
            case INVEST_AUTOMATION -> new CostWeights(1.5, 0.10, 0.2, 0.8, 0.5);
            case MIN_RAW_MATERIALS -> new CostWeights(0.4, 0.05, 0.8, 4.0, 0.5);
        };
    }
}
