package dev.vfyjxf.calculatorcirrus.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.MutableData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.helper.CollectionHelper;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.collection.MutableCollection;
import org.eclipse.collections.api.multimap.ImmutableMultimap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.jetbrains.annotations.Nullable;

public class BasicRecipeData implements RecipeData {

    private final ImmutableMultimap<IngredientType, ? extends IngredientStack> stableData;
    private final ImmutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData;
    private final String uid;

    public static BasicRecipeData create(
            ImmutableMultimap<IngredientType, ? extends IngredientStack> stableData,
            @Nullable String uid
    ) {
        return new BasicRecipeData(stableData, CollectionHelper.emptyMultimap(), uid);
    }

    public static BasicRecipeData create(
            ImmutableMultimap<IngredientType, ? extends IngredientStack> stableData,
            ImmutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData,
            @Nullable String uid
    ) {
        return new BasicRecipeData(stableData, mutableData, uid);
    }

    private BasicRecipeData(
            ImmutableMultimap<IngredientType, ? extends IngredientStack> stableData,
            ImmutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData,
            @Nullable String uid
    ) {
        this.stableData = stableData;
        this.mutableData = mutableData;
        this.uid = uid;
    }

    @Override
    public MutableCollection<? extends IngredientStack> get(IngredientType type) {
        return stableData.get(type).toList();
    }

    @Override
    public MutableCollection<? extends IngredientStack> getCatalysts() {
        return stableData.get(IngredientType.CATALYST).toList();
    }

    @Override
    public MutableCollection<? extends IngredientStack> getOutputs() {
        return stableData.get(IngredientType.OUTPUT).toList();
    }

    @Override
    public MutableCollection<? extends IngredientStack> getInputs() {
        return stableData.get(IngredientType.INPUT).toList();
    }

    @Override
    public MutableMultimap<IngredientType, ? extends IngredientStack> getStableData() {
        return stableData.toMutable();
    }

    @Override
    public MutableMultimap<MutableData.Key<?>, ? extends MutableData<?>> getMutableData() {
        return mutableData.toMutable();
    }

    @Override
    public @Nullable String uid() {
        return uid;
    }

    @Override
    public @Nullable CompoundTag serialize() {
        //TODO: Implement serialization
        return null;
    }
}
