package dev.vfyjxf.calculatorcirrus.api.recipe.routing;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.TransformedRecipeView;

public interface RecipeContentResolution {

    Result resolve(TransformedRecipeView recipeView, InputContent input);

    sealed interface Result {}

    record ResolutionResult(
        RecipeEntry<?> recipe,
        OutputContent output
    ) implements Result {}

    enum Nothing implements Result {
        INSTANCE
    }

}
