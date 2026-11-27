package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;

/**
 * Four orthogonal preference modifiers composable on top of any {@link ObjectivePreset} (M5).
 */
public record PreferenceModifiers(
    double fieldTripAversion,
    double newMachineTolerance,
    StockReferenceMode stockMode,
    StageOverride stageOverride,
    double stageOverrideWeight,
    boolean forbidFieldTrips
) {

    public enum StockReferenceMode {
        ignore(1.0, false),
        expectedFirst(0.55, false),
        protectedStrict(0.85, true),
        aggressiveClear(0.35, false);

        private final double defaultAlphaOpp;
        private final boolean blockProtectedGenericTag;

        StockReferenceMode(double defaultAlphaOpp, boolean blockProtectedGenericTag) {
            this.defaultAlphaOpp = defaultAlphaOpp;
            this.blockProtectedGenericTag = blockProtectedGenericTag;
        }

        public double defaultAlphaOpp() {
            return defaultAlphaOpp;
        }

        public boolean blockProtectedGenericTag() {
            return blockProtectedGenericTag;
        }
    }

    public enum StageOverride {
        auto,
        early,
        mid,
        late
    }

    public static final PreferenceModifiers defaults = new PreferenceModifiers(
        1.0,
        1.0,
        StockReferenceMode.expectedFirst,
        StageOverride.auto,
        0.0,
        false
    );

    public PreferenceModifiers {
        if (!Double.isFinite(fieldTripAversion) || fieldTripAversion < 0.25 || fieldTripAversion > 8.0) {
            throw new IllegalArgumentException("fieldTripAversion must be in [0.25, 8.0], got " + fieldTripAversion);
        }
        if (!Double.isFinite(newMachineTolerance) || newMachineTolerance < 0.25 || newMachineTolerance > 4.0) {
            throw new IllegalArgumentException("newMachineTolerance must be in [0.25, 4.0], got " + newMachineTolerance);
        }
        Objects.requireNonNull(stockMode, "stockMode");
        Objects.requireNonNull(stageOverride, "stageOverride");
        if (!Double.isFinite(stageOverrideWeight) || stageOverrideWeight < 0.0 || stageOverrideWeight > 1.0) {
            throw new IllegalArgumentException("stageOverrideWeight must be in [0, 1], got " + stageOverrideWeight);
        }
    }

    public PreferenceModifiers withFieldTripAversion(double value) {
        return new PreferenceModifiers(value, newMachineTolerance, stockMode, stageOverride, stageOverrideWeight, forbidFieldTrips);
    }

    public PreferenceModifiers withNewMachineTolerance(double value) {
        return new PreferenceModifiers(fieldTripAversion, value, stockMode, stageOverride, stageOverrideWeight, forbidFieldTrips);
    }

    public PreferenceModifiers withStockMode(StockReferenceMode mode) {
        return new PreferenceModifiers(fieldTripAversion, newMachineTolerance, mode, stageOverride, stageOverrideWeight, forbidFieldTrips);
    }

    public PreferenceModifiers withForbidFieldTrips(boolean forbid) {
        return new PreferenceModifiers(fieldTripAversion, newMachineTolerance, stockMode, stageOverride, stageOverrideWeight, forbid);
    }
}
