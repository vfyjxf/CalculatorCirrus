package dev.vfyjxf.cirrus.internal;

import com.google.auto.service.AutoService;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.CirrusTypes.IngredientResolvers;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.register.ContentResolverRegister;
import dev.vfyjxf.cirrus.api.register.ContentTypeRegister;
import dev.vfyjxf.cirrus.api.register.ContentVariantRegister;
import dev.vfyjxf.cirrus.api.register.IngredientResolverRegister;
import dev.vfyjxf.cirrus.api.register.JeiRecipeTypeBridgeRegister;
import dev.vfyjxf.cirrus.api.register.RecipeFacetRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.content.resolver.fluid.VanillaFluidHandlerResolver;
import dev.vfyjxf.cirrus.content.type.variant.EnchantedBookVariantType;
import dev.vfyjxf.cirrus.content.type.variant.FireworkRocketVariantType;
import dev.vfyjxf.cirrus.content.type.variant.InstrumentVariantType;
import dev.vfyjxf.cirrus.content.type.variant.OminousBottleVariantType;
import dev.vfyjxf.cirrus.content.type.variant.PaintingVariantType;
import dev.vfyjxf.cirrus.content.type.variant.PotionVariantType;
import dev.vfyjxf.cirrus.content.type.variant.SuspiciousStewVariantType;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DataComponentIngredientResolver;
import dev.vfyjxf.cirrus.recipe.content.resolver.item.DifferenceIngredientResolver;
import dev.vfyjxf.cirrus.recipe.facet.SkipChancedOutputFacet;
import dev.vfyjxf.cirrus.recipe.facet.UnwrapFluidHandlerFacet;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.world.item.Items;

@AutoService(CirrusPlugin.class)
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
    public void registerRecipeFacet(RecipeFacetRegister register) {
        register.registerFacetTransformer(UnwrapFluidHandlerFacet.type, UnwrapFluidHandlerFacet.transformer);
        register.registerViewTransformer(SkipChancedOutputFacet.type, SkipChancedOutputFacet.viewTransformer);
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
}
