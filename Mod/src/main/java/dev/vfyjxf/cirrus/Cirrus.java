package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.util.LoggedTimer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public sealed abstract class Cirrus permits CirrusClient, CirrusServer {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus");

    protected final ImmutableList<CirrusPlugin> plugins;

    public Cirrus(ModContainer modContainer, IEventBus modBus) {
        plugins = PluginLoader.loadPlugin(logger, "Cirrus Plugin", CirrusPlugin.class).toImmutable();
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
            RecipeApiImpl.attach(new RecipeApiImpl(recipeApiRegistry));
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
