package dev.vfyjxf.cirrus.api.recipe.rule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the tiered weighted {@link ScoreModel}: tier sums, weights,
 * rule-fed components, validation, and the equivalence of the new additive
 * decomposition to the historical magic-penalty behavior.
 */
class ScoreModelTest {

    private static ScoreModel.Component component(String name, String feature, double weight) {
        return new ScoreModel.Component(name, feature == null ? Optional.empty() : Optional.of(feature), weight);
    }

    private static ScoreModel.Tier tier(String name, ScoreModel.Component... components) {
        return new ScoreModel.Tier(name, List.of(components));
    }

    /** The default-shaped model mirroring the historical five-axis scoring. */
    private static ScoreModel defaultShapedModel() {
        return new ScoreModel(List.of(
            tier("safety",
                component("cycle", "activeDemandCycle", 1.0),
                component("roundTrip", "roundTrip", 1.0),
                component("intraLoop", "intraLoopConversion", 1.0)),
            tier("cost",
                component("subtreeCost", "subtreeCost", 1.0),
                component("advisoryExtra", "advisoryCostExcess", 1.0),
                component("contextual", "contextualPenalty", 1.0)),
            tier("quality",
                component("craft", "craftTimes", 1.0),
                component("hintDiscouraged", "hintDiscouraged", 1000.0),
                component("hintLastResort", "hintLastResort", 50000.0),
                component("shortage", "inventoryShortage", 1.0),
                component("depth", "depth", 1.0),
                component("amplification", "amplificationRisk", 1.0))
        ));
    }

    private static FeatureVector features(double subtreeCost, double advisoryExcess, double contextual,
                                          double craftTimes, double shortage, double depth, double amp) {
        FeatureVector vector = new FeatureVector();
        vector.set(FeatureType.subtreeCost, subtreeCost);
        vector.set(FeatureType.advisoryCostExcess, advisoryExcess);
        vector.set(FeatureType.contextualPenalty, contextual);
        vector.set(FeatureType.craftTimes, craftTimes);
        vector.set(FeatureType.inventoryShortage, shortage);
        vector.set(FeatureType.depth, depth);
        vector.set(FeatureType.amplificationRisk, amp);
        return vector;
    }

    @Test
    void evaluateSumsWeightedFeaturesPerTier() {
        ScoreModel.Compiled compiled = defaultShapedModel().compile();
        FeatureVector vector = features(10, 30, 500, 2, 0.25, 3, 0.5);
        vector.set(FeatureType.hintDiscouraged, 1);

        double[] tierSums = new double[compiled.tierCount()];
        compiled.evaluate(vector, tierSums);

        assertEquals(0.0, tierSums[0], 1e-9, "safety tier all-zero flags");
        assertEquals(10 + 30 + 500, tierSums[1], 1e-9);
        assertEquals(2 + 1000 + 0.25 + 3 + 0.5, tierSums[2], 1e-9);
    }

    @Test
    void weightsScaleComponents() {
        ScoreModel model = new ScoreModel(List.of(
            tier("quality",
                component("craft", "craftTimes", 1.0),
                component("depth", "depth", 0.8),
                component("amplification", "amplificationRisk", 1.2))
        ));
        ScoreModel.Compiled compiled = model.compile();
        FeatureVector vector = new FeatureVector();
        vector.set(FeatureType.craftTimes, 5);
        vector.set(FeatureType.depth, 10);
        vector.set(FeatureType.amplificationRisk, 1);

        double[] tierSums = new double[1];
        compiled.evaluate(vector, tierSums);
        assertEquals(5 + 8 + 1.2, tierSums[0], 1e-9, "assuredBounded-style weight deltas");
    }

    @Test
    void ruleFedComponentsStartAtZeroAndAcceptWeightedAdjustments() {
        ScoreModel model = new ScoreModel(List.of(
            tier("cost",
                component("subtreeCost", "subtreeCost", 1.0),
                component("lava", null, 10.0))
        ));
        ScoreModel.Compiled compiled = model.compile();
        FeatureVector vector = new FeatureVector();
        vector.set(FeatureType.subtreeCost, 7);

        double[] tierSums = new double[1];
        compiled.evaluate(vector, tierSums);
        assertEquals(7, tierSums[0], 1e-9, "rule-fed baseline is zero");

        compiled.applyAdjustment(tierSums, "lava", 2.0);
        assertEquals(7 + 20, tierSums[0], 1e-9, "adjustment multiplied by component weight");
    }

