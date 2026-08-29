package dev.vfyjxf.cirrus.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.rule.CandidateContext;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.HardRuleResult;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ScoreAccumulator;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ScoreRule;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleEngine;

/**
 * Compiles candidate-stage {@link dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec}
 * clauses into the {@link HardRule}/{@link ScoreRule} slots of a
 * {@link dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile}: the unified
 * rule language replaces the old clause/condition/effect triple on the authoring
 * side (now {@code RuleSpec} with {@code RuleCondition.Material} and
 * {@code RuleAction} actions), while the planner keeps its hard-gate-then-score
 * pipeline unchanged.
 */
public final class CandidateRuleClauses {

    private CandidateRuleClauses() {
    }

    /** One shared engine evaluation wrapped as the profile's hard gate. */
    public static HardRule hardRule(RuleEngine engine) {
        return context -> {
            RuleEngine.CandidateVerdict verdict = engine.evaluateCandidate(new CandidateRuleInput(context));
            if (verdict.rejected()) {
                return HardRuleResult.reject(RoutingFailureCode.noFeasibleSource, verdict.rejectReason());
            }
            return HardRuleResult.accept();
        };
    }

    /** One shared engine evaluation wrapped as the profile's score contributor. */
    public static ScoreRule scoreRule(RuleEngine engine) {
        return (context, accumulator) -> contribute(engine, context, accumulator);
    }

    private static void contribute(RuleEngine engine, CandidateContext context, ScoreAccumulator accumulator) {
        RuleEngine.CandidateVerdict verdict = engine.evaluateCandidate(new CandidateRuleInput(context));
        for (var adjust : verdict.adjustments()) {
            accumulator.add(adjust.component(), adjust.signedAmount(context.availableCoverage()));
        }
    }
}
