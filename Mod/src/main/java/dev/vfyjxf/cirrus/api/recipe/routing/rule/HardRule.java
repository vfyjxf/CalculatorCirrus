package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Binary accept/reject filter applied to each candidate recipe during
 * evaluation.
 *
 * <p>A hard rule is the first gate in the candidate pipeline: if <b>any</b>
 * hard rule rejects a candidate, that candidate is discarded immediately
 * without scoring. Implementations must be stateless and side-effect-free.
 *
 * @see HardRuleResult
 * @see RuleProfile#hardRules()
 */
public interface HardRule {

    /**
     * Evaluates whether the candidate described by {@code context} is acceptable.
     *
     * @param context the evaluation context for the candidate
     * @return {@link HardRuleResult#accept()} to pass, or
     *         {@link HardRuleResult#reject(dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode, String)}
     *         to reject with a reason
     */
    HardRuleResult evaluate(CandidateContext context);
}

