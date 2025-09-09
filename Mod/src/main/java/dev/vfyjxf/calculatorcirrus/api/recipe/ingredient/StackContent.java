package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Range;

public final class StackContent<T, S> implements InputContent, OutputContent {

    private final StackType<T, S> type;
    private final ContentStack<T, S> stack;
    private final float chance;

    StackContent(StackType<T, S> type, ContentStack<T, S> stack, float chance) {
        Checks.checkRange(chance, 0, 1);
        this.type = type;
        this.stack = stack;
        this.chance = chance;
    }

    public StackType<T, S> type() {
        return type;
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty() || chance == 0;
    }

    @Override
    public boolean notEmpty() {
        return !isEmpty();
    }

    @Override
    public long amount() {
        return stack.amount();
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public StackContent<T, S> copy() {
        return new StackContent<>(type, stack, chance);
    }

    @Override
    public StackContent<T, S> copyWithAmount(long amount) {
        return new StackContent<>(type, stack.copyWithAmount(amount), chance);
    }

    @Override
    public StackContent<T, S> copyWithChance(float chance) {
        return new StackContent<>(type, stack.copy(), chance);
    }

    @Override
    public StackContent<T, S> copyWithAmountAndChance(long amount, float chance) {
        return new StackContent<>(type, stack.copyWithAmount(amount), chance);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MutableList<? extends ContentVariant> equivalents() {
        return Lists.mutable.with(stack.copy());
    }

    @Override
    public boolean satisfy(ContentValue<?> value) {
        return stack.matchesContent(value);
    }

    @Override
    public boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy) {
        return stack.notEmpty() && this.stack.matches(stack);
    }

    @Override
    public boolean equivalent(RecipeContent other) {
        if (!(other instanceof StackContent<?, ?> content)) return false;
        return stack.matches(content.stack);
    }

}
