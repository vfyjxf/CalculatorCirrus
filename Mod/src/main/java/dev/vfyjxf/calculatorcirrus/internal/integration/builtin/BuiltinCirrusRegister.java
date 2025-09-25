package dev.vfyjxf.calculatorcirrus.internal.integration.builtin;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusResolvers;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.content.resolver.fluid.VanillaFluidHandlerResolver;
import dev.vfyjxf.calculatorcirrus.content.type.variant.EnchantedBookVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.FireworkRocketVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.InstrumentVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.OminousBottleVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.PaintingVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.PotionVariantType;
import dev.vfyjxf.calculatorcirrus.content.type.variant.SuspiciousStewVariantType;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class BuiltinCirrusRegister {

    @SubscribeEvent
    static void onRegisterVariantType(RegisterContentEvent event) {
        //region type
        event.registerItemVariantType(EnchantedBookVariantType.instance);
        event.registerItemVariantType(FireworkRocketVariantType.instance);
        event.registerItemVariantType(InstrumentVariantType.instance);
        event.registerItemVariantType(OminousBottleVariantType.instance);
        event.registerItemVariantType(PaintingVariantType.instance);
        event.registerItemVariantType(PotionVariantType.instance);
        event.registerItemVariantType(SuspiciousStewVariantType.instance);
        //endregion

        //region variant

        event.registerVariant(Items.ENCHANTED_BOOK, EnchantedBookVariantType.instance);
        event.registerVariant(Items.FIREWORK_ROCKET, FireworkRocketVariantType.instance);
        //endregion
    }

    @SubscribeEvent
    static void onRegisterResolver(RegisterContentEvent event) {
        //region content
        //endregion

        //region stack

        event.registerStackResolver(CirrusResolvers.FLUID_CONTAINER, CirrusTypes.ITEM_STACK, VanillaFluidHandlerResolver.instance);

        //endregion
    }

}
