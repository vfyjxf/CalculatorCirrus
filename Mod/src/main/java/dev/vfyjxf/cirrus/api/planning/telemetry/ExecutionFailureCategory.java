package dev.vfyjxf.cirrus.api.planning.telemetry;

/** Categorized execution failure reason for M9.2 attributed trust decay. */
public enum ExecutionFailureCategory {
    /** Adapter/recipe binding mismatch -> counts 1.0 failure penalty & revokes semantic cert. */
    wrongBinding(1.0, true),
    /** Undocumented hidden requirement -> counts 1.0 failure penalty & revokes semantic cert. */
    hiddenRequirement(1.0, true),
    /** Player voluntarily cancelled -> counts 0.0 penalty. */
    userCancel(0.0, false),
    /** World machine temporarily busy -> updates world state, 0.0 recipe penalty. */
    machineBusy(0.0, false),
    /** Stock changed before execution -> updates stock snapshot, 0.0 recipe penalty. */
    outOfStock(0.0, false),
    /** Non-attributed misc pause -> 0.0 recipe penalty. */
    other(0.0, false);

    private final double failureWeight;
    private final boolean revokesSemanticCert;

    ExecutionFailureCategory(double failureWeight, boolean revokesSemanticCert) {
        this.failureWeight = failureWeight;
        this.revokesSemanticCert = revokesSemanticCert;
    }

    public double failureWeight() {
        return failureWeight;
    }

    public boolean revokesSemanticCert() {
        return revokesSemanticCert;
    }
}
