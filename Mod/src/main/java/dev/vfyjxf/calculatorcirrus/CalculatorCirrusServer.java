package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.UnloadingContentEvent;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class CalculatorCirrusServer extends CalculatorCirrus {

    public CalculatorCirrusServer(ModContainer modContainer, IEventBus modEventBus) {
        //TODO:call register events
        super(modContainer, modEventBus);
        registerLoadListener();
    }

    @Override
    protected void fireContentRegisterEvent() {
        var contentRegistry = new ContentRegistryBuilder();
        registerBuiltinContent(contentRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterContentEvent(contentRegistry));
        ContentApiImpl contentApi = new ContentApiImpl(contentRegistry);
        Singletons.attachInstance(ContentApi.class, contentApi);
    }

    private void registerLoadListener() {
        AtomicBoolean firstUnTrigger = new AtomicBoolean(true);
        var firstTrigger = new Consumer<ServerStartedEvent>() {
            @Override
            public void accept(ServerStartedEvent event) {
                start();
                NeoForge.EVENT_BUS.unregister(this);
                firstUnTrigger.set(false);
            }
        };
        NeoForge.EVENT_BUS.addListener(firstTrigger);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                (TagsUpdatedEvent event) -> {
                    if (firstUnTrigger.get() || event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)
                        return;
                    ModLoader.postEventWrapContainerInModOrder(new UnloadingContentEvent());
                    start();
                }
        );
    }

}
