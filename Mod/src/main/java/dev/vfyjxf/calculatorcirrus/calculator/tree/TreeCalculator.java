package dev.vfyjxf.calculatorcirrus.calculator.tree;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipe.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.calculator.RecipeCalculator;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

public class TreeCalculator implements RecipeCalculator {
    @Override
    public MutableList<? extends IngredientStack> targets() {
        return null;
    }

    @Override
    public void setTarget(@Nullable ContentStack<?> target) {

    }

    @Override
    public @Nullable ContentStack<?> getTarget() {
        return null;
    }

    @Override
    public CommonRecipe<?> getRecipe() {
        return null;
    }

    @Override
    public void setAmount(@Range(from = 1, to = Integer.MAX_VALUE) int amount) {

    }

    @Override
    public @Range(from = 1, to = Integer.MAX_VALUE) int getAmount() {
        return 0;
    }

    @Override
    public <T> void setRecipe(RecipeSystem<T> recipeSystem, T recipe) {

    }

    @Override
    public void addPath(ContentStack<?> target, CommonRecipe<?> recipe) {

    }

    @Override
    public void addTransform(RecipeTransformer transformer) {

    }

    @Override
    public void setDataBase(@Nullable RecipeDatabase dataBase) {

    }

    @Override
    public void calculate() {

    }

    @Override
    public MutableList<IngredientStack> getResults() {
        return null;
    }

    @Override
    public MutableListMultimap<IngredientType, ? extends IngredientStack> getCalculations() {
        return null;
    }
}
