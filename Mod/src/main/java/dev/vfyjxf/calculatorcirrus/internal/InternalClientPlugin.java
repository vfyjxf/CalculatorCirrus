package dev.vfyjxf.calculatorcirrus.internal;

import com.google.auto.service.AutoService;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.calculatorcirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackRenderer;
import net.minecraft.resources.ResourceLocation;

@AutoService(ClientCirrusPlugin.class)
public class InternalClientPlugin implements ClientCirrusPlugin {
    @Override
    public ResourceLocation pluginId() {
        return builtin;
    }

    @Override
    public void registerContentRenderer(ContentRendererRegister register) {
        register.registerRenderer(CirrusTypes.ITEM_STACK, new ItemStackRenderer());
        register.registerRenderer(CirrusTypes.FLUID_STACK, new FluidStackRenderer());
    }
}
