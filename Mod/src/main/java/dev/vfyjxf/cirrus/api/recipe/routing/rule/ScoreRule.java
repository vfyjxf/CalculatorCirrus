package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Contributes one or more score dimensions for a candidate recipe.
 *
 * <p>Score rules run after all {@link HardRule hard rules} have accepted a
 * candidate. Each rule writes into the shared {@link ScoreAccumulator},
 * adding to named score dimensions (e.g. {@code "craft"}, {@code "depth"},
 * {@code "amplification"}). The accumulated score vector is later compared
 * by the {@link RankPolicy}.
 *
 * <p>Implementations must be stateless and side-effect-free.
 *
 * @see ScoreAccumulator
 * @see RuleProfile#scoreRules()
 */
public interface ScoreRule {

    /**
     * Adds this rule's score contribution to the accumulator.
     *
     * @param context     the evaluation context for the candidate
     * @param accumulator the score vector accumulator to write into
     */
    void contribute(CandidateContext context, ScoreAccumulator accumulator);
}

