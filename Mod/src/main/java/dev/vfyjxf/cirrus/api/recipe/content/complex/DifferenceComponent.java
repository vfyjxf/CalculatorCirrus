package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Range;

public final class DifferenceComponent implements ComplexContent {

    public static InputContent from(InputContent required, InputContent rejected, long amount, float chance) {
        if (amount <= 0) return empty;
        if (required.isEmpty()) return empty;
        if (rejected.isEmpty()) return required.copy();
        required = required.copyWithAmountAndChance(1, MAX_CHANCE);
        rejected = rejected.copyWithAmountAndChance(1, MAX_CHANCE);
        return new DifferenceComponent(required, rejected, amount, chance);
    }

    private final InputContent required;
    private final InputContent rejected;
    private final long amount;
    private final float chance;

    private DifferenceComponent(InputContent required, InputContent rejected, long amount, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.required = required;
        this.rejected = rejected;
        this.amount = amount;
        this.chance = chance;
    }

    public InputContent required() {
        return required;
    }

    public InputContent rejected() {
        return rejected;
    }

    @Override
    public boolean isEmpty() {
        return required.isEmpty();
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
    public DifferenceComponent copy() {
        return new DifferenceComponent(required, rejected, amount, chance);
    }

    @Override
    public DifferenceComponent copyWithAmount(long amount) {
        return new DifferenceComponent(required, rejected, amount, chance);
    }

    @Override
    public DifferenceComponent copyWithChance(float chance) {
        return new DifferenceComponent(required, rejected, amount, chance);
    }

    @Override
    public DifferenceComponent copyWithAmountAndChance(long amount, float chance) {
        return new DifferenceComponent(required, rejected, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        return other instanceof DifferenceComponent o &&
               required.mergeable(o.required) &&
               rejected.mergeable(o.rejected);
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        return required.satisfy(actual) && !rejected.satisfy(actual);
    }

    @Override
    public boolean isEnumerable() {
        return required.isEnumerable() && rejected.isEnumerable();
    }

    @Override
    public RichIterable<? extends ContentVariant> enumerate() {
        return isEnumerable() ? required.enumerate().reject(rejected::satisfy) : Lists.immutable.empty();
    }
}
