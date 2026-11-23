package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record TagDefaultRule(
        String ruleId,
        GuidanceScope scope,
        RuleStrength strength,
        String tagId,
        ResourceKey preferredMember
) implements GuidanceRule {

    public TagDefaultRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(preferredMember, "preferredMember");
    }
}
