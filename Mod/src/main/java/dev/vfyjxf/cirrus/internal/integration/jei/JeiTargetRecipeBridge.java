package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.BrewingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.VanillaBrewingRecipe;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

public final class JeiTargetRecipeBridge {

    private JeiTargetRecipeBridge() {}

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static @Nullable RecipeEntry<?> resolve(RecipeType<?> jeiType, Object recipe) {
        if (JeiRecipeCategoryFilter.excluded(jeiType)) {
            return null;
        }

        RecipeApi recipeApi = RecipeApi.instance();

        // 1. Generic O(1) resolution for any RecipeHolder<?> backed by a registered VanillaRecipeSystem
        if (recipe instanceof RecipeHolder<?> holder) {
            Recipe<?> value = holder.value();
            VanillaRecipeSystem<?> vanillaSystem = recipeApi.findVanillaSystem(value.getType());
            if (vanillaSystem != null) {
                RecipeEntry<?> entry = wrap(vanillaSystem, holder);
                if (entry != null) {
                    return entry;
                }
            }
        }

        // 2. Native Brewing bridge for JEI IJeiBrewingRecipe
        if (recipe instanceof IJeiBrewingRecipe jeiBrewing
                && recipeApi.findSystem(BrewingRecipeSystem.systemId) instanceof BrewingRecipeSystem brewingSystem) {
            VanillaBrewingRecipe matched = brewingSystem.matchJeiRecipe(jeiBrewing);
            if (matched != null) {
                return RecipeEntry.of(brewingSystem, matched);
            }
        }

        // 3. Any other native (non-JEI-fallback) system bridged to this JEI RecipeType
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

        // 4. JEI fallback category system
        RecipeSystem<?> jeiSystem = recipeApi.findSystem(JeiCategoryRecipeSystem.systemId(jeiType));
        if (jeiSystem == null) {
            jeiSystem = CirrusJeiPlugin.findRecipeSystem(jeiType);
        }
        if (jeiSystem != null) {
            return wrap(jeiSystem, recipe);
        }
        return null;
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
