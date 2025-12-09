package dev.vfyjxf.cirrus.api.plugin;

import dev.vfyjxf.cirrus.api.register.ContentResolverRegister;
import dev.vfyjxf.cirrus.api.register.ContentTypeRegister;
import dev.vfyjxf.cirrus.api.register.ContentVariantRegister;
import dev.vfyjxf.cirrus.api.register.IngredientResolverRegister;
import dev.vfyjxf.cirrus.api.register.JeiRecipeTypeBridgeRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRegister;
import dev.vfyjxf.cirrus.api.register.RecipeFacetRegister;
import dev.vfyjxf.cirrus.api.register.RecipeFilterRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemTagRegister;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.plugin.ModPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency.Constraint;
import dev.vfyjxf.cloudlib.api.plugin.PluginDependency.Order;
import dev.vfyjxf.cloudlib.api.plugin.PluginLoader;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.ServiceLoader;

/**
 * A plugin system entrypoint that use java spi to load plugins.
 *
 * @see com.google.auto.service.AutoService
 * @see ServiceLoader
 * @see ModPlugin
 * @see PluginLoader
 * @see ClientCirrusPlugin
 */
public interface CirrusPlugin extends ModPlugin {

    /**
     * The builtin plugin id
     * <p>
     * Including vanilla and neoforge contents
     * <p>
     * You can load before it, as long as you really know what you're doing
     */
    Namespace builtin = CirrusNamespaces.ofMod("builtin");

    PluginDependency afterBuiltin = new PluginDependency(builtin, Order.AFTER, Constraint.REQUIRED);


    //region content registryKey

    default void registerContentType(ContentTypeRegister register) {}

    default void registerContentVariant(ContentVariantRegister register) {}

    default void registerContentResolver(ContentResolverRegister register) {}

    //endregion

    //region recipe registryKey

    default void registerRecipeSystem(RecipeSystemRegister register) {}

    default void registerRecipeFacet(RecipeFacetRegister register) {}

    default void registerRecipeFilter(RecipeFilterRegister register) {}

    default void registerRecipeSystemTag(RecipeSystemTagRegister register) {}

    //endregion

    //region machine registryKey

    default void registerMachineEntry(MachineEntryRegister register) {}

    //endregion

    //region jei bridge

    default void registerJeiRecipeTypeBridge(JeiRecipeTypeBridgeRegister register) {}

    //endregion

    //region ingredient resolver

    default void registerIngredientResolver(IngredientResolverRegister register) {}

    //endregion
}
