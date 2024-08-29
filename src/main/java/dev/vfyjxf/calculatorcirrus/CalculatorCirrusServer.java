package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryImpl;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;

@Mod(value = Constants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class CalculatorCirrusServer extends CalculatorCirrus {
    public CalculatorCirrusServer(IEventBus modEventBus, ModContainer modContainer) {
        super(modEventBus, modContainer);
    }

    @Override
    protected void fireContentRegisterEvent() {
        ContentRegistryImpl contentRegistry = new ContentRegistryImpl();
        Singletons.attachInstance(ContentRegistryImpl.class, contentRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterContentEvent(contentRegistry));
    }
}
