package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import net.minecraft.tags.TagKey;

import java.util.Objects;

public record TagDefaultRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    TagKey<?> tag,
    ComponentStackKey preferredMember
) implements GuidanceRule {

    public TagDefaultRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(preferredMember, "preferredMember");
    }
}
