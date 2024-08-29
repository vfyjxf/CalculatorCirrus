package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import org.eclipse.collections.api.list.MutableList;

/**
 * A simple ingredient calculator.
 * <p>
 * Only support single path calculation.
 * <p>
 * Do not support loop calculation.
 */
public class SimpleCalculator implements RecipeCalculator {

    private Object recipe;

    @Override
    public MutableList<ContentStack<?>> targets() {
        return null;
    }

    @Override
    public Object getRecipe() {
        return null;
    }

    @Override
    public void setRecipe(Object recipe) {

    }

    @Override
    public <T> void addPath(RecipeSystem<T> recipeSystem, T recipe) {

    }
}
