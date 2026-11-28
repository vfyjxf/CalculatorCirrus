package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

public record ItemMachineEntry(ContentStack<Item, ItemStack> machineStack, MachineConstraint constraint)
        implements
            MachineEntry {

    public static ItemMachineEntry fromItemStack(ItemStack stack, MachineConstraint constraint) {
        ContentStack<Item, ItemStack> normalized = ContentStack.fromStack(stack).normalize();
        return new ItemMachineEntry(normalized, constraint);
    }

    public static ItemMachineEntry fromItemStack(ItemStack stack) {
        ContentStack<Item, ItemStack> normalized = ContentStack.fromStack(stack).normalize();
        return new ItemMachineEntry(normalized);
    }

    public ItemMachineEntry(ContentStack<Item, ItemStack> machineStack) {
        this(machineStack, MachineConstraint.empty);
    }

    public ItemMachineEntry {
        Checks.checkNotNull(machineStack, "machineStack");
        Checks.checkNotNull(constraint, "constraint");
        Checks.checkArgument(!machineStack.isEmpty(), "machineStack cannot be empty");
    }

    @Override
    public boolean isEmpty() {
        return machineStack.isEmpty();
    }

    @Override
    public ItemStack toItemStack() {
        return machineStack.type().toItemStack(machineStack);
    }

    @Override
    public Optional<ComponentStackKey> contentKey() {
        return machineStack.isEmpty() ? Optional.empty() : Optional.of(machineStack.normalize().componentKey());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ItemMachineEntry that)) return false;
        return machineStack.componentKey().equals(that.machineStack.componentKey())
                && Objects.equals(constraint, that.constraint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineStack.componentKey(), constraint);
    }
}
