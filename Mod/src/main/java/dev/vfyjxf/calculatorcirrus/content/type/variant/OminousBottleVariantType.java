package dev.vfyjxf.calculatorcirrus.content.type.variant;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

import static net.minecraft.core.component.DataComponents.OMINOUS_BOTTLE_AMPLIFIER;

public class OminousBottleVariantType extends BasicVariantType<Item, ItemStack> {

    public static final OminousBottleVariantType instance = new OminousBottleVariantType();

    private OminousBottleVariantType() {
        super(Locations.ofMc("ominous_bottle"), CirrusTypes.ITEM_STACK);
    }


    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() != Items.OMINOUS_BOTTLE) return ItemStack.EMPTY;
        ItemStack ret = new ItemStack(Items.OMINOUS_BOTTLE, stack.getCount());
        ret.set(OMINOUS_BOTTLE_AMPLIFIER, stack.getOrDefault(OMINOUS_BOTTLE_AMPLIFIER, 0));
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        return Objects.equals(stack1.get(OMINOUS_BOTTLE_AMPLIFIER), stack2.get(OMINOUS_BOTTLE_AMPLIFIER));
    }
}
