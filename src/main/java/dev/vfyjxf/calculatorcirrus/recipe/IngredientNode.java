package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

public class IngredientNode {

    private final IngredientStack ingredient;
    private final @Nullable CommonRecipe<?> recipe;
    private @Nullable MutableList<IngredientNode> children;

    public IngredientNode(IngredientStack ingredient, @Nullable CommonRecipe<?> recipe) {
        this.ingredient = ingredient;
        this.recipe = recipe;
    }
}
