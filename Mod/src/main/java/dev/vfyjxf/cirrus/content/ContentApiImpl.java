package dev.vfyjxf.cirrus.content;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.cirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.cirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cirrus.api.recipe.integration.JeiValueTypeBridge;
import dev.vfyjxf.cloudlib.api.util.MutableSets;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApiStatus.Internal
public sealed class ContentApiImpl implements ContentApi permits ClientContentApiImpl {

    //region instance

    private static volatile ContentApiImpl instance = null;

    public static @Nullable ContentApiImpl instance() {
        return instance;
    }

    public static void attach(ContentApiImpl api) {
        Checks.checkNotNull(api, "api");
        instance = api;
    }

    //endregion

    //region internal

    private final Map<Namespace, ContentType<?>> contentTypes;

    private final Map<ContentType<?>, StackType<?, ?>> stackTypesByType;
    private final Map<Namespace, StackType<?, ?>> stackTypesById;

    private final Map<Namespace, ValueType<?>> valueTypeById;

    private final Map<Namespace, TagType<?>> tagTypesById;
    private final Map<ContentType<?>, TagType<?>> tagTypesByType;

    private final Map<Namespace, VariantType<?, ?>> variantTypes;
    private final Map<ContentWithType<?>, VariantType<?, ?>> variantTypeByContent;
    private final Map<StackType<?, ?>, VariantType<?, ?>> fallbackVariantTypes;

    private final Map<ContentResolverType<?>, Set<ContentResolver<?, ?>>> contentResolvers;
    private final Map<ContentResolverType<?>, Set<StackResolver<?, ?, ?>>> stackResolvers;

