package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.registry.util.RegistryRegister;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

@EventBusSubscriber(modid = CirrusConstants.MOD_ID)
public final class CirrusRegistries {

    private static final RegistryRegister registryRegister = new RegistryRegister();


    public interface Keys {

    }

    @SubscribeEvent
    private static void onRegister(NewRegistryEvent event) {
        registryRegister.register(event);
    }

    private CirrusRegistries() {}
}
