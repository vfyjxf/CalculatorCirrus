package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record SystemRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    String systemId,
    boolean optInEnabled
) implements GuidanceRule {

    public SystemRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(systemId, "systemId");
    }
}
