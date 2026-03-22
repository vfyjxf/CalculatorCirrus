package dev.vfyjxf.cirrus.internal;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.CirrusTypes.IngredientResolvers;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.register.*;
import dev.vfyjxf.cirrus.content.resolver.fluid.VanillaFluidHandlerResolver;
import dev.vfyjxf.cirrus.content.type.variant.*;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DataComponentIngredientResolver;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DifferenceIngredientResolver;
import dev.vfyjxf.cirrus.recipe.storage.source.PlayerInventorySource;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.world.item.Items;

@PluginMarker
public class InternalPlugin implements CirrusPlugin {

    @Override
    public Namespace pluginId() {
        return builtin;
    }

    @Override
    public void registerContentType(ContentTypeRegister register) {
        register.registerContentType(CirrusTypes.Contents.ITEM);
        register.registerContentType(CirrusTypes.Contents.FLUID);

        register.registerTagType(CirrusTypes.Contents.ITEM, CirrusTypes.Contents.ITEM_TAG);
        register.registerTagType(CirrusTypes.Contents.FLUID, CirrusTypes.Contents.FLUID_TAG);


        register.registerStackType(CirrusTypes.Contents.ITEM, CirrusTypes.Contents.ITEM_STACK);
        register.registerStackType(CirrusTypes.Contents.FLUID, CirrusTypes.Contents.FLUID_STACK);
    }

    @Override
    public void registerContentVariant(ContentVariantRegister register) {
        //region type
        register.registerItemVariantType(EnchantedBookVariantType.instance);
        register.registerItemVariantType(FireworkRocketVariantType.instance);
        register.registerItemVariantType(InstrumentVariantType.instance);
        register.registerItemVariantType(OminousBottleVariantType.instance);
        register.registerItemVariantType(PaintingVariantType.instance);
        register.registerItemVariantType(PotionVariantType.instance);
        register.registerItemVariantType(SuspiciousStewVariantType.instance);
        //endregion

        //region content variant

        register.registerVariantForContent(Items.ENCHANTED_BOOK, EnchantedBookVariantType.instance);
        register.registerVariantForContent(Items.FIREWORK_ROCKET, FireworkRocketVariantType.instance);
        //endregion
    }

    @Override
    public void registerContentResolver(ContentResolverRegister register) {
        //region content
        //endregion

        //region stack

        register.registerStackResolver(CirrusTypes.ContentResolvers.FLUID_CONTAINER, CirrusTypes.Contents.ITEM_STACK, VanillaFluidHandlerResolver.instance);

        //endregion
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        register.registerSystem(new CraftingRecipeSystem());
    }

    @Override
    public void registerRecipeClause(RecipeClauseRegister register) {
        register.registerContentTransformer(FluidContainerUnpackClause.type, FluidContainerUnpackClause.transformer);
        register.registerProjectionTransformer(GuaranteedOutputClause.type, GuaranteedOutputClause.projectionTransformer);
    }

    @Override
    public void registerJeiRecipeTypeBridge(JeiRecipeTypeBridgeRegister register) {
        register.registerRecipeTypeForSystem(CraftingRecipeSystem.systemId, RecipeTypes.CRAFTING);
    }

    @Override
    public void registerIngredientResolver(IngredientResolverRegister register) {
        register.registerIngredientResolver(IngredientResolvers.CUSTOM, DataComponentIngredientResolver.instance);
        register.registerIngredientResolver(IngredientResolvers.CUSTOM, DifferenceIngredientResolver.instance);
    }

    @Override
    public void registerStorageType(StorageTypeRegister register) {
        register.registerStorageType(PlayerInventorySource.type);
    }
}
