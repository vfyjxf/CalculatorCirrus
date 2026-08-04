package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

public enum RuleContentChannel {
    any,
    input,
    output;

    public static final Codec<RuleContentChannel> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleContentChannel.class);
}
