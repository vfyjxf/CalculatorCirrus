package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolver;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.ContentResolverType;
import dev.vfyjxf.calculatorcirrus.api.content.resolver.StackResolver;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.cloudlib.api.util.MutableSets;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.eclipse.collections.impl.set.mutable.SetAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public sealed class ContentApiImpl implements ContentApi permits ClientContentApiImpl {

    private static ContentApiImpl instance = null;

    public static @Nullable ContentApiImpl instance() {
        return instance;
    }

    public static void attach(ContentApiImpl api) {
        Checks.checkNotNull(api, "api");
        instance = api;
    }

    private final Map<ResourceLocation, ContentType<?>> contentTypes;

    private final Map<ContentType<?>, StackType<?, ?>> stackTypesByType;
    private final Map<ResourceLocation, StackType<?, ?>> stackTypesById;

    private final Map<ResourceLocation, TagType<?>> tagTypesById;
    private final Map<ContentType<?>, TagType<?>> tagTypesByType;

    private final Map<ResourceLocation, VariantType<?, ?>> variantTypes;
    private final Map<Object, VariantType<?, ?>> variantTypeByContent;

    private final Map<ContentResolverType<?>, Set<ContentResolver<?, ?>>> contentResolvers;
    private final Map<ContentResolverType<?>, Set<StackResolver<?, ?, ?>>> stackResolvers;

    public ContentApiImpl(ContentRegistryBuilder builder) {
        this.contentTypes = new HashMap<>(builder.contentTypes);

        this.stackTypesByType = new HashMap<>(builder.stackTypesByType);
        this.stackTypesById = new HashMap<>(builder.stackTypesById);

        this.tagTypesByType = new HashMap<>(builder.tagTypesByType);
        this.tagTypesById = new HashMap<>(builder.tagTypesById);

        this.variantTypes = new HashMap<>(builder.variantTypes);
        this.variantTypeByContent = new HashMap<>(builder.variantTypeByContent);

        this.contentResolvers = new HashMap<>(builder.contentResolvers);
        this.stackResolvers = new HashMap<>(builder.stackResolvers);
    }

    //region content type

    @Override
    public boolean hasContentType(ResourceLocation typeId) {
        return contentTypes.containsKey(typeId);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentType<T> getContentType(ResourceLocation typeId) {
        ContentType<T> result = (ContentType<T>) contentTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown content type id: " + typeId);
        }
        return result;
    }
    //endregion

    //region stack type

    @Override
    public boolean hasStackType(ResourceLocation typeId) {
        return stackTypesById.containsKey(typeId);
    }

    @Override
    public boolean hasStackType(ContentType<?> contentType) {
        return stackTypesByType.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, S> StackType<T, S> getStackType(ResourceLocation typeId) {
        StackType<T, S> result = (StackType<T, S>) contentTypes.get(typeId);
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

    //endregion

    //region tag type

    @Override
    public boolean hasTagType(ResourceLocation typeId) {
        return tagTypesById.containsKey(typeId);
    }

    @Override
    public boolean hasTagType(ContentType<?> contentType) {
        return tagTypesByType.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TagType<T> getTagType(ResourceLocation typeId) {
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

    @Override
    public boolean hasVariantType(ResourceLocation typeId) {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, S> VariantType<T, S> getVariantType(ResourceLocation typeId) {
        VariantType<T, S> result = (VariantType<T, S>) variantTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown variant type id: " + typeId);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, S> @Nullable VariantType<T, S> findVariantType(StackType<T, S> stackType, T content) {
        return (VariantType<T, S>) variantTypeByContent.get(stackType);
    }

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
    public RichIterable<? extends VariantType<?, ?>> variantTypes() {
        return CollectionAdapter.adapt(variantTypes.values()).asUnmodifiable();
    }
}
