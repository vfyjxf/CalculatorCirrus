package dev.vfyjxf.calculatorcirrus;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import dev.vfyjxf.cloudlib.api.module.ModEntryPoint;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;

public abstract class CalculatorCirrus extends ModEntryPoint {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(ModContainer modContainer, IEventBus modEventBus) {
        super(modContainer, modEventBus);
        modEventBus.addListener(this::onContentRegister);
        modEventBus.addListener(this::onRecipeSystemRegister);
        modEventBus.addListener(this::onRegisterRegistry);
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

    private void onContentRegister(RegisterContentEvent event) {
        logger.info("Registering builtin contents");
        event.registerContent(CirrusContentTypes.ITEM, CirrusContentTypes.ITEM_TAG);
        event.registerContent(CirrusContentTypes.FLUID, CirrusContentTypes.FLUID_TAG);
    }

    private void onRegisterRegistry(NewRegistryEvent event) {
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
