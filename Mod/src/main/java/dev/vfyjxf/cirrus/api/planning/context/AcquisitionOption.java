package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Objects;

public record AcquisitionOption(
    AcquisitionKind kind,
    double humanSecondsPerUnit,
    double passiveSecondsPerUnit,
    double fixedTripHumanSeconds,
    double scarcityPerUnit,
    double p90SecondsPerUnit,
    double cv,
    long guaranteedQuantity,
    String tripEpisodeId,
    String translationKey
) {

    public AcquisitionOption(
        AcquisitionKind kind,
        double humanSecondsPerUnit,
        double passiveSecondsPerUnit,
        double fixedTripHumanSeconds,
        double scarcityPerUnit,
        String translationKey
    ) {
        this(
            kind,
            humanSecondsPerUnit,
            passiveSecondsPerUnit,
            fixedTripHumanSeconds,
            scarcityPerUnit,
            humanSecondsPerUnit,
            0.0,
            Long.MAX_VALUE,
            "",
            translationKey
        );
    }

    public AcquisitionOption {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tripEpisodeId, "tripEpisodeId");
        Objects.requireNonNull(translationKey, "translationKey");
        requireNonNegFinite(humanSecondsPerUnit, "humanSecondsPerUnit");
        requireNonNegFinite(passiveSecondsPerUnit, "passiveSecondsPerUnit");
        requireNonNegFinite(fixedTripHumanSeconds, "fixedTripHumanSeconds");
        requireNonNegFinite(scarcityPerUnit, "scarcityPerUnit");
        requireNonNegFinite(p90SecondsPerUnit, "p90SecondsPerUnit");
        requireNonNegFinite(cv, "cv");
        if (guaranteedQuantity < 0L) {
            throw new IllegalArgumentException("guaranteedQuantity must be >= 0");
        }
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    public boolean requiresFieldTrip() {
        return kind == AcquisitionKind.manualGather;
    }

    public double riskPremiumForQuantity(long quantity, double eta, double c0) {
        if (cv <= 0.0 || eta <= 0.0 || quantity <= 0L) {
            return 0.0;
        }
        double tail = Math.max(0.0, p90SecondsPerUnit - humanSecondsPerUnit) * quantity;
        return eta * (cv / (cv + Math.max(1e-9, c0))) * tail;
    }
}
