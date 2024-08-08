package dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;

public interface IngredientStack<T> {

    float MAX_CHANCE = -1.0f;

    /**
     * @return the getId of the ingredient.
     */
    ContentStack<T> getStack();

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

    default long getExpectation() {
        ContentStack<T> getContent = getStack();
        long amount = getContent.contentType().getAmount(getContent, getContent.value());
        return isChanced() ? Math.max(getRequiredAmount(), Math.round(amount * getChance())) : amount;
    }

    default boolean satisfies() {
        return getCurrentAmount() >= getExpectation();
    }
}
