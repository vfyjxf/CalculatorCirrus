package dev.vfyjxf.calculatorcirrus.api.recipe;


import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientGroup;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import org.eclipse.collections.api.multimap.MutableMultimap;

/**
 * Transformers such as machine levels, plug-ins, and upgrades that affect recipe getStableData
 */
public interface RecipeTransformer {

    boolean shouldTransform(CommonRecipe<?> recipe);

    void transformChance(RecipeData data, RecipeContext context, MutableMultimap<IngredientGroup, ? extends IngredientStack> recipeData);

    void transformMutableData(RecipeData data, RecipeContext context, MutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> modifiable);

}
