package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Access to the underlying slot container and interaction state.
 */
public interface SlotAccess {
    State state();

    ItemStack stack(SlotLayout.Reference slot);

    default boolean isOpen() {
        return state().open();
    }

    interface Interaction {
        boolean click(Click click);

        default void open() {}

        default void close() {}

        default ItemStack carried() {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Snapshot of the active interaction binding for a slot access.
     */
    record State(
        boolean open,
        Namespace interactionId,
        ExecutionSideRequirement side
    ) {}

    record Click(SlotLayout.Reference slot, ClickType type, int button) {}
}
