package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

public final class JeiTargetRecipeBridge {

    private JeiTargetRecipeBridge() {
    }

    public static @Nullable RecipeEntry<?> resolve(IRecipeLayoutDrawable<?> layout) {
        return resolve(layout.getRecipeCategory().getRecipeType(), layout.getRecipe());
    }

    public static @Nullable RecipeEntry<?> resolveCalculator(IRecipeLayoutDrawable<?> layout) {
        RecipeEntry<?> entry = resolve(layout);
        return entry != null && entry.supportsCalculator() ? entry : null;
    }

    public static @Nullable RecipeEntry<?> resolveRecipeDatabase(IRecipeLayoutDrawable<?> layout) {
        RecipeEntry<?> entry = resolve(layout);
        return entry != null && entry.supportsRecipeDatabase() ? entry : null;
    }

    public static @Nullable RecipeEntry<?> resolveExecution(IRecipeLayoutDrawable<?> layout) {
        RecipeEntry<?> entry = resolve(layout);
        return entry != null && entry.supportsExecution() ? entry : null;
    }

    public static @Nullable RecipeEntry<?> resolve(RecipeType<?> jeiType, Object recipe) {
        if (JeiRecipeCategoryFilter.excluded(jeiType)) {
            return null;
        }

        if (isCraftingType(jeiType)
            && recipe instanceof RecipeHolder<?> holder
            && holder.value() instanceof CraftingRecipe) {
            VanillaRecipeSystem<CraftingRecipe> system =
                RecipeApi.instance().findVanillaSystem(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
            if (system != null) {
                @SuppressWarnings("unchecked")
                RecipeEntry<?> entry = RecipeEntry.of((RecipeSystem) system, holder);
                return entry;
            }
        }

        RecipeApi recipeApi = RecipeApi.instance();
        for (RecipeSystem<?> system : recipeApi.allSystems()) {
            if (system instanceof JeiCategoryRecipeSystem<?>) {
                continue;
            }
            if (!matchesJeiType(recipeApi, system, jeiType)) {
                continue;
            }
            RecipeEntry<?> entry = wrap(system, recipe);
            if (entry != null) {
                return entry;
            }
        }
        RecipeSystem<?> jeiSystem = recipeApi.findSystem(JeiCategoryRecipeSystem.systemId(jeiType));
        if (jeiSystem == null) {
            jeiSystem = CirrusJeiPlugin.findRecipeSystem(jeiType);
        }
        if (jeiSystem != null) {
            return wrap(jeiSystem, recipe);
        }
        return null;
    }

    private static boolean isCraftingType(RecipeType<?> jeiType) {
        var uid = jeiType.getUid();
        var recipeClass = jeiType.getRecipeClass();
        return uid != null
            && "minecraft".equals(uid.getNamespace())
            && "crafting".equals(uid.getPath())
            && RecipeHolder.class.isAssignableFrom(recipeClass);
    }

    private static boolean matchesJeiType(RecipeApi recipeApi, RecipeSystem<?> system, RecipeType<?> jeiType) {
        for (RecipeType<?> mappedType : recipeApi.getJeiRecipeTypeForSystem(system.systemId())) {
            if (mappedType.equals(jeiType)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable RecipeEntry<?> wrap(RecipeSystem<?> system, Object recipe) {
        if (!claims((RecipeSystem) system, recipe)) {
            return null;
        }
        return RecipeEntry.of((RecipeSystem) system, recipe);
    }

    @SuppressWarnings("unchecked")
    private static <R> boolean claims(RecipeSystem<R> system, Object recipe) {
        if (!system.basicType().isInstance(recipe)) {
            return false;
        }
        return system.claim((R) recipe).claimed();
    }
}
