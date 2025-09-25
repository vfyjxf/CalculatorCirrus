package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface ContentApi {

    static ContentApi instance() {
        return Singletons.get(ContentApi.class);
    }

    //region content type

    boolean hasContentType(ResourceLocation typeId);

    <T> ContentType<T> getContentType(ResourceLocation typeId);

    default @Nullable <T> ContentType<T> getContentType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getContentType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getContentType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region stack type

    boolean hasStackType(ResourceLocation typeId);

    boolean hasStackType(ContentType<?> contentType);

    <T, S> StackType<T, S> getStackType(ResourceLocation typeId);

    <T> StackType<T, ?> getStackType(ContentType<T> contentType);

    default @Nullable <T, S> StackType<T, S> getStackType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getStackType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getStackType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region tag type

    boolean hasTagType(ResourceLocation typeId);

    boolean hasTagType(ContentType<?> contentType);

    <T> TagType<T> getTagType(ResourceLocation typeId);

    <T> TagType<T> getTagType(ContentType<T> contentType);

    //endregion

    //region variant type

    boolean hasVariantType(ResourceLocation typeId);

    <T, S> VariantType<T, S> getVariantType(ResourceLocation typeId);

    <T, S> @Nullable VariantType<T, S> findVariantType(StackType<T, S> stackType, T content);

    //endregion

    //region resolver

    boolean hasContentResolver(ResolverType<?> type);

    boolean hasStackResolver(ResolverType<?> type);

    @UnmodifiableView
    <T, R> RichIterable<ContentResolver<T, R>> getResolver(ResolverType<R> type, ContentType<T> contentType);

    @UnmodifiableView
    <T, S, R> RichIterable<StackResolver<T, S, R>> getStackResolver(ResolverType<R> type, StackType<T, S> stackType);

    @Nullable <T, S, R> StackResolver<T, S, R> findStackResolver(ResolverType<R> type, StackType<T, S> stackType, S stack);

    @Nullable <T, R> ContentResolver<T, R> findContentResolver(ResolverType<R> type, ContentType<T> contentType, T content);

    //endregion

    //region types

    @UnmodifiableView
    RichIterable<? extends ContentType<?>> contentTypes();

    @UnmodifiableView
    RichIterable<? extends StackType<?, ?>> stackTypes();

    @UnmodifiableView
    RichIterable<? extends TagType<?>> tagTypes();

    RichIterable<? extends VariantType<?, ?>> variantTypes();

    //endregion

}
