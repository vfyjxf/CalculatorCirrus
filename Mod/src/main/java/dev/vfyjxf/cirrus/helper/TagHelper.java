package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TagHelper {

    private static final Map<CacheKey, AllTagsSnapshot> allTagsCache = new ConcurrentHashMap<>();
    private static final Map<CacheKey, TagMemberIndexSnapshot> tagMemberIndexCache = new ConcurrentHashMap<>();

    public static <T, S> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        StackType<T, S> stackType,
        @Unmodifiable Collection<? extends S> stacks
    ) {
        if (stacks.isEmpty()) return null;
        ContentType<T> contentType = tagType.contentType();
        HashSet<Holder<?>> holders = new HashSet<>(stacks.size());
        for (S stack : stacks) {
            holders.add(contentType.getHolder(stackType.getContent(stack)));
        }
        // A tag holds each member exactly once, so the DEDUPED holder set is the only
        // thing a tag can match. The old duplicate-stack path compared a deduped tag
        // size against the un-deduped input count, matched nothing, and fell back to
        // an O(allTags) scan per resolution (the top in-game hotspot) that always
        // returned null for such inputs.
        return findIndexedTag(tagType, holders.size(), holders);
    }

    public static <T> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        @Unmodifiable Collection<? extends ContentStack<T, ?>> stacks
    ) {
        if (stacks.isEmpty()) return null;
        ContentType<T> contentType = tagType.contentType();
        HashSet<Holder<?>> holders = new HashSet<>(stacks.size());
        for (ContentStack<T, ?> stack : stacks) {
            holders.add(contentType.getHolder(stack.content()));
        }
        return findIndexedTag(tagType, holders.size(), holders);
    }

    @SuppressWarnings("unchecked")
    private static <T> TagKey<T>[] allTags(TagType<T> tagType) {
        CacheKey key = new CacheKey(tagType.typeId(), tagType.registryKey().location());
        var tagSet = tagType.allTags();
        AllTagsSnapshot cached = allTagsCache.get(key);
        if (cached != null && cached.sourceTagSet == tagSet) {
            return (TagKey<T>[]) cached.tags;
        }
        TagKey<T>[] tags = tagSet.toArray(new TagKey[0]);
        allTagsCache.put(key, new AllTagsSnapshot(tagSet, tags));
        return tags;
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable TagKey<T> findIndexedTag(TagType<T> tagType, int memberCount, Set<Holder<?>> holders) {
        CacheKey key = new CacheKey(tagType.typeId(), tagType.registryKey().location());
        var tagSet = tagType.allTags();
        TagMemberIndexSnapshot cached = tagMemberIndexCache.get(key);
        if (cached == null || cached.sourceTagSet != tagSet) {
            cached = buildTagMemberIndex(tagType, tagSet);
            tagMemberIndexCache.put(key, cached);
        }
        return (TagKey<T>) cached.tagsByMembers.get(new TagMemberKey(memberCount, holders));
    }

    private static <T> TagMemberIndexSnapshot buildTagMemberIndex(TagType<T> tagType, Object sourceTagSet) {
        Map<TagMemberKey, TagKey<?>> tagsByMembers = new HashMap<>();
        for (TagKey<T> tag : allTags(tagType)) {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() == 0) {
                continue;
            }
            HashSet<Holder<?>> holders = new HashSet<>(tagSet.size());
            for (var holder : tagSet) {
                holders.add(holder);
            }
            tagsByMembers.putIfAbsent(new TagMemberKey(tagSet.size(), holders), tag);
        }
        return new TagMemberIndexSnapshot(sourceTagSet, tagsByMembers);
    }

    public static void clearCaches() {
        allTagsCache.clear();
        tagMemberIndexCache.clear();
    }

    private TagHelper() {}

    private record AllTagsSnapshot(Object sourceTagSet, TagKey<?>[] tags) {}

    private record TagMemberIndexSnapshot(Object sourceTagSet, Map<TagMemberKey, TagKey<?>> tagsByMembers) {}

    private record TagMemberKey(int memberCount, Set<Holder<?>> holders) {
        private TagMemberKey {
            holders = Set.copyOf(holders);
        }
    }

    private record CacheKey(Namespace typeId, ResourceLocation registryLocation) {}
}
