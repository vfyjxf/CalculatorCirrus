package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Shared trace helpers for slot-based execution overlays.
 */
public final class SlotTrace {

    private SlotTrace() {
    }

    //region palette

    public record Palette(
        int inputColor,
        int inventoryColor,
        int outputColor,
        int moveColor
    ) {
    }

    public static final class Roles {
        public static final String inputPreview = "input-preview";
        public static final String inputExecuting = "input-executing";
        public static final String output = "output";
        public static final String inventory = "inventory";

        private Roles() {
        }
    }
    //endregion

    //region contract

    @FunctionalInterface
    public interface InventoryTargetSelector {
        @Nullable SlotLayout.Reference select(
            SlotFlow.Context context,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            ItemStack stack
        );

        static InventoryTargetSelector moveItemStackTo(boolean reverse) {
            return (context, inventorySlots, stack) -> selectInventoryTarget(context, inventorySlots, stack, reverse);
        }
    }
    //endregion

    //region trace building

    public static ImmutableList<SlotLayout.Reference> orderedSlotReferences(SlotFlow.Context context, SlotLayout.Role role) {
        SlotLayout.Index index = context.layout().slots(role);
        if (index.keys().isEmpty()) {
            return Lists.immutable.empty();
        }
        ArrayList<SlotLayout.Key> orderedKeys = new ArrayList<>(index.keys());
        orderedKeys.sort(index.order());
        var ordered = Lists.mutable.<SlotLayout.Reference>empty();
        for (SlotLayout.Key key : orderedKeys) {
            SlotLayout.Reference reference = index.get(key);
            if (reference != null) {
                ordered.add(reference);
            }
        }
        return ordered.toImmutable();
    }

    public static boolean placementsSatisfied(SlotFlow.Context context, ImmutableList<SlotFlow.Placement> placements) {
        if (placements.isEmpty()) {
            return false;
        }
        for (SlotFlow.Placement placement : placements) {
            if (!placementSatisfied(context, placement)) {
                return false;
            }
        }
        return true;
    }

    public static ExecutionOverlayTraceSnapshot prepare(
        SlotFlow.Context context,
        String recipeLabel,
        ItemStack recipeIcon,
        ImmutableList<SlotFlow.Placement> placements,
        Palette palette
    ) {
        if (placements.isEmpty()) {
            return ExecutionOverlayTraceSnapshot.empty();
        }
        ImmutableList<SlotLayout.Reference> inventorySlots = orderedSlotReferences(context, SlotLayout.Role.inventory);
        if (inventorySlots.isEmpty()) {
            return ExecutionOverlayTraceSnapshot.empty();
        }

        ArrayList<ExecutionOverlayTraceSnapshot.SlotHighlight> highlights = new ArrayList<>();
        ArrayList<ExecutionOverlayTraceSnapshot.SlotMove> moves = new ArrayList<>();
        LinkedHashMap<SlotLayout.Reference, ItemStack> inventoryState = inventorySnapshot(context, inventorySlots);
        LinkedHashMap<SlotLayout.Reference, ItemStack> inventoryHighlights = new LinkedHashMap<>();
        LinkedHashMap<SlotLayout.Reference, ItemStack> inputHighlights = new LinkedHashMap<>();

        for (SlotFlow.Placement placement : placements) {
            ItemStack template = normalize(placement.template());
            if (template.isEmpty()) {
                continue;
            }
            inputHighlights.putIfAbsent(placement.slot(), template);
            long remaining = remainingPlacementAmount(context, placement);
            while (remaining > 0L) {
                SlotLayout.Reference source = firstInventorySource(inventoryState, inventorySlots, placement, template);
                if (source == null) {
                    break;
                }
                ItemStack sourceStack = inventoryState.get(source);
                if (sourceStack.isEmpty()) {
                    break;
                }
                long moved = Math.min(remaining, Math.max(1, sourceStack.getCount()));
                inventoryHighlights.putIfAbsent(source, normalize(context.access().stack(source)));
                moves.add(new ExecutionOverlayTraceSnapshot.SlotMove(
                    source,
                    placement.slot(),
                    template,
                    moved,
                    palette.moveColor()
                ));
                decrement(sourceStack, moved);
                remaining -= moved;
            }
        }

        for (var entry : inventoryHighlights.entrySet()) {
            highlights.add(new ExecutionOverlayTraceSnapshot.SlotHighlight(
                entry.getKey(),
                Roles.inventory,
                Roles.inventory,
                entry.getValue(),
                palette.inventoryColor()
            ));
        }
        for (var entry : inputHighlights.entrySet()) {
            highlights.add(new ExecutionOverlayTraceSnapshot.SlotHighlight(
                entry.getKey(),
                Roles.inputPreview,
                Roles.inputPreview,
                entry.getValue(),
                palette.inputColor()
            ));
        }

        return new ExecutionOverlayTraceSnapshot(
            true,
            recipeLabel,
            normalize(recipeIcon),
            Lists.immutable.ofAll(highlights),
            Lists.immutable.ofAll(moves)
        );
    }

