package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Budget limits governing cyclic (loop) expansion during planning.
 *
 * <p>These limits are enforced by the {@link dev.vfyjxf.cirrus.api.recipe.routing.state.CycleGuard}
 * and prevent the planner from spiraling into unbounded recursive expansion
 * when the recipe graph contains cycles.
 *
 * @param maxRepeatsPerScc          maximum number of times any recipe within the same
 *                                  strongly connected component may be re-entered
 * @param maxRepeatsPerRecipe       maximum number of times a specific recipe may be
 *                                  expanded across the entire planning run
 * @param maxLoopExpansionsGlobal   hard cap on the total number of loop-induced
 *                                  expansions across all recipes and SCCs
 * @see LoopPolicy
 */
public record LoopBudget(
    int maxRepeatsPerScc,
    int maxRepeatsPerRecipe,
    int maxLoopExpansionsGlobal
) {
    public LoopBudget {
        Checks.checkArgument(maxRepeatsPerScc > 0, "maxRepeatsPerScc must be positive");
        Checks.checkArgument(maxRepeatsPerRecipe > 0, "maxRepeatsPerRecipe must be positive");
        Checks.checkArgument(maxLoopExpansionsGlobal > 0, "maxLoopExpansionsGlobal must be positive");
    }

    /** Default loop budget for {@link PlannerMode#standard} — conservative limits. */
    public static LoopBudget standardDefault() {
        return new LoopBudget(3, 4, 40);
    }

    /** Default loop budget for assured modes — more generous limits. */
    public static LoopBudget assuredDefault() {
        return new LoopBudget(12, 16, 400);
    }
}

