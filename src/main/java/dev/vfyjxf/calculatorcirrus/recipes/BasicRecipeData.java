package dev.vfyjxf.calculatorcirrus.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.multimap.ImmutableMultimap;
import org.jetbrains.annotations.Nullable;

public class BasicRecipeData implements RecipeData {

    private final ImmutableMultimap<IngredientType, IngredientStack<?>> data;
    private final String uid;

    public BasicRecipeData(ImmutableMultimap<IngredientType, IngredientStack<?>> data, @Nullable String uid) {
        this.data = data;
        this.uid = uid;
    }

    @Override
    public ImmutableMultimap<IngredientType, IngredientStack<?>> data() {
        return data;
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
