package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.event.EventListeners;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiBookmarkOverlayButtons;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiFallbackRecipeSystemPlugin;
import dev.vfyjxf.cirrus.internal.integration.jei.PlanFlowRecipeCategory;
import dev.vfyjxf.cirrus.internal.lifecycle.CirrusClientRuntime;
import dev.vfyjxf.cirrus.internal.lifecycle.CirrusLifecycleSources;
import dev.vfyjxf.cirrus.internal.lifecycle.CirrusLifecycleStates;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverRegistry;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeLayoutTooltipComponent;
import dev.vfyjxf.cirrus.ui.execution.tooltip.JeiRecipeTooltipComponent;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorInputKeyMappings;
import dev.vfyjxf.cirrus.ui.widget.calculator.RecipeDatabaseKeyMappings;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleDriver;
import dev.vfyjxf.cloudlib.api.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cloudlib.api.plugin.PluginDispatcher;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.plugin.ProgressTracker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = CirrusConstants.modId, dist = Dist.CLIENT)
public final class CirrusClient extends Cirrus {

    public static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Client");
    private final PluginDispatcher<ClientCirrusPlugin> clientPlugins;

    public CirrusClient(ModContainer modContainer, IEventBus modBus) {
        super(modBus);
        ExecutionOverlayClientConfig.register(modContainer);
        registerLifecycleDriver(modBus);
        clientPlugins = PluginLoader.loadAndDispatcher(
            logger,
            "Client Cirrus Plugin",
            AnnotationPluginLookup.of(ClientCirrusPlugin.class),
            pluginExecutor
        );
        modBus.addListener(this::registerTooltipFactories);
        modBus.addListener(this::registerKeyMappings);
        registerJeiBookmarkOverlayButtons();
    }

    private void registerJeiBookmarkOverlayButtons() {
        EventListeners.register(ScreenEvent.Render.Post.class, JeiBookmarkOverlayButtons::render);
        EventListeners.register(ScreenEvent.MouseButtonPressed.Pre.class, JeiBookmarkOverlayButtons::mousePressed);
        EventListeners.register(ScreenEvent.MouseButtonReleased.Pre.class, JeiBookmarkOverlayButtons::mouseReleased);
        EventListeners.register(
            ScreenEvent.MouseButtonPressed.Pre.class,
            EventPriority.HIGH,
            PlanFlowRecipeCategory::onMousePressed
        );
        EventListeners.register(
            ScreenEvent.MouseDragged.Pre.class,
            EventPriority.HIGH,
            PlanFlowRecipeCategory::onMouseDragged
        );
        EventListeners.register(
            ScreenEvent.MouseButtonReleased.Pre.class,
            EventPriority.HIGH,
            PlanFlowRecipeCategory::onMouseReleased
        );
        EventListeners.register(
            ScreenEvent.MouseScrolled.Pre.class,
            EventPriority.HIGH,
            PlanFlowRecipeCategory::onMouseScrolled
        );
    }

    private void registerLifecycleDriver(IEventBus modBus) {
        LifecycleDriver.builder("Calculator Cirrus Client").logger(logger)
                .require(CirrusLifecycleStates.clientConnection).require(CirrusLifecycleStates.tagsReady)
                .require(CirrusLifecycleStates.recipesReady).require(CirrusLifecycleStates.dataMapsReady)
                .source(CirrusLifecycleSources.client(modBus)).onLoad(this::load).onUnload(this::unload).build();
    }

    @Override
    protected void afterLifecycleLoad() {
        CirrusClientRuntime.afterLoad();
    }

    @Override
    protected void beforeLifecycleUnload() {
        CirrusClientRuntime.beforeUnload();
    }

    private void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RichTooltipComponent.class, RichTooltipComponent::toClient);
        event.register(ExecutionRecipeLayoutTooltipComponent.class, ExecutionRecipeLayoutTooltipComponent::toClient);
        event.register(JeiRecipeTooltipComponent.class, JeiRecipeTooltipComponent::toClient);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        RecipeDatabaseKeyMappings.register(event);
        CalculatorInputKeyMappings.register(event);
    }

    @Override
    protected MutableList<CirrusPlugin> builtinPlugins() {
        return MutableLists.with(new JeiFallbackRecipeSystemPlugin());
    }

    @Override
    protected void buildContentApi(ProgressTracker tracker) {
        var contentRegistry = new ClientContentRegistryBuilder();
        buildCommonContentApi(contentRegistry, tracker);
        clientPlugins.dispatchLogged(
            plugin -> plugin.registerContentRenderer(contentRegistry),
            "Registering Content Renderers",
            logger,
            tracker.phase("Registering Content Renderers")
        );
        var contentApi = new ClientContentApiImpl(contentRegistry);
        ContentApiImpl.attach(contentApi);
    }

    @Override
    protected void buildExecutionDriverApi(ExecutionDriverRegistry driverRegistry, ProgressTracker tracker) {
        clientPlugins.dispatchLogged(
            plugin -> plugin.registerExecutionDriver(driverRegistry),
            "Registering Client Execution Drivers",
            logger,
            tracker.phase("Registering Client Execution Drivers")
        );
    }

}