    @Test
    void unknownComponentAdjustmentFailsLoudly() {
        ScoreModel.Compiled compiled = defaultShapedModel().compile();
        double[] tierSums = new double[compiled.tierCount()];
        assertThrows(IllegalArgumentException.class, () -> compiled.applyAdjustment(tierSums, "typo", 1.0));
        assertFalse(compiled.hasComponent("typo"));
        assertTrue(compiled.hasComponent("contextual"));
    }

    @Test
    void cycleDominatesCostByTierNotMagnitude() {
        // The historical model encoded "cycle beats any cost" as +1_000_000 on
        // the cost axis; here the tier ordering declares it directly.
        ScoreModel.Compiled compiled = defaultShapedModel().compile();

        FeatureVector cycler = features(5_000_000, 0, 0, 1, 0, 0, 0);
        cycler.set(FeatureType.activeDemandCycle, 1);
        double[] cycleSums = new double[compiled.tierCount()];
        compiled.evaluate(cycler, cycleSums);

        FeatureVector cheap = features(1, 0, 0, 1, 0, 0, 0);
        double[] cheapSums = new double[compiled.tierCount()];
        compiled.evaluate(cheap, cheapSums);

        assertTrue(compare(cycleSums, cheapSums) > 0, "cycler must rank worse in the safety tier");
        assertEquals(1, cycleSums[0], 1e-9);
        assertEquals(0, cheapSums[0], 1e-9);
    }

    @Test
    void hintPenaltiesMatchHistoricalCraftAxis() {
        // Old craft axis = craftTimes + 1000*discouraged + 50000*lastResort;
        // the quality-tier weighted sum reproduces it exactly.
        ScoreModel.Compiled compiled = defaultShapedModel().compile();

        FeatureVector lastResort = features(0, 0, 0, 3, 0, 0, 0);
        lastResort.set(FeatureType.hintLastResort, 1);
        double[] sums = new double[compiled.tierCount()];
        compiled.evaluate(lastResort, sums);
        assertEquals(3 + 50000, sums[2], 1e-9);

        FeatureVector discouraged = features(0, 0, 0, 3, 0, 0, 0);
        discouraged.set(FeatureType.hintDiscouraged, 1);
        double[] sums2 = new double[compiled.tierCount()];
        compiled.evaluate(discouraged, sums2);
        assertEquals(3 + 1000, sums2[2], 1e-9);
    }

    @Test
    void advisoryExcessKeepsCostTierAdditive() {
        // generation ×1000 semantics: base 2 + excess 2*(1000-1) == old cost
        // 2*1000 within the same tier sum.
        ScoreModel.Compiled compiled = defaultShapedModel().compile();
        FeatureVector vector = features(2, 2 * 999, 0, 0, 0, 0, 0);
        double[] sums = new double[compiled.tierCount()];
        compiled.evaluate(vector, sums);
        assertEquals(2000, sums[1], 1e-9);
    }

    @Test
    void validationCatchesStructuralProblems() {
        assertTrue(new ScoreModel(List.of()).validate().stream().anyMatch(p -> p.contains("no tiers")));
        assertTrue(new ScoreModel(List.of(tier("empty"))).validate()
            .stream().anyMatch(p -> p.contains("no components")));
        assertTrue(new ScoreModel(List.of(
            tier("a", component("x", "subtreeCost", 1)),
            tier("b", component("x", "depth", 1))
        )).validate().stream().anyMatch(p -> p.contains("duplicate")));
        assertTrue(new ScoreModel(List.of(
            tier("a", component("x", "notAFeature", 1))
        )).validate().stream().anyMatch(p -> p.contains("unknown feature")));
        assertThrows(IllegalArgumentException.class, () ->
            new ScoreModel(List.of(tier("a", component("x", "notAFeature", 1)))).compile());
        assertDoesNotThrow(() -> defaultShapedModel().compile());
    }

    @Test
    void describeRendersComponentBreakdown() {
        ScoreModel.Compiled compiled = defaultShapedModel().compile();
        FeatureVector vector = features(10, 0, 0, 2, 0, 0, 0);
        double[] sums = new double[compiled.tierCount()];
        compiled.evaluate(vector, sums);
        String text = compiled.describe(sums, vector);
        assertTrue(text.contains("safety=0.0"));
        assertTrue(text.contains("cost=10.0"));
        assertTrue(text.contains("subtreeCost=10.0"));
        assertTrue(text.contains("hintLastResort=0.0"));
    }

    private static int compare(double[] left, double[] right) {
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int cmp = Double.compare(left[i], right[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.length, right.length);
    }
}
