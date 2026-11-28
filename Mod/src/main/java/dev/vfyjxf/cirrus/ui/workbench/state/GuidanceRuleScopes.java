package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;

final class GuidanceRuleScopes {

    private GuidanceRuleScopes() {}

    static GuidanceRule withScope(GuidanceRule rule, GuidanceScope scope) {
        if (rule.scope() == scope) {
            return rule;
        }
        return switch (rule) {
            case RecipeRule r -> new RecipeRule(r.ruleId(), scope, r.strength(), r.recipeKey());
            case SystemRule r -> new SystemRule(r.ruleId(), scope, r.strength(), r.systemId(), r.optInEnabled());
            case MachineRule r ->
                new MachineRule(r.ruleId(), scope, r.strength(), r.machine(), r.willingToBuildNew());
            case ResourceRule r ->
                new ResourceRule(r.ruleId(), scope, r.strength(), r.resource(), r.treatAsFreeBaseSource());
            case TagDefaultRule r ->
                new TagDefaultRule(r.ruleId(), scope, r.strength(), r.tag(), r.preferredMember());
            case CycleBreakRule r ->
                new CycleBreakRule(r.ruleId(), scope, r.strength(), r.targetResource(), r.bannedSourceRecipe());
            case CostOverrideRule r ->
                new CostOverrideRule(r.ruleId(), scope, r.strength(), r.resource(), r.overrideUnitCost());
            case ModNamespaceRule r -> new ModNamespaceRule(r.ruleId(), scope, r.strength(), r.modNamespace());
        };
    }
}
