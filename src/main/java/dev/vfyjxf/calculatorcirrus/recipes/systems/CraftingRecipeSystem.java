package dev.vfyjxf.calculatorcirrus.recipes.systems;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.VanillaRecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import dev.vfyjxf.calculatorcirrus.recipes.BasicRecipeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CraftingRecipeSystem implements VanillaRecipeSystem<CraftingRecipe> {

    private static final ResourceLocation SYSTEM_ID = RecipeSystem.createId(Constants.MOD_ID, "crafting");

    private final RecipeManager recipeManager;

    public CraftingRecipeSystem() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("Cannot create CraftingRecipeSystem without a world");
        }
        this.recipeManager = minecraft.level.getRecipeManager();
    }

    @Override
    public Class<? extends CraftingRecipe> recipeType() {
        return CraftingRecipe.class;
    }

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName(RecipeHolder<CraftingRecipe> recipeHolder) {
        return recipeHolder.id();
    }

    @Nullable
    @Override
    public RecipeHolder<CraftingRecipe> getRecipe(String uniqueId) {
        ResourceLocation id = ResourceLocation.parse(uniqueId);
        return getRecipe(id);
    }

    @Nullable
    @Override
    public RecipeHolder<CraftingRecipe> getRecipe(ResourceLocation registryName) {
        List<RecipeHolder<CraftingRecipe>> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        return allRecipes.stream()
                .filter(recipe -> recipe.id().equals(registryName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public RecipeData parseRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        MutableMultimap<IngredientType, IngredientStack<?>> recipeStableData = FastListMultimap.newMultimap();
        for (Ingredient ingredient : recipe.getIngredients()) {
            IngredientStack<ItemStack> stack = IngredientStack.create(ingredient);
            recipeStableData.put(IngredientType.INPUT, stack);
        }
        recipeStableData.put(IngredientType.OUTPUT, IngredientStack.create(RecipeHelper.getOutput(recipe)));
        return BasicRecipeData.create(recipeStableData.toImmutable(), recipeHolder.id().toString());
    }
}
