package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Objects;

/**
 * M4 & [TODO-PLAN-MC-01] JER Monte Carlo compatible acquisition estimate.
 * Reduces identically to deterministic (startTripSeconds + meanActiveSeconds) when cv == 0.
 */
public record AcquisitionEstimate(
    AcquisitionKind mode,
    double startTripSeconds,
    double meanActiveSeconds,
    double p90ActiveSeconds,
    double passiveSeconds,
    double scarcityPerUnit,
    double cv,
    long guaranteedQuantity,
    String tripEpisodeId,
    String evidenceVersion,
    String coverage,
    String sourceId
) {

    public static final double defaultRiskEta = 0.65;
    public static final double defaultRiskC0 = 0.50;

    public AcquisitionEstimate {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(tripEpisodeId, "tripEpisodeId");
        Objects.requireNonNull(evidenceVersion, "evidenceVersion");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(sourceId, "sourceId");
        requireNonNegFinite(startTripSeconds, "startTripSeconds");
        requireNonNegFinite(meanActiveSeconds, "meanActiveSeconds");
        requireNonNegFinite(p90ActiveSeconds, "p90ActiveSeconds");
        requireNonNegFinite(passiveSeconds, "passiveSeconds");
        requireNonNegFinite(scarcityPerUnit, "scarcityPerUnit");
        requireNonNegFinite(cv, "cv");
        if (guaranteedQuantity < 0L) {
            throw new IllegalArgumentException("guaranteedQuantity must be >= 0, got " + guaranteedQuantity);
        }
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    /**
     * M4 risk-sensitive acquisition time:
     * T_risk = t_0 + mu + eta * (cv / (cv + c_0)) * max(0, q_90 - mu).
     * Strictly reduces to t_0 + mu when cv == 0.
     */
    public double riskAdjustedSeconds(double eta, double c0) {
        if (cv <= 0.0 || eta <= 0.0) {
            return startTripSeconds + meanActiveSeconds;
        }
        double denom = cv + Math.max(1e-9, c0);
        double tail = Math.max(0.0, p90ActiveSeconds - meanActiveSeconds);
        return startTripSeconds + meanActiveSeconds + eta * (cv / denom) * tail;
    }

    public double riskPremiumSeconds(double eta, double c0) {
        return Math.max(0.0, riskAdjustedSeconds(eta, c0) - (startTripSeconds + meanActiveSeconds));
    }

    public AcquisitionOption toOption(long quantity, String translationKey) {
        long q = Math.max(1L, quantity);
        return new AcquisitionOption(
            mode,
            meanActiveSeconds / q,
            passiveSeconds / q,
            startTripSeconds,
            scarcityPerUnit,
            p90ActiveSeconds / q,
            cv,
            guaranteedQuantity,
            tripEpisodeId,
            translationKey
        );
    }
}
