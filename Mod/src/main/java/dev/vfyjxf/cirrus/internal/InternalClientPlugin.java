package dev.vfyjxf.cirrus.internal;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.plugin.ClientCirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.machine.EmptyMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.cirrus.api.register.ExecutionDriverRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRendererRegister;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.recipe.handler.EmptyMachineEntryRenderer;
import dev.vfyjxf.cirrus.recipe.handler.ItemMachineEntryRenderer;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;

@PluginMarker
public class InternalClientPlugin implements ClientCirrusPlugin, ModPlugin {

    @Override
    public Namespace pluginId() {
        return builtin;
    }

    @Override
    public void registerContentRenderer(ContentRendererRegister register) {
        register.registerRenderer(CirrusTypes.Contents.itemStack, ItemStackRenderer.instance);
        register.registerRenderer(CirrusTypes.Contents.fluidStack, FluidStackRenderer.instance);
    }

    @Override
    public void registerHandlerEntryRenderer(MachineEntryRendererRegister register) {
        register.registerHandlerRenderer(EmptyMachineEntry.class, EmptyMachineEntryRenderer.instance);
        register.registerHandlerRenderer(ItemMachineEntry.class, ItemMachineEntryRenderer.instance);
    }

    @Override
    public void registerExecutionDriver(ExecutionDriverRegister register) {
        register.registerClient(CraftingTableExecutionDriver.selector, CraftingTableExecutionDriver::create);
    }
}
