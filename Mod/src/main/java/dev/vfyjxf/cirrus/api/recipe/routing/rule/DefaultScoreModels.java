package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.rule.FeatureType;
import dev.vfyjxf.cirrus.api.recipe.rule.ScoreModel;

import java.util.List;
import java.util.Optional;

/**
 * The built-in tiered score models. This is the single, visible definition of
 * the planner's scoring function — every magnitude that used to be a
 * hand-tuned constant in {@code StandardPlanner} lives here as a declared
 * weight or multiplier:
 *
 * <ul>
 *     <li><b>safety tier</b> — cycle/round-trip/intra-loop flags. Being in
 *     the lowest tier, any of these absolutely dominates every cost
 *     consideration (formerly enforced by {@code +1_000_000} /
 *     {@code +100_000} penalties sized against each other).</li>
 *     <li><b>cost tier</b> — base subtree cost, advisory excess
 *     (generation/soft multipliers expressed additively via
 *     {@code full − base}), and the request-scoped contextual penalty.</li>
 *     <li><b>quality tier</b> — craft count, hint penalties (formerly
 *     {@code +1_000} discouraged / {@code +50_000} last-resort added to the
 *     craft axis), stock shortage, depth, and amplification risk.</li>
 * </ul>
 *
 * <p>Preset deltas: {@code assuredBounded} weighs depth ×0.8 and
 * amplification ×1.2 (formerly hard-coded in the fast-scoring branch).
 */
public final class DefaultScoreModels {

    // component names referenced by rule effects (candidate-stage clauses)
    public static final String COMPONENT_CYCLE = "cycle";
    public static final String COMPONENT_ROUND_TRIP = "roundTrip";
    public static final String COMPONENT_INTRA_LOOP = "intraLoop";
    public static final String COMPONENT_SUBTREE_COST = "subtreeCost";
    public static final String COMPONENT_ADVISORY_EXTRA = "advisoryExtra";
    public static final String COMPONENT_CONTEXTUAL = "contextual";
    public static final String COMPONENT_CRAFT = "craft";
    public static final String COMPONENT_HINT_DISCOURAGED = "hintDiscouraged";
    public static final String COMPONENT_HINT_LAST_RESORT = "hintLastResort";
    public static final String COMPONENT_HINT_PENALTY = "hintPenalty";
    /** Named {@code inventoryShortage} to stay compatible with the RulesPage default score key. */
    public static final String COMPONENT_SHORTAGE = "inventoryShortage";
    public static final String COMPONENT_DEPTH = "depth";
    public static final String COMPONENT_AMPLIFICATION = "amplification";

    /** Hint weights (single source; the quality tier and the producer pre-ranking both use them). */
    public static final double HINT_DISCOURAGED_WEIGHT = 1_000.0d;
    public static final double HINT_LAST_RESORT_WEIGHT = 50_000.0d;

    private DefaultScoreModels() {
    }

    /** The standard model (depth ×1.0, amplification ×1.0). */
    public static ScoreModel standard() {
        return build(1.0d, 1.0d);
    }

    /** The assured-bounded model (depth ×0.8, amplification ×1.2). */
    public static ScoreModel assuredBounded() {
        return build(0.8d, 1.2d);
    }

    private static ScoreModel build(double depthWeight, double amplificationWeight) {
        return new ScoreModel(
            List.of(
                new ScoreModel.Tier("safety", List.of(
                    component(COMPONENT_CYCLE, FeatureType.activeDemandCycle, 1.0d),
                    component(COMPONENT_ROUND_TRIP, FeatureType.roundTrip, 1.0d),
                    component(COMPONENT_INTRA_LOOP, FeatureType.intraLoopConversion, 1.0d)
                )),
                new ScoreModel.Tier("cost", List.of(
                    component(COMPONENT_SUBTREE_COST, FeatureType.subtreeCost, 1.0d),
                    component(COMPONENT_ADVISORY_EXTRA, FeatureType.advisoryCostExcess, 1.0d),
                    component(COMPONENT_CONTEXTUAL, FeatureType.contextualPenalty, 1.0d)
                )),
                new ScoreModel.Tier("quality", List.of(
                    component(COMPONENT_CRAFT, FeatureType.craftTimes, 1.0d),
                    component(COMPONENT_HINT_DISCOURAGED, FeatureType.hintDiscouraged, HINT_DISCOURAGED_WEIGHT),
                    component(COMPONENT_HINT_LAST_RESORT, FeatureType.hintLastResort, HINT_LAST_RESORT_WEIGHT),
                    component(COMPONENT_HINT_PENALTY, FeatureType.hintPenalty, 1.0d),
                    component(COMPONENT_SHORTAGE, FeatureType.inventoryShortage, 1.0d),
                    component(COMPONENT_DEPTH, FeatureType.depth, depthWeight),
                    component(COMPONENT_AMPLIFICATION, FeatureType.amplificationRisk, amplificationWeight)
                ))
            ),
            ScoreModel.DEFAULT_GENERATION_COST_MULTIPLIER,
            ScoreModel.DEFAULT_SOFT_ADVISORY_MULTIPLIER
        );
    }

    private static ScoreModel.Component component(String name, FeatureType feature, double weight) {
        return new ScoreModel.Component(name, Optional.of(feature.name()), weight);
    }
}
