package dev.vfyjxf.calculatorcirrus.api.recipe.system;

import dev.vfyjxf.calculatorcirrus.util.GameInstances;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public interface VanillaRecipeSystem<T extends Recipe<?>> extends RecipeSystem<RecipeHolder<T>> {

    static RecipeManager recipeManager() {
        return GameInstances.getRecipeManager();
    }

    @Nullable
    static <T extends Recipe<?>> VanillaRecipeSystem<T> byRecipeType(RecipeType<T> recipeType) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    RecipeType<T> recipeType();

    @Override
    default Class<?> basicType() {
        return RecipeHolder.class;
    }

    @Override
    default boolean supportsRecipe(RecipeHolder<T> recipe) {
        return recipeType() == recipe.value().getType();
    }

    @Nullable
    @Override
    default ResourceLocation getRecipeId(RecipeHolder<T> recipe) {
        return recipe.id();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    default RecipeHolder<T> getRecipe(ResourceLocation registryName) {
        RecipeType<T> recipeType = recipeType();
        return (RecipeHolder<T>) recipeManager().
            byKey(registryName)
            .filter(recipeHolder -> recipeHolder.value().getType() == recipeType)
            .orElse(null);
    }

    @Nullable
    @Override
    default RecipeHolder<T> getRecipe(String uniqueId) {
        return getRecipe(CirrusLocations.parse(uniqueId));
    }

}
