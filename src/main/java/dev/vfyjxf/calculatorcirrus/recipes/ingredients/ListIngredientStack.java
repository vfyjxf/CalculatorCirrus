package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

@ApiStatus.Internal
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
        this.stacks = stacks.collect(ContentStack::normalize);
        this.amount = amount;
        this.chance = chance;
    }

    @Override
    public IngredientStack copy() {
        return new ListIngredientStack(stacks, amount, chance);
    }

    @Override
    public @Unmodifiable MutableList<? extends ContentStack<?>> getStacks() {
        return stacks.asUnmodifiable();
    }

    @Override
    public ContentStack<?> getAny() {
        return stacks.getAny();
    }

    @Override
    public ContentStack<?> getOnly() {
        return stacks.getOnly();
    }

    @Override
    public ContentStack<?> getFirst() {
        return stacks.getFirst();
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
    public long getAmount() {
        return amount;
    }

    @Override
    public IngredientStack setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ListIngredientStack that = (ListIngredientStack) o;
        return amount == that.amount && Float.compare(chance, that.chance) == 0 && stacks.equals(that.stacks);
    }

    @Override
    public int hashCode() {
        int result = stacks.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ListIngredientStack{" +
                "stacks=" + stacks.collect(ContentStack::getAsString).makeString() +
                ", amount=" + amount +
                ", chance=" + chance +
                '}';
    }
}
