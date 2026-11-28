package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.Objects;

public record MachineRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    MachineEntry machine,
    boolean willingToBuildNew
) implements GuidanceRule {

    public MachineRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(machine, "machine");
    }
}
