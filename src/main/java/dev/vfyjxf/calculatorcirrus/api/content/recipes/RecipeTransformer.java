package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.multimap.ImmutableMultimap;
import org.eclipse.collections.api.multimap.MutableMultimap;

/**
 * Transformers such as machine levels, plug-ins, and upgrades that affect recipe getStableData
 */
public interface RecipeTransformer {

    void transformChance(RecipeData data, RecipeContext context, MutableMultimap<IngredientType, ? extends IngredientStack<?>> recipeData);

    void transformMutableData(RecipeData data, RecipeContext context, MutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> modifiable);

}
