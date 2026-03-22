package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public final class TagHelper {

    public static <T, S> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        StackType<T, S> stackType,
        @Unmodifiable Collection<? extends S> stacks
    ) {
        var tagKeys = tagType.allTags();
        return tagKeys.detect(tag -> {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() != stacks.size()) return false;
            ContentType<T> contentType = tagType.contentType();
            for (S stack : stacks) {
                var holder = contentType.getHolder(stackType.getContent(stack));
                if (!tagSet.contains(holder)) return false;
            }
            return true;
        });
    }

    public static <T> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        @Unmodifiable Collection<? extends ContentStack<T, ?>> stacks
    ) {
        if (stacks.isEmpty()) return null;
        var tagKeys = tagType.allTags();
        return tagKeys.detect(tag -> {
            HolderSet.Named<T> tagSet = tagType.getTagSet(tag);
            if (tagSet == null || tagSet.size() != stacks.size()) return false;
            for (ContentStack<T, ?> stack : stacks) {
                var holder = tagType.contentType().getHolder(stack.content());
                if (!tagSet.contains(holder)) return false;
            }
            return true;
        });
    }

    private TagHelper() {}
}
