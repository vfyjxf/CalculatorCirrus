package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

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

    public ImmutableList<ContentStack<?, ?>> stacks() {
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
    public StackListContent copy() {
        return new StackListContent(this.stacks, this.amount, this.chance);
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
    public boolean preserveOriginalShortageSemantics() {
        return true;
    }

    @Override
    public StackListContent copyWithAmount(long amount) {
        return new StackListContent(this.stacks, amount, this.chance);
    }

    @Override
    public StackListContent copyWithChance(float chance) {
        return new StackListContent(this.stacks, this.amount, chance);
    }

    @Override
    public StackListContent copyWithAmountAndChance(long amount, float chance) {
        return new StackListContent(this.stacks, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof StackListContent o)) return false;
        if (stacks.size() != o.stacks.size()) return false;
        for (int i = 0; i < stacks.size(); i++) {
            ContentStack<?, ?> a = stacks.get(i);
            ContentStack<?, ?> b = o.stacks.get(i);
            if (!a.matches(b, MatchStrategy.identityAndComponent)) return false;
        }
        return true;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (!(actual instanceof ContentStack<?, ?> stack)) return false;
        for (ContentStack<?, ?> contentStack : stacks) {
            if (contentStack.matches(stack, MatchStrategy.identityAndComponent)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "StackListContent{" +
               "stacks=" + stacks +
               ", amount=" + amount +
               ", chance=" + chance +
               '}';
    }
}