    public static ExecutionOverlayTraceSnapshot execute(
        SlotFlow.Context context,
        String recipeLabel,
        ItemStack recipeIcon,
        ItemStack outputStack,
        Palette palette,
        InventoryTargetSelector targetSelector
    ) {
        return outputTransfer(
            context,
            recipeLabel,
            recipeIcon,
            outputStack,
            palette,
            targetSelector
        );
    }

    public static ExecutionOverlayTraceSnapshot collect(
        SlotFlow.Context context,
        String recipeLabel,
        ItemStack recipeIcon,
        ItemStack outputStack,
        Palette palette,
        InventoryTargetSelector targetSelector
    ) {
        return outputTransfer(
            context,
            recipeLabel,
            recipeIcon,
            outputStack,
            palette,
            targetSelector
        );
    }

    private static ExecutionOverlayTraceSnapshot outputTransfer(
        SlotFlow.Context context,
        String recipeLabel,
        ItemStack recipeIcon,
        ItemStack outputStack,
        Palette palette,
        InventoryTargetSelector targetSelector
    ) {
        ArrayList<ExecutionOverlayTraceSnapshot.SlotHighlight> highlights = new ArrayList<>();
        ArrayList<ExecutionOverlayTraceSnapshot.SlotMove> moves = new ArrayList<>();
        SlotLayout.Reference outputSlot = findOutputSlot(context);
        if (outputSlot == null) {
            return ExecutionOverlayTraceSnapshot.empty();
        }

        ItemStack normalizedOutput = normalize(outputStack);
        if (normalizedOutput.isEmpty()) {
            normalizedOutput = normalize(context.access().stack(outputSlot));
        }
        if (normalizedOutput.isEmpty()) {
            return ExecutionOverlayTraceSnapshot.empty();
        }
        highlights.add(new ExecutionOverlayTraceSnapshot.SlotHighlight(
            outputSlot,
            Roles.output,
            Roles.output,
            normalizedOutput,
            palette.outputColor()
        ));

        ImmutableList<SlotLayout.Reference> inventorySlots = orderedSlotReferences(context, SlotLayout.Role.inventory);
        SlotLayout.Reference target = targetSelector.select(context, inventorySlots, normalizedOutput);
        if (target != null) {
            ItemStack inventoryDisplay = normalize(context.access().stack(target));
            if (inventoryDisplay.isEmpty()) {
                inventoryDisplay = normalizedOutput;
            }
            highlights.add(new ExecutionOverlayTraceSnapshot.SlotHighlight(
                target,
                Roles.inventory,
                Roles.inventory,
                inventoryDisplay,
                palette.inventoryColor()
            ));
            moves.add(new ExecutionOverlayTraceSnapshot.SlotMove(
                outputSlot,
                target,
                normalizedOutput,
                Math.max(1L, normalizedOutput.getCount()),
                palette.moveColor()
            ));
        }

        return new ExecutionOverlayTraceSnapshot(
            true,
            recipeLabel,
            normalize(recipeIcon),
            Lists.immutable.ofAll(highlights),
            Lists.immutable.ofAll(moves)
        );
    }
    //endregion

