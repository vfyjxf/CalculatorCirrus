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

public interface RecipeSystem<T> {

    static ResourceLocation createId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "recipe_system/" + path);
    }

    default String modId() {
        return systemId().getNamespace();
    }

    ResourceLocation systemId();

    Class<?> basicRecipeType();

    default boolean supportsRecipe(T recipe) {
        return containsRecipe(recipe);
    }

    default boolean containsRecipe(T recipe) {
        return basicRecipeType().isInstance(recipe);
    }

    @Nullable
    default String getId(T recipe) {
        ResourceLocation registryName = getRegistryName(recipe);
        if (registryName == null) return null;
        else return registryName.toString();
    }

    @Nullable
    ResourceLocation getRegistryName(T recipe);

    @Nullable
    T getRecipe(String uniqueId);

    @Nullable
    T getRecipe(ResourceLocation registryName);

    RecipeData parseRecipe(T recipe);

    @Unmodifiable
    MutableList<ContentStack<ItemStack>> getMachines();

    static MutableList<ContentStack<ItemStack>> getMachinesFromJei(RecipeType<?> recipeType) {
        return CirrusJeiPlugin.recipeManager.createRecipeCatalystLookup(recipeType)
                .get()
                .flatMap(i -> i.getItemStack().stream())
                .map(ContentStack::create)
                .collect(Collectors2.toList());
    }

    default RecipeLookup<T> createLookup() {
        return RecipeLookup.empty();
    }

    @Unmodifiable
    OrderedIterable<T> allRecipes();

}
