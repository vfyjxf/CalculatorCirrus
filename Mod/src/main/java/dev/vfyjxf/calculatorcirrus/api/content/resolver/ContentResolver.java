package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;

public interface ContentResolver<T, R> {

    ContentResolverType<R> type();

    ContentType<T> contentType();

    boolean resolvable(T content);

    R resolve(T content);

    @SuppressWarnings("unchecked")
    default <A, B> ContentResolver<A, B> cast() {
        return (ContentResolver<A, B>) this;
    }

}
