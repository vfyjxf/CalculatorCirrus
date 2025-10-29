package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import net.minecraft.resources.ResourceLocation;

/**
 * @param <R> the return type
 * @param id  the resolver type id
 */
@SuppressWarnings("unused")
public record ContentResolverType<R>(
    ResourceLocation id
) {

    public static <T> ContentResolverType<T> of(ResourceLocation id) {
        return new ContentResolverType<>(id);
    }

}
