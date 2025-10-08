package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.util.LoggedTimer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;

public abstract class CalculatorCirrus {
    private static final Logger logger = LogUtils.getLogger();

    protected final MutableList<CirrusPlugin> plugins;

    public CalculatorCirrus(ModContainer modContainer, IEventBus modBus) {
        plugins = loadPlugin("Cirrus Plugin", CirrusPlugin.class);
    }

    protected static <T extends ModPlugin> MutableList<T> loadPlugin(String className, Class<T> pluginClass) {
        PluginLoader.LoadingResult<T> loadingResult = PluginLoader.load(pluginClass);
        if (loadingResult.failures().notEmpty()) {
            var failureByType =
                loadingResult.failures()
                    .groupBy(PluginLoader.LoadingFailure::type);
            var warnings = failureByType.get(PluginLoader.FailureType.WARNING);
            for (var warning : warnings) {
                logger.warn("{} {} is skipped because : {}", className, warning.instance().pluginId(), warning.reason());
            }
            var fatal = failureByType.get(PluginLoader.FailureType.FATAL);
            if (fatal.notEmpty()) {
                String errorString = fatal.makeString("", ",\n", "");
                throw new IllegalStateException("Fatal error when loading" + className + "s: " + errorString);
            }
        }
        return loadingResult.plugins();
    }

    protected final void load() {
        LoggedTimer startTimer = new LoggedTimer();
        startTimer.start("Calculator Cirrus Start");

        //region content api
        {
            LoggedTimer timer = new LoggedTimer();
            timer.start("Building Content Api Registry");
            buildContentApi();
            timer.stop();
        }
        //endregion

        //region recipe api
        {
            LoggedTimer timer = new LoggedTimer();
            timer.start("Building Recipe Api Registry");
            var recipeApiRegistry = new RecipeApiRegistryBuilder();
            for (CirrusPlugin plugin : plugins) {
                plugin.registerRecipeSystem(recipeApiRegistry);
            }
            for (CirrusPlugin plugin : plugins) {
                plugin.registerRecipeFacet(recipeApiRegistry);
            }
            Singletons.attachInstance(RecipeApi.class, new RecipeApiImpl(recipeApiRegistry));
            timer.stop();
        }
        //endregion
        startTimer.stop();
    }

    protected void unload() {
        //TODO:unload stuff
    }

    protected abstract void buildContentApi();

    protected final void buildCommonContentApi(ContentRegistryBuilder contentRegistry) {
        for (CirrusPlugin plugin : plugins) plugin.registerContentType(contentRegistry);
        for (CirrusPlugin plugin : plugins) plugin.registerContentVariant(contentRegistry);
        for (CirrusPlugin plugin : plugins) plugin.registerContentResolver(contentRegistry);
    }
}
