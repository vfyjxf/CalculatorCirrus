package dev.vfyjxf.calculatorcirrus.api.recipe.content.complex;

import com.google.common.collect.ImmutableList;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;
import org.jetbrains.annotations.Range;

public final class IntersectionContent implements ComplexContent {

    private final ImmutableList<InputContent> contents;
    private final long amount;
    private final float chance;

    IntersectionContent(ImmutableList<InputContent> contents, long amount, float chance) {
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
    public IntersectionContent copy() {
        return new IntersectionContent(contents, amount, chance);
    }

    @Override
    public IntersectionContent copyWithAmount(long amount) {
        return new IntersectionContent(contents, amount, chance);
    }

    @Override
    public IntersectionContent copyWithChance(float chance) {
        return new IntersectionContent(contents, amount, chance);
    }

    @Override
    public IntersectionContent copyWithAmountAndChance(long amount, float chance) {
        return new IntersectionContent(contents, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent other) {
        if (!(other instanceof IntersectionContent o)) return false;
        if (contents.size() != o.contents.size()) return false;
        for (int i = 0; i < contents.size(); i++) {
            if (!contents.get(i).mergeable(o.contents.get(i))) return false;
        }
        return true;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        return false;
    }


}
