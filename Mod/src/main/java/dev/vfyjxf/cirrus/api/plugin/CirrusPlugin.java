package dev.vfyjxf.cirrus.api.plugin;

import dev.vfyjxf.cirrus.api.register.*;
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
 * A plugin system entrypoint that use cloudlib plugin system.
 *
 * @see PluginMarker
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

    PluginDependency afterBuiltin = new PluginDependency(builtin, Order.after, Constraint.required);


    //region content registryKey

    default void registerContentType(ContentTypeRegister register) {}

    default void registerContentVariant(ContentVariantRegister register) {}

    default void registerContentResolver(ContentResolverRegister register) {}

    default void registerJeiTypeBridge(JeiTypeBridgeRegister register) {}

    //endregion

    //region recipe registryKey

    default void registerRecipeSystem(RecipeSystemRegister register) {}

    default void registerRecipeClause(RecipeClauseRegister register) {}

    default void registerProjectionPlugin(ProjectionPluginRegister register) {}

    default void registerProjectionContextOption(ProjectionContextOptionRegister register) {}

    default void registerRecipeFilter(RecipeFilterRegister register) {}

    default void registerRecipeSystemTag(RecipeSystemTagRegister register) {}

    default void registerRecipeContentRenderer(RecipeContentRendererRegister register) {}

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

    //region storage type

    default void registerStorageType(StorageTypeRegister register) {}

    //endregion

    //region execution driver

    default void registerExecutionDriver(ExecutionDriverRegister register) {}

    //endregion
}
