package dev.vfyjxf.calculatorcirrus.api.recipe.machine;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.constraint.MachineConstraint;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface MachineEntry {

    static ItemMachineEntry fromItemStack(ItemStack stack) {
        return ItemMachineEntry.fromItemStack(stack);
    }

    default @Nullable MachineConstraint constraint() {return null;}

    default ItemStack toItemStack() {return ItemStack.EMPTY;}

}
