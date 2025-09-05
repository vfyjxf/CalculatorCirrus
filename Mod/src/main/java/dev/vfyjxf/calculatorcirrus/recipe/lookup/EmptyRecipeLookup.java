package dev.vfyjxf.calculatorcirrus.recipe.lookup;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeLookup;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;

import java.util.Collection;
import java.util.function.Predicate;

public class EmptyRecipeLookup<T> implements RecipeLookup<T> {

    @SuppressWarnings("rawtypes")
    private static final RichIterable EMPTY = Lists.immutable.empty();

    @SuppressWarnings("rawtypes")
    public static final RecipeLookup INSTANCE = new EmptyRecipeLookup<>();

    private EmptyRecipeLookup() {
    }

    @Override
    public RecipeLookup<T> filter(Predicate<T> predicate) {
        return this;
    }

    @Override
    public RecipeLookup<T> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
        return this;
    }

    @Override
    public RecipeLookup<T> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
        return this;
    }

    @Override
    public RecipeLookup<T> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
        return this;
    }

    @Override
    public RecipeLookup<T> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RichIterable<T> get() {
        return EMPTY;
    }
}
