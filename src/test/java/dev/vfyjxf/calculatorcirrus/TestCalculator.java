package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.calculator.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import dev.vfyjxf.calculatorcirrus.recipes.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipes.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModLoader;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@TestMethodOrder(MethodOrderer.MethodName.class)
@ExtendWith(EphemeralTestServerProvider.class)
public class TestCalculator {

    @BeforeAll
    static void setup() {
        ContentApiImpl contentRegistry = new ContentApiImpl();
        Singletons.attachInstance(ContentApiImpl.class, contentRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterContentEvent(contentRegistry));
        RecipeApiImpl recipeRegistry = new RecipeApiImpl();
        Singletons.attachInstance(RecipeApi.class, recipeRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeRegistry));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void test(MinecraftServer server) {
//        if (Minecraft.getInstance() == null) return;
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
