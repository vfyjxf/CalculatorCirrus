package dev.vfyjxf.calculatorcirrus.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.MutableData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.helper.CollectionHelper;
import dev.vfyjxf.calculatorcirrus.helper.ContentHelper;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.ImmutableListMultimap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.jetbrains.annotations.Nullable;

public class BasicRecipeData implements RecipeData {

    private final ImmutableListMultimap<IngredientType, ? extends IngredientStack> stableData;
    private final ImmutableListMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData;
    private final String uid;

    public static BasicRecipeData create(
            ImmutableListMultimap<IngredientType, ? extends IngredientStack> stableData,
            @Nullable String uid
    ) {
        return new BasicRecipeData(stableData, CollectionHelper.emptyMultimap(), uid);
    }

    public static BasicRecipeData create(
            ImmutableListMultimap<IngredientType, ? extends IngredientStack> stableData,
            ImmutableListMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData,
            @Nullable String uid
    ) {
        return new BasicRecipeData(stableData, mutableData, uid);
    }

    private BasicRecipeData(
            ImmutableListMultimap<IngredientType, ? extends IngredientStack> stableData,
            ImmutableListMultimap<MutableData.Key<?>, ? extends MutableData<?>> mutableData,
            @Nullable String uid
    ) {
        this.stableData = stableData;
        this.mutableData = mutableData;
        this.uid = uid;
    }

    @Override
    public MutableList<? extends IngredientStack> get(IngredientType type) {
        return stableData.get(type).toList();
    }

    @Override
    public MutableList<? extends IngredientStack> getCatalysts() {
        return stableData.get(IngredientType.CATALYST).toList();
    }

    @Override
    public MutableList<? extends IngredientStack> getOutputs() {
        return stableData.get(IngredientType.OUTPUT).toList();
    }

    @Override
    public MutableList<? extends IngredientStack> getInputs() {
        return stableData.get(IngredientType.INPUT).toList();
    }

    @Override
    public MutableListMultimap<IngredientType, ? extends IngredientStack> getStableData() {
        return stableData.toMutable();
    }

    @Override
    public MutableListMultimap<MutableData.Key<?>, ? extends MutableData<?>> getMutableData() {
        return mutableData.toMutable();
    }

    @Override
    public RecipeData mergeData() {
        MutableListMultimap<IngredientType, IngredientStack> mergedData = FastListMultimap.newMultimap();
        for (IngredientType ingredientType : stableData.keySet()) {
            ImmutableList<? extends IngredientStack> stacks = stableData.get(ingredientType);
            RichIterable<? extends IngredientStack> merged = ContentHelper.mergeIngredients(stacks);
            mergedData.putAll(ingredientType, merged);
        }
        return new BasicRecipeData(mergedData.toImmutable(), mutableData.toImmutable(), uid);
    }

    @Override
    public @Nullable String id() {
        return uid;
    }

    @Override
    public @Nullable CompoundTag serialize() {
        //TODO: Implement serialization
        return null;
    }
}
