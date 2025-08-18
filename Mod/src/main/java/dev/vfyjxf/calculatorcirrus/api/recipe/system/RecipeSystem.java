package dev.vfyjxf.calculatorcirrus.api.recipe.system;


import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeData;
import dev.vfyjxf.calculatorcirrus.internal.integration.jei.CirrusJeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nullable;

public interface RecipeSystem<R> {

    static ResourceLocation systemId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "recipe_system/" + path);
    }

    default String namespace() {
        return systemId().getNamespace();
    }

    ResourceLocation systemId();

    Class<?> basicType();

    default boolean supportsRecipe(R recipe) {
        return contains(recipe);
    }

    default boolean contains(R recipe) {
        return basicType().isInstance(recipe);
    }

    @Nullable
    ResourceLocation getRecipeId(R recipe);

    @Nullable
    R getRecipe(String uniqueId);

    @Nullable
    R getRecipe(ResourceLocation registryName);

    RecipeData parseRecipe(R recipe);

    @Unmodifiable
    MutableList<ContentStack<ItemStack>> getMachines();

    static MutableList<ContentStack<ItemStack>> getMachinesFromJei(RecipeType<?> recipeType) {
        return CirrusJeiPlugin.recipeManager.createRecipeCatalystLookup(recipeType)
            .get()
            .flatMap(i -> i.getItemStack().stream())
            .map(ContentStack::create)
            .collect(Collectors2.toList());
    }

    default RecipeLookup<R> createLookup() {
        return RecipeLookup.empty();
    }

    @Unmodifiable
    OrderedIterable<R> allRecipes();

}
