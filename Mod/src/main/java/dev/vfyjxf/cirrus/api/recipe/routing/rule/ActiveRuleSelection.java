package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public record ActiveRuleSelection(
    RuleCollectionKey collection,
    Namespace ruleId
) {
    public static final Codec<ActiveRuleSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuleCollectionKey.CODEC.fieldOf("collection").forGetter(ActiveRuleSelection::collection),
        Namespace.codec.fieldOf("rule_id").forGetter(ActiveRuleSelection::ruleId)
    ).apply(instance, ActiveRuleSelection::new));

    public ActiveRuleSelection {
        Checks.checkNotNull(collection, "collection");
        Checks.checkNotNull(ruleId, "ruleId");
    }
}
