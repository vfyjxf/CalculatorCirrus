package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.value.EmptyStackContentType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemStackType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import static net.minecraft.resources.ResourceLocation.fromNamespaceAndPath;

@SuppressWarnings("deprecated")
public final class CirrusContentTypes {

    public static final StackContentType<?> EMPTY = EmptyStackContentType.INSTANCE;
    public static final StackContentType<ItemStack> ITEM = ItemStackType.INSTANCE;
    public static final StackContentType<FluidStack> FLUID = FluidStackType.INSTANCE;

    public static final TagType<Item, ItemStack> ITEM_TAG = TagType.fromRegistry(
            of("tag/item"), Item.class, ITEM, BuiltInRegistries.ITEM, ItemStack::getItem,
            (item, amount, componentPatch) -> new ItemStack(item.builtInRegistryHolder(), (int) amount, componentPatch)
    );
    public static final TagType<Fluid, FluidStack> FLUID_TAG = TagType.fromRegistry(
            of("tag/fluid"), Fluid.class, FLUID, BuiltInRegistries.FLUID, FluidStack::getFluid,
            (fluid, amount, componentPatch) -> new FluidStack(fluid.builtInRegistryHolder(), ((int) amount), componentPatch));


    public static StackContentType<ItemStack> itemType() {
        return ITEM;
    }

    public static StackContentType<FluidStack> fluidType() {
        return FLUID;
    }

    private static ResourceLocation of(String path) {
        return fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    private CirrusContentTypes() {throw new AssertionError("This class should not be instantiated!");}
}
