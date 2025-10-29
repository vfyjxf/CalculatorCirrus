package dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver;

import net.minecraft.resources.ResourceLocation;

public record IngredientResolverType<T>(
    ResourceLocation id
) {
    public static <T> IngredientResolverType<T> of(ResourceLocation id) {
        return new IngredientResolverType<>(id);
    }
}
