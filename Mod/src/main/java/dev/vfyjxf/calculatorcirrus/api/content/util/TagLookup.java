package dev.vfyjxf.calculatorcirrus.api.content.util;

import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.set.MutableSet;

import java.util.Set;
import java.util.function.Predicate;

public sealed interface TagLookup<T> permits TagLookupImpl {

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
