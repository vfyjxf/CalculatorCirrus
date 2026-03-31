package dev.vfyjxf.cirrus.api.recipe.content.constraint;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;

/**
 * Content constraint from recipe.
 *
 * @param <R> the recipe type
 * @see MachineConstraint
 */
public interface RecipeConstraint<R> {

    /**
     * Get the default recipe constraint.
     * @param <R> the recipe type
     * @return the default recipe constraint.
     */
    @SuppressWarnings("unchecked")
    static <R> RecipeConstraint<R> ingredientConstraint() {
        return (RecipeConstraint<R>) DefaultRecipeConstraint.instance;
    }

    /**
     * Whether this content satisfies the actual content.
     * @param entry the recipe entry.
     * @param expect the expected content.
     * @param actual the actual content.
     * @return true if this content satisfies the actual content.
     */
    boolean satisfy(RecipeEntry<R> entry, InputContent expect, ContentVariant actual);

}

class DefaultRecipeConstraint<R> implements RecipeConstraint<R> {

    static final RecipeConstraint<?> instance = new DefaultRecipeConstraint<>();

    private DefaultRecipeConstraint() {}

    @Override
    public boolean satisfy(RecipeEntry<R> entry, InputContent expect, ContentVariant actual) {
        return expect.satisfy(actual);
    }
}