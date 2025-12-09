package dev.vfyjxf.cirrus.api.content.resolver;

import dev.vfyjxf.cloudlib.api.util.Namespace;

/**
 * @param <R> the return type
 * @param id  the resolver type id
 */
@SuppressWarnings("unused")
public record ContentResolverType<R>(
    Namespace id
) {

    public static <T> ContentResolverType<T> of(Namespace id) {
        return new ContentResolverType<>(id);
    }

}
