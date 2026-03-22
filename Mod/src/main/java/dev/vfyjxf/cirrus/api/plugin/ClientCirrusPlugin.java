package dev.vfyjxf.cirrus.api.plugin;

import dev.vfyjxf.cirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRendererRegister;
import dev.vfyjxf.cirrus.api.register.StorageSourceRendererRegister;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency.Constraint;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency.Order;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.ServiceLoader;

/**
 * Load after all {@link CirrusPlugin}
 *
 * @see PluginMarker
 * @see ServiceLoader
 * @see ModPlugin
 * @see PluginLoader
 * @see CirrusPlugin
 */
public interface ClientCirrusPlugin extends ModPlugin {

    Namespace builtin = CirrusNamespaces.ofMod("client/builtin");
    PluginDependency afterBuiltin = new PluginDependency(ClientCirrusPlugin.builtin, Order.after, Constraint.required);


    //region client content registryKey

    default void registerContentRenderer(ContentRendererRegister register) {}

    //endregion

    //region handler registryKey

    default void registerHandlerEntryRenderer(MachineEntryRendererRegister register) {}

    //endregion


    //region storage source renderer

    default void registerStorageSourceRenderer(StorageSourceRendererRegister register) {}

    //endregion

}
