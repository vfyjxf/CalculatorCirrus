package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record ModNamespaceRule(
        String ruleId,
        GuidanceScope scope,
        RuleStrength strength,
        String modNamespace
) implements GuidanceRule {

    public ModNamespaceRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(modNamespace, "modNamespace");
    }
}
