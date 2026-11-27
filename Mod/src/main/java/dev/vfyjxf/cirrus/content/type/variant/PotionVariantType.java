package dev.vfyjxf.cirrus.content.type.variant;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Objects;

public class PotionVariantType extends BasicVariantType<Item, ItemStack> {

    public static final PotionVariantType instance = new PotionVariantType();

    private PotionVariantType() {
        super(Namespace.ofMc("potion"), CirrusTypes.Contents.itemStack);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() == Items.AIR) return ItemStack.EMPTY;
        var potion = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var ret = new ItemStack(stack.getItem(), stack.getCount());
        ret.set(DataComponents.POTION_CONTENTS, potion);
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        return stack1.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .equals(stack2.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY));
    }

    @Override
    public int hashIdentity(ItemStack stack) {
        return Objects.hashCode(stack.get(DataComponents.POTION_CONTENTS));
    }
}
