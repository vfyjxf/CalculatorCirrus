package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record ResourceRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    ComponentStackKey resource,
    boolean treatAsFreeBaseSource
) implements GuidanceRule {

    public ResourceRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(resource, "resource");
    }
}
