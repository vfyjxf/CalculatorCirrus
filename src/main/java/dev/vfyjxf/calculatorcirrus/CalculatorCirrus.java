package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.calculator.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.recipes.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipes.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(IEventBus modEventBus) {
        modEventBus.addListener(this::onContentRegister);
        modEventBus.addListener(this::onRecipeSystemRegister);
    }

    protected void start() {
        logger.info("Starting Calculator Cirrus");
        logger.info("Registering contents");
        fireContentRegisterEvent();
        logger.info("Registering recipe systems");
        RecipeApiImpl recipeRegistry = new RecipeApiImpl();
        Singletons.attachInstance(RecipeApi.class, recipeRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeRegistry));
//        test();
    }

    protected abstract void fireContentRegisterEvent();

    @SubscribeEvent
    private void onContentRegister(RegisterContentEvent event) {
        logger.info("Registering builtin contents");
        event.registerContent(CirrusContentTypes.ITEM, CirrusContentTypes.ITEM_TAG);
        event.registerContent(CirrusContentTypes.FLUID, CirrusContentTypes.FLUID_TAG);
    }

    @SubscribeEvent
    private void onRecipeSystemRegister(RegisterRecipeSystemEvent event) {
        logger.info("Registering builtin recipe systems");
        CraftingRecipeSystem craftingRecipeSystem = new CraftingRecipeSystem();
        event.registerSystem(craftingRecipeSystem);
    }

    @SuppressWarnings("ConstantConditions")
    private static void test() {
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
