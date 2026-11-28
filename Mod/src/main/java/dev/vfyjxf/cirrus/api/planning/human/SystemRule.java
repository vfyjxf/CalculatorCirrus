package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Objects;

public record SystemRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    Namespace systemId,
    boolean optInEnabled
) implements GuidanceRule {

    public SystemRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(systemId, "systemId");
    }
}
