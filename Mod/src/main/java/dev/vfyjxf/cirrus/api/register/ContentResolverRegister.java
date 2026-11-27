package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ContentResolverRegister {

    <T, R> void registerContentResolver(
        ContentResolverType<R> type,
        ContentType<T> contentType,
        ContentResolver<T, R> resolver
    );

    <T, R> void registerFallbackContentResolver(
        ContentResolverType<R> type,
        ContentType<T> contentType,
        ContentResolver<T, R> resolver
    );

    <T, S, R> void registerStackResolver(
        ContentResolverType<R> type,
        StackType<T, S> stackType,
        StackResolver<T, S, R> resolver
    );

    <T, S, R> void registerFallbackStackResolver(
        ContentResolverType<R> type,
        StackType<T, S> stackType,
        StackResolver<T, S, R> resolver
    );

}
