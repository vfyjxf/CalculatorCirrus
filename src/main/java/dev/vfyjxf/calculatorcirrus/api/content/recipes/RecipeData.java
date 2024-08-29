package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.collection.MutableCollection;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.jetbrains.annotations.Nullable;

public interface RecipeData {

    @Nullable
    static <T> RecipeData from(T recipe) {
        throw new UnsupportedOperationException("Not implemented");
    }

    default MutableCollection<? extends IngredientStack> get(IngredientType type) {
        return getStableData().get(type);
    }

    default MutableCollection<? extends IngredientStack> getInputs() {
        return getStableData().get(IngredientType.INPUT);
    }

    default MutableCollection<? extends IngredientStack> getOutputs() {
        return getStableData().get(IngredientType.OUTPUT);
    }

    default MutableCollection<? extends IngredientStack> getCatalysts() {
        return getStableData().get(IngredientType.CATALYST);
    }

    /**
     * @return the recipe getStableData;
     */
    MutableMultimap<IngredientType, ? extends IngredientStack> getStableData();

    MutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> getMutableData();

    @Nullable
    String uid();

    @Nullable
    CompoundTag serialize();

}
