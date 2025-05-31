package dev.vfyjxf.calculatorcirrus.recipe.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.FixedSizeList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Unmodifiable;

public class TypedIngredientStack<T> implements IngredientStack {

    private final FixedSizeList<ContentStack<T>> stacks;
    private float chance;
    private long amount;

    public TypedIngredientStack(ContentStack<T> stack, long amount) {
        this(stack, amount, MAX_CHANCE);
    }

    public TypedIngredientStack(ContentStack<T> stack, long amount, float chance) {
        this.amount = amount;
        this.chance = chance;
        this.stacks = Lists.fixedSize.of(stack.normalize());
    }

    public StackContentType<T> getType() {
        return stacks.getFirst().contentType();
    }

    @Override
    public IngredientStack copy() {
        return new TypedIngredientStack<>(stacks.getFirst(), amount, chance);
    }

    @Override
    public @Unmodifiable MutableList<? extends ContentStack<?>> getStacks() {
        return stacks.asUnmodifiable();
    }

    @Override
    public ContentStack<?> getOnly() {
        return stacks.getOnly();
    }

    @Override
    public ContentStack<?> getAny() {
        return stacks.getAny();
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
    public boolean matches(IngredientStack stack, MatchStrategy strategy) {
        if (stack.isSingle()) {
            return stacks.getFirst().matches(stack.getStacks().getFirst(), strategy);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedIngredientStack<?> that = (TypedIngredientStack<?>) o;
        return Float.compare(chance, that.chance) == 0 && amount == that.amount && stacks.equals(that.stacks);
    }

    @Override
    public int hashCode() {
        return stacks.hashCode();
    }

    @Override
    public String toString() {
        return "TypedIngredientStack{" +
                "chance=" + chance +
                ", amount=" + amount +
                ", stack=" + stacks.getOnly().asString() +
                '}';
    }
}
