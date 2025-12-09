package dev.vfyjxf.cirrus.api.recipe.content.resolver;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;

public interface IngredientResolver<T> {

    boolean select(T ingredient);

    InputContent resolve(T ingredient, long amount, float chance);

}
