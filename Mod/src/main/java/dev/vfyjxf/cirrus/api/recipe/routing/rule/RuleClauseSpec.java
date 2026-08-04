package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.Collection;

public record RuleClauseSpec(
    Namespace clauseId,
    boolean enabled,
    String label,
    RuleConditionSpec condition,
    RuleEffectSpec effect
) {
    public static final Codec<RuleClauseSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("clause_id").forGetter(RuleClauseSpec::clauseId),
        Codec.BOOL.fieldOf("enabled").forGetter(RuleClauseSpec::enabled),
        Codec.STRING.fieldOf("label").forGetter(RuleClauseSpec::label),
        RuleConditionSpec.CODEC.fieldOf("condition").forGetter(RuleClauseSpec::condition),
        RuleEffectSpec.CODEC.fieldOf("effect").forGetter(RuleClauseSpec::effect)
    ).apply(instance, RuleClauseSpec::new));

    public RuleClauseSpec {
        Checks.checkNotNull(clauseId, "clauseId");
        label = label == null ? "" : label.trim();
        Checks.checkNotNull(condition, "condition");
        Checks.checkNotNull(effect, "effect");
    }

    public static RuleClauseSpec materialScoreAdjustment(
        Namespace clauseId,
        String label,
        RuleContentChannel channel,
        Collection<String> materials,
        String scoreKey,
        double amount
    ) {
        return new RuleClauseSpec(
            clauseId,
            true,
            label,
            RuleConditionSpec.materials(channel, materials),
            RuleEffectSpec.scoreAdjustment(scoreKey, amount)
        );
    }

    public static RuleClauseSpec materialCoverageScoreAdjustment(
        Namespace clauseId,
        String label,
        RuleContentChannel channel,
        Collection<String> materials,
        String scoreKey,
        double amount
    ) {
        return new RuleClauseSpec(
            clauseId,
            true,
            label,
            RuleConditionSpec.materials(channel, materials),
            RuleEffectSpec.coverageScoreAdjustment(scoreKey, amount)
        );
    }

    public static RuleClauseSpec materialReject(
        Namespace clauseId,
        String label,
        RuleContentChannel channel,
        Collection<String> materials
    ) {
        return new RuleClauseSpec(
            clauseId,
            true,
            label,
            RuleConditionSpec.materials(channel, materials),
            RuleEffectSpec.rejectCandidate()
        );
    }

    public boolean hardReject() {
        return effect.rejectsCandidate();
    }

    public HardRuleResult evaluate(CandidateContext context) {
        if (!enabled || !hardReject() || !condition.matches(context)) {
            return HardRuleResult.accept();
        }
        String reason = label.isBlank() ? "Rejected by routing rule " + clauseId : label;
        return HardRuleResult.reject(RoutingFailureCode.noFeasibleSource, reason);
    }

    public void contribute(CandidateContext context, ScoreAccumulator accumulator) {
        if (enabled && !hardReject() && condition.matches(context)) {
            effect.apply(context, accumulator);
        }
    }
}
