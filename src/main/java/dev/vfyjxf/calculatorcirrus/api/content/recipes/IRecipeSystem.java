package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public interface IRecipeSystem<T> {

    String modSource();

    ResourceLocation systemId();

    Class<? extends T> basicRecipeType();

    default boolean containsRecipe(T recipe) {
        return basicRecipeType().isInstance(recipe);
    }

    default boolean containsRecipe(Class<T> recipeType) {
        return basicRecipeType().isAssignableFrom(recipeType);
    }

    @Nullable
    default String getUniqueId(T recipe) {
        return getRegistryName(recipe).toString();
    }

    ResourceLocation getRegistryName(T recipe);

    @Nullable
    T getRecipe(String uniqueId);

    @Nullable
    T getRecipe(ResourceLocation registryName);

    IRecipeData parseRecipe(T recipe);

}
