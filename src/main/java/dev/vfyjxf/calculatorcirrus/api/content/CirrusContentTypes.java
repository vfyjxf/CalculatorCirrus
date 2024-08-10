package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.content.types.EmptyType;
import dev.vfyjxf.calculatorcirrus.content.types.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.types.ItemStackType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import static net.minecraft.resources.ResourceLocation.fromNamespaceAndPath;

public final class CirrusContentTypes {

    @SuppressWarnings("rawtypes")
    public static final ContentType EMPTY = new EmptyType();
    public static final ContentType<ItemStack> ITEM = new ItemStackType();
    public static final ContentType<FluidStack> FLUID = new FluidStackType();

    public static final TagType<ItemStack, Item> ITEM_TAG = TagType.fromRegistry(of("tag/item"), ITEM, Item.class, (item, amount) -> new ItemStack(item, (int) amount), ItemStack::getItem, BuiltInRegistries.ITEM);
    public static final TagType<FluidStack, Fluid> FLUID_TAG = TagType.fromRegistry(of("tag/fluid"), FLUID, Fluid.class, (fluid, amount) -> new FluidStack(fluid, ((int) amount)), FluidStack::getFluid, BuiltInRegistries.FLUID);


    @SuppressWarnings("unchecked")
    public static <T> ContentType<T> emptyType() {
        return (ContentType<T>) EMPTY;
    }

    private static ResourceLocation of(String path) {
        return fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    private CirrusContentTypes() {
    }
}
