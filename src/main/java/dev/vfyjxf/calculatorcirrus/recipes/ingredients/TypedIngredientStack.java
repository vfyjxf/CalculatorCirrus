package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

public class TypedIngredientStack<T> implements IngredientStack {

    private final ImmutableList<ContentStack<T>> stacks;
    private float chance = MAX_CHANCE;
    private long required;

    public TypedIngredientStack(ContentStack<T> stack, long required) {
        this(stack, required, MAX_CHANCE);
    }

    public TypedIngredientStack(ContentStack<T> stack, long required, float chance) {
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
    public IngredientStack setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public long getRequired() {
        return required;
    }

    @Override
    public IngredientStack setRequiredAmount(long amount) {
        this.required = amount;
        return this;
    }
}
