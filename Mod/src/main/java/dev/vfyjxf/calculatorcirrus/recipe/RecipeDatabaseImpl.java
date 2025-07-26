package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.CommonRecipe;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeDatabase;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.recipe.ingredient.TagIngredientStack;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecipeDatabaseImpl implements RecipeDatabase {

    private final MutableMap<ContentStack<?>, CommonRecipe<?>> recipes = Maps.mutable.empty();
    private final MutableMap<Object, ContentStack<?>> defaultContents = Maps.mutable.empty();
    private final Component name;

    public RecipeDatabaseImpl(Component name) {
        this.name = name;
    }

    public RecipeDatabaseImpl() {
        this.name = RecipeLangs.defaultDatabaseName.get();
    }

    @Override
    public Component name() {
        return name.copy();
    }

    @Override
    public ContentStack<?> getDefault(IngredientStack ingredientStack) {
        if (ingredientStack.isSingle()) return ingredientStack.getStacks().getOnly().normalize();
        if (ingredientStack instanceof TagIngredientStack tag) return defaultContents.get(tag.getInternalStack());
        return defaultContents.get(ingredientStack.normalize());
    }

    @Override
    public void putDefaultContent(IngredientStack ingredientStack, ContentStack<?> contentStack) {
        ingredientStack = ingredientStack.normalize();
        defaultContents.put(ingredientStack, contentStack.normalize());
    }

    @Override
    public @Nullable CommonRecipe<?> get(ContentStack<?> target) {
        return recipes.get(target.normalize());
    }

    @Override
    public @Nullable CommonRecipe<?> getOrDefault(ContentStack<?> target, @Nullable CommonRecipe<?> recipe) {
        return recipes.getOrDefault(target.normalize(), recipe);
    }

    @Override
    public void put(ContentStack<?> target, @NotNull CommonRecipe<?> recipe) {
        recipes.put(target.normalize(), recipe);
    }
}
