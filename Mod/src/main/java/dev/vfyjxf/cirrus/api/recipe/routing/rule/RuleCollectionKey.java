package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.util.Checks;

public record RuleCollectionKey(
    RuleCollectionScope scope,
    String id
) {
    public static final Codec<RuleCollectionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuleCollectionScope.CODEC.fieldOf("scope").forGetter(RuleCollectionKey::scope),
        Codec.STRING.fieldOf("id").forGetter(RuleCollectionKey::id)
    ).apply(instance, RuleCollectionKey::new));

    public RuleCollectionKey {
        Checks.checkNotNull(scope, "scope");
        Checks.checkNotNull(id, "id");
    }
}
