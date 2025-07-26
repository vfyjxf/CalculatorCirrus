package dev.vfyjxf.calculatorcirrus;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.calculatorcirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.UnloadingContentEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.api.ui.annotations.ClientTooltip;
import dev.vfyjxf.calculatorcirrus.content.ClientContentApiImpl;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryBuilder;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackContentRenderer;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackContentRenderer;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import dev.vfyjxf.calculatorcirrus.util.StartEventObserver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.annotation.ElementType;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class CalculatorCirrusClient extends CalculatorCirrus {

    public static final Logger logger = LogUtils.getLogger();

    public CalculatorCirrusClient(IEventBus modEventBus) {
        super(modEventBus);
        new StartEventObserver(
            this::start,
            () -> ModLoader.postEventWrapContainerInModOrder(new UnloadingContentEvent())
        ).register();
        modEventBus.addListener(this::onClientTooltipComponentRegister);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onClientTooltipComponentRegister(RegisterClientTooltipComponentFactoriesEvent event) {
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(ClientTooltip.class, ElementType.TYPE)
                .forEach(annotation -> {
                    try {
                        Class clazz = Class.forName(annotation.memberName());
                        if (TooltipComponent.class.isAssignableFrom(clazz) && ClientTooltipComponent.class.isAssignableFrom(clazz)) {
                            event.register(clazz, t -> (ClientTooltipComponent) t);
                        }
                    } catch (ClassNotFoundException e) {
                        logger.error("Failed to register client richTooltip component: {}", annotation.memberName(), e);
                    }
                });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onClientRegisterContent(ClientRegisterContentEvent event) {
        event.registerRenderer(
            CirrusContentTypes.EMPTY, new ContentRenderer() {
                @Override
                public void render(Object content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {}

                @Override
                public void render(Object content, GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

                @Override
                public void decorate(ContentStack content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY) {}

                @Override
                public @Nullable RichTooltip getTooltip(Object content, TooltipContext context) {return null;}

                @Override
                public Component getName(Object content) {return Component.empty();}
            }
        );
        event.registerRenderer(CirrusContentTypes.ITEM, new ItemStackContentRenderer());
        event.registerRenderer(CirrusContentTypes.FLUID, new FluidStackContentRenderer());
    }

}
