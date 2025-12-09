package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.util.StartEventObserver;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.CLIENT)
public final class CirrusClient extends Cirrus {

    public static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Client");
    private final MutableList<ClientCirrusPlugin> clientPlugins;

    public CirrusClient(ModContainer modContainer, IEventBus modBus) {
        super(modContainer, modBus);
        new StartEventObserver(this::load, this::unload).register();

        clientPlugins = PluginLoader.loadPlugin(logger, "Client Cirrus Plugin", ClientCirrusPlugin.class);
    }

    @Override
    protected void buildContentApi() {
        var contentRegistry = new ClientContentRegistryBuilder();
        buildCommonContentApi(contentRegistry);
        for (var clientPlugin : clientPlugins) clientPlugin.registerContentRenderer(contentRegistry);

        var contentApi = new ClientContentApiImpl(contentRegistry);
        ContentApiImpl.attach(contentApi);
    }

}
