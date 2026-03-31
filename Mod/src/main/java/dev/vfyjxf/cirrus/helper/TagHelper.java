package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TagHelper {

    private static final Map<CacheKey, AllTagsSnapshot> allTagsCache = new ConcurrentHashMap<>();

    public static <T, S> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        StackType<T, S> stackType,
        @Unmodifiable Collection<? extends S> stacks
    ) {
        if (stacks.isEmpty()) return null;
        ContentType<T> contentType = tagType.contentType();
        for (TagKey<T> tag : allTags(tagType)) {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() != stacks.size()) continue;
            boolean matched = true;
            for (S stack : stacks) {
                var holder = contentType.getHolder(stackType.getContent(stack));
                if (!tagSet.contains(holder)) {
                    matched = false;
                    break;
                }
            }
            if (matched) return tag;
        }
        return null;
    }

    public static <T> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        @Unmodifiable Collection<? extends ContentStack<T, ?>> stacks
    ) {
        if (stacks.isEmpty()) return null;
        ContentType<T> contentType = tagType.contentType();
        for (TagKey<T> tag : allTags(tagType)) {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() != stacks.size()) continue;
            boolean matched = true;
            for (ContentStack<T, ?> stack : stacks) {
                var holder = contentType.getHolder(stack.content());
                if (!tagSet.contains(holder)) {
                    matched = false;
                    break;
                }
            }
            if (matched) return tag;
        }
        return null;
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

    private TagHelper() {}

    private record AllTagsSnapshot(Object sourceTagSet, TagKey<?>[] tags) {}

    private record CacheKey(Namespace typeId, ResourceLocation registryLocation) {}
}
