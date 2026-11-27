package dev.vfyjxf.cirrus.api.recipe.content.resolver;

import dev.vfyjxf.cloudlib.api.util.Namespace;

public record IngredientResolverType<T>(Namespace id) {
    public static <T> IngredientResolverType<T> of(Namespace id) {
        return new IngredientResolverType<>(id);
    }
}
