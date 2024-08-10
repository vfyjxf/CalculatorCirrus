package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

public class TypedIngredientStack<T> implements IngredientStack<T> {

    private final ContentStack<T> stack;
    private final ImmutableList<ContentStack<T>> stacks;
    private float chance = MAX_CHANCE;
    private long required;
    private long current = 0;

    public TypedIngredientStack(ContentStack<T> stack, long required) {
        this(stack, required, MAX_CHANCE);
    }

    public TypedIngredientStack(ContentStack<T> stack, long required, float chance) {
        this.stack = stack;
        this.required = required;
        this.chance = chance;
        this.stacks = Lists.immutable.of(stack);
    }

    @Override
    public MutableList<? extends ContentStack<?>> getStacks() {
        return stacks.toList();
    }

    @Override
    public float getChance() {
        return chance;
    }

    @Override
    public IngredientStack<T> setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public final boolean isChanced() {
        return true;
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