    //region helpers

    private static boolean placementSatisfied(SlotFlow.Context context, SlotFlow.Placement placement) {
        ItemStack template = normalize(placement.template());
        if (template.isEmpty()) {
            return false;
        }
        ItemStack stack = context.access().stack(placement.slot());
        return !stack.isEmpty() && placement.matches(stack) && stack.getCount() >= placement.amount();
    }

    private static long remainingPlacementAmount(SlotFlow.Context context, SlotFlow.Placement placement) {
        ItemStack template = normalize(placement.template());
        if (template.isEmpty()) {
            return 0L;
        }
        long targetAmount = Math.max(0L, placement.amount());
        if (targetAmount <= 0L) {
            return 0L;
        }
        ItemStack current = context.access().stack(placement.slot());
        if (!current.isEmpty() && placement.matches(current)) {
            return Math.max(0L, targetAmount - current.getCount());
        }
        return targetAmount;
    }

    private static LinkedHashMap<SlotLayout.Reference, ItemStack> inventorySnapshot(
        SlotFlow.Context context,
        ImmutableList<SlotLayout.Reference> inventorySlots
    ) {
        LinkedHashMap<SlotLayout.Reference, ItemStack> snapshot = new LinkedHashMap<>();
        for (SlotLayout.Reference slot : inventorySlots) {
            snapshot.put(slot, context.access().stack(slot).copy());
        }
        return snapshot;
    }

    private static @Nullable SlotLayout.Reference firstInventorySource(
        LinkedHashMap<SlotLayout.Reference, ItemStack> inventoryState,
        ImmutableList<SlotLayout.Reference> inventorySlots,
        SlotFlow.Placement placement,
        ItemStack template
    ) {
        for (SlotLayout.Reference slot : inventorySlots) {
            ItemStack stack = inventoryState.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (sameItem(stack, template)) {
                return slot;
            }
        }
        for (SlotLayout.Reference slot : inventorySlots) {
            ItemStack stack = inventoryState.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (placement.matches(stack)) {
                return slot;
            }
        }
        return null;
    }

    private static @Nullable SlotLayout.Reference findOutputSlot(SlotFlow.Context context) {
        ImmutableList<SlotLayout.Reference> outputSlots = orderedSlotReferences(context, SlotLayout.Role.output);
        if (outputSlots.isEmpty()) {
            return null;
        }
        for (SlotLayout.Reference slot : outputSlots) {
            if (!context.access().stack(slot).isEmpty()) {
                return slot;
            }
        }
        return outputSlots.getFirst();
    }

    private static @Nullable SlotLayout.Reference selectInventoryTarget(
        SlotFlow.Context context,
        ImmutableList<SlotLayout.Reference> inventorySlots,
        ItemStack stack,
        boolean reverse
    ) {
        if (inventorySlots.isEmpty()) {
            return null;
        }
        List<SlotLayout.Reference> ordered = new ArrayList<>(inventorySlots.castToList());
        if (reverse) {
        Collections.reverse(ordered);
        }
        if (stack.isStackable()) {
            for (SlotLayout.Reference slot : ordered) {
                ItemStack current = context.access().stack(slot);
                if (!current.isEmpty() && sameItem(current, stack) && current.getCount() < current.getMaxStackSize()) {
                    return slot;
                }
            }
        }
        for (SlotLayout.Reference slot : ordered) {
            if (context.access().stack(slot).isEmpty()) {
                return slot;
            }
        }
        return null;
    }

    private static void decrement(ItemStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0L) {
            return;
        }
        int remaining = (int) Math.max(0L, stack.getCount() - amount);
        stack.setCount(remaining);
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty()
            && ItemStack.isSameItemSameComponents(left, right);
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(Math.max(1, copy.getCount()));
        return copy;
    }
    //endregion
}

