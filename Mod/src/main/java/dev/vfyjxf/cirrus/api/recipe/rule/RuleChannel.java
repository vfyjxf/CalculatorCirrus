package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;

/**
 * Which side(s) of a recipe a slot-oriented condition inspects: inputs,
 * outputs, or both ({@code any}).
 */
public enum RuleChannel {
    any,
    input,
    output;

    public static final Codec<RuleChannel> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleChannel.class);
}
