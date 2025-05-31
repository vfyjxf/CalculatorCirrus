package dev.vfyjxf.calculatorcirrus.recipe.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Unmodifiable;

import java.util.function.ToIntFunction;

public class WrapperIngredientStack implements IngredientStack {

    public static final ToIntFunction<IngredientStack> DEFAULT_HASH = IngredientStack::hashCode;
    public static final ToIntFunction<IngredientStack> HASH_WITH_AMOUNT = stack -> stack.hashCode() * 31 + Long.hashCode(stack.getAmount());
    public static final ToIntFunction<IngredientStack> HASH_ALL = stack -> stack.hashCode() * 31 + Long.hashCode(stack.getAmount()) * 31 + Float.hashCode(stack.getChance());

    private final IngredientStack delegate;
    private ToIntFunction<IngredientStack> hashFunction = DEFAULT_HASH;

    public static WrapperIngredientStack of(IngredientStack stack) {
        if (stack instanceof WrapperIngredientStack wrapper) {
            return wrapper;
        }
        return new WrapperIngredientStack(stack);
    }

    private WrapperIngredientStack(IngredientStack delegate) {
        this.delegate = delegate;
    }

    public IngredientStack delegate() {
        return delegate;
    }

    public WrapperIngredientStack hashFunction(ToIntFunction<IngredientStack> hashFunction) {
        this.hashFunction = hashFunction;
        return this;
    }

    @Override
    public IngredientStack copy() {
        return delegate.copy();
    }

    @Override
    public @Unmodifiable MutableList<? extends ContentStack<?>> getStacks() {
        return delegate.getStacks();
    }

    @Override
    public IngredientStack setChance(float chance) {
        delegate.setChance(chance);
        return this;
    }

    @Override
    public long getAmount() {
        return delegate.getAmount();
    }

    @Override
    public IngredientStack setAmount(long amount) {
        delegate.setAmount(amount);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngredientStack)) return false;

        if (o instanceof WrapperIngredientStack wrapper) {
            return delegate.equals(wrapper.delegate);
        } else {
            return delegate.equals(o);
        }
    }

    @Override
    public int hashCode() {
        return hashFunction.applyAsInt(delegate);
    }

    @Override
    public String toString() {
        return "WrapperIngredientStack{" +
                "delegate=" + delegate +
                '}';
    }
}
