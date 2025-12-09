package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;

public interface IngredientResolverRegister {

    <T> void registerIngredientResolver(IngredientResolverType<T> type, IngredientResolver<T> resolver);

}
