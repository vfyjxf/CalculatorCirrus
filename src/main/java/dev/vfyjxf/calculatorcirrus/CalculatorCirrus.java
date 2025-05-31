package dev.vfyjxf.calculatorcirrus;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipe.systems.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
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
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.info("Starting Calculator Cirrus");
        logger.info("Registering contents");
        fireContentRegisterEvent();
        logger.info("Registering recipe systems");
        RecipeApiImpl recipeRegistry = new RecipeApiImpl();
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeRegistry));
        Singletons.attachInstance(RecipeApi.class, recipeRegistry);
        stopwatch.stop();
        logger.info("Calculator Cirrus started in {}", stopwatch);
    }

    protected abstract void fireContentRegisterEvent();

    @SubscribeEvent
    private void onContentRegister(RegisterContentEvent event) {
        logger.info("Registering builtin contents");
        event.registerContent(CirrusContentTypes.ITEM, CirrusContentTypes.ITEM_TAG);
        event.registerContent(CirrusContentTypes.FLUID, CirrusContentTypes.FLUID_TAG);
    }

    void registerBuiltinContent(ContentRegistryBuilder registryBuilder) {
        registryBuilder.registerContent(CirrusContentTypes.EMPTY);
    }

    @SubscribeEvent
    private void onRecipeSystemRegister(RegisterRecipeSystemEvent event) {
        logger.info("Registering builtin recipe systems");
        CraftingRecipeSystem craftingRecipeSystem = new CraftingRecipeSystem();
        event.registerSystem(craftingRecipeSystem);
    }
}
