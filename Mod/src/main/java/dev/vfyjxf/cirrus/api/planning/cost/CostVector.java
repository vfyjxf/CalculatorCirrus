package dev.vfyjxf.cirrus.api.planning.cost;

/**
 * 6-dimensional physical cost vector (M1.3):
 * base human craft seconds, field-trip active seconds, passive wall-clock wait seconds,
 * newly built machine capital units (CU), scarce resource consumption units (SU),
 * and compressed manual step/switch friction count.
 */
public record CostVector(
    double humanActiveSeconds,
    double tripActiveSeconds,
    double passiveWaitSeconds,
    double machineSetupCapital,
    double materialScarcity,
    double cognitiveFriction
) {

    public static final double eps = 1e-9;
    public static final CostVector zero = new CostVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    public CostVector(
        double humanActiveSeconds,
        double passiveWaitSeconds,
        double machineSetupCapital,
        double materialScarcity,
        double cognitiveFriction
    ) {
        this(humanActiveSeconds, 0.0, passiveWaitSeconds, machineSetupCapital, materialScarcity, cognitiveFriction);
    }

    public CostVector {
        requireNonNegFinite(humanActiveSeconds, "humanActiveSeconds");
        requireNonNegFinite(tripActiveSeconds, "tripActiveSeconds");
        requireNonNegFinite(passiveWaitSeconds, "passiveWaitSeconds");
        requireNonNegFinite(machineSetupCapital, "machineSetupCapital");
        requireNonNegFinite(materialScarcity, "materialScarcity");
        requireNonNegFinite(cognitiveFriction, "cognitiveFriction");
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    public double totalActiveSeconds() {
        return humanActiveSeconds + tripActiveSeconds;
    }

    public double[] asArray() {
        return new double[]{
            humanActiveSeconds,
            tripActiveSeconds,
            passiveWaitSeconds,
            machineSetupCapital,
            materialScarcity,
            cognitiveFriction
        };
    }

    public CostVector addSerial(CostVector o) {
        return new CostVector(
            humanActiveSeconds + o.humanActiveSeconds,
            tripActiveSeconds + o.tripActiveSeconds,
            passiveWaitSeconds + o.passiveWaitSeconds,
            machineSetupCapital + o.machineSetupCapital,
            materialScarcity + o.materialScarcity,
            cognitiveFriction + o.cognitiveFriction
        );
    }

    public CostVector addParallelPassive(CostVector o) {
        return new CostVector(
            humanActiveSeconds + o.humanActiveSeconds,
            tripActiveSeconds + o.tripActiveSeconds,
            Math.max(passiveWaitSeconds, o.passiveWaitSeconds),
            machineSetupCapital + o.machineSetupCapital,
            materialScarcity + o.materialScarcity,
            cognitiveFriction + o.cognitiveFriction
        );
    }

    public CostVector scaleLinear(double factor) {
        requireNonNegFinite(factor, "factor");
        return new CostVector(
            humanActiveSeconds * factor,
            tripActiveSeconds * factor,
            passiveWaitSeconds * factor,
            machineSetupCapital * factor,
            materialScarcity * factor,
            cognitiveFriction * factor
        );
    }

    public boolean dominates(CostVector o) {
        boolean allLe = humanActiveSeconds <= o.humanActiveSeconds + eps
                && tripActiveSeconds <= o.tripActiveSeconds + eps
                && passiveWaitSeconds <= o.passiveWaitSeconds + eps
                && machineSetupCapital <= o.machineSetupCapital + eps
                && materialScarcity <= o.materialScarcity + eps
                && cognitiveFriction <= o.cognitiveFriction + eps;
        boolean anyLt = humanActiveSeconds < o.humanActiveSeconds - eps
                || tripActiveSeconds < o.tripActiveSeconds - eps
                || passiveWaitSeconds < o.passiveWaitSeconds - eps
                || machineSetupCapital < o.machineSetupCapital - eps
                || materialScarcity < o.materialScarcity - eps
                || cognitiveFriction < o.cognitiveFriction - eps;
        return allLe && anyLt;
    }

    public String quantizedSignature() {
        return Math.round(humanActiveSeconds * 1_000_000.0) + ":"
                + Math.round(tripActiveSeconds * 1_000_000.0) + ":"
                + Math.round(passiveWaitSeconds * 1_000_000.0) + ":"
                + Math.round(machineSetupCapital * 1_000_000.0) + ":"
                + Math.round(materialScarcity * 1_000_000.0) + ":"
                + Math.round(cognitiveFriction * 1_000_000.0);
    }
}
