package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public interface MachineEntry {

    MachineEntry empty = EmptyMachineEntry.instance;

    Comparator<MachineEntry> comparator = (a, b) -> {
        if (a == b || Objects.equals(a, b)) {
            return 0;
        }
        Optional<ComponentStackKey> ka = a.contentKey();
        Optional<ComponentStackKey> kb = b.contentKey();
        if (ka.isPresent() && kb.isPresent()) {
            int c = ka.get().compareTo(kb.get());
            if (c != 0) return c;
        } else if (ka.isPresent() != kb.isPresent()) {
            return ka.isPresent() ? -1 : 1;
        }
        int c = a.getClass().getName().compareTo(b.getClass().getName());
        if (c != 0) return c;
        return a.canonicalKey().compareTo(b.canonicalKey());
    };

    static ItemMachineEntry fromItemStack(ItemStack stack) {
        return ItemMachineEntry.fromItemStack(stack);
    }

    static ItemMachineEntry fromItemStack(ItemStack stack, MachineConstraint constraint) {
        return ItemMachineEntry.fromItemStack(stack, constraint);
    }

    static MachineEntry fromContentStack(ContentStack<?, ?> stack) {
        return fromContentStack(stack, MachineConstraint.empty);
    }

    static MachineEntry fromContentStack(ContentStack<?, ?> stack, MachineConstraint constraint) {
        if (stack == null || stack.isEmpty()) {
            return empty;
        }
        ContentStack<?, ?> normalized = stack.normalize();
        if (normalized.isItem()) {
            ContentStack<Item, ItemStack> itemStack = normalized.cast();
            return new ItemMachineEntry(itemStack, constraint == null ? MachineConstraint.empty : constraint);
        }
        return new StackMachineEntry(normalized, constraint == null ? MachineConstraint.empty : constraint);
    }

    static MachineEntry fromKey(ComponentStackKey key) {
        Objects.requireNonNull(key, "key");
        return fromContentStack(key.stack(), MachineConstraint.empty);
    }

    boolean isEmpty();

    default MachineConstraint constraint() {
        return MachineConstraint.empty;
    }

    default ItemStack toItemStack() {
        return ItemStack.EMPTY;
    }

    default Optional<ComponentStackKey> contentKey() {
        if (isEmpty()) {
            return Optional.empty();
        }
        return ComponentStackKey.fromItemStack(toItemStack());
    }

    default String canonicalKey() {
        return contentKey()
                .map(ComponentStackKey::canonicalKey)
                .orElseGet(() -> getClass().getName() + ":" + toString());
    }
}
