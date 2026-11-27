package dev.vfyjxf.cirrus.api.content;

import dev.vfyjxf.cirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.recipe.integration.JeiValueTypeBridge;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ContentApi {

    /**
     * Get an instance of ContentApi
     *
     * @return an instance of ContentApi
     * @throws IllegalStateException if ContentApi is not initialized
     */
    static ContentApi instance() {
        ContentApi instance = ContentApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("ContentApi is not initialized");
        }
        return instance;
    }

    //region content type

    /**
     * Check if a content type is registered
     *
     * @param typeId the id of the content type
     * @return true if the content type is registered, false otherwise
     */
    boolean hasContentType(Namespace typeId);

    /**
     * Get a content type
     *
     * @param typeId the id of the content type
     * @param <T>    the content type
     * @return the content type
     * @throws IllegalArgumentException if the content type is not registered
     */
    <T> ContentType<T> getContentType(Namespace typeId);

    /**
     * Find a content type
     *
     * @param typeId the id of the content type
     * @param <T>    the content type
     * @return the content type, or null if not found
     */
    <T> @Nullable ContentType<T> findContentType(Namespace typeId);

    default <T> @Nullable ContentType<T> findContentType(String typeId) {
        return findContentType(Namespace.parse(typeId));
    }
    //endregion

    //region stack type

    /**
     * Check if a stack type is registered
     *
     * @param typeId the id of the stack type
     * @return true if the stack type is registered, false otherwise
     */
    boolean hasStackType(Namespace typeId);

    /**
     * Check if a stack type is registered
     *
     * @param contentType the content type
     * @return true if the stack type is registered, false otherwise
     */
    boolean hasStackType(ContentType<?> contentType);

    /**
     * Get a stack type
     *
     * @param typeId the id of the stack type
     * @param <T>    the content type
     * @param <S>    the stack type
     * @return the stack type
     */
    <T, S> StackType<T, S> getStackType(Namespace typeId);

    /**
     * Get a stack type
     *
     * @param contentType the content type
     * @param <T>         the content type
     * @return the stack type
     */
    <T> StackType<T, ?> getStackType(ContentType<T> contentType);

    /**
     * Find a stack type
     *
     * @param typeId the id of the stack type
     * @param <T>    the content type
     * @param <S>    the stack type
     * @return the stack type, or null if not found
     */
    <T, S> @Nullable StackType<T, S> findStackType(Namespace typeId);

    /**
     * Find a stack type
     *
     * @param typeId the id of the stack type
     * @param <T>    the content type
     * @param <S>    the stack type
     * @return the stack type, or null if not found
     */
    default <T, S> @Nullable StackType<T, S> findStackType(String typeId) {
        return findStackType(Namespace.parse(typeId));
    }
    //endregion

    //region value type

    /**
     * Check if a value type is registered
     *
     * @param typeId the id of the type
     * @return true if the value type is registered
     */
    boolean hasValueType(Namespace typeId);

    /**
     * Get a value type
     *
     * @param typeId the id of the type
     * @param <S>    the value type
     * @return the value type
     */
    <S> ValueType<S> getValueType(Namespace typeId);

    /**
     * Find a value type
     *
     * @param typeId the id of the type
     * @param <S>    the value type
     * @return the value type, or null if not found
     */
    <S> @Nullable ValueType<S> findValueType(Namespace typeId);

    /**
     * Find a value type
     *
     * @param typeId the id of the type
     * @param <S>    the value type
     * @return the value type, or null if not found
     */
    default <S> @Nullable ValueType<S> findValueType(String typeId) {
        return findValueType(Namespace.parse(typeId));
    }

    //endregion

    //region tag type

    /**
     * Check if a tag type exists
     *
     * @param typeId the id of the tag type
     * @return true if the tag type exists
     */
    boolean hasTagType(Namespace typeId);

    /**
     * Check if a tag type exists for the given content type
     *
     * @param contentType the content type
     * @return true if the tag type exists
     */
    boolean hasTagType(ContentType<?> contentType);

    /**
     * Get a tag type
     *
     * @param typeId the id of the tag type
     * @param <T>    the content type
     * @return the tag type
     */
    <T> TagType<T> getTagType(Namespace typeId);

    /**
     * Get a tag type
     *
     * @param contentType the content type
     * @param <T>         the content type
     * @return the tag type
     */
    <T> TagType<T> getTagType(ContentType<T> contentType);

    //endregion

    //region variant type

    /**
     * Check if a variant type exists
     *
     * @param typeId the type id
     * @return whether a variant type exists
     */
    boolean hasVariantType(Namespace typeId);

    /**
     * Get a variant type
     *
     * @param typeId the type id
     * @param <T>    the content type
     * @param <S>    the stack type
     * @return the variant type
     */
    <T, S> VariantType<T, S> getVariantType(Namespace typeId);

    /**
     * Find a variant type for the given stack and content
     *
     * @param stackType the stack type
     * @param content   to find the variant type for
     * @param <T>       the content type
     * @param <S>       the stack type
     * @return the variant type for the given stack and content, or {@link VariantType#fallback(StackType)} if no variant type is found
     */
    <T, S> VariantType<T, S> findVariantType(StackType<T, S> stackType, T content);

    //endregion

    //region resolver

    /**
     * @param type the resolver type
     * @return whether a resolver of the given type exists
     */
    boolean hasContentResolver(ContentResolverType<?> type);

    /**
     * @param type the resolver type
     * @return whether a stack resolver of the given type exists
     */
    boolean hasStackResolver(ContentResolverType<?> type);

    /**
     * @param type        the resolver type
     * @param contentType the content type
     * @param <T>         the content type
     * @param <R>         the resolver type
     * @return an unmodifiable view of all resolvers for the given type and content type
     */
    <T, R> @UnmodifiableView RichIterable<ContentResolver<T, R>> getResolver(
        ContentResolverType<R> type,
        ContentType<T> contentType
    );

    /**
     * @param type        the resolver type
     * @param stackType   the stack type
     * @param <T>         the content type
     * @param <S>         the stack type
     * @param <R>         the resolver type
     * @return an unmodifiable view of all resolvers for the given type and stack type
     */
    <T, S, R> @UnmodifiableView RichIterable<StackResolver<T, S, R>> getStackResolver(
        ContentResolverType<R> type,
        StackType<T, S> stackType
    );

    /**
     * @param type        the resolver type
     * @param stackType   the stack type
     * @param stack       the stack
     * @param <T>         the content type
     * @param <S>         the stack type
     * @param <R>         the resolver type
     * @return a resolver for the given type, stack type and stack, or null if no resolver is found
     */
    <T, S, R> @Nullable StackResolver<T, S, R> findStackResolver(
        ContentResolverType<R> type,
        StackType<T, S> stackType,
        S stack
    );

    /**
     * Find a content resolver for the given type, content type and content
     *
     * @param type        the resolver type
     * @param contentType the content type
     * @param content     the content
     * @param <T>         the content type
     * @param <R>         the resolver type
     * @return a resolver for the given type, content type and content, or null if no resolver is found
     */
    <T, R> @Nullable ContentResolver<T, R> findContentResolver(
        ContentResolverType<R> type,
        ContentType<T> contentType,
        T content
    );

    //endregion

    //region jei bridge

    /**
     * Find a stack type for the given ingredient type
     *
     * @param ingredientType the ingredient type
     * @param <T>            the content type
     * @param <S>            the stack type
     * @return the stack type for the given ingredient type, or null if no stack type is found
     */
    @Nullable
    <T, S> StackType<T, S> findStackTypeForIngredientType(IIngredientTypeWithSubtypes<T, S> ingredientType);

    /**
     * Find a stack type for the given ingredient type
     *
     * @param ingredientType the ingredient type
     * @param <S>            the stack type
     * @return the stack type for the given ingredient type, or null if no stack type is found
     */
    @Nullable
    <S> StackType<?, S> findStackTypeForIngredientType(IIngredientType<S> ingredientType);

    /**
     * Find a value type bridge for the given ingredient type
     *
     * @param ingredientType the ingredient type
     * @param <S>            the stack type
     * @param <T>            the value type
     * @return the value type bridge for the given ingredient type, or null if no value type bridge is found
     */
    @Nullable
    <S, T> JeiValueTypeBridge<S, T> findValueTypeBridge(IIngredientType<S> ingredientType);

    //endregion

    //region type instances

    /**
     * @return an unmodifiable view of all registered content types
     */
    @UnmodifiableView
    RichIterable<? extends ContentType<?>> contentTypes();

    /**
     * @return an unmodifiable view of all registered stack types
     */
    @UnmodifiableView
    RichIterable<? extends StackType<?, ?>> stackTypes();

    /**
     * @return an unmodifiable view of all registered tag types
     */
    @UnmodifiableView
    RichIterable<? extends TagType<?>> tagTypes();

    /**
     * @return an unmodifiable view of all registered variant types
     */
    @UnmodifiableView
    RichIterable<? extends VariantType<?, ?>> variantTypes();

    //endregion

}
