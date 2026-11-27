package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Runtime input passed to an interaction performer for one recipe execution step.
 */
public record InteractionContext(
    RecipeEntry<?> recipe,
    int times,
    ImmutableList<InputBinding> inputs,
    ImmutableList<OutputBinding> expectedOutputs
) {

    public InteractionContext {
        Checks.checkArgument(times > 0, "times must be positive");
    }
}
