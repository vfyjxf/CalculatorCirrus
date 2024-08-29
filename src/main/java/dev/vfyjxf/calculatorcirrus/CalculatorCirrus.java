package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.UnloadingContentEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeRegistry;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.recipes.RecipeRegistryImpl;
import dev.vfyjxf.calculatorcirrus.recipes.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import dev.vfyjxf.calculatorcirrus.utils.StartEventObserver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(IEventBus modEventBus, ModContainer modContainer) {
        new StartEventObserver(
                this::start,
                () -> ModLoader.postEventWrapContainerInModOrder(new UnloadingContentEvent())
        ).register();
        modEventBus.addListener(this::onContentRegister);
        modEventBus.addListener(this::onRecipeSystemRegister);
    }

    private void start() {
        logger.info("Starting Calculator Cirrus");
        logger.info("Registering contents");
        fireContentRegisterEvent();
        logger.info("Registering recipe systems");
        RecipeRegistryImpl recipeRegistry = new RecipeRegistryImpl();
        Singletons.attachInstance(RecipeRegistry.class, recipeRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeRegistry));

        for (RecipeSystem<?> system : recipeRegistry.allSystems()) {
            MutableList<RecipeData> recipes = parseRecipe(system);
            System.out.println(recipes);
        }
    }

    private static <T> MutableList<RecipeData> parseRecipe(RecipeSystem<T> system) {
        return system.allRecipes().collect(system::parseRecipe).toList();
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
}
