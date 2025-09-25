package dev.vfyjxf.calculatorcirrus.helper;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.SequencedCollection;
import java.util.stream.IntStream;

public final class TagHelper {

    public static <T, S> @Nullable TagKey<T> findTagMatchStacks(
            TagType<T> tagType,
            StackType<T, S> stackType,
            @Unmodifiable SequencedCollection<? extends S> stacks
    ) {
        var tagKeys = tagType.allTags();

        return tagKeys.detect(tag -> {
            MutableList<T> tagValues = tagType.getTagValues(tag);
            if (tagValues.size() != stacks.size()) return false;

            MutableList<T> stackValues = stacks.stream()
                    .map(stackType::getContent)
                    .collect(Collectors2.toList());

            ContentType<T> contentType = stackType.contentType();

            return IntStream.range(0, tagValues.size())
                    .allMatch(index -> contentType.matches(tagValues.get(index), stackValues.get(index)));
        });
    }

    private TagHelper() {}
}
