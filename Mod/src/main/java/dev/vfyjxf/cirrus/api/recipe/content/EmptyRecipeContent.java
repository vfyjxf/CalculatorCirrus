package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
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
    public boolean is(ContentVariant variant) {
        if (variant instanceof ContentStack<?, ?> stack) {
            return stack.isEmpty();
        } else return false;
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
    public boolean satisfy(ContentVariant actual) {
        return false;
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        return other == this;
    }

    @Override
    public String toString() {
        return "empty content";
    }
}
