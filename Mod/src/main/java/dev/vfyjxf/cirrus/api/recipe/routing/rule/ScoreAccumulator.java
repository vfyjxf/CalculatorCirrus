package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.Map;

/**
 * Mutable score vector accumulator keyed by named score dimensions.
 *
 * <p>Created by {@link RuleProfile#newAccumulator(double[])} with a fresh
 * score vector. {@link ScoreRule} implementations call {@link #add(String, double)}
 * to contribute their score deltas. Unknown keys are silently ignored,
 * making it safe to use with profiles that have different score dimensions.
 *
 * @see ScoreRule
 * @see RuleProfile#newScoreVector()
 */
public final class ScoreAccumulator {
    private final double[] values;
    private final Map<String, Integer> keyToIndex;

    /**
     * Creates an accumulator backed by the given score vector.
     *
     * @param values     the mutable score vector to write into
     * @param keyToIndex mapping from score key names to vector indices
     */
    public ScoreAccumulator(double[] values, Map<String, Integer> keyToIndex) {
        this.values = Checks.checkNotNull(values, "values");
        this.keyToIndex = Checks.checkNotNull(keyToIndex, "keyToIndex");
    }

    /**
     * Adds a delta to the named score dimension.
     *
     * <p>If the key is not present in this accumulator's key map,
     * the call is silently ignored.
     *
     * @param key   the score dimension name (e.g. {@code "craft"}, {@code "depth"})
     * @param delta the value to add
     */
    public void add(String key, double delta) {
        Integer index = keyToIndex.get(key);
        if (index != null) {
            values[index] += delta;
        }
    }
}