    private final Map<IIngredientType<?>, StackType<?, ?>> stackTypeByIngredientType;
    private final Map<IIngredientType<?>, JeiValueTypeBridge<?, ?>> valueTypeByIngredientType;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ContentApiImpl(ContentRegistryBuilder builder) {
        this.contentTypes = new HashMap<>(builder.contentTypes);

        this.stackTypesByType = new HashMap<>(builder.stackTypesByType);
        this.stackTypesById = new HashMap<>(builder.stackTypesById);

        this.valueTypeById = new HashMap<>(builder.valueTypes);

        this.tagTypesByType = new HashMap<>(builder.tagTypesByType);
        this.tagTypesById = new HashMap<>(builder.tagTypesById);

        this.variantTypes = new HashMap<>(builder.variantTypes);

        this.variantTypeByContent = builder.variantTypeByContent.entrySet().stream().map(entry -> {
            var content = entry.getKey();
            ContentType contentType = entry.getValue().contentType();
            return Pair.of(new ContentWithType(content, contentType), entry.getValue());
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        this.fallbackVariantTypes = stackTypesById.values()
                                                  .stream()
                                                  .map(VariantType::fallback)
                                                  .collect(Collectors.toMap(VariantType::stackType, Function.identity()));

        this.contentResolvers = new HashMap<>(builder.contentResolvers);
        this.stackResolvers = new HashMap<>(builder.stackResolvers);

        this.stackTypeByIngredientType =
            builder.stackTypeIngredientTypeBridges.entrySet()
                                                  .stream()
                                                  .map(entry -> {
                                                      var stackType = findStackType(entry.getKey());
                                                      return Pair.of(entry.getValue(), stackType);
                                                  }).filter(pair -> pair.getValue() != null)
                                                  .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        this.valueTypeByIngredientType = new HashMap<>(builder.valueTypeBridges);
    }

    //endregion

    //region content type

    @Override
    public boolean hasContentType(Namespace typeId) {
        return contentTypes.containsKey(typeId);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentType<T> getContentType(Namespace typeId) {
        ContentType<T> result = (ContentType<T>) contentTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown content type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentType<T> findContentType(Namespace typeId) {
        return (ContentType<T>) contentTypes.get(typeId);
    }
    //endregion

    //region stack type

    @Override
    public boolean hasStackType(Namespace typeId) {
        return stackTypesById.containsKey(typeId);
    }

    @Override
    public boolean hasStackType(ContentType<?> contentType) {
        return stackTypesByType.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, S> StackType<T, S> getStackType(Namespace typeId) {
        StackType<T, S> result = (StackType<T, S>) stackTypesById.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown stack type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> StackType<T, ?> getStackType(ContentType<T> contentType) {
        StackType<T, ?> result = (StackType<T, ?>) stackTypesByType.get(contentType);
        if (result == null) {
            throw new IllegalArgumentException("Unknown stack type for: " + contentType.typeId());
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, S> StackType<T, S> findStackType(Namespace typeId) {
        return (StackType<T, S>) stackTypesById.get(typeId);

    }

    //endregion

    //region value type

    @Override
    public boolean hasValueType(Namespace typeId) {
        return valueTypeById.containsKey(typeId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ValueType<S> getValueType(Namespace typeId) {
        ValueType<S> result = (ValueType<S>) valueTypeById.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ValueType<S> findValueType(Namespace typeId) {
        return (ValueType<S>) valueTypeById.get(typeId);
    }

    //endregion

    //region tag type

    @Override
    public boolean hasTagType(Namespace typeId) {
        return tagTypesById.containsKey(typeId);
    }

    @Override
    public boolean hasTagType(ContentType<?> contentType) {
        return tagTypesByType.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TagType<T> getTagType(Namespace typeId) {
        TagType<T> result = (TagType<T>) tagTypesById.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown tag type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TagType<T> getTagType(ContentType<T> contentType) {
        TagType<T> result = (TagType<T>) tagTypesByType.get(contentType);
        if (result == null) {
            throw new IllegalArgumentException("Unknown tag type for content type: " + contentType.typeId());
        }
        return result;
    }

    //endregion

    //region variant type

    private record ContentWithType<T>(T content, ContentType<T> contentType) {

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            ContentWithType<?> that = (ContentWithType<?>) o;
            return contentType == that.contentType && contentType.matches(content, (T) that.content);
        }

        @Override
        public int hashCode() {
            int hash = contentType.hashCode();
            hash = 31 * hash + contentType.hash(content);
            return hash;
        }
    }

    @Override
    public boolean hasVariantType(Namespace typeId) {
        return variantTypes.containsKey(typeId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, S> VariantType<T, S> getVariantType(Namespace typeId) {
        VariantType<T, S> result = (VariantType<T, S>) variantTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown variant type id: " + typeId);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, S> VariantType<T, S> findVariantType(StackType<T, S> stackType, T content) {
        var contentWithType = new ContentWithType<>(content, stackType.contentType());
        VariantType<T, S> ret = (VariantType<T, S>) variantTypeByContent.get(contentWithType);
        if (ret == null || ret.stackType() != stackType) {
            ret = (VariantType<T, S>) fallbackVariantTypes.get(stackType);
        }
        return ret;
    }

    //endregion

    //region resolver

    @Override
    public boolean hasContentResolver(ContentResolverType<?> type) {
        return contentResolvers.containsKey(type);
    }

    @Override
    public boolean hasStackResolver(ContentResolverType<?> type) {
        return stackResolvers.containsKey(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public @UnmodifiableView <T, R> RichIterable<ContentResolver<T, R>> getResolver(ContentResolverType<R> type, ContentType<T> contentType) {
        var resolvers = contentResolvers.get(type);
        if (resolvers == null) return MutableSets.empty();
        return (RichIterable) SetAdapter.adapt(resolvers)
                                        .select(r -> r.contentType() == contentType)
                                        .asUnmodifiable();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public @UnmodifiableView <T, S, R> RichIterable<StackResolver<T, S, R>> getStackResolver(ContentResolverType<R> type, StackType<T, S> stackType) {
        var resolvers = stackResolvers.get(type);
        if (resolvers == null) return MutableSets.empty();
        return (RichIterable) SetAdapter.adapt(resolvers)
                                        .select(r -> r.stackType() == stackType)
                                        .asUnmodifiable();
    }

    @Override
    public @Nullable <T, S, R> StackResolver<T, S, R> findStackResolver(ContentResolverType<R> type, StackType<T, S> stackType, S stack) {
        var resolvers = stackResolvers.get(type);
        if (resolvers == null) return null;
        var content = stackType.getContent(stack);
        for (var resolver : resolvers) {
            if (resolver.stackType() == stackType && resolver.cast().resolvable(content, stack)) {
                return resolver.cast();
            }
        }
        return null;
    }

    @Override
    public @Nullable <T, R> ContentResolver<T, R> findContentResolver(ContentResolverType<R> type, ContentType<T> contentType, T content) {
        var resolvers = contentResolvers.get(type);
        if (resolvers == null) return null;
        for (var resolver : resolvers) {
            if (resolver.contentType() == contentType && resolver.cast().resolvable(content)) {
                return resolver.cast();
            }
        }
        return null;
    }

    //endregion

    //region jei bridge

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T, S> StackType<T, S> findStackTypeForIngredientType(IIngredientTypeWithSubtypes<T, S> ingredientType) {
        return (StackType<T, S>) stackTypeByIngredientType.get(ingredientType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <S> StackType<?, S> findStackTypeForIngredientType(IIngredientType<S> ingredientType) {
        return (StackType<?, S>) stackTypeByIngredientType.get(ingredientType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <S, T> JeiValueTypeBridge<S, T> findValueTypeBridge(IIngredientType<S> ingredientType) {
        return (JeiValueTypeBridge<S, T>) valueTypeByIngredientType.get(ingredientType);
    }

    //endregion

    //region misc

    @Override
    public @UnmodifiableView RichIterable<? extends ContentType<?>> contentTypes() {
        return CollectionAdapter.adapt(contentTypes.values()).asUnmodifiable();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends StackType<?, ?>> stackTypes() {
        return CollectionAdapter.adapt(stackTypesByType.values()).asUnmodifiable();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends TagType<?>> tagTypes() {
        return CollectionAdapter.adapt(tagTypesByType.values()).asUnmodifiable();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ValueType<?>> valueTypes() {
        return CollectionAdapter.adapt(valueTypeById.values()).asUnmodifiable();
    }

    @Override
    public RichIterable<? extends VariantType<?, ?>> variantTypes() {
        return CollectionAdapter.adapt(variantTypes.values()).asUnmodifiable();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public @UnmodifiableView RichIterable<? extends ContentApi.VariantBinding<?, ?>> variantBindings() {
        Set<Map.Entry<ContentWithType, VariantType>> rawEntries = (Set) variantTypeByContent.entrySet();
        return (RichIterable) CollectionAdapter.adapt(rawEntries).collect(entry -> {
            ContentApi.VariantBinding binding = new ContentApi.VariantBinding(
                entry.getKey().content(),
                entry.getKey().contentType(),
                entry.getValue()
            );
            return binding;
        }).asUnmodifiable();
    }

    //endregion
}
