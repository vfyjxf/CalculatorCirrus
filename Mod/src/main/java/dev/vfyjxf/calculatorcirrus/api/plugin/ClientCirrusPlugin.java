package dev.vfyjxf.calculatorcirrus.api.plugin;

import dev.vfyjxf.calculatorcirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import net.minecraft.resources.ResourceLocation;

/**
 * Load after all {@link CirrusPlugin}
 * @see CirrusPlugin
 */
public interface ClientCirrusPlugin extends ModPlugin {

    ResourceLocation builtin = CirrusLocations.ofMod("client/builtin");
    PluginDependency afterBuiltin = new PluginDependency(ClientCirrusPlugin.builtin, PluginDependency.Order.AFTER, PluginDependency.Constraint.REQUIRED);


    //region client content registry

    default void registerContentRenderer(ContentRendererRegister register) {}

    //endregion

}
