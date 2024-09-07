package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.calculator.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.recipes.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class TestCalculator {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void test(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.getInstance();
        CraftingRecipeSystem recipeSystem = (CraftingRecipeSystem) recipeApi.allSystems().getAny();
        RecipeHolder<CraftingRecipe> target = recipeSystem.getRecipe(Locations.ofDefault("gray_candle"));

        SimpleCalculator calculator = new SimpleCalculator();
        calculator.setRecipe(recipeSystem, target);

        calculator.setAmount(2);
        calculator.calculate();
        System.out.println(calculator.getResults());
    }
}
