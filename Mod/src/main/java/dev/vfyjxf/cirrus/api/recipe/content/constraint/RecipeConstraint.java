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

    @SuppressWarnings("unchecked")
    static <R> RecipeConstraint<R> ingredientConstraint() {
        return (RecipeConstraint<R>) DefaultRecipeConstraint.INSTANCE;
    }

    boolean satisfy(RecipeEntry<R> entry, InputContent expect, ContentVariant actual);

}

class DefaultRecipeConstraint<R> implements RecipeConstraint<R> {

    static final RecipeConstraint<?> INSTANCE = new DefaultRecipeConstraint<>();

    private DefaultRecipeConstraint() {}

    @Override
    public boolean satisfy(RecipeEntry<R> entry, InputContent expect, ContentVariant actual) {
        return expect.satisfy(actual);
    }
}