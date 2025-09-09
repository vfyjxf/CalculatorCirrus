package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.UnloadingContentEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.content.ClientContentApiImpl;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import dev.vfyjxf.calculatorcirrus.util.StartEventObserver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = CirrusConstants.MOD_ID, dist = Dist.CLIENT)
public class CalculatorCirrusClient extends CalculatorCirrus {

    public static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrusClient(ModContainer modContainer, IEventBus modEventBus) {
        super(modContainer, modEventBus);
        new StartEventObserver(
                this::start,
                () -> ModLoader.postEventWrapContainerInModOrder(new UnloadingContentEvent())
        ).register();
        modEventBus.addListener(this::onClientRegisterContent);
    }

    @Override
    protected void fireContentRegisterEvent() {
        var contentRegistry = new ClientContentRegistryBuilder();
        registerBuiltinContent(contentRegistry);
        ModLoader.postEventWrapContainerInModOrder(new RegisterContentEvent(contentRegistry));
        ModLoader.postEventWrapContainerInModOrder(new ClientRegisterContentEvent(contentRegistry));

        var contentApi = new ClientContentApiImpl(contentRegistry);
        Singletons.attachInstance(ContentApi.class, contentApi);
        Singletons.attachInstance(ClientContentApiImpl.class, contentApi);
    }

    private void onClientRegisterContent(ClientRegisterContentEvent event) {
        //TODO:Rewrite this
        event.registerRenderer(CirrusTypes.itemStack, new ItemStackRenderer());
        event.registerRenderer(CirrusTypes.fluidStack, new FluidStackRenderer());
    }

}
