package dev.vfyjxf.cirrus.api.content.util;

import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.helper.TagHelper;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.set.MutableSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

public sealed interface TagLookup<T> permits TagLookupImpl {

    static <T, S> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        StackType<T, S> stackType,
        @Unmodifiable Collection<? extends S> stacks
    ) {
        return TagHelper.findTagMatchStacks(tagType, stackType, stacks);
    }

    static <T> @Nullable TagKey<T> findTagMatchStacks(
        TagType<T> tagType,
        @Unmodifiable Collection<? extends ContentStack<T, ?>> stacks
    ) {
        return TagHelper.findTagMatchStacks(tagType, stacks);
    }

    static <T> TagLookup<T> create(TagType<T> tagType) {
        return new TagLookupImpl<>(tagType);
    }

    //region query operations

    TagLookup<T> include(T element);

    TagLookup<T> without(T element);

    TagLookup<T> predicate(Predicate<T> predicate);

    TagLookup<T> matchSet(Set<T> set);

    //endregion

    MutableSet<TagKey<T>> lookup();

}
