package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

public enum RuleConditionKind {
    always,
    material;

    public static final Codec<RuleConditionKind> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleConditionKind.class);
}
