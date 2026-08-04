package dev.vfyjxf.cirrus;

import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.lifecycle.CirrusLifecycleSources;
import dev.vfyjxf.cirrus.internal.lifecycle.CirrusLifecycleStates;
import dev.vfyjxf.cloudlib.api.lifecycle.LifecycleDriver;
import dev.vfyjxf.cloudlib.api.plugin.ProgressTracker;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = CirrusConstants.modId, dist = Dist.DEDICATED_SERVER)
public final class CirrusServer extends Cirrus {

    public static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Server");

    public CirrusServer(ModContainer modContainer, IEventBus modbus) {
        super(modbus);
        registerLifecycleDriver();
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

    private void registerLifecycleDriver() {
        LifecycleDriver.builder("Calculator Cirrus Server")
                .logger(logger)
                .require(CirrusLifecycleStates.serverStarted)
                .require(CirrusLifecycleStates.serverDataReady)
                .source(CirrusLifecycleSources.server())
                .onLoad(this::load)
                .onUnload(this::unload)
                .build();
    }

}
