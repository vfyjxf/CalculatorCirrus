package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class JeiSystemCatalog {
    private volatile List<RecipeSystem<?>> systems = List.of();
    private volatile long version;

    public synchronized void rebuild(IRecipeManager manager) {
        try {
            systems = manager.createRecipeCategoryLookup().get()
                    .filter(category -> JeiRecipeCategoryFilter.include(category.getRecipeType()))
                    .<RecipeSystem<?>>map(category -> JeiCategoryRecipeSystem.create(category.getRecipeType()))
                    .toList();
        } catch (RuntimeException | LinkageError ignored) {
            systems = List.of();
        }
        version++;
    }

    public List<RecipeSystem<?>> systems() {
        return systems;
    }

    public long version() {
        return version;
    }

    public @Nullable RecipeSystem<?> findByRecipeType(RecipeType<?> recipeType) {
        for (RecipeSystem<?> system : systems) {
            if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem && jeiSystem.recipeType().equals(recipeType)) {
                return system;
            }
        }
        return null;
    }
}
