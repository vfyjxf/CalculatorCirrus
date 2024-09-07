package dev.vfyjxf.calculatorcirrus.calculator;

import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;

public class DecomposeList {
    private final MutableList<IngredientStack> ingredients;

    public DecomposeList(MutableList<? extends IngredientStack> ingredients) {
        this.ingredients = ingredients.collect(ingredientStack -> ingredientStack.copy().multiply(-1));
    }

    public void multiply(double multiplier) {
        ingredients.forEach(ingredient -> ingredient.multiply(multiplier));
    }

    public DecomposeList copy() {
        return new DecomposeList(ingredients.collect(IngredientStack::copy));
    }

    public DecomposeList merge(DecomposeList other) {
        return merge(other, MatchContext.EXACT);
    }

    public DecomposeList merge(DecomposeList other, MatchContext matchContext) {
        return merge(other, matchContext, false);
    }

    public DecomposeList merge(DecomposeList other, MatchContext matchContext, boolean replace) {
        DecomposeList ret = replace ? copy() : this;
        outer:
        for (IngredientStack toMerge : other.ingredients) {
            for (IngredientStack ingredient : ret.ingredients) {
                boolean merge = ingredient.merge(toMerge, matchContext);
                if (merge) continue outer;
            }
            ret.ingredients.add(toMerge);
        }
        return this;
    }

    public boolean isEmpty() {
        return ingredients.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DecomposeList that = (DecomposeList) o;
        return ingredients.equals(that.ingredients);
    }

    @Override
    public int hashCode() {
        return ingredients.hashCode();
    }
}
