package dev.vfyjxf.calculatorcirrus;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.calculator.simple.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.recipes.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipes.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(IEventBus modEventBus) {
        modEventBus.addListener(this::onContentRegister);
        modEventBus.addListener(this::onRecipeSystemRegister);
    }

    protected void start() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.info("Starting Calculator Cirrus");
        logger.info("Registering contents");
        fireContentRegisterEvent();
        logger.info("Registering recipe systems");
        RecipeApiImpl recipeRegistry = new RecipeApiImpl();
        Singletons.attachInstance(RecipeApi.class, recipeRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeRegistry));
        stopwatch.stop();
        logger.info("Calculator Cirrus started in {}", stopwatch);
        test();
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
        CraftingRecipeSystem recipeSystem = (CraftingRecipeSystem) recipeApi.allSystems().detect(system -> system instanceof CraftingRecipeSystem);
        RecipeHolder<CraftingRecipe> target = recipeSystem.getRecipe(Locations.ofDefault("gray_candle"));
        RecipeHolder<CraftingRecipe> candleRecipe = recipeSystem.getRecipe(Locations.ofDefault("candle"));
        RecipeHolder<CraftingRecipe> garyDyeRecipe = recipeSystem.getRecipe(Locations.ofDefault("gray_dye"));
        RecipeHolder<CraftingRecipe> whiteDyeRecipe = recipeSystem.getRecipe(Locations.ofDefault("white_dye"));
        RecipeHolder<CraftingRecipe> boneMealRecipe = recipeSystem.getRecipe(Locations.ofDefault("bone_meal"));
        RecipeHolder<CraftingRecipe> blackDyeRecipe = recipeSystem.getRecipe(Locations.ofDefault("black_dye"));
        SimpleCalculator calculator = new SimpleCalculator();
        calculator.setRecipe(recipeSystem, target);
        calculator.addPath(ContentStack.create(new ItemStack(Items.CANDLE)), recipeSystem, candleRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.GRAY_DYE)), recipeSystem, garyDyeRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.WHITE_DYE)), recipeSystem, whiteDyeRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.BONE_MEAL)), recipeSystem, boneMealRecipe);
        calculator.addPath(ContentStack.create(new ItemStack(Items.BLACK_DYE)), recipeSystem, blackDyeRecipe);

        calculator.setAmount(1000);
        calculator.calculate();
        MutableListMultimap<IngredientType, ? extends IngredientStack> calculations = calculator.getCalculations();
        System.out.println(calculations);
    }
}
