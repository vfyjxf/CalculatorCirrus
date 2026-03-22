package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;

import java.util.ArrayList;
import java.util.List;

public final class JeiSystemCatalog {
    private final List<RecipeSystem<?>> systems = new ArrayList<>();

    public void rebuild(IRecipeManager manager) {
        systems.clear();
        manager.createRecipeCategoryLookup()
               .get()
               .forEach(category -> systems.add(JeiCategoryRecipeSystem.create(manager, category)));
    }

    public List<RecipeSystem<?>> systems() {
        return systems;
    }
}
