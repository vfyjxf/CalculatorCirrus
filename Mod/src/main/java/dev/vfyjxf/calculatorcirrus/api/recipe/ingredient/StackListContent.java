package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;

import java.util.SequencedCollection;

public final class StackListContent implements InputContent {

    private final ImmutableList<ContentStack<?, ?>> stacks;
    private final long amount;
    private final float chance;

    StackListContent(SequencedCollection<? extends ContentStack<?, ?>> stacks, long amount, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.stacks = CollectionAdapter.adapt(stacks)
            .reject(ContentStack::isEmpty)
            .<ContentStack<?, ?>>collect(ContentStack::normalize)
            .toImmutableList();
        this.amount = amount;
        this.chance = chance;
    }

    private StackListContent(ImmutableList<ContentStack<?, ?>> stacks, long amount, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
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
    public InputContent copy() {
        return new StackListContent(this.stacks, this.amount, this.chance);
    }

    @Override
    public InputContent copyWithAmount(long amount) {
        return new StackListContent(this.stacks, amount, this.chance);
    }

    @Override
    public InputContent copyWithChance(float chance) {
        return new StackListContent(this.stacks, this.amount, chance);
    }

    @Override
    public InputContent copyWithAmountAndChance(long amount, float chance) {
        return new StackListContent(this.stacks, amount, chance);
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
        if (!(other instanceof StackListContent content)) return false;
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
}
