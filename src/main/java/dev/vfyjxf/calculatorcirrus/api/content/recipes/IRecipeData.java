package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IIngredientStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IRecipeData {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    static <T> IRecipeData from(T recipe) {
        IRecipeRegistry registry = IRecipeRegistry.getInstance();
        IRecipeHandler handler = registry.getRecipeHandler(recipe.getClass());
        if (handler == null) {
            handler = registry.allUniversalHandlers()
                    .stream()
                    .filter(it -> it.isSupport(recipe.getClass()))
                    .findAny()
                    .orElse(null);
        }
        if (handler == null) return null;
        ResourceLocation uid = handler.getRecipeUid(recipe);
        String id = uid == null ? null : uid.toString();
        return new RecipeData(handler.parse(recipe), id);
    }

    IRecipeDataSource source();

    default List<IIngredientStack<?>> get(IngredientType type) {
        return data().getOrDefault(type, Collections.emptyList());
    }

    Map<IngredientType, List<IIngredientStack<?>>> data();

    @Nullable
    String uid();

    @Nullable
    CompoundTag serialize();

}
