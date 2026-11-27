package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
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
        assertTrue(
            trace.highlights().stream().anyMatch(
                highlight -> highlight.role().equals(SlotTrace.Roles.output) && highlight.slot().equals(output)
            )
        );
        assertTrue(
            trace.highlights().stream().anyMatch(
                highlight -> highlight.role().equals(SlotTrace.Roles.inventory) && highlight.slot().equals(invB)
            )
        );
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
        assertNotNull(trace.highlights().getFirst());
    }

    @Test
    void prepareFlowClassifiesReadyPreviewMissingAndOutputPreview() {
        FakeAccess access = new FakeAccess(true);
        SlotLayout.Reference input0 = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference input1 = new SlotLayout.Reference.Index(2);
        SlotLayout.Reference input2 = new SlotLayout.Reference.Index(3);
        SlotLayout.Reference inv0 = new SlotLayout.Reference.Index(10);
        access.put(input0, stack(Items.OAK_LOG, 1));
        access.put(inv0, stack(Items.OAK_LOG, 4));
        SlotLayout wideLayout = SlotLayout.builder()
                .add(
                    SlotLayout.Role.output,
                    new SlotLayout.Key(Namespace.of("test", "out0"), 0),
                    new SlotLayout.Reference.Index(0)
                ).add(SlotLayout.Role.input, new SlotLayout.Key(Namespace.of("test", "in0"), 0), input0)
                .add(SlotLayout.Role.input, new SlotLayout.Key(Namespace.of("test", "in1"), 1), input1)
                .add(SlotLayout.Role.input, new SlotLayout.Key(Namespace.of("test", "in2"), 2), input2)
                .add(SlotLayout.Role.inventory, new SlotLayout.Key(Namespace.of("test", "inv0"), 0), inv0).build();
        SlotFlow.Context context = new SlotFlow.Context(wideLayout, access, null, null);

        ExecutionOverlayTraceSnapshot trace = SlotTrace.prepareFlow(
            context,
            "test:mixed",
            stack(Items.BLAST_FURNACE, 1),
            stack(Items.BLAST_FURNACE, 1),
            Lists.immutable.of(
                new SlotFlow.Placement(input0, stack(Items.OAK_LOG, 1), 1),
                new SlotFlow.Placement(input1, stack(Items.OAK_LOG, 1), 2),
                new SlotFlow.Placement(input2, stack(Items.IRON_INGOT, 1), 1)
            ),
            palette()
        );

        assertTrue(trace.available());
        assertEquals(1, trace.moves().size());
        assertEquals(inv0, trace.moves().getFirst().from());
        assertEquals(input1, trace.moves().getFirst().to());
        assertEquals(2L, trace.moves().getFirst().amount());
        assertTrue(
            trace.highlights().anySatisfy(h -> h.slot().equals(input0) && SlotTrace.Roles.inputReady.equals(h.role()))
        );
        assertTrue(
            trace.highlights().anySatisfy(h -> h.slot().equals(input1) && SlotTrace.Roles.inputPreview.equals(h.role()))
        );
        assertTrue(
            trace.highlights().anySatisfy(h -> h.slot().equals(input2) && SlotTrace.Roles.inputMissing.equals(h.role()))
        );
        assertTrue(trace.highlights().anySatisfy(h -> h.slot().equals(inv0) && "-2".equals(h.label())));
        assertTrue(trace.highlights().anySatisfy(h -> SlotTrace.Roles.outputPreview.equals(h.role())));
    }

    @Test
    void executeFlowAndCollectFlowEmitFullMaterialPipeline() {
        FakeAccess access = new FakeAccess(true);
        SlotLayout.Reference output = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference input = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference invA = new SlotLayout.Reference.Index(10);
        access.put(input, stack(Items.OAK_LOG, 2));
        access.put(output, stack(Items.OAK_PLANKS, 4));
        access.put(invA, stack(Items.OAK_PLANKS, 8));
        SlotFlow.Context context = new SlotFlow.Context(layout(), access, null, null);

        ExecutionOverlayTraceSnapshot execTrace = SlotTrace.executeFlow(
            context,
            "oak_planks",
            stack(Items.OAK_PLANKS, 4),
            stack(Items.OAK_PLANKS, 4),
            Lists.immutable.of(new SlotFlow.Placement(input, stack(Items.OAK_LOG, 1), 2)),
            palette(),
            SlotTrace.InventoryTargetSelector.moveItemStackTo(true)
        );
        assertTrue(execTrace.available());
        assertEquals(2, execTrace.moves().size());
        assertTrue(execTrace.moves().anySatisfy(m -> m.from().equals(input) && m.to().equals(output)));
        assertTrue(execTrace.moves().anySatisfy(m -> m.from().equals(output) && m.to().equals(invA)));
        assertTrue(
            execTrace.highlights()
                    .anySatisfy(h -> h.slot().equals(invA) && SlotTrace.Roles.inventoryTarget.equals(h.role()))
        );

        ExecutionOverlayTraceSnapshot collectTrace = SlotTrace.collectFlow(
            context,
            "oak_planks",
            stack(Items.OAK_PLANKS, 4),
            stack(Items.OAK_PLANKS, 4),
            palette(),
            SlotTrace.InventoryTargetSelector.moveItemStackTo(true)
        );
        assertTrue(collectTrace.available());
        assertEquals(1, collectTrace.moves().size());
        assertEquals(output, collectTrace.moves().getFirst().from());
        assertEquals(invA, collectTrace.moves().getFirst().to());
    }

    private static SlotLayout layout() {
        return SlotLayout.builder()
                .add(
                    SlotLayout.Role.output,
                    new SlotLayout.Key(Namespace.of("test", "output0"), 0),
                    new SlotLayout.Reference.Index(0)
                )
                .add(
                    SlotLayout.Role.input,
                    new SlotLayout.Key(Namespace.of("test", "input0"), 0),
                    new SlotLayout.Reference.Index(1)
                )
                .add(
                    SlotLayout.Role.inventory,
                    new SlotLayout.Key(Namespace.of("test", "inventory0"), 0),
                    new SlotLayout.Reference.Index(10)
                )
                .add(
                    SlotLayout.Role.inventory,
                    new SlotLayout.Key(Namespace.of("test", "inventory1"), 1),
                    new SlotLayout.Reference.Index(11)
                ).build();
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
