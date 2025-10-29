package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.calculatorcirrus.content.ClientContentApiImpl;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import dev.vfyjxf.calculatorcirrus.util.StartEventObserver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.CLIENT)
public class CalculatorCirrusClient extends CalculatorCirrus {

    public static final Logger logger = LogUtils.getLogger();
    private final MutableList<ClientCirrusPlugin> clientPlugins;

    public CalculatorCirrusClient(ModContainer modContainer, IEventBus modBus) {
        super(modContainer, modBus);
        new StartEventObserver(this::load, this::unload).register();

        clientPlugins = loadPlugin("Client Cirrus Plugin", ClientCirrusPlugin.class);
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
