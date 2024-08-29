package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public interface VanillaRecipeSystem<T extends Recipe<?>> extends RecipeSystem<RecipeHolder<T>> {

    RecipeType<T> recipeType();

    @Override
    default Class<?> basicRecipeType() {
        return RecipeHolder.class;
    }

    @Override
    default boolean supportsRecipe(RecipeHolder<T> recipe) {
        return recipeType() == recipe.value().getType();
    }
}
