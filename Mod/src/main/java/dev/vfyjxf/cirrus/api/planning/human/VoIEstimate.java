package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

/**
 * Counterfactual Expected Value of Sample Information (EVSI) and uncertainty interval (M9.1).
 */
public record VoIEstimate(
    double grossEvsiSeconds,
    double askCostSeconds,
    double netDeltaJ,
    double answerEntropy,
    double evsiLowerBoundSeconds,
    double evsiUpperBoundSeconds,
    String uncertaintyStatus
) {

    public static final VoIEstimate zero = new VoIEstimate(0.0, 3.0, 0.0, 0.0, 0.0, 0.0, "EMPIRICAL");

    public VoIEstimate {
        Objects.requireNonNull(uncertaintyStatus, "uncertaintyStatus");
        if (!Double.isFinite(grossEvsiSeconds) || grossEvsiSeconds < 0.0) {
            throw new IllegalArgumentException("grossEvsiSeconds must be >= 0");
        }
        if (!Double.isFinite(askCostSeconds) || askCostSeconds < 0.0) {
            throw new IllegalArgumentException("askCostSeconds must be >= 0");
        }
        if (!Double.isFinite(answerEntropy) || answerEntropy < 0.0) {
            throw new IllegalArgumentException("answerEntropy must be >= 0");
        }
    }

    public double netEvsiSeconds() {
        return Math.max(0.0, grossEvsiSeconds - askCostSeconds);
    }

    public double conservativeNetSeconds() {
        return Math.max(0.0, evsiLowerBoundSeconds - askCostSeconds);
    }
}
