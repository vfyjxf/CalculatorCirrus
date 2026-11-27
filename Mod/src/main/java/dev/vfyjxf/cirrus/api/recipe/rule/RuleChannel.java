package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * Which side(s) of a recipe a slot-oriented condition inspects: inputs,
 * outputs, or both ({@code any}).
 */
public enum RuleChannel {
    any, input, output;

    public static final Codec<RuleChannel> codec = Codecs.lowerCaseEnum(RuleChannel.class);
}
