package dev.vfyjxf.calculatorcirrus.api.plugin;

import dev.vfyjxf.calculatorcirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.calculatorcirrus.api.register.MachineEntryRendererRegister;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.ServiceLoader;

/**
 * Load after all {@link CirrusPlugin}
 *
 * @see ServiceLoader
 * @see ModPlugin
 * @see PluginLoader
 * @see CirrusPlugin
 */
public interface ClientCirrusPlugin extends ModPlugin {

    ResourceLocation builtin = CirrusLocations.ofMod("client/builtin");
    PluginDependency afterBuiltin = new PluginDependency(ClientCirrusPlugin.builtin, PluginDependency.Order.AFTER, PluginDependency.Constraint.REQUIRED);


    //region client content registry

    default void registerContentRenderer(ContentRendererRegister register) {}

    //endregion

    //region handler registry

    default void registerHandlerEntryRenderer(MachineEntryRendererRegister register) {}

    //endregion

}
