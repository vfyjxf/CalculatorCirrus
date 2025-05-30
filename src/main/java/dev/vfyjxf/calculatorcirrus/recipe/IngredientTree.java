package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeDatabase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

public class IngredientTree {

    private final CommonRecipe<?> target;
    /**
     * Number of times the target recipe should be crafted.
     */
    @Range(from = 1, to = Integer.MAX_VALUE)
    private final int amount;
    @Nullable
    private final RecipeDatabase dataBase;

    public IngredientTree(CommonRecipe<?> target, @Nullable RecipeDatabase dataBase) {
        this(target, 1, dataBase);
    }

    public IngredientTree(CommonRecipe<?> target, @Range(from = 1, to = Integer.MAX_VALUE) int amount, @Nullable RecipeDatabase dataBase) {
        this.target = target;
        this.amount = amount;
        this.dataBase = dataBase;
        var targetData = target.data();
        if (targetData.getInputs().isEmpty() || targetData.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("Invalid target recipe.");
        }
    }
}
