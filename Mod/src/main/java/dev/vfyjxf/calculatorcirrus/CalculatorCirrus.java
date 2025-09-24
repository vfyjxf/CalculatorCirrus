package dev.vfyjxf.calculatorcirrus;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.facet.UnwrapFluidHandlerFacet;
import dev.vfyjxf.calculatorcirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrus(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(this::onRecipeSystemRegister);
        modEventBus.addListener(this::onRegisterRegistry);
        MutableLongList mutableLongList = LongLists.mutable.withInitialCapacity(10);
    }

    protected void start() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.info("Starting Calculator Cirrus");
        logger.info("Registering contents");
        fireContentRegisterEvent();
        logger.info("Registering recipe systems");
        var recipeApiRegistry = new RecipeApiRegistryBuilder();
        ModLoader.postEventWrapContainerInModOrder(new RegisterRecipeSystemEvent(recipeApiRegistry));
        Singletons.attachInstance(RecipeApi.class, new RecipeApiImpl(recipeApiRegistry));
        stopwatch.stop();
        logger.info("Calculator Cirrus started in {}", stopwatch);
    }

    protected abstract void fireContentRegisterEvent();

    private void onRegisterRegistry(NewRegistryEvent event) {
    }

    void registerBuiltinContent(ContentRegistryBuilder registryBuilder) {
        logger.info("Registering builtin contents");
        registryBuilder.registerContentType(CirrusTypes.ITEM);
        registryBuilder.registerContentType(CirrusTypes.FLUID);
        registryBuilder.registerTagType(CirrusTypes.ITEM, CirrusTypes.ITEM_TAG);
        registryBuilder.registerTagType(CirrusTypes.FLUID, CirrusTypes.FLUID_TAG);
        registryBuilder.registerStackType(CirrusTypes.ITEM, CirrusTypes.ITEM_STACK);
        registryBuilder.registerStackType(CirrusTypes.FLUID, CirrusTypes.FLUID_STACK);
    }

    @SubscribeEvent
    private void onRecipeSystemRegister(RegisterRecipeSystemEvent event) {
        logger.info("Registering builtin recipe systems");
        CraftingRecipeSystem craftingRecipeSystem = new CraftingRecipeSystem();
        event.registerSystem(craftingRecipeSystem);

        event.registerFacetTransformer(UnwrapFluidHandlerFacet.TYPE, UnwrapFluidHandlerFacet.transformer);
    }
}
