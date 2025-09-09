package dev.vfyjxf.calculatorcirrus.api.recipe.system;


import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.data.RecipeData;
import dev.vfyjxf.calculatorcirrus.internal.integration.jei.CirrusJeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
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

    default boolean supports(R recipe) {
        return contains(recipe);
    }

    default boolean contains(R recipe) {
        return basicType().isInstance(recipe);
    }

    @Nullable
    ResourceLocation getId(R recipe);

    @Nullable
    R findRecipe(String uniqueId);

    @Nullable
    R findRecipe(ResourceLocation registryName);

    RecipeData getData(R recipe);

    @Unmodifiable
    MutableList<ContentStack<Item, ItemStack>> getMachines();

    static MutableList<ContentStack<Item, ItemStack>> getMachinesFromJei(RecipeType<?> recipeType) {
        return CirrusJeiPlugin.recipeManager.createRecipeCatalystLookup(recipeType)
                .get()
                .flatMap(i -> i.getItemStack().stream())
                .map(ContentStack::fromStack)
                .collect(Collectors2.toList());
    }

    default RecipeLookup<R> createLookup() {
        return RecipeLookup.empty();
    }

    @Unmodifiable
    RichIterable<R> allRecipes();

}
