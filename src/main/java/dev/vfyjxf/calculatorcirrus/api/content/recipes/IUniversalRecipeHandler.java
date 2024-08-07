package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IIngredientStack;

import java.util.List;
import java.util.Map;

public interface IUniversalRecipeHandler extends IRecipeHandler<Object> {

    <T> boolean isSupport(Class<T> recipeClass);

    @Override
    Map<IngredientType, List<IIngredientStack<?>>> parse(Object recipe);
}
