package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * A contextual screening rule: scores a candidate recipe in the context of
 * the live routing request.
 *
 * <p>General recipe screening (pattern rules, structural rules) runs once per
 * recipe and marks it excluded or advisory globally. Contextual rules are the
 * complement: they fire per candidate during candidate evaluation and return
 * a penalty that only makes sense relative to the current target, stock, and
 * plan-tree position. Example: a machine recipe is a fine recipe in general,
 * but a poor choice when the player does not own the machine.
 *
 * <p>Rules must be cheap, side-effect free, and return {@code 0} when they
 * have no opinion. Positive values penalize the candidate; the magnitude
 * ordering follows {@code StandardPlanner}'s penalty scale (amplification
 * guards use 100_000).
 */
public interface ContextualRecipeRule {

    /** Stable id used for dedup on registration and debug logging. */
    String ruleId();

    /**
     * Returns the penalty for the candidate in this context, {@code 0} when
     * the rule has no opinion.
     */
    double penalty(ContextualRuleContext context);
}
