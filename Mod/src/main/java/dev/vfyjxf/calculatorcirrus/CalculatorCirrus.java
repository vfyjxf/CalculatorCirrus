package dev.vfyjxf.calculatorcirrus;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(ModContainer modContainer, IEventBus modEventBus) {
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

    private void onRegisterRegistry(NewRegistryEvent event) {
    }

    void registerBuiltinContent(ContentRegistryBuilder registryBuilder) {
        logger.info("Registering builtin contents");
        registryBuilder.registerContentType(CirrusTypes.item);
        registryBuilder.registerContentType(CirrusTypes.fluid);
        registryBuilder.registerTag(CirrusTypes.item, CirrusTypes.itemTag);
        registryBuilder.registerTag(CirrusTypes.fluid, CirrusTypes.fluidTag);
        registryBuilder.registerStackType(CirrusTypes.item, CirrusTypes.itemStack);
        registryBuilder.registerStackType(CirrusTypes.fluid, CirrusTypes.fluidStack);
    }

    @SubscribeEvent
    private void onRecipeSystemRegister(RegisterRecipeSystemEvent event) {
        logger.info("Registering builtin recipe systems");
        CraftingRecipeSystem craftingRecipeSystem = new CraftingRecipeSystem();
        event.registerSystem(craftingRecipeSystem);
    }
}
