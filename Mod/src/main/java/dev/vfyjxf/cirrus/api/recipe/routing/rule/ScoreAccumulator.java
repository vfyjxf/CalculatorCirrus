package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.rule.ScoreModel;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Mutable score accumulator for candidate-stage rules, keyed by score-model
 * component names.
 *
 * <p>Created by {@link RuleProfile#newAccumulator(double[])} with the tier-sum
 * vector. {@link ScoreRule} implementations call {@link #add(String, double)}
 * to contribute deltas; the delta is multiplied by the component's weight and
 * added to its tier's sum. Unknown component names throw — the profile
 * compiles only rule sets whose component references were validated against
 * the score model, so a failure here is a wiring bug, not a configuration
 * typo (those are rejected at load time).
 *
 * @see ScoreRule
 * @see RuleProfile#newScoreVector()
 * @see ScoreModel
 */
public final class ScoreAccumulator {
    private final double[] tierSums;
    private final ScoreModel.Compiled model;

    /**
     * Creates an accumulator backed by the given tier-sum vector.
     *
     * @param tierSums the mutable tier-sum vector to write into
     * @param model    the compiled score model mapping components to tiers
     */
    public ScoreAccumulator(double[] tierSums, ScoreModel.Compiled model) {
        this.tierSums = Checks.checkNotNull(tierSums, "tierSums");
        this.model = Checks.checkNotNull(model, "model");
    }

    /**
     * Adds a weighted delta to the named component's tier sum.
     *
     * @param component the score-model component name
     * @param delta     the raw contribution in component units
     */
    public void add(String component, double delta) {
        model.applyAdjustment(tierSums, component, delta);
    }
}
