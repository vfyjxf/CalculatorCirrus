package dev.vfyjxf.cirrus.internal.integration.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;

public final class JeiRecipeCategoryFilter {
    private JeiRecipeCategoryFilter() {}

    public static boolean include(RecipeType<?> recipeType) {
        return !excluded(recipeType);
    }

    public static boolean excluded(RecipeType<?> recipeType) {
        if (RecipeTypes.INFORMATION.equals(recipeType)) {
            return true;
        }
        // JEI's per-registry tag-info entries are editor metadata, not craftable recipes.
        // 19.28+ registers them as "<registry_ns>:tag_recipes/<registry_path>";
        // older versions used "<ns>:item_tag_information"-style paths.
        String path = recipeType.getUid().getPath();
        return path.startsWith("tag_recipes/") || path.endsWith("_tag_information") || path.equals("tag_information");
    }
}
