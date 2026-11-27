package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

public final class ValueContent<T> implements InputContent, OutputContent {

    private final ContentValue<T> content;
    private final float chance;

    ValueContent(ContentValue<T> content, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.content = content.copy();
        this.chance = chance;
    }

    ValueContent(ValueType<T> type, T value, float chance) {
        this(ContentValue.fromValue(type, value), chance);
    }

    public ValueType<T> type() {
        return content.type();
    }

    public ContentValue<T> content() {
        return content;
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
    public @Range(from = 0, to = Long.MAX_VALUE) long amount() {
        return content.value();
    }

    @Override
    public float chance() {
        return chance;
    }

    @Override
    public ValueContent<T> copy() {
        return new ValueContent<>(content.copy(), chance);
    }

    @Override
    public boolean isEnumerable() {
        return true;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ContentVariant> enumerate() {
        return Lists.immutable.of(content);
    }

    @Override
    public boolean is(ContentVariant variant) {
        return variant instanceof ContentValue<?> value && value.type() == this.content.type();
    }

    @Override
    public ValueContent<T> copyWithAmount(long amount) {
        return new ValueContent<>(type(), type().fromValue(amount), chance);
    }

    @Override
    public ValueContent<T> copyWithChance(float chance) {
        return new ValueContent<>(content.copy(), chance);
    }

    @Override
    public ValueContent<T> copyWithAmountAndChance(long amount, float chance) {
        return new ValueContent<>(type(), type().fromValue(amount), chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof ValueContent<?> o)) return false;
        return type() == o.type();
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        if (actual instanceof ContentValue<?> value) {
            return type() == value.type();
        } else return false;
    }

    @Override
    public String toString() {
        return "ValueContent{" + "content=" + content + ", chance=" + chance + '}';
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.value;
    }
}
