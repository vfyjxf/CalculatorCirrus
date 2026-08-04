package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cloudlib.util.Checks;

public final class ActiveRoutingRule {
    private static volatile RuleDefinition activeRule = DefaultRuleDefinitions.standardExecution();
    private static volatile long version = 0L;

    private ActiveRoutingRule() {
    }

    public static RuleDefinition activeRule() {
        return activeRule;
    }

    public static long version() {
        return version;
    }

    public static void setActiveRule(RuleDefinition rule) {
        activeRule = Checks.checkNotNull(rule, "rule");
        version++;
    }

    public static void applyView(RuleRepositoryView view) {
        setActiveRule(view.activeRuleOrDefault());
    }

    public static void registerActiveRule(RoutingService service) {
        service.registerRuleDefinition(activeRule);
    }
}
