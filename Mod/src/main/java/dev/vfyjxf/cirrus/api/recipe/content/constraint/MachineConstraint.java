package dev.vfyjxf.cirrus.api.recipe.content.constraint;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

/**
 * Content constraint from machine.
 *
 * @see RecipeConstraint
 */
public interface MachineConstraint {

    MachineConstraint empty = (recipe, machine, expect, actual) -> true;

    boolean satisfy(RecipeEntry<?> recipe, MachineEntry machine, InputContent expect, ContentVariant actual);

}
