package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeTransformer;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TagIngredientStack;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

public interface RecipeCalculator {

    MutableList<? extends IngredientStack> targets();

    void setTarget(@Nullable ContentStack<?> target);

    @Nullable
    ContentStack<?> getTarget();

    /**
     * @return the target recipe to calculate.
     */
    CommonRecipe<?> getRecipe();

    /**
     * @param amount the Number of times the target recipe should be crafted.
     */
    void setAmount(@Range(from = 1, to = Integer.MAX_VALUE) int amount);

    /**
     * @return the Number of times the target recipe should be crafted.
     */
    @Range(from = 1, to = Integer.MAX_VALUE)
    int getAmount();

    <T> void setRecipe(RecipeSystem<T> recipeSystem, T recipe);

    void addPath(ContentStack<?> target, CommonRecipe<?> recipe);

    default <T> void addPath(ContentStack<?> target, RecipeSystem<T> recipeSystem, T recipe) {
        addPath(target, CommonRecipe.create(recipeSystem, recipe));
    }

    default void addPath(IngredientStack ingredient, CommonRecipe<?> recipe) {
        if (ingredient.isSingle()) {
            addPath(ingredient.getStacks().getOnly(), recipe);
        } else {
            if (ingredient instanceof TagIngredientStack tagIngredient) {
                addPath(ContentStack.create(tagIngredient.getType().cast(), tagIngredient.getTagKey()), recipe);
            } else if (ingredient.isList()) {
                ContentStack<?> detect = ingredient.getStacks().detect(stack -> recipe.data().getOutputs().anySatisfy(output -> output.contains(stack)));
                if (detect != null) {
                    addPath(detect, recipe);
                } else {
                    throw new IllegalArgumentException("The ingredient isn't an output of the recipe.");
                }
            } else {
                throw new IllegalArgumentException("Unsupported ingredient type: " + ingredient.getClass());
            }
        }
    }

    default <T> void addPath(IngredientStack ingredient, RecipeSystem<T> recipeSystem, T recipe) {
        addPath(ingredient, CommonRecipe.create(recipeSystem, recipe));
    }

    void addTransform(RecipeTransformer transformer);

    void setDataBase(@Nullable RecipeDatabase dataBase);

    void calculate();

    MutableList<IngredientStack> getResults();

    MutableListMultimap<IngredientType, ? extends IngredientStack> getCalculations();

}
