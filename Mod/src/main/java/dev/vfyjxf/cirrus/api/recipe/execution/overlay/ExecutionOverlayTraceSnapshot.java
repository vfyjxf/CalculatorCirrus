package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

public record ExecutionOverlayTraceSnapshot(
    boolean available,
    @Nullable String recipeLabel,
    ItemStack recipeIcon,
    ImmutableList<SlotHighlight> highlights,
    ImmutableList<SlotMove> moves
) {

    public ExecutionOverlayTraceSnapshot {
        recipeIcon = recipeIcon.copy();
    }

    public static ExecutionOverlayTraceSnapshot empty() {
        return new ExecutionOverlayTraceSnapshot(false, null, ItemStack.EMPTY, Lists.immutable.empty(), Lists.immutable.empty());
    }

    public record SlotHighlight(
        SlotLayout.Reference slot,
        String role,
        @Nullable String label,
        ItemStack stack,
        int color
    ) {
        public SlotHighlight {
            stack = stack.copy();
        }
    }

    public record SlotMove(
        SlotLayout.Reference from,
        SlotLayout.Reference to,
        ItemStack stack,
        long amount,
        int color
    ) {
        public SlotMove {
            stack = stack.copy();
            amount = Math.max(0L, amount);
        }
    }
}
