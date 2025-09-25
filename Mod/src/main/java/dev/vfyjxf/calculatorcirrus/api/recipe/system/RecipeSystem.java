package dev.vfyjxf.calculatorcirrus.api.recipe.system;


import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeView;
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

    //region system info

    default String namespace() {
        return systemId().getNamespace();
    }

    ResourceLocation systemId();

    Class<?> basicType();

    //endregion

    //region lookup

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

    //endregion

    RecipeView resolveRecipe(R recipe);

    //region system data

    //TODO:provide a custom data query api.
    //  something like data component system,but for recipe system.

    @Unmodifiable
    MutableList<ContentStack<Item, ItemStack>> getMachines();

    //endregion

    //region util

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

    //endregion

}
