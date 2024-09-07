package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.jetbrains.annotations.Nullable;

public interface RecipeData {

    @Nullable
    static <T> RecipeData from(T recipe) {
        throw new UnsupportedOperationException("Not implemented");
    }

    default MutableList<? extends IngredientStack> get(IngredientType type) {
        return getStableData().get(type);
    }

    default MutableList<? extends IngredientStack> getInputs() {
        return getStableData().get(IngredientType.INPUT);
    }

    default MutableList<? extends IngredientStack> getOutputs() {
        return getStableData().get(IngredientType.OUTPUT);
    }

    default MutableList<? extends IngredientStack> getCatalysts() {
        return getStableData().get(IngredientType.CATALYST);
    }

    /**
     * @return the recipe getStableData;
     */
    MutableListMultimap<IngredientType, ? extends IngredientStack> getStableData();

    MutableListMultimap<MutableData.Key<?>, ? extends MutableData<?>> getMutableData();

    RecipeData mergeData();

    @Nullable
    String id();

    @Nullable
    CompoundTag serialize();

}
