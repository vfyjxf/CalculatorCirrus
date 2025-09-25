package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;

/**
 *  Aka Stack Property Extractor
 * @param <T> the content type
 * @param <S> the stack type
 * @param <R> the resolver result type
 * @see ContentResolver
 */
public interface StackResolver<T, S, R> {

    ResolverType<R> type();

    StackType<T, S> stackType();

    boolean resolvable(T content, S stack);

    R resolve(T content, S stack);

    @SuppressWarnings("unchecked")
    default <A, B, C> StackResolver<A, B, C> cast() {
        return (StackResolver<A, B, C>) this;
    }

}
