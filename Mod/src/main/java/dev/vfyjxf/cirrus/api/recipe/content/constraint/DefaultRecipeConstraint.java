package dev.vfyjxf.cirrus.api.recipe.content.constraint;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;

class DefaultRecipeConstraint<R> implements RecipeConstraint<R> {

    static final RecipeConstraint<?> instance = new DefaultRecipeConstraint<>();

    private DefaultRecipeConstraint() {}

    @Override
    public boolean satisfy(RecipeEntry<R> entry, InputContent expect, ContentVariant actual) {
        return expect.satisfy(actual);
    }
}
