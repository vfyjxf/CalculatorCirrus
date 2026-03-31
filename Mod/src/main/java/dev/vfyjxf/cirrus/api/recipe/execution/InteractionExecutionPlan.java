package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * In-memory runtime plan produced by translating a routing plan into interaction steps.
 */
public record InteractionExecutionPlan(
    RecipeEntry<?> targetRecipe,
    int targetTimes,
    ImmutableList<InteractionStep> steps
) {

    public InteractionExecutionPlan {
        Checks.checkArgument(targetTimes > 0, "targetTimes must be positive");
    }

    //region derived state

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public ImmutableList<ExecutionCapability> requiredCapabilities() {
        return steps.collect(InteractionStep::requiredCapability).distinct().toImmutable();
    }

    //endregion
}
