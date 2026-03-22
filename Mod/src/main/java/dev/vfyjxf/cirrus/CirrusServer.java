package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cloudlib.api.plugin.ProgressTracker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class CirrusServer extends Cirrus {

    public static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Server");

    public CirrusServer(ModContainer modContainer, IEventBus modbus) {
        super(modbus);
        registerLoadListener();
    }

    @Override
    protected MutableList<CirrusPlugin> builtinPlugins() {
        return MutableLists.empty();
    }

    @Override
    protected void buildContentApi(ProgressTracker tracker) {
        var contentRegistry = new ContentRegistryBuilder();
        buildCommonContentApi(contentRegistry, tracker);
        ContentApiImpl.attach(new ContentApiImpl(contentRegistry));
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
