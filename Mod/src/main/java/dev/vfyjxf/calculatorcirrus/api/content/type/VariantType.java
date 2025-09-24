package dev.vfyjxf.calculatorcirrus.api.content.type;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.PotionItem;

/**
 * Items like {@link EnchantedBookItem} and {@link PotionItem}  usually have only one item instance,
 * they share the same item instance, and special data on the ItemStack,
 * such as {@link DataComponents#POTION_CONTENTS}, determines what exactly they are.
 */
public interface VariantType<T, S> {

    ResourceLocation typeId();

    default ContentType<T> contentType() {
        return stackType().contentType();
    }

    StackType<T, S> stackType();

    S copyWithIdentity(S stack);

    /**
     * Checks if two stacks are the same variant identity.
     * @param stack1 the first stack
     * @param stack2 the second stack
     * @return true if the two stacks are the same variant identity, false otherwise.
     */
    boolean matchesVariantIdentity(S stack1, S stack2);


}
