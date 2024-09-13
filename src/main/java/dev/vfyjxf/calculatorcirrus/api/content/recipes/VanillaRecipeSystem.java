package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VanillaRecipeSystem<T extends Recipe<?>> extends RecipeSystem<RecipeHolder<T>> {

    static RecipeManager recipeManager() {
        return RecipeHelper.getRecipeManager();
    }

    RecipeType<T> recipeType();

    @Override
    default Class<?> basicRecipeType() {
        return RecipeHolder.class;
    }

    @Override
    default boolean supportsRecipe(RecipeHolder<T> recipe) {
        return recipeType() == recipe.value().getType();
    }

    @Nullable
    @Override
    default ResourceLocation getRegistryName(RecipeHolder<T> recipe) {
        return recipe.id();
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    default RecipeHolder<T> getRecipe(ResourceLocation registryName) {
        RecipeType recipeType = recipeType();
        return ((List<RecipeHolder<T>>) recipeManager().getAllRecipesFor(recipeType)).stream()
                .filter(recipe -> recipe.id().equals(registryName))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    @Override
    default RecipeHolder<T> getRecipe(String uniqueId) {
        return getRecipe(Locations.parse(uniqueId));
    }

}
