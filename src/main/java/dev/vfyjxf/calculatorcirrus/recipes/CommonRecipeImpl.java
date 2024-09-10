package dev.vfyjxf.calculatorcirrus.recipes;

import com.google.common.base.Suppliers;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;

import java.util.function.Supplier;

public class CommonRecipeImpl<T> implements CommonRecipe<T> {

    private final RecipeSystem<T> recipeSystem;
    private final T recipe;
    private final Supplier<RecipeData> recipeData;

    public CommonRecipeImpl(RecipeSystem<T> recipeSystem, T recipe) {
        this.recipeSystem = recipeSystem;
        this.recipe = recipe;
        this.recipeData = Suppliers.memoize(() -> recipeSystem.parseRecipe(recipe));
    }

    @Override
    public RecipeSystem<T> recipeSystem() {
        return recipeSystem;
    }

    @Override
    public T recipe() {
        return recipe;
    }

    @Override
    public RecipeData data() {
        return recipeData.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommonRecipeImpl<?> that = (CommonRecipeImpl<?>) o;
        return recipeSystem.equals(that.recipeSystem) && recipe.equals(that.recipe);
    }

    @Override
    public int hashCode() {
        int result = recipeSystem.hashCode();
        result = 31 * result + recipe.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CommonRecipeImpl{" +
                "recipe=" + recipe +
                ", recipeSystem=" + recipeSystem +
                '}';
    }
}
