package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Budget limits governing cyclic (loop) expansion during planning.
 *
 * <p>These limits are enforced by the {@link dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard}
 * and prevent the planner from spiraling into unbounded recursive expansion
 * when the recipe graph contains cycles.
 *
 * @param maxActiveRepeatsPerRecipe maximum number of active stack entries allowed
 *                                  for the same recipe
 * @param maxLoopExpansionsGlobal   hard cap on the total number of accepted
 *                                  loop-induced expansions across all recipes
 * @see LoopPolicy
 */
public record LoopBudget(
    int maxActiveRepeatsPerRecipe,
    int maxLoopExpansionsGlobal
) {
    public LoopBudget {
        Checks.checkArgument(maxActiveRepeatsPerRecipe > 0, "maxActiveRepeatsPerRecipe must be positive");
        Checks.checkArgument(maxLoopExpansionsGlobal > 0, "maxLoopExpansionsGlobal must be positive");
    }

    /** Default loop budget for {@link PlannerMode#standard} — conservative limits. */
    public static LoopBudget standardDefault() {
        return new LoopBudget(4, 40);
    }

    /** Default loop budget for assured modes — more generous limits. */
    public static LoopBudget assuredDefault() {
        return new LoopBudget(16, 400);
    }
}

