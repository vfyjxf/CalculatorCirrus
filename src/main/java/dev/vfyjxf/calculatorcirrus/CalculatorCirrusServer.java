package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;

@Mod(value = Constants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class CalculatorCirrusServer extends CalculatorCirrus {

    public CalculatorCirrusServer(IEventBus modEventBus) {
        super(modEventBus);
    }

    @Override
    protected void fireContentRegisterEvent() {
        ContentApiImpl contentRegistry = new ContentApiImpl();
        Singletons.attachInstance(ContentApiImpl.class, contentRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterContentEvent(contentRegistry));
    }
}
