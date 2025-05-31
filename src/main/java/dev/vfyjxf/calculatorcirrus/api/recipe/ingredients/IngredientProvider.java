package dev.vfyjxf.calculatorcirrus.api.recipe.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import org.eclipse.collections.api.list.MutableList;

/**
 * The Container which provides the ingredients for a recipe.
 */
public interface IngredientProvider {

    MutableList<ContentStack<?>> getContents();

}
