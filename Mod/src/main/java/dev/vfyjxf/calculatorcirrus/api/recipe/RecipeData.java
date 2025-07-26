package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.helper.CollectionHelper;
import dev.vfyjxf.calculatorcirrus.recipe.BasicRecipeData;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.ListMultimap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.jetbrains.annotations.Nullable;

//TODO:support recipes which have time, energy, etc(pertick input/output).
public interface RecipeData {

    static RecipeData create(
            ListMultimap<IngredientType, ? extends IngredientStack> stableData,
            @Nullable String uid
    ) {
        return BasicRecipeData.create(stableData, CollectionHelper.emptyMultimap(), uid);
    }

    static RecipeData create(
            ListMultimap<IngredientType, ? extends IngredientStack> stableData,
            ListMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData,
            @Nullable String uid
    ) {
        return BasicRecipeData.create(stableData, mutableData, uid);
    }

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

    @Nullable
    String id();

    @Nullable
    CompoundTag serialize();

}
