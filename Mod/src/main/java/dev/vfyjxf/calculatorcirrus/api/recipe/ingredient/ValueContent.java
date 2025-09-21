package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Range;

public final class ValueContent<T> implements InputContent, OutputContent {

    private final ValueType<T> type;
    private final T value;
    private final float chance;

    ValueContent(ValueType<T> type, T value, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.type = type;
        this.value = type.copy(value);
        this.chance = chance;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean notEmpty() {
        return true;
    }

    @Override
    public long amount() {
        return type.getValue(value);
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return chance;
    }

    @Override
    public ValueContent<T> copy() {
        return new ValueContent<>(type, type.copy(value), chance);
    }

    @Override
    public ValueContent<T> copyWithAmount(long amount) {
        return new ValueContent<>(type, type.fromValue(amount), chance);
    }

    @Override
    public ValueContent<T> copyWithChance(float chance) {
        return new ValueContent<>(type, type.copy(value), chance);
    }

    @Override
    public ValueContent<T> copyWithAmountAndChance(long amount, float chance) {
        return new ValueContent<>(type, type.fromValue(amount), chance);
    }

    @Override
    public MutableList<? extends ContentVariant> equivalents() {
        return MutableLists.empty();
    }

    @Override
    public boolean satisfy(ContentValue<?> value) {
        return false;
    }

    @Override
    public boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy) {
        return false;
    }

    @Override
    public boolean equivalent(RecipeContent other) {
        if (!(other instanceof ValueContent<?> content)) return false;
        return type == content.type;
    }

}
