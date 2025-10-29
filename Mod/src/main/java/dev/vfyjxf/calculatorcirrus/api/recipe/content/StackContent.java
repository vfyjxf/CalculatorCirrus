package dev.vfyjxf.calculatorcirrus.api.recipe.content;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Range;

import java.util.Objects;

public final class StackContent<T, S> implements InputContent, OutputContent {

    private final StackType<T, S> type;
    private final ContentStack<T, S> stack;
    private final float chance;

    StackContent(StackType<T, S> type, ContentStack<T, S> stack, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        Checks.checkArgument(stack.notEmpty(), "stack value cannot be empty");
        this.type = type;
        this.stack = stack;
        this.chance = chance;
    }


    public StackType<T, S> type() {return type;}

    public ContentStack<T, S> stack() {return stack;}

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public boolean notEmpty() {
        return !isEmpty();
    }

    @Override
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return stack.amount();
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
    public boolean mergeable(RecipeContent other) {
        if (getClass() != other.getClass()) return false;
        StackContent<?, ?> o = (StackContent<?, ?>) other;
        return stack.matches(o.stack, MatchStrategy.identityAndComponent);
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (!(actual instanceof ContentStack<?, ?> other)) return false;
        return stack.matches(other, MatchStrategy.identityAndComponent);
    }

    //region extra utils

    public boolean isFluid() {return stack.isFluid();}

    public boolean isItem() {return stack.isItem();}

    public boolean is(StackType<?, ?> type) {return stack.is(type);}

    @SuppressWarnings("unchecked")
    public <C, R> StackContent<C, R> cast() {
        return (StackContent<C, R>) this;
    }

    //endregion

    @Override
    public float chance() {return chance;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (StackContent<?, ?>) obj;
        return Objects.equals(this.type, that.type) &&
               Objects.equals(this.stack, that.stack) &&
               Float.floatToIntBits(this.chance) == Float.floatToIntBits(that.chance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stack, chance);
    }

    @Override
    public String toString() {
        return "StackContent{" +
               "type=" + type +
               ", stack=" + stack +
               ", chance=" + chance +
               '}';
    }

}
