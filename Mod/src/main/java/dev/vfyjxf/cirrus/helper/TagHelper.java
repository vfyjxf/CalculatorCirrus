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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        ArrayList<Holder<?>> holderList = new ArrayList<>(stacks.size());
        HashSet<Holder<?>> holders = new HashSet<>(stacks.size());
        for (S stack : stacks) {
            Holder<T> holder = contentType.getHolder(stackType.getContent(stack));
            holderList.add(holder);
            holders.add(holder);
        }
        if (holders.size() != stacks.size()) {
            return scanTags(tagType, holderList, stacks.size());
        }
        return findIndexedTag(tagType, stacks.size(), holders);
    }

    public static <T> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        @Unmodifiable Collection<? extends ContentStack<T, ?>> stacks
    ) {
        if (stacks.isEmpty()) return null;
        ContentType<T> contentType = tagType.contentType();
        ArrayList<Holder<?>> holderList = new ArrayList<>(stacks.size());
        HashSet<Holder<?>> holders = new HashSet<>(stacks.size());
        for (ContentStack<T, ?> stack : stacks) {
            Holder<T> holder = contentType.getHolder(stack.content());
            holderList.add(holder);
            holders.add(holder);
        }
        if (holders.size() != stacks.size()) {
            return scanTags(tagType, holderList, stacks.size());
        }
        return findIndexedTag(tagType, stacks.size(), holders);
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

    private static <T> @Nullable TagKey<T> scanTags(TagType<T> tagType, List<Holder<?>> holders, int memberCount) {
        for (TagKey<T> tag : allTags(tagType)) {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() != memberCount) {
                continue;
            }
            boolean matched = true;
            for (Holder<?> holder : holders) {
                if (!tagSet.contains((Holder<T>) holder)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return tag;
            }
        }
        return null;
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
