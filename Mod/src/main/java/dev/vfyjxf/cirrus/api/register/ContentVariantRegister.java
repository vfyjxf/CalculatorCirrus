package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ContentVariantRegister {

    <T, S> void registerVariantType(StackType<T, S> stackType, VariantType<T, S> variantType);

    default void registerItemVariantType(VariantType<Item, ItemStack> variantType) {
        registerVariantType(CirrusTypes.Contents.itemStack, variantType);
    }

    <T, S> void registerVariantForContent(T content, VariantType<T, S> variantType);

}
