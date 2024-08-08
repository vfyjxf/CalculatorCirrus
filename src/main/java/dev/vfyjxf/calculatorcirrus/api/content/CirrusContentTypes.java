package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.content.types.EmptyContentType;
import dev.vfyjxf.calculatorcirrus.content.types.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.types.ItemStackType;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CirrusContentTypes {

    public static final ContentType<Unit> EMPTY = new EmptyContentType();
    public static final ContentType<ItemStack> ITEM = new ItemStackType();
    public static final ContentType<FluidStack> FLUID = new FluidStackType();

    private CirrusContentTypes() {
    }
}
