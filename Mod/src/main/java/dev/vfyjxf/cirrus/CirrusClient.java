package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.cirrus.event.EventListeners;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.ui.execution.workbench.WorkbenchExecutionOverlayManager;
import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayCommandEntry;
import dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.ExecutionRecipePreviewTooltipComponent;
import dev.vfyjxf.cirrus.util.StartEventObserver;
import dev.vfyjxf.cloudlib.api.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cloudlib.api.plugin.PluginDispatcher;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.plugin.ProgressTracker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.CLIENT)
public final class CirrusClient extends Cirrus {

    public static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Client");
    private final PluginDispatcher<ClientCirrusPlugin> clientPlugins;

    public CirrusClient(ModContainer modContainer, IEventBus modBus) {
        super(modBus);
        new StartEventObserver(this::load, this::unload).register();
        WorkbenchExecutionOverlayManager.register();
        EventListeners.register(RegisterClientCommandsEvent.class, ExecutionOverlayCommandEntry::register);

        clientPlugins = PluginLoader.loadAndDispatcher(logger, "Client Cirrus Plugin", AnnotationPluginLookup.of(ClientCirrusPlugin.class), pluginExecutor);
        modBus.addListener(this::registerTooltipFactories);
    }

    private void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(FlowChartTooltipComponent.class, FlowChartTooltipComponent::toClient);
        event.register(ExecutionRecipePreviewTooltipComponent.class, ExecutionRecipePreviewTooltipComponent::toClient);
    }

    @Override
    protected MutableList<CirrusPlugin> builtinPlugins() {
        return MutableLists.empty();
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

}
