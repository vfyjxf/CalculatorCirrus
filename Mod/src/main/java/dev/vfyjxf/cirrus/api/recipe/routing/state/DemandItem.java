package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * One unresolved input demand in the planner's work queue.
 *
 * <p>Each {@code DemandItem} represents a specific amount of a specific input
 * that the planner still needs to resolve, either by consuming from stock or
 * by finding a producer recipe.
 *
 * @param input           the input content that is demanded
 * @param amount          the remaining amount needed (must be positive)
 * @param depth           the search depth at which this demand was created
 * @param fromRecipeIndex the catalog index of the recipe that created this demand,
 *                        or {@code -1} if this is the root demand
 */
public record DemandItem(
    InputContent input,
    long amount,
    int depth,
    int fromRecipeIndex
) {
    public DemandItem {
        Checks.checkNotNull(input, "input");
        Checks.checkArgument(amount > 0, "amount must be positive");
        Checks.checkArgument(depth >= 0, "depth must be non-negative");
        Checks.checkArgument(fromRecipeIndex >= -1, "fromRecipeIndex must be -1 or greater");
    }
}

