package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

public enum RuleCollectionScope {
    defaults,
    player,
    world;

    public static final Codec<RuleCollectionScope> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleCollectionScope.class);
}
