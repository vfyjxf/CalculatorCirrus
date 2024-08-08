package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;

public class TypedIngredientStack<T> implements IngredientStack<T> {

    private final ContentStack<T> stack;

    private float chance = MAX_CHANCE;
    private long required;
    private long current = 0;

    public TypedIngredientStack(ContentStack<T> stack, long required) {
        this.stack = stack;
        this.required = required;
    }

    public TypedIngredientStack(ContentStack<T> stack, long required, float chance) {
        this.stack = stack;
        this.required = required;
        this.chance = chance;
    }

    @Override
    public ContentStack<T> getStack() {
        return stack;
    }

    @Override
    public IngredientStack<T> setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public long getRequiredAmount() {
        return required;
    }

    @Override
    public long getCurrentAmount() {
        return current;
    }

    @Override
    public IngredientStack<T> recalculate() {
        this.current = 0;
        return this;
    }

    @Override
    public IngredientStack<T> addCurrent(long amount) {
        this.current += amount;
        return this;
    }

    @Override
    public IngredientStack<T> setRequiredAmount(long amount) {
        this.required = amount;
        return this;
    }
}
