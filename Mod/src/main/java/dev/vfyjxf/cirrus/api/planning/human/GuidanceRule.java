package dev.vfyjxf.cirrus.api.planning.human;

public sealed interface GuidanceRule
        permits RecipeRule, SystemRule, MachineRule, ResourceRule, TagDefaultRule,
                CycleBreakRule, CostOverrideRule, ModNamespaceRule {

    String ruleId();

    GuidanceScope scope();

    RuleStrength strength();
}
