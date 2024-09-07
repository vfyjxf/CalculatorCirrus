package dev.vfyjxf.calculatorcirrus.helper;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

public class ContentHelper {

    public static RichIterable<? extends IngredientStack> mergeIngredients(RichIterable<? extends IngredientStack> ingredients) {
        MutableList<IngredientStack> merged = Lists.mutable.empty();
        for (IngredientStack ingredient : ingredients) {
            boolean added = false;
            for (IngredientStack stack : merged) {
                if (stack.getChance() == ingredient.getChance() && stack.merge(ingredient)) {
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
