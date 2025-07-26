package dev.vfyjxf.calculatorcirrus.helper;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

public class ContentHelper {

    public RichIterable<? extends IngredientStack> getExpectedIngredients(RichIterable<? extends IngredientStack> ingredients, boolean consume) {
        MutableList<IngredientStack> ret = Lists.mutable.empty();
        for (IngredientStack ingredient : ingredients) {
            if (!ingredient.isChanced()) ret.add(ingredient.copy());
            else {
                IngredientStack copy = ingredient.copy();
                copy.setChance(IngredientStack.MAX_CHANCE);
                copy.setAmount(copy.getExpectation(consume));
            }
        }
        return ret;
    }

    public static RichIterable<? extends IngredientStack> mergeIngredients(RichIterable<? extends IngredientStack> ingredients) {
        MutableList<IngredientStack> merged = Lists.mutable.empty();
        for (IngredientStack ingredient : ingredients) {
            boolean added = false;
            for (IngredientStack stack : merged) {
                if (stack.matches(ingredient)) {

                    added = true;
                    break;
                }
            }
            if (!added) {
                merged.add(ingredient.copy());
            }
        }
        return merged;
    }

    private ContentHelper() {
    }
}
