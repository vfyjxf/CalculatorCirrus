package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public final class TypedStackListContent<T, S> implements InputContent {

    private final StackType<T, S> type;
    private final ImmutableList<ContentStack<T, S>> stacks;
    private final long amount;
    private final float chance;

    TypedStackListContent(StackType<T, S> type, Collection<? extends ContentStack<T, S>> stacks, long amount, float chance) {
        this.type = type;
        this.stacks = CollectionAdapter.adapt(stacks)
                .reject(ContentStack::isEmpty)
                .collect(ContentStack::normalize)
                .toImmutableList();
        this.amount = amount;
        this.chance = chance;
    }

    private TypedStackListContent(StackType<T, S> type, ImmutableList<ContentStack<T, S>> stacks, long amount, float chance) {
        this.type = type;
        this.stacks = stacks;
        this.amount = amount;
        this.chance = chance;
    }


    @Override
    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public RecipeContent copy() {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public RecipeContent copyWithAmount(long amount) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public RecipeContent copyWithChance(float chance) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public RecipeContent copyWithAmountAndChance(long amount, float chance) {
        return new TypedStackListContent<>(type, stacks, amount, chance);
    }

    @Override
    public MutableList<@Unmodifiable ? extends ContentVariant> equivalents() {
        return stacks.toList();
    }

    @Override
    public boolean satisfy(ContentValue<?> value) {
        return false;
    }

    @Override
    public boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy) {
        return stack.notEmpty() && stacks.anySatisfy(s -> s.matches(stack, strategy));
    }

    @Override
    public boolean equivalent(RecipeContent other) {
        if (!(other instanceof TypedStackListContent<?, ?> content)) return false;
        if (stacks.size() != content.stacks.size()) return false;
        //region all match
        for (int i = 0; i < stacks.size(); i++) {
            var s1 = stacks.get(i);
            var s2 = content.stacks.get(i);
            if (!s1.matches(s2)) return false;
        }
        //endregion
        return true;
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
