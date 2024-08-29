package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;

public class ListIngredientStack implements IngredientStack {

    private final MutableList<? extends ContentStack<?>> stacks;
    private long amount;
    private float chance;

    public ListIngredientStack(MutableList<? extends ContentStack<?>> stacks) {
        this(stacks, 1);
    }

    public ListIngredientStack(MutableList<? extends ContentStack<?>> stacks, long amount) {
        this(stacks, amount, MAX_CHANCE);
    }

    public ListIngredientStack(MutableList<? extends ContentStack<?>> stacks, long amount, float chance) {
        this.stacks = stacks;
        this.amount = amount;
        this.chance = chance;
    }

    @Override
    public MutableList<? extends ContentStack<?>> getStacks() {
        return stacks;
    }

    @Override
    public IngredientStack setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public IngredientStack setAmount(long amount) {
        this.amount = amount;
        return this;
    }
}
