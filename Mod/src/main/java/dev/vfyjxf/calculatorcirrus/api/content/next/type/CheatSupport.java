package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import net.minecraft.world.item.ItemStack;

public interface CheatSupport<T> {

    ItemStack cheatStack(T value);

}
