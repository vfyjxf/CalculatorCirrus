package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.storage.StorageApiBuilder;
import dev.vfyjxf.cirrus.recipe.storage.StorageApiImpl;
import dev.vfyjxf.cloudlib.api.plugin.*;
import net.neoforged.bus.api.IEventBus;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public sealed abstract class Cirrus permits CirrusClient, CirrusServer {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus");

    protected final PluginDispatcher<CirrusPlugin> plugins;
    protected final ForkJoinPool pluginExecutor;

    public Cirrus(IEventBus modBus) {
        var pluginsWithBuiltin = PluginLoader.loadPlugin(logger, "Cirrus Plugin", AnnotationPluginLookup.of(CirrusPlugin.class));
        pluginsWithBuiltin.addAll(builtinPlugins());
        pluginExecutor = new ForkJoinPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            pool -> {
                var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                thread.setName("cirrus-plugin-" + thread.getPoolIndex());
                thread.setDaemon(true);
                return thread;
            },
            null, false
        );
        plugins = PluginDispatcher.create(pluginsWithBuiltin, pluginExecutor);
    }

    protected final void load() {
        ProgressTracker tracker = new ProgressTracker();

        //region content api
        buildContentApi(tracker);
        //endregion

        //region recipe api
        {
            var recipeApiRegistry = new RecipeApiRegistryBuilder();
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeSystem(recipeApiRegistry),
                "Registering Recipe Systems",
                logger,
                tracker.phase("Registering Recipe Systems")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeClause(recipeApiRegistry),
                "Registering Recipe Facets",
                logger,
                tracker.phase("Registering Recipe Facets")
            );
            RecipeApiImpl.attach(new RecipeApiImpl(recipeApiRegistry));
        }
        //endregion

        //region storage api
        {
            var storageApiRegistry = new StorageApiBuilder();
            plugins.dispatchLogged(
                plugin -> plugin.registerStorageType(storageApiRegistry),
                "Registering Storage Types",
                logger,
                tracker.phase("Registering Storage Types")
            );
            StorageApiImpl.attach(new StorageApiImpl(storageApiRegistry));
        }
        //endregion
        tracker.logTimings(logger, "Cirrus Loading");
    }

    protected void unload() {
        //TODO:unload stuff
    }

    protected abstract MutableList<CirrusPlugin> builtinPlugins();

    protected abstract void buildContentApi(ProgressTracker tracker);

    protected final void buildCommonContentApi(ContentRegistryBuilder contentRegistry, ProgressTracker tracker) {
        plugins.dispatchLogged(
            plugin -> plugin.registerContentType(contentRegistry),
            "Registering Content Types",
            logger,
            tracker.phase("Registering Content Types")
        );
        plugins.dispatchLogged(
            plugin -> plugin.registerContentVariant(contentRegistry),
            "Registering Content Variants",
            logger,
            tracker.phase("Registering Content Variants")
        );
        plugins.dispatchLogged(
            plugin -> plugin.registerContentResolver(contentRegistry),
            "Registering Content Resolvers",
            logger,
            tracker.phase("Registering Content Resolvers")
        );
    }
}
