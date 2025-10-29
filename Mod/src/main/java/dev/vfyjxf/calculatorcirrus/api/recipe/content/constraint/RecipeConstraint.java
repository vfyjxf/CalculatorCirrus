package dev.vfyjxf.calculatorcirrus.api.recipe.content.constraint;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;

/**
 * @param <R> the recipe type
 * @see MachineConstraint
 */
public interface RecipeConstraint<R> {

    boolean satisfy(RecipeEntry<R> entry, InputContent except, Object actual);

}
