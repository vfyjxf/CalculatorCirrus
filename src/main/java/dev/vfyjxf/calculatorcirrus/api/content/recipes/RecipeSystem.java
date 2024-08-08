package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public interface RecipeSystem<T> {

    default String modId() {
        return systemId().getNamespace();
    }

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
        ResourceLocation registryName = getRegistryName(recipe);
        if (registryName == null) return null;
        else return registryName.toString();
    }

    @Nullable
    ResourceLocation getRegistryName(T recipe);

    @Nullable
    T getRecipe(String uniqueId);

    @Nullable
    T getRecipe(ResourceLocation registryName);

    RecipeData parseRecipe(T recipe);

}
