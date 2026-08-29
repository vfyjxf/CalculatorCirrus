package dev.vfyjxf.cirrus.api.recipe.rule;

/**
 * Mutable carrier for one candidate's feature values, indexed by
 * {@link FeatureType#ordinal()}. Instances are reused by the planner's
 * per-candidate hot loop: {@link #clear()} before filling, never retained
 * across candidates.
 */
public final class FeatureVector {

    private final double[] values = new double[FeatureType.values().length];

    /** Zeroes all features. */
    public void clear() {
        java.util.Arrays.fill(values, 0.0d);
    }

    public double get(FeatureType feature) {
        return values[feature.ordinal()];
    }

    public void set(FeatureType feature, double value) {
        values[feature.ordinal()] = value;
    }

    public void add(FeatureType feature, double delta) {
        values[feature.ordinal()] += delta;
    }

    /** Package-private raw access for {@link ScoreModel.Compiled}. */
    double[] values() {
        return values;
    }
}
