package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Global registry for lightweight recipe routing hints.
 */
public final class RecipeRoutingHeuristics {
    private static final CopyOnWriteArrayList<RecipeRoutingHeuristicRule> rules = new CopyOnWriteArrayList<>();

    private RecipeRoutingHeuristics() {
    }

    public static Builder add(String ruleId) {
        return add(parseNamespace(ruleId));
    }

    public static Builder add(Namespace ruleId) {
        return new Builder(ruleId);
    }

    public static List<RecipeRoutingHeuristicRule> snapshot() {
        return List.copyOf(rules);
    }

    public static boolean hasRules() {
        return !rules.isEmpty();
    }

    public static void clear() {
        rules.clear();
    }

    public static RecipeRoutingHints matchingHints(RecipeHeuristicContext context) {
        RecipeRoutingHints merged = RecipeRoutingHints.empty();
        for (RecipeRoutingHeuristicRule rule : rules) {
            if (rule.matches(context)) {
                merged = merged.merge(rule.hints());
            }
        }
        return merged;
    }

    private static Namespace parseNamespace(String id) {
        Checks.checkNotNull(id, "id");
        int colon = id.indexOf(':');
        if (colon > 0 && colon < id.length() - 1) {
            return Namespace.of(id.substring(0, colon), id.substring(colon + 1));
        }
        return Namespace.of("cirrus", id);
    }

    public static final class Builder {
        private final Namespace ruleId;
        private final ArrayList<Predicate<RecipeHeuristicContext>> matchers = new ArrayList<>();
        private final RecipeRoutingHints.Builder hints = RecipeRoutingHints.builder();

        private Builder(Namespace ruleId) {
            this.ruleId = Checks.checkNotNull(ruleId, "ruleId");
        }

        public Builder match(Predicate<RecipeHeuristicContext> matcher) {
            matchers.add(Checks.checkNotNull(matcher, "matcher"));
            return this;
        }

        public Builder always() {
            return match(context -> true);
        }

        public Builder system(String systemId) {
            return match(context -> context.system(systemId));
        }

        public Builder machine(String machineId) {
            return match(context -> context.machine(machineId));
        }

        public Builder recipeIdContains(String fragment) {
            return match(context -> context.recipeIdContains(fragment));
        }

        public Builder recipePathContains(String fragment) {
            return match(context -> context.recipePathContains(fragment));
        }

        public Builder input(String id) {
            return match(context -> context.input(id));
        }

        public Builder output(String id) {
            return match(context -> context.output(id));
        }

        public Builder policy(RecipeUsePolicy policy) {
            hints.policy(policy);
            return this;
        }

        public Builder discouraged() {
            hints.discouraged();
            return this;
        }

        public Builder lastResort() {
            hints.lastResort();
            return this;
        }

        public Builder disabledByDefault() {
            hints.disabledByDefault();
            return this;
        }

        public Builder targetOnly() {
            hints.targetOnly();
            return this;
        }

        public Builder recycling() {
            hints.recycling();
            return this;
        }

        public Builder role(Namespace role) {
            hints.role(role);
            return this;
        }

        public Builder penalty(int penalty) {
            hints.penalty(penalty);
            return this;
        }

        public Builder addPenalty(int penalty) {
            hints.addPenalty(penalty);
            return this;
        }

        public Builder reasonKey(String reasonKey) {
            hints.reasonKey(reasonKey);
            return this;
        }

        public RecipeRoutingHeuristicRule register() {
            List<Predicate<RecipeHeuristicContext>> effectiveMatchers = matchers.isEmpty()
                ? List.of(context -> true)
                : List.copyOf(matchers);
            RecipeRoutingHeuristicRule rule = new RecipeRoutingHeuristicRule(ruleId, effectiveMatchers, hints.build());
            rules.add(rule);
            return rule;
        }
    }
}
