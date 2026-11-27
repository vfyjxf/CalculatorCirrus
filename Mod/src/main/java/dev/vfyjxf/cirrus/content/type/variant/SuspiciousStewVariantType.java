package dev.vfyjxf.cirrus.content.type.variant;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;

import java.util.Objects;

public class SuspiciousStewVariantType extends BasicVariantType<Item, ItemStack> {

    public static final SuspiciousStewVariantType instance = new SuspiciousStewVariantType();

    private SuspiciousStewVariantType() {
        super(Namespace.ofMc("suspicious_stew"), CirrusTypes.Contents.itemStack);
    }

    @Override
    public ItemStack copyWithIdentity(ItemStack stack) {
        if (stack.getItem() != Items.SUSPICIOUS_STEW) return ItemStack.EMPTY;
        ItemStack ret = new ItemStack(Items.SUSPICIOUS_STEW, stack.getCount());
        ret.set(
            DataComponents.SUSPICIOUS_STEW_EFFECTS,
            stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY)
        );
        return ret;
    }

    @Override
    public boolean matchesVariantIdentity(ItemStack stack1, ItemStack stack2) {
        return stack1.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY)
                .equals(stack2.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY));
    }

    @Override
    public int hashIdentity(ItemStack stack) {
        return Objects.hashCode(stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS));
    }
}
