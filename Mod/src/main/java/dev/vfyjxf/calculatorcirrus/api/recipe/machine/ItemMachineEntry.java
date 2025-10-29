package dev.vfyjxf.calculatorcirrus.api.recipe.machine;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemMachineEntry(
    ContentStack<Item, ItemStack> machineStack
) implements MachineEntry {

    public static ItemMachineEntry fromItemStack(ItemStack stack) {
        Checks.checkNotNull(stack, "stack");
        Checks.checkArgument(stack.isEmpty(), "stack cannot be empty");
        ContentStack<Item, ItemStack> normalized = ContentStack.fromStack(stack).normalize();
        return new ItemMachineEntry(normalized);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        ItemMachineEntry that = (ItemMachineEntry) object;
        return machineStack.equals(that.machineStack);
    }

    @Override
    public int hashCode() {
        return machineStack.hashCode();
    }
}
