package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import org.jetbrains.annotations.Range;

/**
 * Aka recipe data & constraint
 *
 * @see dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint
 * @see dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint
 */
public sealed interface RecipeContent permits InputContent, OutputContent {

    //region constants

    EmptyRecipeContent empty = new EmptyRecipeContent();

    float MAX_CHANCE = 1f;
    float MIN_CHANCE = 0f;

    static EmptyRecipeContent empty() {
        return empty;
    }

    //endregion

    //region basic info

    boolean isEmpty();

    default boolean notEmpty() {
        return !isEmpty();
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    long amount();

    @Range(from = 0, to = 1)
    float chance();

    default boolean constantly() {
        return chance() == MAX_CHANCE ||
               chance() == MIN_CHANCE;
    }

    //endregion

    //region copy

    RecipeContent copy();

    RecipeContent copyWithAmount(long amount);

    RecipeContent copyWithChance(float chance);

    RecipeContent copyWithAmountAndChance(long amount, float chance);

    //endregion

    //region match

    boolean mergeable(RecipeContent other);

    //endregion

    //region constraint

    boolean satisfy(ContentVariant actual);

    //endregion
}
