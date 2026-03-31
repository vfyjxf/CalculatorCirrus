package dev.vfyjxf.cirrus.recipe.lookup;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.lazy.LazyIterableAdapter;

import java.util.Collection;
import java.util.function.Predicate;

//TODO:move to constraint api
public class SystemRecipeLookup<T> implements RecipeLookup<T> {

    private Predicate<T> predicate = t -> true;
    private final RichIterable<T> recipes;

    public SystemRecipeLookup(Iterable<T> recipes) {
        this.recipes = new LazyIterableAdapter<>(recipes);
    }

    @Override
    public RecipeLookup<T> filter(Predicate<T> predicate) {
        this.predicate = this.predicate.and(predicate);
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
    public RichIterable<T> get() {
        return this.recipes.select(t -> this.predicate.test(t));
    }
}
