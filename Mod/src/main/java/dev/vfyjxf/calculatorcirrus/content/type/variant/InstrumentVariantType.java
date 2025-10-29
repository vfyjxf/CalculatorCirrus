package dev.vfyjxf.calculatorcirrus.content.type.variant;

import dev.vfyjxf.calculatorcirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

public class InstrumentVariantType extends BasicVariantType<Item, ItemStack> {

    public static final InstrumentVariantType instance = new InstrumentVariantType();

    private InstrumentVariantType() {
        super(Locations.ofMc("instrument"), CirrusTypes.Contents.ITEM_STACK);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() == Items.AIR) return ItemStack.EMPTY;
        ItemStack ret = new ItemStack(stack.getItem(), stack.getCount());
        ret.set(DataComponents.INSTRUMENT, stack.get(DataComponents.INSTRUMENT));
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        return Objects.equals(stack1.get(DataComponents.INSTRUMENT), stack2.get(DataComponents.INSTRUMENT));
    }

    @Override
    public int hashIdentity(ItemStack stack) {
        return Objects.hashCode(stack.get(DataComponents.INSTRUMENT));
    }
}
