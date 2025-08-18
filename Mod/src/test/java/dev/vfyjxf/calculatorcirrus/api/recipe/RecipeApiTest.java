package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientGroup;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.calculator.simple.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class RecipeApiTest {

    void testCraftingRecipeSystemParse(MinecraftServer server) {

    }

    private void recipeTest() {
        RecipeApi recipeApi = RecipeApi.getInstance();
        CraftingRecipeSystem recipeSystem = (CraftingRecipeSystem) recipeApi.allSystems().detect(system -> system instanceof CraftingRecipeSystem);
        RecipeHolder<CraftingRecipe> target = recipeSystem.getRecipe(CirrusLocations.create("gray_candle"));
        RecipeHolder<CraftingRecipe> candleRecipe = recipeSystem.getRecipe(CirrusLocations.create("candle"));
        RecipeHolder<CraftingRecipe> garyDyeRecipe = recipeSystem.getRecipe(CirrusLocations.create("gray_dye"));
        RecipeHolder<CraftingRecipe> whiteDyeRecipe = recipeSystem.getRecipe(CirrusLocations.create("white_dye"));
        RecipeHolder<CraftingRecipe> boneMealRecipe = recipeSystem.getRecipe(CirrusLocations.create("bone_meal"));
        RecipeHolder<CraftingRecipe> blackDyeRecipe = recipeSystem.getRecipe(CirrusLocations.create("black_dye"));
        SimpleCalculator calculator = new SimpleCalculator();
        calculator.setRecipe(recipeSystem, target);
        calculator.addPath(ContentStack.create(new ItemStack(Items.CANDLE)), recipeSystem, candleRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.GRAY_DYE)), recipeSystem, garyDyeRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.WHITE_DYE)), recipeSystem, whiteDyeRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.BONE_MEAL)), recipeSystem, boneMealRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.BLACK_DYE)), recipeSystem, blackDyeRecipe);

        calculator.setAmount(1000);
        calculator.calculate();
        MutableListMultimap<IngredientGroup, ? extends IngredientStack> calculations = calculator.getCalculations();
        System.out.println(calculations);
    }

}
