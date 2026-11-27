package dev.vfyjxf.cirrus.api.content.util;

import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

final class TagLookupImpl<T> implements TagLookup<T> {

    private final TagType<T> tagType;
    private final MutableList<QueryPredicate> predicates = MutableLists.empty();
    private final LinkedHashSet<T> includes = new LinkedHashSet<>();
    private final LinkedHashSet<T> excludes = new LinkedHashSet<>();
    private final MutableList<Predicate<T>> customPredicates = MutableLists.empty();
    private @Nullable Set<T> exactSet;

    TagLookupImpl(TagType<T> tagType) {
        this.tagType = tagType;
    }

    @Override
    public TagLookup<T> include(T element) {
        if (element != null && includes.add(element)) {
            predicates.add(new Include<>(element));
        }
        return this;
    }

    @Override
    public TagLookup<T> without(T element) {
        if (element != null && excludes.add(element)) {
            predicates.add(new Without<>(element));
        }
        return this;
    }

    @Override
    public TagLookup<T> predicate(Predicate<T> predicate) {
        if (predicate != null) {
            customPredicates.add(predicate);
            predicates.add(new CustomPredicate(predicate));
        }
        return this;
    }

    @Override
    public TagLookup<T> matchSet(Set<T> set) {
        exactSet = set == null ? Set.of() : Set.copyOf(set);
        return this;
    }

    @Override
    public MutableSet<TagKey<T>> lookup() {
        MutableSet<TagKey<T>> result = Sets.mutable.empty();
        if (exactSet != null && exactSet.isEmpty()) {
            return result;
        }
        for (TagKey<T> tag : tagType.allTags()) {
            var tagSet = tagType.getTagSet(tag);
            if (tagSet == null) {
                continue;
            }
            if (exactSet != null && tagSet.size() != exactSet.size()) {
                continue;
            }
            if (!matches(tagSet)) {
                continue;
            }
            result.add(tag);
        }
        return result;
    }

    private boolean matches(Iterable<?> holders) {
        LinkedHashSet<T> remainingIncludes = includes.isEmpty() ? null : new LinkedHashSet<>(includes);
        int count = 0;
        for (Object rawHolder : holders) {
            @SuppressWarnings("unchecked")
            T value = ((Holder<T>) rawHolder).value();
            count++;
            if (excludes.contains(value)) {
                return false;
            }
            if (exactSet != null && !exactSet.contains(value)) {
                return false;
            }
            if (remainingIncludes != null) {
                remainingIncludes.remove(value);
            }
            for (Predicate<T> predicate : customPredicates) {
                if (!predicate.test(value)) {
                    return false;
                }
            }
        }
        if (exactSet != null && count != exactSet.size()) {
            return false;
        }
        return remainingIncludes == null || remainingIncludes.isEmpty();
    }

    private sealed interface QueryPredicate {}

    private record Include<T>(T target) implements QueryPredicate {}

    private record Without<T>(T blocked) implements QueryPredicate {}

    private record CustomPredicate<T>(Predicate<T> predicate) implements QueryPredicate {}

}
