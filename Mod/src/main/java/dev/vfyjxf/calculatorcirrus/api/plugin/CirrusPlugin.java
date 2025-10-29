package dev.vfyjxf.calculatorcirrus.api.plugin;

import dev.vfyjxf.calculatorcirrus.api.register.*;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.ServiceLoader;

/**
 * A plugin system entrypoint that use java spi to load plugins.
 *
 * @see ServiceLoader
 * @see ModPlugin
 * @see PluginLoader
 * @see ClientCirrusPlugin
 */
public interface CirrusPlugin extends ModPlugin {

    /**
     * The builtin plugin id
     * Including vanilla and neoforge contents
     */
    ResourceLocation builtin = CirrusLocations.ofMod("builtin");

    PluginDependency afterBuiltin = new PluginDependency(builtin, PluginDependency.Order.AFTER, PluginDependency.Constraint.REQUIRED);


    //region content registry

    default void registerContentType(ContentTypeRegister register) {}

    default void registerContentVariant(ContentVariantRegister register) {}

    default void registerContentResolver(ContentResolverRegister register) {}

    //endregion

    //region recipe registry

    default void registerRecipeSystem(RecipeSystemRegister register) {}

    default void registerRecipeFacet(RecipeFacetRegister register) {}

    //endregion

    //region machine registry

    default void registerMachineEntry(MachineEntryRegister register) {}

    //endregion

    //region jei bridge

    default void registerJeiRecipeTypeBridge(JeiRecipeTypeBridgeRegister register) {}

    //endregion

    //region ingredient resolver

    default void registerIngredientResolver(IngredientResolverRegister register) {}

    //endregion
}
