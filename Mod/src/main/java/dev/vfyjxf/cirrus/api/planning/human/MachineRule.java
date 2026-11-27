package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record MachineRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    String machineId,
    boolean willingToBuildNew
) implements GuidanceRule {

    public MachineRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(machineId, "machineId");
    }
}
