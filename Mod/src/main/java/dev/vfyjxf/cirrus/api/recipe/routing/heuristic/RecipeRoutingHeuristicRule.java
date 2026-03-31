package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;
import java.util.function.Predicate;

public record RecipeRoutingHeuristicRule(
    Namespace ruleId,
    List<Predicate<RecipeHeuristicContext>> matchers,
    RecipeRoutingHints hints
) {
    public RecipeRoutingHeuristicRule {
        Checks.checkNotNull(ruleId, "ruleId");
        Checks.checkNotNull(matchers, "matchers");
        Checks.checkNotNull(hints, "hints");
        matchers = List.copyOf(matchers);
    }

    public boolean matches(RecipeHeuristicContext context) {
        for (Predicate<RecipeHeuristicContext> matcher : matchers) {
            if (!matcher.test(context)) {
                return false;
            }
        }
        return true;
    }
}
