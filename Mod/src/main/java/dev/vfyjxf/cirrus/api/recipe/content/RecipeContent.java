package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
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

    /**
     * Whether this content is empty.
     * @return true if this content is empty.
     */
    boolean isEmpty();

    /**
     * Whether this content is not empty.
     * @return true if this content is not empty.
     */
    default boolean notEmpty() {
        return !isEmpty();
    }

    /**
     * The amount of this content.
     * @return the amount of this content.
     */
    @Range(from = 0, to = Long.MAX_VALUE)
    long amount();

    /**
     * The consumption/production chance of this content.
     * if chance of {@link InputContent} is {@link RecipeContent#MAX_CHANCE}, input will be recognized as catalyst.
     * @return the chance of this content.
     * @see #constantly()
     * @see InputContent
     * @see OutputContent
     */
    @Range(from = 0, to = 1)
    float chance();

    /**
     * Whether this content is constantly consumed/produced.
     * @return true if this content is constantly consumed/produced.
     * @see #chance()
     * @see InputContent
     * @see OutputContent
     */
    default boolean constantly() {
        return chance() == MAX_CHANCE ||
               chance() == MIN_CHANCE;
    }

    default RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.of(this);
    }

    //endregion

    //region copy

    /**
     * Create a copy of this content.
     * @return a copy of this content.
     */
    RecipeContent copy();

    /**
     * Create a copy of this content with a new amount.
     * @param amount the new amount.
     * @return a copy of this content with a new amount.
     */
    RecipeContent copyWithAmount(long amount);

    /**
     * Create a copy of this content with a new chance.
     * @param chance the new chance.
     * @return a copy of this content with a new chance.
     */
    RecipeContent copyWithChance(float chance);

    /**
     * Create a copy of this content with a new amount and chance.
     * @param amount the new amount.
     * @param chance the new chance.
     * @return a copy of this content with a new amount and chance.
     */
    RecipeContent copyWithAmountAndChance(long amount, float chance);

    //endregion

    //region match

    /**
     * Whether this content can be merged with another content.
     * @param other the other content.
     * @return true if this content can be merged with the other content.
     */
    boolean mergeable(RecipeContent other);

    //endregion

    //region constraint

    /**
     * Whether this content satisfies the actual content.
     * @param actual the actual content.
     * @return true if this content satisfies the actual content.
     */
    boolean satisfy(ContentVariant actual);

    //endregion
}
