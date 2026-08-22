package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static registry of {@link ContextualRecipeRule}s consulted during candidate
 * evaluation.
 *
 * <p>The registry starts empty; built-in rules are installed from the
 * implementation side ({@code BuiltInContextualRules}) when the routing
 * service initializes. Custom rules may be added with {@link #register};
 * registration dedups by {@link ContextualRecipeRule#ruleId()}.
 */
public final class ContextualRecipeRules {

    private static final List<ContextualRecipeRule> rules = new CopyOnWriteArrayList<>();

    private ContextualRecipeRules() {
    }

    /** Registers the rule unless one with the same id is already present. */
    public static void register(ContextualRecipeRule rule) {
        for (ContextualRecipeRule existing : rules) {
            if (existing.ruleId().equals(rule.ruleId())) {
                return;
            }
        }
        rules.add(rule);
    }

    /** Whether no rules are registered — lets the planner skip context construction. */
    public static boolean isEmpty() {
        return rules.isEmpty();
    }

    /** Sum of all rule penalties for the candidate; {@code 0} means no opinion. */
    public static double totalPenalty(ContextualRuleContext context) {
        double total = 0.0d;
        for (ContextualRecipeRule rule : rules) {
            total += rule.penalty(context);
        }
        return total;
    }
}
