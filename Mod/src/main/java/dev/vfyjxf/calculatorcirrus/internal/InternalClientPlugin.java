package dev.vfyjxf.calculatorcirrus.internal;

import com.google.auto.service.AutoService;
import dev.vfyjxf.calculatorcirrus.api.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.calculatorcirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.calculatorcirrus.api.register.MachineEntryRendererRegister;
import dev.vfyjxf.calculatorcirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.calculatorcirrus.recipe.handler.ItemMachineEntryRenderer;
import net.minecraft.resources.ResourceLocation;

@AutoService(ClientCirrusPlugin.class)
public class InternalClientPlugin implements ClientCirrusPlugin {
    @Override
    public ResourceLocation pluginId() {
        return builtin;
    }

    @Override
    public void registerContentRenderer(ContentRendererRegister register) {
        register.registerRenderer(CirrusTypes.Contents.ITEM_STACK, ItemStackRenderer.instance);
        register.registerRenderer(CirrusTypes.Contents.FLUID_STACK, FluidStackRenderer.instance);
    }

    @Override
    public void registerHandlerEntryRenderer(MachineEntryRendererRegister register) {
        register.registerHandlerRenderer(ItemMachineEntry.class, new ItemMachineEntryRenderer());
    }
}
