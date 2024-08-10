package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public interface RecipeSystem<T> {

    static ResourceLocation createId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "recipe_system/" + path);
    }

    default String modId() {
        return systemId().getNamespace();
    }

    ResourceLocation systemId();

    Class<? extends T> basicRecipeType();

    default boolean supportsRecipe(T recipe) {
        return containsRecipe(recipe);
    }

    default boolean supportsRecipe(Class<?> recipeType) {
        return basicRecipeType().isAssignableFrom(recipeType);
    }

    default boolean containsRecipe(T recipe) {
        return basicRecipeType().isInstance(recipe);
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
