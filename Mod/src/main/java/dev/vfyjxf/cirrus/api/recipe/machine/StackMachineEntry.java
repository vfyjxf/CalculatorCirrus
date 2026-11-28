package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Generic {@link MachineEntry} backed by any Cirrus {@link ContentStack} (supports non-item modded
 * machines and synthetic stacks in offline/test catalogs without hardcoding {@code minecraft:item}).
 */
public record StackMachineEntry(ContentStack<?, ?> machineStack, MachineConstraint constraint) implements MachineEntry {

    public StackMachineEntry(ContentStack<?, ?> machineStack) {
        this(machineStack, MachineConstraint.empty);
    }

    public StackMachineEntry {
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
        ContentStack<Item, ItemStack> cheats = machineStack.cheatsStack();
        return cheats.isEmpty() ? ItemStack.EMPTY : cheats.value();
    }

    @Override
    public Optional<ComponentStackKey> contentKey() {
        return machineStack.isEmpty() ? Optional.empty() : Optional.of(machineStack.normalize().componentKey());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StackMachineEntry that)) return false;
        return machineStack.componentKey().equals(that.machineStack.componentKey())
                && Objects.equals(constraint, that.constraint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineStack.componentKey(), constraint);
    }
}
