package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import org.eclipse.collections.api.list.MutableList;

public class ListIngredientStack implements IngredientStack {

    @Override
    public MutableList<? extends ContentStack<?>> getStacks() {
        return null;
    }

    @Override
    public IngredientStack setChance(float chance) {
        return null;
    }

    @Override
    public long getRequired() {
        return 0;
    }

    @Override
    public IngredientStack setRequiredAmount(long amount) {
        return null;
    }
}
