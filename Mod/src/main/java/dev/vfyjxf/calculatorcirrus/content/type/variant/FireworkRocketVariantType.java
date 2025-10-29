package dev.vfyjxf.calculatorcirrus.content.type.variant;

import dev.vfyjxf.calculatorcirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;

import java.util.Objects;

public class FireworkRocketVariantType extends BasicVariantType<Item, ItemStack> {

    public static final FireworkRocketVariantType instance = new FireworkRocketVariantType();

    private FireworkRocketVariantType() {
        super(Locations.ofMc("firework"), CirrusTypes.Contents.ITEM_STACK);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() != Items.FIREWORK_ROCKET) return ItemStack.EMPTY;
        Fireworks firework = stack.get(DataComponents.FIREWORKS);
        if (firework == null) {
            return new ItemStack(Items.FIREWORK_ROCKET, stack.getCount());
        }
        ItemStack ret = new ItemStack(Items.FIREWORK_ROCKET, stack.getCount());
        ret.set(DataComponents.FIREWORKS, firework);
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        return Objects.equals(stack1.get(DataComponents.FIREWORKS), stack2.get(DataComponents.FIREWORKS));
    }

    @Override
    public int hashIdentity(ItemStack stack) {
        return Objects.hashCode(stack.get(DataComponents.FIREWORKS));
    }
}
