package dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;

public interface IIngredientStack<T> {

    float MAX_CHANCE = -1.0f;

    /**
     * @return the getId of the ingredient.
     */
    IContentStack<T> getStack();

    /**
     * Because from the specificity from floats, we use negative numbers to represent the maximum probability (100%)
     */
    default float getChance() {
        return MAX_CHANCE;
    }

    IIngredientStack<T> setChance(float chance);

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

    IIngredientStack<T> recalculate();

    default boolean merge(IIngredientStack<T> other) {
        IContentType<T> def = getStack().type();
        if (def.matches(getStack(), other.getStack(), MatchContext.FUZZY)) {
            setRequiredAmount(getRequiredAmount() + other.getRequiredAmount());
            addCurrent(other.getCurrentAmount());
            return true;
        }
        return false;
    }

    IIngredientStack<T> addCurrent(long amount);

    default IIngredientStack<T> addCurrent(IContentStack<?> stack) {
        IContentType<T> selfDef = getStack().type();
        if (selfDef != stack.type()) return this;
        IContentStack<T> casted = stack.cast();
        if (selfDef.matches(getStack(), casted, MatchContext.FUZZY)) {
            long amount = selfDef.getAmount(casted, casted.value());
            addCurrent(amount);
        }
        return this;
    }


    default IIngredientStack<T> addCurrent(IIngredientStack<?> stack) {
        boolean sameType = getStack().type() == stack.getStack().type();
        if (sameType) merge((IIngredientStack<T>) stack);
        return this;
    }


    IIngredientStack<T> setRequiredAmount(long amount);

    default long getExpectation() {
        IContentStack<T> getContent = getStack();
        long amount = getContent.type().getAmount(getContent, getContent.value());
        return isChanced() ? Math.max(getRequiredAmount(), Math.round(amount * getChance())) : amount;
    }

    default boolean satisfies() {
        return getCurrentAmount() >= getExpectation();
    }
}
