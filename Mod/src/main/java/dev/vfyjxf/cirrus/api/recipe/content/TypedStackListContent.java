package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;

public final class TypedStackListContent<T, S> implements InputContent {

    private final StackType<T, S> type;
    private final ImmutableList<ContentStack<T, S>> stacks;
    private final long amount;
    private final float chance;

    TypedStackListContent(StackType<T, S> type, Collection<? extends ContentStack<T, S>> stacks, long amount, float chance) {
        this.type = type;
        this.amount = amount;
        this.chance = chance;
        this.stacks = CollectionAdapter.adapt(stacks)
                                       .reject(ContentStack::isEmpty)
                                       .collect(ContentStack::normalize)
                                       .toImmutableList();
    }

    private TypedStackListContent(StackType<T, S> type, ImmutableList<ContentStack<T, S>> stacks, long amount, float chance) {
        this.type = type;
        this.stacks = stacks;
        this.amount = amount;
        this.chance = chance;
    }

    public StackType<T, S> type() {
        return type;
    }

    public ImmutableList<ContentStack<T, S>> stacks() {
        return stacks;
    }

    public int size() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public TypedStackListContent<T, S> copy() {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public boolean isEnumerable() {
        return true;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ContentVariant> enumerate() {
        return stacks;
    }

    @Override
    public TypedStackListContent<T, S> copyWithAmount(long amount) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public TypedStackListContent<T, S> copyWithChance(float chance) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public TypedStackListContent<T, S> copyWithAmountAndChance(long amount, float chance) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof TypedStackListContent<?, ?> o)) return false;
        if (type != o.type || stacks.size() != o.stacks.size()) return false;
        for (int i = 0; i < stacks.size(); i++) {
            ContentStack<?, ?> a = stacks.get(i);
            ContentStack<?, ?> b = o.stacks.get(i);
            if (!a.matches(b, MatchStrategy.identityAndComponent)) return false;
        }
        return false;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (!(actual instanceof ContentStack<?, ?> stack)) return false;
        return type == stack.type() && stacks.anySatisfy(s -> s.matches(stack, MatchStrategy.identityAndComponent));
    }

    @Override
    public String toString() {
        return "TypedStackListContent{" +
               "type=" + type +
               ", stacks=" + stacks +
               ", amount=" + amount +
               ", chance=" + chance +
               '}';
    }
}
