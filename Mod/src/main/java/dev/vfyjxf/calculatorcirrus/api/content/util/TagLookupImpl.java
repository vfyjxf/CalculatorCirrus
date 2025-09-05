package dev.vfyjxf.calculatorcirrus.api.content.util;

import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;

import java.util.Set;
import java.util.function.Predicate;

//TODO:finish this
final class TagLookupImpl<T> implements TagLookup<T> {

    private final TagType<T> tagType;
    private final MutableList<QueryPredicate> predicates = Lists.mutable.empty();

    TagLookupImpl(TagType<T> tagType) {
        this.tagType = tagType;
    }

    @Override
    public TagLookup<T> include(T element) {
        boolean alreadyIncluded = predicates.isEmpty() || predicates.anySatisfy(predicate -> switch (predicate) {
            case Include(var target) -> element == target;
            default -> false;
        });
        if (!alreadyIncluded) predicates.add(new Include<>(element));
        return this;
    }

    @Override
    public TagLookup<T> without(T element) {
        boolean alreadyExcluded = predicates.isEmpty() || predicates.anySatisfy(predicate -> switch (predicate) {
            case Without(var target) -> element == target;
            default -> false;
        });
        return this;
    }

    @Override
    public TagLookup<T> predicate(Predicate<T> predicate) {

        return this;
    }

    @Override
    public TagLookup<T> matchSet(Set<T> set) {
        return this;
    }

    @Override
    public MutableSet<TagKey<T>> lookup() {
        return null;
    }

    private sealed interface QueryPredicate {
    }

    private record Include<T>(T target) implements QueryPredicate {}

    private record Without<T>(T blocked) implements QueryPredicate {}

    private record CustomPredicate() implements QueryPredicate {}

}
