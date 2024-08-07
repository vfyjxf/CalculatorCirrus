package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IIngredientStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface IRecipeHandler<R> {

    /**
     * Get the recipe's uid.
     *
     * @param recipe The recipe to parse.
     * @return The recipe's uid, or null if the recipe is not supported.
     */
    @Nullable
    ResourceLocation getRecipeUid(R recipe);

    /**
     * Different types from ingredients in a recipe.
     * Each element in the list represents a "slot".
     */
    Map<IngredientType, List<IIngredientStack<?>>> parse(R recipe);

}
