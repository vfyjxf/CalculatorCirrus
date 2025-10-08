package dev.vfyjxf.calculatorcirrus.internal;

import com.google.auto.service.AutoService;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusResolvers;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.calculatorcirrus.api.register.ContentResolverRegister;
import dev.vfyjxf.calculatorcirrus.api.register.ContentTypeRegister;
import dev.vfyjxf.calculatorcirrus.api.register.ContentVariantRegister;
import dev.vfyjxf.calculatorcirrus.api.register.RecipeFacetRegister;
import dev.vfyjxf.calculatorcirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.calculatorcirrus.content.resolver.fluid.VanillaFluidHandlerResolver;
import dev.vfyjxf.calculatorcirrus.content.type.variant.EnchantedBookVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.FireworkRocketVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.InstrumentVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.OminousBottleVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.PaintingVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.PotionVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.SuspiciousStewVariantType;
import dev.vfyjxf.calculatorcirrus.recipe.facet.UnwrapFluidHandlerFacet;
import dev.vfyjxf.calculatorcirrus.recipe.system.CraftingRecipeSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

@AutoService(CirrusPlugin.class)
public class InternalPlugin implements CirrusPlugin {

    @Override
    public ResourceLocation pluginId() {
        return builtin;
    }

    @Override
    public void registerContentType(ContentTypeRegister register) {
        register.registerContentType(CirrusTypes.ITEM);
        register.registerContentType(CirrusTypes.FLUID);

        register.registerTagType(CirrusTypes.ITEM, CirrusTypes.ITEM_TAG);
        register.registerTagType(CirrusTypes.FLUID, CirrusTypes.FLUID_TAG);


        register.registerStackType(CirrusTypes.ITEM, CirrusTypes.ITEM_STACK);
        register.registerStackType(CirrusTypes.FLUID, CirrusTypes.FLUID_STACK);
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

        register.registerStackResolver(CirrusResolvers.FLUID_CONTAINER, CirrusTypes.ITEM_STACK, VanillaFluidHandlerResolver.instance);

        //endregion
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        register.registerSystem(new CraftingRecipeSystem());
    }

    @Override
    public void registerRecipeFacet(RecipeFacetRegister register) {
        register.registerFacetTransformer(UnwrapFluidHandlerFacet.TYPE, UnwrapFluidHandlerFacet.transformer);
    }


}
