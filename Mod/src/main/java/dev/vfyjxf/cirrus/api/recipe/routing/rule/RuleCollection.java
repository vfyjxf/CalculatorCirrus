package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

public record RuleCollection(
    RuleCollectionKey key,
    List<RuleDefinition> rules
) {
    public static final Codec<RuleCollection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuleCollectionKey.CODEC.fieldOf("key").forGetter(RuleCollection::key),
        RuleDefinition.CODEC.listOf().fieldOf("rules").forGetter(RuleCollection::rules)
    ).apply(instance, RuleCollection::new));

    public RuleCollection {
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(rules, "rules");
        rules = List.copyOf(rules);
    }
}
