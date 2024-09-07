package dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.ListIngredientStack;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TagIngredientStack;
import dev.vfyjxf.calculatorcirrus.recipes.ingredients.TypedIngredientStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;

public interface IngredientStack {

    float MAX_CHANCE = 1f;

    IngredientStack EMPTY = new TypedIngredientStack<>(ContentStack.empty(), 0, MAX_CHANCE);

    static IngredientStack empty() {
        return EMPTY;
    }

    static <T> IngredientStack create(ContentStack<T> stack) {
        long amount = stack.getAmount() <= 0 ? 1 : stack.getAmount();
        return create(stack, amount);
    }

    static <T> IngredientStack create(ContentStack<T> stack, long amount) {
        return create(stack, amount, IngredientStack.MAX_CHANCE);
    }

    static <T> IngredientStack create(ContentStack<T> stack, long amount, float chance) {
        ContentType<T> contentType = stack.contentType();
        T normalized = contentType.normalize(stack, stack.value());
        ContentStack<T> normalizedStack = ContentStack.create(contentType, normalized);
        return new TypedIngredientStack<>(normalizedStack, amount, chance);
    }

    static IngredientStack create(ItemStack itemStack) {
        return create(ContentStack.create(itemStack), itemStack.getCount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(ItemStack itemStack, long amount) {
        return create(ContentStack.create(itemStack), amount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(ItemStack itemStack, long amount, float chance) {
        return create(ContentStack.create(itemStack), amount, chance);
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

    static IngredientStack create(Ingredient ingredient, long amount) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        return create(ingredient, amount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(Ingredient ingredient, long amount, float chance) {
        if (ingredient.isEmpty()) return create(ItemStack.EMPTY);
        MutableList<ContentStack<ItemStack>> stacks = ArrayAdapter.adapt(ingredient.getItems()).collect(ContentStack::create).toList();
        if (stacks.size() == 1) {
            return create(stacks.getFirst(), amount, chance);
        } else {
            TagKey<Item> tag = CirrusContentTypes.ITEM_TAG.getTagFrom(stacks);
            if (tag == null) return new ListIngredientStack(stacks, amount, chance);
            return new TagIngredientStack(CirrusContentTypes.ITEM_TAG, tag, amount, chance);
        }
    }

    static IngredientStack create(FluidStack fluidStack) {
        return create(ContentStack.create(fluidStack), fluidStack.getAmount(), IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(FluidStack fluidStack, long amount) {
        return create(ContentStack.create(fluidStack), amount, IngredientStack.MAX_CHANCE);
    }

    static IngredientStack create(FluidStack fluidStack, long amount, float chance) {
        return create(ContentStack.create(fluidStack), amount, chance);
    }

    @Contract(" -> new")
    IngredientStack copy();

    @Contract(" -> new")
    default IngredientStack normalize() {
        if (isNormalized()) return this;
        return copy()
                .setAmount(1)
                .setChance(MAX_CHANCE);
    }

    @Unmodifiable
    MutableList<? extends ContentStack<?>> getStacks();

    default ContentStack<?> getAny() {
        return getStacks().getAny();
    }

    default ContentStack<?> getOnly() {
        return getStacks().getOnly();
    }

    default ContentStack<?> getFirst() {
        return getStacks().getFirst();
    }

    default boolean isTag() {
        return this instanceof TagIngredientStack;
    }

    default boolean isSingle() {
        return getStacks().size() == 1 && !isEmpty();
    }

    /**
     * @return true if this is a list ingredient
     */
    default boolean isList() {
        return this instanceof ListIngredientStack;
    }

    default boolean isEmpty() {
        return this == EMPTY || getStacks().allSatisfy(ContentStack::isEmpty);
    }

    default boolean notEmpty() {
        return !isEmpty();
    }

    default boolean isNormalized() {
        return getAmount() == 1 && getChance() == MAX_CHANCE;
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
    long getAmount();

    default IngredientStack multiply(double multiplier) {
        double result = getAmount() * multiplier;
        if (result > Long.MAX_VALUE) {
            throw new ArithmeticException("The result of the multiplication is too large to be stored in a long.");
        }
        setAmount((long) result);
        return this;
    }

    default IngredientStack multiply(long multiplier) {
        return multiply((double) multiplier);
    }

    default IngredientStack reverse() {
        return multiply(-1);
    }

    static boolean contains(IngredientStack first, IngredientStack second) {
        MutableList<? extends ContentStack<?>> firstStacks = first.getStacks();
        MutableList<? extends ContentStack<?>> secondStacks = second.getStacks();
        if (firstStacks.size() > secondStacks.size()) {
            return secondStacks.allSatisfy(stack -> firstStacks.anySatisfyWith(ContentStack::matches, stack));
        } else {
            return firstStacks.allSatisfy(stack -> secondStacks.anySatisfyWith(ContentStack::matches, stack));
        }
    }

    default boolean contains(ContentStack<?> contentStack) {
        return contains(contentStack, MatchContext.EXACT);
    }

    default boolean contains(ContentStack<?> contentStack, MatchContext context) {
        MutableList<? extends ContentStack<?>> selfStacks = this.getStacks();
        return selfStacks.anySatisfy(stack -> stack.matches(contentStack, context));
    }

    default boolean matches(IngredientStack stack) {
        return matches(stack, MatchContext.EXACT);
    }

    default boolean matches(IngredientStack stack, MatchContext context) {
        if (this == stack) return true;
        if (isEmpty() || stack.isEmpty()) return false;
        MutableList<? extends ContentStack<?>> self = this.getStacks();
        MutableList<? extends ContentStack<?>> other = stack.getStacks();
        if (self.size() != other.size()) return false;
        for (int i = 0; i < self.size(); i++) {
            ContentStack<?> selfStack = self.get(i);
            ContentStack<?> otherStack = other.get(i);
            if (!selfStack.matches(otherStack, context)) return false;
        }
        return true;
    }

    default boolean merge(IngredientStack other) {
        return merge(other, MatchContext.EXACT);
    }

    default boolean merge(IngredientStack other, MatchContext context) {
        if (!matches(other, context)) return false;
        setAmount(getAmount() + other.getAmount());
        return true;
    }

    @Contract("_ -> this")
    IngredientStack setAmount(long amount);

    default boolean satisfies(long requirement) {
        return getAmount() <= requirement;
    }

    default long getMissing(long requirement) {
        return Math.max(0, getAmount() - requirement);
    }

}
