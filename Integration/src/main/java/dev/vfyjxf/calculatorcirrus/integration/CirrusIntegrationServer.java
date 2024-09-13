package dev.vfyjxf.calculatorcirrus.integration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = "cirrus_integration", dist = Dist.DEDICATED_SERVER)
public class CirrusIntegrationServer extends CirrusIntegration {
    public CirrusIntegrationServer(IEventBus modBus, ModContainer container) {
        super(modBus, container);
    }
}
