package dev.vfyjxf.calculatorcirrus.recipe.content.resolver.item;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.complex.DataComponentContent;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;

public class DataComponentIngredientResolver implements IngredientResolver<ICustomIngredient> {

    public static final DataComponentIngredientResolver instance = new DataComponentIngredientResolver();

    private DataComponentIngredientResolver() {}

    @Override
    public boolean select(ICustomIngredient ingredient) {
        return ingredient instanceof DataComponentIngredient || ingredient instanceof DataComponentFluidIngredient;
    }

    @Override
    public InputContent resolve(ICustomIngredient ingredient, long amount, float chance) {
        if (ingredient instanceof DataComponentIngredient dataComponentIngredient) {
            return DataComponentContent.fromItemIngredient(dataComponentIngredient, amount, chance);
        } else if (ingredient instanceof DataComponentFluidIngredient dataComponentFluidIngredient) {
            return DataComponentContent.fromFluidIngredient(dataComponentFluidIngredient, amount, chance);
        }
        //never reach
        throw new IllegalArgumentException("Invalid ingredient type: " + ingredient.getClass().getName());
    }
}
