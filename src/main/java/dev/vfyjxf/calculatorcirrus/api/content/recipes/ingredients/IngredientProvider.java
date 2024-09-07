package dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import org.eclipse.collections.api.list.MutableList;

/**
 * The Container which provides the ingredients for a recipe.
 */
public interface IngredientProvider {

    MutableList<ContentStack<?>> getContents();

}
