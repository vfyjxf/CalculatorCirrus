package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import org.eclipse.collections.api.list.MutableList;

public interface RecipeCalculator {

    MutableList<ContentStack<?>> targets();

    /**
     * @return the target recipe to calculate.
     */
    Object getRecipe();

    void setRecipe(Object recipe);

    <T> void addPath(RecipeSystem<T> recipeSystem, T recipe);


}
