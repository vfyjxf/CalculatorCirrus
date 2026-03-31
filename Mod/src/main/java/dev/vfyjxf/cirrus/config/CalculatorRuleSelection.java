package dev.vfyjxf.cirrus.config;

import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRoutingRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;

public enum CalculatorRuleSelection {
    preview,
    active,
    execution,
    strict,
    assured;

    public RuleDefinition resolve() {
        return switch (this) {
            case preview -> DefaultRuleDefinitions.calculatorPreview();
            case active -> ActiveRoutingRule.activeRule();
            case execution -> DefaultRuleDefinitions.standardExecution();
            case strict -> DefaultRuleDefinitions.standardDefault();
            case assured -> DefaultRuleDefinitions.assuredBounded();
        };
    }
}
