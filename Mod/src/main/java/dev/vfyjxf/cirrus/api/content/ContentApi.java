package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface ContentApi {

    static ContentApi instance() {
        ContentApi instance = ContentApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("ContentApi is not initialized");
        }
        return instance;
    }

    //region content type

    boolean hasContentType(Namespace typeId);

    <T> ContentType<T> getContentType(Namespace typeId);

    default <T> @Nullable ContentType
        <T> getContentType(String typeId) {
        return getContentType(Namespace.parse(typeId));
    }
    //endregion

    //region stack type

    boolean hasStackType(Namespace typeId);

    boolean hasStackType(ContentType<?> contentType);

    <T, S> StackType<T, S> getStackType(Namespace typeId);

    <T> StackType<T, ?> getStackType(ContentType<T> contentType);

    default <T, S> @Nullable StackType<T, S> getStackType(String typeId) {
        return getStackType(Namespace.parse(typeId));
    }
    //endregion

    //region tag type

    boolean hasTagType(Namespace typeId);

    boolean hasTagType(ContentType<?> contentType);

    <T> TagType<T> getTagType(Namespace typeId);

    <T> TagType<T> getTagType(ContentType<T> contentType);

    //endregion

    //region variant type

    boolean hasVariantType(Namespace typeId);

    <T, S> VariantType<T, S> getVariantType(Namespace typeId);

    /**
     * @param stackType the stack type
     * @param content   to find the variant type for
     * @param <T>       the content type
     * @param <S>       the stack type
     * @return the variant type for the given stack and content, or {@link VariantType#fallback(StackType)} if no variant type is found
     */
    <T, S> VariantType<T, S> findVariantType(StackType<T, S> stackType, T content);

    //endregion

    //region resolver

    boolean hasContentResolver(ContentResolverType<?> type);

    boolean hasStackResolver(ContentResolverType<?> type);

    <T, R> @UnmodifiableView RichIterable<ContentResolver<T, R>> getResolver(ContentResolverType<R> type, ContentType<T> contentType);

    <T, S, R> @UnmodifiableView RichIterable<StackResolver<T, S, R>> getStackResolver(ContentResolverType<R> type, StackType<T, S> stackType);

    <T, S, R> @Nullable StackResolver<T, S, R> findStackResolver(ContentResolverType<R> type, StackType<T, S> stackType, S stack);

    <T, R> @Nullable ContentResolver<T, R> findContentResolver(ContentResolverType<R> type, ContentType<T> contentType, T content);

    //endregion

    //region type instances

    @UnmodifiableView
    RichIterable<? extends ContentType<?>> contentTypes();

    @UnmodifiableView
    RichIterable<? extends StackType<?, ?>> stackTypes();

    @UnmodifiableView
    RichIterable<? extends TagType<?>> tagTypes();

    RichIterable<? extends VariantType<?, ?>> variantTypes();

    //endregion

}
