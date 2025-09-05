package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidContentType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemContentType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemStackType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import static net.minecraft.resources.ResourceLocation.fromNamespaceAndPath;

public final class CirrusTypes {

    //region content
    public static final ContentType<Item> item = ItemContentType.instance;
    public static final ContentType<Fluid> fluid = FluidContentType.instance;
    //endregion

    //region stack
    public static final StackType<Item, ItemStack> itemStack = ItemStackType.instance;
    public static final StackType<Fluid, FluidStack> fluidStack = FluidStackType.instance;
    //endregion

    //region tag
    public static final TagType<Item> itemTag = TagType.fromRegistry(of("tag/item"), item, BuiltInRegistries.ITEM);
    public static final TagType<Fluid> fluidTag = TagType.fromRegistry(of("tag/fluid"), fluid, BuiltInRegistries.FLUID);
    //endregion

    private static ResourceLocation of(String path) {
        return fromNamespaceAndPath(CirrusConstants.MOD_ID, path);
    }

    private CirrusTypes() {throw new AssertionError("This class should not be instantiated!");}
}
