package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ContentResolverRegister {

    <T, R> void registerContentResolver(ResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver);

    <T, R> void registerFallbackContentResolver(ResolverType<R> type, ContentType<T> contentType, ContentResolver<T, R> resolver);

    <T, S, R> void registerStackResolver(ResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver);

    <T, S, R> void registerFallbackStackResolver(ResolverType<R> type, StackType<T, S> stackType, StackResolver<T, S, R> resolver);

}
