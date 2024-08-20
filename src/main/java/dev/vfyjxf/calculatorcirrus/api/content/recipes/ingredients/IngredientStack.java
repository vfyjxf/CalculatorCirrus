package dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TypedIngredientStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.jetbrains.annotations.Contract;

import java.util.Arrays;

public interface IngredientStack {

    static <T> IngredientStack empty() {
        return new TypedIngredientStack<>(ContentStack.empty(), 0, IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack create(ContentStack<T> stack) {
        return new TypedIngredientStack<>(stack, stack.getAmount(), IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack create(ContentStack<T> stack, long requiredAmount) {
        return new TypedIngredientStack<>(stack, requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack create(ContentStack<T> stack, long requiredAmount, float chance) {
        ContentType<T> contentType = stack.contentType();
        T normalized = contentType.normalize(stack, stack.value());
        ContentStack<T> normalizedStack = ContentStack.create(contentType, normalized);
        return new TypedIngredientStack<>(normalizedStack, requiredAmount, chance);
    }

    static IngredientStack create(ItemStack itemStack) {
        return create(ContentStack.create(itemStack), itemStack.getCount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(ItemStack itemStack, long requiredAmount) {
        return create(ContentStack.create(itemStack), requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(ItemStack itemStack, long requiredAmount, float chance) {
        return create(ContentStack.create(itemStack), requiredAmount, chance);
    }

    static IngredientStack create(Ingredient ingredient) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        ItemStack[] items = ingredient.getItems();
        int amount = 1;
        if (items.length > 0) {
            amount = items[0].getCount();
            final int catchAmount = amount;
            boolean allAmountsEqual = Arrays.stream(items).allMatch(stack -> stack.getCount() == catchAmount);
            if (!allAmountsEqual) {
                amount = 1;
            }
        }
        return create(ingredient, amount);
    }

    static IngredientStack create(Ingredient ingredient, long requiredAmount) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        return create(ingredient, requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(Ingredient ingredient, long requiredAmount, float chance) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        //TODO:
        MutableList<ContentStack<ItemStack>> stacks = ArrayAdapter.adapt(ingredient.getItems()).collect(ContentStack::create).toList();
        if (stacks.size() == 1) {
            return create(stacks.getFirst(), requiredAmount, chance);
        }
        return null;
    }

    static IngredientStack create(FluidStack fluidStack) {
        return create(ContentStack.create(fluidStack), fluidStack.getAmount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(FluidStack fluidStack, long requiredAmount) {
        return create(ContentStack.create(fluidStack), requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(FluidStack fluidStack, long requiredAmount, float chance) {
        return create(ContentStack.create(fluidStack), requiredAmount, chance);
    }

    float MAX_CHANCE = -1.0f;

    MutableList<? extends ContentStack<?>> getStacks();

    default boolean isSingle() {
        return getStacks().size() == 1 && !isEmpty();
    }

    default boolean isEmpty() {
        return getStacks().allSatisfy(ContentStack::isEmpty);
    }

    /**
     * Because from the specificity from floats, we use negative numbers to represent the maximum probability (100%)
     */
    default float getChance() {
        return MAX_CHANCE;
    }

    IngredientStack setChance(float chance);

    default boolean isChanced() {
        return getChance() >= 0;
    }

    /**
     * Minimum amount required for recipe processing at one time
     *
     * @return the required amount
     */
    long getRequired();

    default IngredientStack multiply(double multiplier) {
        setRequiredAmount((long) (getRequired() * multiplier));
        return this;
    }

    default boolean matches(IngredientStack stack) {
        if (this == stack) return true;
        if (isEmpty() || stack.isEmpty()) return false;
        if (isSingle() && stack.isSingle()) {
            ContentStack<?> selfStack = getStacks().getOnly();
            ContentStack<?> otherStack = stack.getStacks().getOnly();
            return selfStack.matches(otherStack, MatchContext.EXACT);
        } else {
            return false;
        }
    }

    default boolean merge(IngredientStack other) {
        MutableList<? extends ContentStack<?>> selfStacks = getStacks();
        MutableList<? extends ContentStack<?>> otherStacks = other.getStacks();
        if (this.isEmpty() || other.isEmpty()) return false;
        if (selfStacks.size() != otherStacks.size()) return false;
        if (this.isSingle() && other.isSingle()) {
            ContentStack<?> selfStack = selfStacks.getOnly();
            ContentStack<?> otherStack = otherStacks.getOnly();
            if (selfStack.contentType() != otherStack.contentType()) return false;
            setRequiredAmount(getRequired() + other.getRequired());
            return true;
        } else {

        }
    }

    @Contract("_ -> this")
    IngredientStack setRequiredAmount(long amount);

    default boolean satisfies(long amount) {
        return getRequired() <= amount;
    }

    default long getMissing(long amount) {
        return Math.max(0, getRequired() - amount);
    }

}
