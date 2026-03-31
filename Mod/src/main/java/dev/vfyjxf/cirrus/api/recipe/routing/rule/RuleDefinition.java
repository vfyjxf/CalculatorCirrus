package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

public record RuleDefinition(
    Namespace ruleId,
    String name,
    RuleProfileSpec profile,
    SourcePolicy sourcePolicy,
    ImmutableList<RuleClauseSpec> clauses,
    boolean editable
) {
    public static final Codec<RuleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("rule_id").forGetter(RuleDefinition::ruleId),
        Codec.STRING.fieldOf("name").forGetter(RuleDefinition::name),
        RuleProfileSpec.CODEC.fieldOf("profile").forGetter(RuleDefinition::profile),
        SourcePolicy.CODEC.fieldOf("source_policy").forGetter(RuleDefinition::sourcePolicy),
        dev.vfyjxf.cloudlib.api.util.Codecs.immutableList(RuleClauseSpec.CODEC).fieldOf("clauses").forGetter(RuleDefinition::clauses),
        Codec.BOOL.fieldOf("editable").forGetter(RuleDefinition::editable)
    ).apply(instance, RuleDefinition::new));

    public RuleDefinition {
        Checks.checkNotNull(ruleId, "ruleId");
        Checks.checkNotNull(name, "name");
        Checks.checkNotNull(profile, "profile");
        Checks.checkNotNull(sourcePolicy, "sourcePolicy");
        Checks.checkNotNull(clauses, "clauses");
    }

    public RuleProfile toProfile() {
        return profile.toProfile(clauses);
    }
}
