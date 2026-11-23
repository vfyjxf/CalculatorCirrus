package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Objects;

public record AcquisitionOption(
        AcquisitionKind kind,
        double humanSecondsPerUnit,
        double passiveSecondsPerUnit,
        double fixedTripHumanSeconds,
        double scarcityPerUnit,
        String translationKey
) {

    public AcquisitionOption {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(translationKey, "translationKey");
        requireNonNegFinite(humanSecondsPerUnit, "humanSecondsPerUnit");
        requireNonNegFinite(passiveSecondsPerUnit, "passiveSecondsPerUnit");
        requireNonNegFinite(fixedTripHumanSeconds, "fixedTripHumanSeconds");
        requireNonNegFinite(scarcityPerUnit, "scarcityPerUnit");
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }
}
