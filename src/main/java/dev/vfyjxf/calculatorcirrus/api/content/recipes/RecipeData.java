package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import net.minecraft.nbt.CompoundTag;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.multimap.ImmutableMultimap;
import org.jetbrains.annotations.Nullable;

public interface RecipeData {

    @Nullable
    static <T> RecipeData from(T recipe) {
        throw new UnsupportedOperationException("Not implemented");
    }

    default ImmutableCollection<IngredientStack<?>> get(IngredientType type) {
        return data().get(type);
    }

    ImmutableMultimap<IngredientType, IngredientStack<?>> data();

    @Nullable
    String uid();

    @Nullable
    CompoundTag serialize();

}
