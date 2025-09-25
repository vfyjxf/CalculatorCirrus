package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import net.minecraft.resources.ResourceLocation;

/**
 * @param <R> the return type
 * @param id  the resolver type id
 */
@SuppressWarnings("unused")
public record ResolverType<R>(
    ResourceLocation id
) {

    public static <T> ResolverType<T> of(ResourceLocation id) {
        return new ResolverType<>(id);
    }

}
