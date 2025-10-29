package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolverType;

public interface IngredientResolverRegister {

    <T> void registerIngredientResolver(IngredientResolverType<T> type, IngredientResolver<T> resolver);

}
