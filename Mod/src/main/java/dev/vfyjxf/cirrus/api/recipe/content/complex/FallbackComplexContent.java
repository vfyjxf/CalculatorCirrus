package dev.vfyjxf.cirrus.api.recipe.content.complex;

import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import org.jetbrains.annotations.Range;

public class FallbackComplexContent implements ComplexContent {

    public static InputContent from(ICustomIngredient ingredient, long amount, float chance) {
        if (amount < 0) return empty;
        return new FallbackComplexContent(ingredient, amount, chance);
    }

    private final ICustomIngredient ingredient;
    private final long amount;
    private final float chance;

    FallbackComplexContent(ICustomIngredient ingredient, long amount, float chance) {
        this.ingredient = ingredient;
        this.amount = amount;
        this.chance = chance;
    }

    @Override
    public boolean isEmpty() {
        return false;
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
    public InputContent copy() {
        return new FallbackComplexContent(ingredient, amount, chance);
    }

    @Override
    public InputContent copyWithAmount(long amount) {
        return new FallbackComplexContent(ingredient, amount, chance);
    }

    @Override
    public InputContent copyWithChance(float chance) {
        return new FallbackComplexContent(ingredient, amount, chance);
    }

    @Override
    public InputContent copyWithAmountAndChance(long amount, float chance) {
        return new FallbackComplexContent(ingredient, amount, chance);
    }

    @Override
    public boolean mergeable(RecipeContent content) {
        if (!(content instanceof FallbackComplexContent other)) return false;
        return this.ingredient == other.ingredient;
    }

    @Override
    public boolean satisfy(ContentVariant actual) {
        return switch (actual) {
            case ContentStack<?, ?> stack -> stack.isItem() && ingredient.test(stack.<Item, ItemStack>cast().value());
            case ContentKind<?> kind -> kind.isItem() && ingredient.test(kind.type().cast().toItemStack(kind.value()));
            default -> false;
        };
    }

    @Override
    public RecipeContentType<? extends RecipeContent> recipeType() {
        return RecipeContentType.COMPLEX;
    }
}
