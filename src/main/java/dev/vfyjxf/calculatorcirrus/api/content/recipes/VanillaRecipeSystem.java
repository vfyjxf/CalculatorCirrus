package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public interface VanillaRecipeSystem<T extends Recipe<?>> extends RecipeSystem<RecipeHolder<T>> {

    Class<? extends T> recipeType();

    @SuppressWarnings("unchecked")
    @Override
    default Class<? extends RecipeHolder<T>> basicRecipeType() {
        return (Class<? extends RecipeHolder<T>>) RecipeHolder.class;
    }

    @Override
    default boolean supportsRecipe(RecipeHolder<T> recipe) {
        return recipeType().isInstance(recipe.value());
    }

    @Override
    default boolean supportsRecipe(Class<?> recipeType) {
        return recipeType().isAssignableFrom(recipeType);
    }
}
