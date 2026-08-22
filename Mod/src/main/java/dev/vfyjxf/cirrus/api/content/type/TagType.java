package dev.vfyjxf.cirrus.api.content.type;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.cirrus.api.content.util.TagLookup;
import dev.vfyjxf.cirrus.helper.TagHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

//TODO:我们应该扩充Tag的概念吗，支持不使用minecraft注册表的系统？
public record TagType<T>(
        Namespace typeId,
        ContentType<T> contentType,
        ResourceKey<Registry<T>> registryKey
) {
    private static final Map<CacheKey, MutableSet<TagKey<?>>> allTagsCache = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Registry<?>> registryCache = new ConcurrentHashMap<>();
    private static final Map<TagValuesKey, ImmutableList<?>> tagValuesCache = new ConcurrentHashMap<>();

    public static <T> TagType<T> fromRegistry(
            String id,
            ContentType<T> contentType,
            ResourceKey<Registry<T>> registryKey
    ) {
        return fromRegistry(Namespace.parse(id), contentType, registryKey);
    }

    public static <T> TagType<T> fromRegistry(
            Namespace id,
            ContentType<T> contentType,
            ResourceKey<Registry<T>> registryKey
    ) {
        return new TagType<>(id, contentType, registryKey);
    }

    public TagType {
        Checks.checkNotNull(typeId, "typeId");
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(registryKey, "registryKey");
        Checks.checkArgument(contentType.hasRegistry(), "TagType must have a registry!");
        Checks.checkArgument(contentType.getRegistryKey() == registryKey, "TagType must have the same registry key as its content type!");
    }

    //region simple query

    @SuppressWarnings("unchecked")
    private Registry<T> registry() {
        return (Registry<T>) registryCache.computeIfAbsent(
                registryKey.location(),
                location -> Objects.requireNonNull(BuiltInRegistries.REGISTRY).get(location)
        );
    }


    public boolean hasTag(ResourceLocation tagKey) {
        return registry()
                .getTags()
                .anyMatch(tag -> tag.getFirst().location().equals(tagKey));
    }

    public boolean hasTag(TagKey<T> tagKey) {
        return registry().getTag(tagKey).isPresent();
    }

    public @Nullable HolderSet.Named<T> getTagSet(ResourceLocation tagKey) {
        return registry().getTags()
                         .filter(tag -> tag.getFirst().location().equals(tagKey))
                         .findAny()
                         .map(Pair::getSecond)
                         .orElse(null);
    }

    public HolderSet.@Nullable Named<T> getTagSet(TagKey<T> tagKey) {
        return registry().getTag(tagKey).orElse(null);
    }

    public MutableSet<TagKey<T>> getTagFor(T value) {
        var registry = registry();
        return registry.getHolder(registry.getId(value))
                       .stream()
                       .flatMap(Holder::tags)
                       .collect(Collectors2.toSet());
    }

    public MutableSet<T> getTagValues(ResourceLocation location) {
        TagKey<T> tagKey = TagKey.create(registryKey, location);
        return getTagValues(tagKey);
    }

    public MutableSet<T> getTagValues(TagKey<T> tagKey) {
        return this.cachedTagValues(tagKey).toSet();
    }

    /**
     * The tag's values as an immutable list, memoized per registry session.
     * Resolving a tag walks the registry holder set and used to rerun on
     * every {@code TagStackContent} construction (recipe graph and database
     * builds create them by the hundred thousand); prefer this method on
     * hot paths. Invalidated together with the other tag caches by
     * {@link #clearAllTagsCache()} (client unload / world switch).
     */
    public ImmutableList<T> getTagValuesList(TagKey<T> tagKey) {
        return this.cachedTagValues(tagKey);
    }

    public MutableList<T> getSequencedTagValues(TagKey<T> tagKey) {
        return this.cachedTagValues(tagKey).toList();
    }

    @SuppressWarnings("unchecked")
    private ImmutableList<T> cachedTagValues(TagKey<T> tagKey) {
        return (ImmutableList<T>) tagValuesCache.computeIfAbsent(
                new TagValuesKey(registryKey.location(), tagKey.location()),
                ignored -> registry().getTag(tagKey)
                                     .stream()
                                     .flatMap(HolderSet.Named::stream)
                                     .map(Holder::value)
                                     .collect(Collectors2.toImmutableList())
        );
    }

    public <S> MutableSet<S> getAsStacks(TagKey<T> tagKey, StackType<T, S> stackType) {
        return this.getTagValues(tagKey)
                   .collect(content -> stackType.fromContent(content, stackType.quantity()));
    }

    public MutableSet<TagKey<T>> allTags() {
        CacheKey key = new CacheKey(typeId, registryKey.location());
        @SuppressWarnings("unchecked")
        MutableSet<TagKey<T>> tags = (MutableSet<TagKey<T>>) (MutableSet<?>) allTagsCache.computeIfAbsent(
                key,
                ignored -> registry().getTagNames().collect(Collectors2.toSet())
        );
        return tags;
    }

    public static void clearAllTagsCache() {
        allTagsCache.clear();
        registryCache.clear();
        tagValuesCache.clear();
        TagHelper.clearCaches();
    }

    //endregion

    //region lookup

    public TagLookup<T> createLookup() {
        return TagLookup.create(this);
    }

    //endregion

    @Override
    public String toString() {
        return "Tag{" +
                "typeId=" + typeId +
                ", contentType=" + contentType +
                '}';
    }

    private record CacheKey(Namespace typeId, ResourceLocation registryLocation) {}

    private record TagValuesKey(ResourceLocation registryLocation, ResourceLocation tagLocation) {}
}
