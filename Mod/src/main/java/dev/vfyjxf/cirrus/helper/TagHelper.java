package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
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

    private TagHelper() {}
}
