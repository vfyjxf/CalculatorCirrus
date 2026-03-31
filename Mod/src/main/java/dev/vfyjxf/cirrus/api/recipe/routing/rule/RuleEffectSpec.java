package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.util.Checks;

public record RuleEffectSpec(
    RuleEffectKind kind,
    String scoreKey,
    double amount
) {
    public static final Codec<RuleEffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuleEffectKind.CODEC.fieldOf("kind").forGetter(RuleEffectSpec::kind),
        Codec.STRING.fieldOf("score_key").forGetter(RuleEffectSpec::scoreKey),
        Codec.DOUBLE.fieldOf("amount").forGetter(RuleEffectSpec::amount)
    ).apply(instance, RuleEffectSpec::new));

    public RuleEffectSpec {
        Checks.checkNotNull(kind, "kind");
        Checks.checkNotNull(scoreKey, "scoreKey");
        scoreKey = scoreKey.trim();
    }

    public static RuleEffectSpec scoreAdjustment(String scoreKey, double amount) {
        return new RuleEffectSpec(RuleEffectKind.scoreAdjustment, scoreKey, amount);
    }

    public static RuleEffectSpec coverageScoreAdjustment(String scoreKey, double amount) {
        return new RuleEffectSpec(RuleEffectKind.coverageScoreAdjustment, scoreKey, amount);
    }

    public static RuleEffectSpec rejectCandidate() {
        return new RuleEffectSpec(RuleEffectKind.rejectCandidate, "", 0.0d);
    }

    public boolean rejectsCandidate() {
        return kind == RuleEffectKind.rejectCandidate;
    }

    public void apply(CandidateContext context, ScoreAccumulator accumulator) {
        if (scoreKey.isBlank()) {
            return;
        }
        switch (kind) {
            case scoreAdjustment -> accumulator.add(scoreKey, amount);
            case coverageScoreAdjustment -> accumulator.add(scoreKey, amount * (1.0d - Math.min(1.0d, context.availableCoverage())));
            case rejectCandidate -> {
            }
        }
    }
}
