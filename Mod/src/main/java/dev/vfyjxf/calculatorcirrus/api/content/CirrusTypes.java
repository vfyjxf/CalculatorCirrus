package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.EnergyContentType;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.EnergyValue;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.TimeValue;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.TimeValueType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidContentType;
import dev.vfyjxf.calculatorcirrus.content.type.FluidStackType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemContentType;
import dev.vfyjxf.calculatorcirrus.content.type.ItemStackType;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CirrusTypes {

    //region content
    public static final ContentType<Item> ITEM = ItemContentType.instance;
    public static final ContentType<Fluid> FLUID = FluidContentType.instance;
    //endregion

    //region stack
    public static final StackType<Item, ItemStack> ITEM_STACK = ItemStackType.instance;
    public static final StackType<Fluid, FluidStack> FLUID_STACK = FluidStackType.instance;
    //endregion

    //region tag
    public static final TagType<Item> ITEM_TAG = TagType.fromRegistry(Locations.ofMc("tag/item"), ITEM, BuiltInRegistries.ITEM);
    public static final TagType<Fluid> FLUID_TAG = TagType.fromRegistry(Locations.ofMc("tag/fluid"), FLUID, BuiltInRegistries.FLUID);
    //endregion

    //region recipe content
    public static final ValueType<TimeValue> TIME = TimeValueType.instance;
    public static final ValueType<EnergyValue> ENERGY = EnergyContentType.instance;
    //endregion

    private CirrusTypes() {throw new AssertionError("This class should not be instantiated!");}
}
