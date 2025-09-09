package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Range;

public final class EmptyRecipeContent implements InputContent, OutputContent {

    EmptyRecipeContent() {}

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public long amount() {
        return 0;
    }

    @Override
    public @Range(from = 0, to = 1) float chance() {
        return 0;
    }

    @Override
    public EmptyRecipeContent copy() {
        return this;
    }

    @Override
    public EmptyRecipeContent copyWithAmount(long amount) {
        return this;
    }

    @Override
    public EmptyRecipeContent copyWithChance(float chance) {
        return this;
    }

    @Override
    public EmptyRecipeContent copyWithAmountAndChance(long amount, float chance) {
        return this;
    }

    @Override
    public MutableList<? extends ContentVariant> equivalents() {
        return Lists.mutable.empty();
    }

    @Override
    public boolean satisfy(ContentValue<?> value) {
        return value.isEmpty();
    }

    @Override
    public boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy) {
        return stack.isEmpty();
    }

    @Override
    public boolean equivalent(RecipeContent other) {
        return other == this || other.isEmpty();
    }

}
