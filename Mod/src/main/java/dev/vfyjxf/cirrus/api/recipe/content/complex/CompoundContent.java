package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.util.Checks;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Range;

import java.util.Collection;

public final class CompoundContent implements ComplexContent {

    public static InputContent fromIngredient(CompoundIngredient ingredient, long amount, float chance) {
        if (amount <= 0 || ingredient.children().isEmpty()) return empty;
        var contents = ingredient.children().stream().map(InputContent::fromItemIngredient)
                .filter(InputContent::notEmpty).collect(Collectors2.toImmutableList());
        if (contents.isEmpty()) return empty;
        return new CompoundContent(contents, amount, chance);
    }

    public static InputContent fromContents(Collection<? extends InputContent> contents, long amount, float chance) {
        if (contents.isEmpty() || amount <= 0) return empty;
        var collect = contents.stream().filter(InputContent::notEmpty).map(InputContent::copy)
                .collect(Collectors2.toImmutableList());
        if (collect.isEmpty()) return empty;
        return new CompoundContent(collect, amount, chance);
    }

    private final ImmutableList<InputContent> contents;
    private final long amount;
    private final float chance;

    CompoundContent(ImmutableList<InputContent> contents, long amount, float chance) {
        Checks.checkRangeClosed(chance, 0, 1);
        this.contents = contents;
        this.amount = amount;
        this.chance = chance;
    }

    @Override
    public boolean isEmpty() {
        return contents.isEmpty();
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
    public CompoundContent copy() {
        return new CompoundContent(contents, amount, chance);
    }

    @Override
    public CompoundContent copyWithAmount(long amount) {
        return new CompoundContent(contents, amount, chance);
    }

    @Override
    public CompoundContent copyWithChance(float chance) {
        return new CompoundContent(contents, amount, chance);
    }

    @Override
    public CompoundContent copyWithAmountAndChance(long amount, float chance) {
        return new CompoundContent(contents, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof CompoundContent o)) return false;
        if (contents.size() != o.contents.size()) return false;
        for (int i = 0; i < contents.size(); i++) {
            if (!contents.get(i).mergeable(o.contents.get(i))) return false;
        }
        return true;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        for (InputContent content : contents) {
            if (content.satisfy(actual)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEnumerable() {
        return contents.allSatisfy(InputContent::isEnumerable);
    }

    @Override
    public RichIterable<? extends ContentVariant> enumerate() {
        return isEnumerable() ? contents.flatCollect(InputContent::enumerate).distinct() : Lists.immutable.empty();
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.COMPLEX;
    }
}
