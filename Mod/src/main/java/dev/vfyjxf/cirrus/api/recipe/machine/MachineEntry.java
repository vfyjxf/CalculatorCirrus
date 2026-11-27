package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint;
import net.minecraft.world.item.ItemStack;

public interface MachineEntry {

    MachineEntry empty = EmptyMachineEntry.instance;

    static ItemMachineEntry fromItemStack(ItemStack stack) {
        return ItemMachineEntry.fromItemStack(stack);
    }

    static ItemMachineEntry fromItemStack(ItemStack stack, MachineConstraint constraint) {
        return ItemMachineEntry.fromItemStack(stack, constraint);
    }

    boolean isEmpty();

    default MachineConstraint constraint() {
        return MachineConstraint.empty;
    }

    default ItemStack toItemStack() {
        return ItemStack.EMPTY;
    }

}
