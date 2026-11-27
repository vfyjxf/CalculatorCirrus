package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.debug.CirrusDiagnosticsCommands;
import dev.vfyjxf.cirrus.network.CirrusPayloads;
import dev.vfyjxf.cirrus.planning.bridge.PlanningExtensionRegistry;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererApi;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverApiImpl;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverRegistry;
import dev.vfyjxf.cirrus.recipe.storage.StorageApiBuilder;
import dev.vfyjxf.cirrus.recipe.storage.StorageApiImpl;
import dev.vfyjxf.cloudlib.api.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cloudlib.api.plugin.PluginDispatcher;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.plugin.ProgressTracker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public sealed abstract class Cirrus permits CirrusClient, CirrusServer {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus");

    protected final PluginDispatcher<CirrusPlugin> plugins;
    protected final ForkJoinPool pluginExecutor;

    public Cirrus(IEventBus modBus) {
        var pluginsWithBuiltin = PluginLoader
                .loadPlugin(logger, "Cirrus Plugin", AnnotationPluginLookup.of(CirrusPlugin.class));
        pluginsWithBuiltin.addAll(builtinPlugins());
        pluginExecutor = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), pool -> {
            var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("cirrus-plugin-" + thread.getPoolIndex());
            thread.setDaemon(true);
            return thread;
        }, null, false);
        plugins = PluginDispatcher.create(pluginsWithBuiltin, pluginExecutor);
        modBus.addListener(CirrusPayloads::register);
        NeoForge.EVENT_BUS.addListener(CirrusDiagnosticsCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            try {
                var overworld = event.getServer().overworld();
                if (overworld == null) {
                    logger.warn("world not fully loaded; facility registry not mounted");
                    return;
                }
                WorldFacilityRegistry.mount(overworld);
            } catch (RuntimeException e) {
                logger.warn("world not fully loaded; facility registry not mounted", e);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> WorldFacilityRegistry.unmount());
    }

    protected final void load() {
        ProgressTracker tracker = new ProgressTracker();

        //region content api
        buildContentApi(tracker);
        //endregion

        //region recipe api
        {
            var recipeApiRegistry = new RecipeApiRegistryBuilder();
            var recipeContentRendererRegistry = new RecipeContentRendererRegistryBuilder();
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeSystem(recipeApiRegistry),
                "Registering Recipe Systems",
                logger,
                tracker.phase("Registering Recipe Systems")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeSystemTag(recipeApiRegistry),
                "Registering Recipe System Tags",
                logger,
                tracker.phase("Registering Recipe System Tags")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerJeiRecipeTypeBridge(recipeApiRegistry),
                "Registering JEI Recipe Type Bridges",
                logger,
                tracker.phase("Registering JEI Recipe Type Bridges")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerMachineEntry(recipeApiRegistry),
                "Registering Machine Entries",
                logger,
                tracker.phase("Registering Machine Entries")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerProjectionPlugin(recipeApiRegistry),
                "Registering Projection Plugins",
                logger,
                tracker.phase("Registering Projection Plugins")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeClause(recipeApiRegistry),
                "Registering Recipe Facets",
                logger,
                tracker.phase("Registering Recipe Facets")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerProjectionContextOption(recipeApiRegistry),
                "Registering Projection Context Options",
                logger,
                tracker.phase("Registering Projection Context Options")
            );
            plugins.dispatchLogged(
                plugin -> plugin.registerRecipeContentRenderer(recipeContentRendererRegistry),
                "Registering Recipe Content Renderers",
                logger,
                tracker.phase("Registering Recipe Content Renderers")
            );
            RecipeApiImpl.attach(new RecipeApiImpl(recipeApiRegistry));
            RecipeContentRendererApi.attach(new RecipeContentRendererApi(recipeContentRendererRegistry));
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

        //region execution driver api
        {
            var driverRegistry = new ExecutionDriverRegistry();
            plugins.dispatchLogged(
                plugin -> plugin.registerExecutionDriver(driverRegistry),
                "Registering Execution Drivers",
                logger,
                tracker.phase("Registering Execution Drivers")
            );
            buildExecutionDriverApi(driverRegistry, tracker);
            ExecutionDriverApiImpl.attach(new ExecutionDriverApiImpl(driverRegistry));
        }
        //endregion

        //region planning extensions
        {
            var planningExtensionRegistry = new PlanningExtensionRegistry();
            plugins.dispatchLogged(
                plugin -> plugin.registerPlanningExtension(planningExtensionRegistry),
                "Registering Planning Extensions",
                logger,
                tracker.phase("Registering Planning Extensions")
            );
            PlanningExtensionRegistry.attach(planningExtensionRegistry);
        }
        //endregion
        tracker.logTimings(logger, "Cirrus Loading");

        afterLifecycleLoad();
    }

    protected void unload() {
        beforeLifecycleUnload();
        TagType.clearAllTagsCache();
    }

    protected void afterLifecycleLoad() {}

    protected void beforeLifecycleUnload() {}

    protected abstract MutableList<CirrusPlugin> builtinPlugins();

    protected abstract void buildContentApi(ProgressTracker tracker);

    protected void buildExecutionDriverApi(ExecutionDriverRegistry driverRegistry, ProgressTracker tracker) {}

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
        plugins.dispatchLogged(
            plugin -> plugin.registerJeiTypeBridge(contentRegistry),
            "Registering JEI Ingredient Type Bridges",
            logger,
            tracker.phase("Registering JEI Ingredient Type Bridges")
        );
    }
}
