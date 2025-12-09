package dev.vfyjxf.cirrus.integration;

import dev.vfyjxf.cirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.cloudlib.api.util.ServiceLoading;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.eclipse.collections.api.list.MutableList;

@Mod(value = "cirrus_integration", dist = Dist.CLIENT)
public class CirrusIntegrationClient extends CirrusIntegration {

    private MutableList<ClientCirrusAdapter> clientPlugins;

    public CirrusIntegrationClient(IEventBus modBus, ModContainer container) {
        super(modBus, container);
        modBus.addListener(this::registerClientContent);
    }

    private void registerClientContent(ClientRegisterContentEvent event) {
        if (this.clientPlugins == null) {
            this.clientPlugins = ServiceLoading.load(ClientCirrusAdapter.class);
        }
        for (ClientCirrusAdapter plugin : clientPlugins) {
            plugin.registerContent(event);
        }
    }

}
