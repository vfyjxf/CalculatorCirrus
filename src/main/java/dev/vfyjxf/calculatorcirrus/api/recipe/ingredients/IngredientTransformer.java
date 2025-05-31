package dev.vfyjxf.calculatorcirrus.api.recipe.ingredients;

import java.util.List;

/**
 * Transform ingredients from one type to another
 * such as from a {@link net.minecraft.world.item.crafting.Ingredient} to a {@link net.neoforged.neoforge.fluids.FluidStack}
 */
public interface IngredientTransformer {

    /**
     * Transform an ingredient from one type to another
     *
     * @param ingredient the ingredient to transform
     * @return the transformed ingredient
     */
    List<IngredientStack> transform(IngredientStack ingredient);
}
