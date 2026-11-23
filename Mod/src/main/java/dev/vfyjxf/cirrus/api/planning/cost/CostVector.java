package dev.vfyjxf.cirrus.api.planning.cost;

public record CostVector(
        double humanActiveSeconds,
        double passiveWaitSeconds,
        double machineSetupCapital,
        double materialScarcity,
        double cognitiveFriction
) {

    public static final double EPS = 1e-9;
    public static final CostVector ZERO = new CostVector(0.0, 0.0, 0.0, 0.0, 0.0);

    public CostVector {
        requireNonNegFinite(humanActiveSeconds, "humanActiveSeconds");
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

    public CostVector addSerial(CostVector o) {
        return new CostVector(
                humanActiveSeconds + o.humanActiveSeconds,
                passiveWaitSeconds + o.passiveWaitSeconds,
                machineSetupCapital + o.machineSetupCapital,
                materialScarcity + o.materialScarcity,
                cognitiveFriction + o.cognitiveFriction
        );
    }

    public CostVector addParallelPassive(CostVector o) {
        return new CostVector(
                humanActiveSeconds + o.humanActiveSeconds,
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
                passiveWaitSeconds * factor,
                machineSetupCapital * factor,
                materialScarcity * factor,
                cognitiveFriction * factor
        );
    }

    public boolean dominates(CostVector o) {
        boolean allLe = humanActiveSeconds <= o.humanActiveSeconds + EPS
                && passiveWaitSeconds <= o.passiveWaitSeconds + EPS
                && machineSetupCapital <= o.machineSetupCapital + EPS
                && materialScarcity <= o.materialScarcity + EPS
                && cognitiveFriction <= o.cognitiveFriction + EPS;
        boolean anyLt = humanActiveSeconds < o.humanActiveSeconds - EPS
                || passiveWaitSeconds < o.passiveWaitSeconds - EPS
                || machineSetupCapital < o.machineSetupCapital - EPS
                || materialScarcity < o.materialScarcity - EPS
                || cognitiveFriction < o.cognitiveFriction - EPS;
        return allLe && anyLt;
    }

    public String quantizedSignature() {
        return Math.round(humanActiveSeconds * 1_000_000.0) + ":"
                + Math.round(passiveWaitSeconds * 1_000_000.0) + ":"
                + Math.round(machineSetupCapital * 1_000_000.0) + ":"
                + Math.round(materialScarcity * 1_000_000.0) + ":"
                + Math.round(cognitiveFriction * 1_000_000.0);
    }
}
