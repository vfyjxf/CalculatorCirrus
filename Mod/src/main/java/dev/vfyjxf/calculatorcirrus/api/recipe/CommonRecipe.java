package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.recipe.CommonRecipeImpl;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

public interface CommonRecipe<T> {

    static <T> CommonRecipe<T> create(RecipeSystem<T> system, T recipe) {
        return new CommonRecipeImpl<>(system, recipe);
    }

    RecipeSystem<T> recipeSystem();

    T recipe();

    default @Nullable ResourceLocation id() {
        return recipeSystem().getRecipeId(recipe());
    }

    RecipeData data();

    default MutableList<? extends IngredientStack> getInputs() {
        return data().getInputs();
    }

    default MutableList<? extends IngredientStack> getOutputs() {
        return data().getOutputs();
    }

    default MutableList<? extends IngredientStack> getCatalysts() {
        return data().getCatalysts();
    }

}
