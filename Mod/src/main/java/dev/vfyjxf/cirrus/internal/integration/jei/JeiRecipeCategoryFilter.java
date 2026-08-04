package dev.vfyjxf.cirrus.internal.integration.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.plugins.jei.tags.ITagInfoRecipe;

public final class JeiRecipeCategoryFilter {
    private JeiRecipeCategoryFilter() {
    }

    public static boolean include(RecipeType<?> recipeType) {
        return !excluded(recipeType);
    }

    public static boolean excluded(RecipeType<?> recipeType) {
        return RecipeTypes.INFORMATION.equals(recipeType)
                || recipeType.getRecipeClass().isAssignableFrom(ITagInfoRecipe.class);
    }
}
