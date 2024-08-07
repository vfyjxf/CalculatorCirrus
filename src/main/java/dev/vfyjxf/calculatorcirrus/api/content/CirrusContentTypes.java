package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.content.types.EmptyContentType;
import dev.vfyjxf.calculatorcirrus.content.types.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.types.ItemStackType;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CirrusContentTypes {

    public static final IContentType<Unit> EMPTY = new EmptyContentType();
    public static final IContentType<ItemStack> ITEM = new ItemStackType();
    public static final IContentType<FluidStack> FLUID = new FluidStackType();

    private CirrusContentTypes() {
    }
}
