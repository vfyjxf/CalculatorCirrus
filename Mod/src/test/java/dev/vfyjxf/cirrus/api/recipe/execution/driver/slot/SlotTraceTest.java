package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotFlow;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotTrace;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotTraceTest {

    private static final Namespace testInteractionId = Namespace.of("test", "slot_trace");

    @Test
    void prepareTraceUsesInventoryToInputMoves() {
        FakeAccess access = new FakeAccess(true);
        SlotLayout.Reference inventory = new SlotLayout.Reference.Index(10);
        SlotLayout.Reference input = new SlotLayout.Reference.Index(1);
        access.put(inventory, stack(Items.OAK_LOG, 8));
        SlotFlow.Context context = new SlotFlow.Context(layout(), access, null, null);

        ExecutionOverlayTraceSnapshot trace = SlotTrace.prepare(
            context,
            "oak_planks",
            stack(Items.OAK_PLANKS, 4),
            Lists.immutable.of(new SlotFlow.Placement(input, stack(Items.OAK_LOG, 1), 1)),
            palette()
        );

        assertTrue(trace.available());
        assertEquals(1, trace.moves().size());
        assertEquals(inventory, trace.moves().getFirst().from());
        assertEquals(input, trace.moves().getFirst().to());
    }

    @Test
    void executeTraceHighlightsOutputAndInventoryTarget() {
        FakeAccess access = new FakeAccess(true);
        SlotLayout.Reference output = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference invA = new SlotLayout.Reference.Index(10);
        SlotLayout.Reference invB = new SlotLayout.Reference.Index(11);
        access.put(output, stack(Items.OAK_PLANKS, 4));
        access.put(invA, stack(Items.OAK_PLANKS, 32));
        access.put(invB, stack(Items.OAK_PLANKS, 16));
        SlotFlow.Context context = new SlotFlow.Context(layout(), access, null, null);

        ExecutionOverlayTraceSnapshot trace = SlotTrace.execute(
            context,
            "oak_planks",
            stack(Items.OAK_PLANKS, 4),
            stack(Items.OAK_PLANKS, 4),
            palette(),
            SlotTrace.InventoryTargetSelector.moveItemStackTo(true)
        );

        assertTrue(trace.available());
        assertEquals(2, trace.highlights().size());
        assertEquals(1, trace.moves().size());
        assertTrue(trace.highlights().stream().anyMatch(highlight ->
            highlight.role().equals(SlotTrace.Roles.output) && highlight.slot().equals(output)
        ));
        assertTrue(trace.highlights().stream().anyMatch(highlight ->
            highlight.role().equals(SlotTrace.Roles.inventory) && highlight.slot().equals(invB)
        ));
        assertEquals(invB, trace.moves().getFirst().to());
    }

    @Test
    void collectTraceCanBeSuppressedByCaller() {
        ExecutionOverlayTraceSnapshot trace = ExecutionOverlayTraceSnapshot.empty();

        assertFalse(trace.available());
        assertTrue(trace.highlights().isEmpty());
        assertTrue(trace.moves().isEmpty());
    }

    @Test
    void prepareTraceSkipsGhostWhenPlacementAlreadySatisfied() {
        FakeAccess access = new FakeAccess(true);
        SlotLayout.Reference inventory = new SlotLayout.Reference.Index(10);
        SlotLayout.Reference input = new SlotLayout.Reference.Index(1);
        access.put(inventory, stack(Items.OAK_LOG, 8));
        access.put(input, stack(Items.OAK_LOG, 1));
        SlotFlow.Context context = new SlotFlow.Context(layout(), access, null, null);

        ExecutionOverlayTraceSnapshot trace = SlotTrace.prepare(
            context,
            "oak_planks",
            stack(Items.OAK_PLANKS, 4),
            Lists.immutable.of(new SlotFlow.Placement(input, stack(Items.OAK_LOG, 1), 1)),
            palette()
        );

        assertTrue(trace.available());
        assertTrue(trace.moves().isEmpty());
        assertFalse(trace.highlights().isEmpty());
        assertNotNull(trace.highlights().getFirst());
    }

    private static SlotLayout layout() {
        return SlotLayout.builder()
            .add(SlotLayout.Role.output, new SlotLayout.Key(Namespace.of("test", "output0"), 0), new SlotLayout.Reference.Index(0))
            .add(SlotLayout.Role.input, new SlotLayout.Key(Namespace.of("test", "input0"), 0), new SlotLayout.Reference.Index(1))
            .add(SlotLayout.Role.inventory, new SlotLayout.Key(Namespace.of("test", "inventory0"), 0), new SlotLayout.Reference.Index(10))
            .add(SlotLayout.Role.inventory, new SlotLayout.Key(Namespace.of("test", "inventory1"), 1), new SlotLayout.Reference.Index(11))
            .build();
    }

    private static SlotTrace.Palette palette() {
        return new SlotTrace.Palette(0x10, 0x20, 0x30, 0x40);
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(count);
        return stack;
    }

    private static final class FakeAccess implements SlotAccess {
        private final boolean open;
        private final Map<SlotLayout.Reference, ItemStack> stacks = new LinkedHashMap<>();

        private FakeAccess(boolean open) {
            this.open = open;
        }

        void put(SlotLayout.Reference slot, ItemStack stack) {
            stacks.put(slot, stack.copy());
        }

        @Override
        public State state() {
            return new State(open, testInteractionId, ExecutionSideRequirement.client);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            ItemStack stack = stacks.get(slot);
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }
}
