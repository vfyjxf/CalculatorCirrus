package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

/**
 * The raw quality score of one recipe from the Bayesian junk model, kept
 * separate from the calibrated {@link RecipeCostHeuristic#penalty(int)} so the
 * model output stays inspectable (probability scale, log-odds, evidence).
 *
 * @param junkProbability P(junk | features), clamped to a stable range
 * @param logOdds         log P(junk|f) − log P(useful|f)
 * @param contributions   the features that fired and their signed weights
 */
public record RecipeBayesScore(
    double junkProbability,
    double logOdds,
    List<RecipeCostHeuristic.FeatureContribution> contributions
) {
    public RecipeBayesScore {
        Checks.checkArgument(junkProbability >= 0.0 && junkProbability <= 1.0, "junkProbability out of range");
        Checks.checkNotNull(contributions, "contributions");
        contributions = List.copyOf(contributions);
    }
}
