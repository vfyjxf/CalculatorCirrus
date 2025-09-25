package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.resolver.ResolverType;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import net.minecraft.world.item.ItemStack;

public final class CirrusResolvers {

    private CirrusResolvers() {}

    public static final ResolverType<ItemStack> FLUID_CONTAINER = ResolverType.of(CirrusLocations.ofMod("fluid_container"));
    public static final ResolverType<ItemStack> ITEM_CONTAINER = ResolverType.of(CirrusLocations.ofMod("item_container"));
}
