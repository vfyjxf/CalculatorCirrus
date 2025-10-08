package dev.vfyjxf.calculatorcirrus;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class CalculatorCirrusServer extends CalculatorCirrus {

    public CalculatorCirrusServer(ModContainer modContainer, IEventBus modbus) {
        //TODO:call register events
        super(modContainer, modbus);
        registerLoadListener();
    }

    @Override
    protected void buildContentApi() {
        var contentRegistry = new ContentRegistryBuilder();
        buildCommonContentApi(contentRegistry);
        Singletons.attachInstance(ContentApi.class, new ContentApiImpl(contentRegistry));
    }

    private void registerLoadListener() {
        AtomicBoolean firstUntrigger = new AtomicBoolean(true);
        var firstTrigger = new Consumer<ServerStartedEvent>() {
            @Override
            public void accept(ServerStartedEvent event) {
                load();
                NeoForge.EVENT_BUS.unregister(this);
                firstUntrigger.set(false);
            }
        };

        NeoForge.EVENT_BUS.addListener(firstTrigger);
        NeoForge.EVENT_BUS.addListener(
            EventPriority.LOWEST,
            (TagsUpdatedEvent event) -> {
                //trigger after DataMap loaded
                boolean notRun = firstUntrigger.get() || event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD;
                if (notRun) return;
                unload();
                load();
            }
        );
    }

}
