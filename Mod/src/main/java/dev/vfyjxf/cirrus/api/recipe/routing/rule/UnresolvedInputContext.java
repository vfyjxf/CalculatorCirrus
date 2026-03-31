package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Context exposed to {@link UnresolvedInputRule} when an input cannot be
 * fully satisfied by stock, byproducts, or recursive recipe expansion.
 *
 * @param recipe the recipe currently being resolved
 * @param input the unresolved input
 * @param amount the remaining unresolved amount
 * @param phase where the planner got stuck
 * @param activeCycleCandidate whether the exhausted candidate set included a recipe already on the active path
 */
public record UnresolvedInputContext(
    RecipeEntry<?> recipe,
    InputContent input,
    long amount,
    Phase phase,
    boolean activeCycleCandidate
) {
    public UnresolvedInputContext {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(input, "input");
        Checks.checkNotNull(phase, "phase");
        Checks.checkArgument(amount > 0, "amount must be positive");
    }

    public enum Phase {
        noSource,
        candidateExhausted
    }
}
