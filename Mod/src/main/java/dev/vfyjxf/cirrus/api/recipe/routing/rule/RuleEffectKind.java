package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

public enum RuleEffectKind {
    scoreAdjustment,
    coverageScoreAdjustment,
    rejectCandidate;

    public static final Codec<RuleEffectKind> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleEffectKind.class);
}
