package dev.vfyjxf.calculatorcirrus.content.type.variant;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.cloudlib.util.Locations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class EnchantedBookVariantType extends BasicVariantType<Item, ItemStack> {

    public static final EnchantedBookVariantType instance = new EnchantedBookVariantType();

    private EnchantedBookVariantType() {
        super(Locations.ofMc("enchanted_book"), CirrusTypes.ITEM_STACK);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return ItemStack.EMPTY;

        int count = stack.getCount();
        var enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        var ret = new ItemStack(Items.ENCHANTED_BOOK, count);
        ret.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack1);
        var enchantments2 = EnchantmentHelper.getEnchantmentsForCrafting(stack2);
        return enchantments.equals(enchantments2);
    }

}

