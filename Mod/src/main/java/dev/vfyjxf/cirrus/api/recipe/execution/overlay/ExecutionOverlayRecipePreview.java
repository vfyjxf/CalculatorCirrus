package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Compact recipe preview payload carried by execution overlay timeline nodes.
 */
public record ExecutionOverlayRecipePreview(
    RecipeEntry<?> recipe,
    int times,
    ImmutableList<InputBinding> inputs,
    ImmutableList<OutputBinding> outputs
) {

    public ExecutionOverlayRecipePreview {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkArgument(times > 0, "times must be positive");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
    }

    public static ExecutionOverlayRecipePreview recipeOnly(RecipeEntry<?> recipe, int times) {
        return new ExecutionOverlayRecipePreview(
            recipe,
            Math.max(1, times),
            Lists.immutable.empty(),
            Lists.immutable.empty()
        );
    }

    public boolean hasBoundMaterials() {
        return inputs.notEmpty() || outputs.notEmpty();
    }
}
