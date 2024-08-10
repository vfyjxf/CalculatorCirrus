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
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

public interface IngredientStack<T> {

    static <T> IngredientStack<T> empty() {
        return new TypedIngredientStack<>(ContentStack.empty(), 0, IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack<T> create(ContentStack<T> stack) {
        return new TypedIngredientStack<>(stack, stack.getAmount(), IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack<T> create(ContentStack<T> stack, long requiredAmount) {
        return new TypedIngredientStack<>(stack, requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack<T> create(ContentStack<T> stack, long requiredAmount, float chance) {
        ContentType<T> contentType = stack.contentType();
        T normalized = contentType.normalize(stack, stack.value());
        ContentStack<T> normalizedStack = ContentStack.create(contentType, normalized);
        return new TypedIngredientStack<>(normalizedStack, requiredAmount, chance);
    }

    static IngredientStack<ItemStack> create(ItemStack itemStack) {
        return create(ContentStack.create(itemStack), itemStack.getCount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack<ItemStack> create(ItemStack itemStack, long requiredAmount) {
        return create(ContentStack.create(itemStack), requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack<ItemStack> create(ItemStack itemStack, long requiredAmount, float chance) {
        return create(ContentStack.create(itemStack), requiredAmount, chance);
    }

    static IngredientStack<ItemStack> create(Ingredient ingredient) {
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

    static IngredientStack<ItemStack> create(Ingredient ingredient, long requiredAmount) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        return create(ingredient, requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack<ItemStack> create(Ingredient ingredient, long requiredAmount, float chance) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        //TODO:
        MutableList<ContentStack<ItemStack>> stacks = ArrayAdapter.adapt(ingredient.getItems()).collect(ContentStack::create).toList();
        if (stacks.size() == 1) {
            return create(stacks.getFirst(), requiredAmount, chance);
        }
        return null;
    }

    static IngredientStack<FluidStack> create(FluidStack fluidStack) {
        return create(ContentStack.create(fluidStack), fluidStack.getAmount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack<FluidStack> create(FluidStack fluidStack, long requiredAmount) {
        return create(ContentStack.create(fluidStack), requiredAmount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack<FluidStack> create(FluidStack fluidStack, long requiredAmount, float chance) {
        return create(ContentStack.create(fluidStack), requiredAmount, chance);
    }

    float MAX_CHANCE = -1.0f;

    MutableList<? extends ContentStack<?>> getStacks();

    default boolean single() {
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

    IngredientStack<T> setChance(float chance);

    default boolean isChanced() {
        return getChance() >= 0;
    }

    /**
     * Minimum amount required for recipe processing at one time
     *
     * @return the required amount
     */
    long getRequiredAmount();

    long getCurrentAmount();

    default IngredientStack<T> multiply(double multiplier) {
        setRequiredAmount((long) (getRequiredAmount() * multiplier));
        return this;
    }

    IngredientStack<T> recalculate();

    default boolean merge(IngredientStack<T> other) {
        MutableList<? extends ContentStack<?>> selfStacks = getStacks();
        MutableList<? extends ContentStack<?>> otherStacks = other.getStacks();
        if (selfStacks.size() != otherStacks.size()) return false;


        ContentType<T> def = getStack().contentType();
        if (def.matches(getStack(), other.getStack(), MatchContext.FUZZY)) {
            setRequiredAmount(getRequiredAmount() + other.getRequiredAmount());
            addCurrent(other.getCurrentAmount());
            return true;
        }
        return false;
    }

    IngredientStack<T> addCurrent(long amount);

    default IngredientStack<T> addCurrent(ContentStack<?> stack) {
        ContentType<T> selfDef = getStack().contentType();
        if (selfDef != stack.contentType()) return this;
        ContentStack<T> casted = stack.cast();
        if (selfDef.matches(getStack(), casted, MatchContext.FUZZY)) {
            long amount = selfDef.getAmount(casted, casted.value());
            addCurrent(amount);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    default IngredientStack<T> addCurrent(IngredientStack<?> stack) {
        boolean sameType = getStack().contentType() == stack.getStack().contentType();
        if (sameType) merge((IngredientStack<T>) stack);
        return this;
    }

    IngredientStack<T> setRequiredAmount(long amount);

    @ApiStatus.Internal
    default double getInternalExpectation() {
        long amount = getAmount();
        return isChanced() ? (amount * getChance()) : amount;
    }

    default long getExpectation() {
        long amount = getRequiredAmount();
        return isChanced() ? Math.round(amount * getChance()) : amount;
    }

    default boolean satisfies() {
        return getCurrentAmount() >= getExpectation();
    }
}
