package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotAccess;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class CraftingTableExecutionPlacementTest {

    private static final Namespace testInteractionId = Namespace.of("test", "crafting_table_placement");

    @Test
    void placementDistributesAggregatedInputAmountAcrossRepeatedIngredientSlots(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(10, stack(Items.OAK_LOG, 16));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(
                Ingredient.of(Items.OAK_LOG),
                Ingredient.of(Items.OAK_LOG),
                Ingredient.of(Items.OAK_LOG),
                Ingredient.of(Items.OAK_LOG)
            ),
            2,
            2,
            context(access),
            Lists.immutable.of(new PlanAction.InputBinding(
                InputContent.fromItemStack(stack(Items.OAK_LOG, 1), 1),
                16
            ))
        );

        assertEquals(4, placements.size());
        for (SlotFlow.Placement placement : placements) {
            assertEquals(4L, placement.amount());
            assertEquals(Items.OAK_LOG, placement.template().getItem());
        }
    }

    @Test
    void placementKeepsExistingGridCandidateBeforeReplacingWithLargerInventoryCandidate(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(1, stack(Items.BIRCH_PLANKS, 16));
        access.put(10, stack(Items.OAK_PLANKS, 64));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)),
            1,
            1,
            context(access),
            Lists.immutable.of(new PlanAction.InputBinding(
                InputContent.fromItemTag(ItemTags.PLANKS, 1),
                16
            ))
        );

        assertEquals(1, placements.size());
        assertEquals(16L, placements.getFirst().amount());
        assertEquals(Items.BIRCH_PLANKS, placements.getFirst().template().getItem());
    }

    @Test
    void placementUsesAvailableNonFirstTagCandidateFromInventory(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(10, stack(Items.BIRCH_PLANKS, 16));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)),
            1,
            1,
            context(access),
            Lists.immutable.of(new PlanAction.InputBinding(
                InputContent.fromItemTag(ItemTags.PLANKS, 1),
                16
            ))
        );

        assertEquals(1, placements.size());
        assertEquals(16L, placements.getFirst().amount());
        assertEquals(Items.BIRCH_PLANKS, placements.getFirst().template().getItem());
    }

    @Test
    void shapelessPlacementKeepsExistingMatchedGridSlots(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(2, stack(Items.STICK, 1));
        access.put(5, stack(Items.COAL, 1));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(
                Ingredient.of(Items.COAL),
                Ingredient.of(Items.STICK)
            ),
            1,
            1,
            context(access),
            Lists.immutable.of(
                new PlanAction.InputBinding(InputContent.fromItemStack(stack(Items.COAL, 1), 1), 1),
                new PlanAction.InputBinding(InputContent.fromItemStack(stack(Items.STICK, 1), 1), 1)
            )
        );

        assertEquals(2, placements.size());
        assertEquals(5, ((SlotLayout.Reference.Index) placements.get(0).slot()).index());
        assertEquals(Items.COAL, placements.get(0).template().getItem());
        assertEquals(2, ((SlotLayout.Reference.Index) placements.get(1).slot()).index());
        assertEquals(Items.STICK, placements.get(1).template().getItem());
    }

    @Test
    void smokerPlacementForSixteenCraftsUsesSixteenPerOccupiedSlot(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(10, stack(Items.FURNACE, 16));
        access.put(11, stack(Items.OAK_LOG, 64));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(
                Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG), Ingredient.EMPTY,
                Ingredient.of(Items.OAK_LOG), Ingredient.of(Items.FURNACE), Ingredient.of(Items.OAK_LOG),
                Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG), Ingredient.EMPTY
            ),
            3,
            3,
            context(access),
            Lists.immutable.of(
                new PlanAction.InputBinding(
                    InputContent.fromItemStack(stack(Items.FURNACE, 1), 1),
                    16
                ),
                new PlanAction.InputBinding(
                    InputContent.fromItemTag(ItemTags.LOGS, 1),
                    64
                )
            )
        );

        assertEquals(5, placements.size());
        assertPlacementAmount(placements, 2, Items.OAK_LOG, 16L);
        assertPlacementAmount(placements, 4, Items.OAK_LOG, 16L);
        assertPlacementAmount(placements, 5, Items.FURNACE, 16L);
        assertPlacementAmount(placements, 6, Items.OAK_LOG, 16L);
        assertPlacementAmount(placements, 8, Items.OAK_LOG, 16L);
    }

    @Test
    void previewKeepsShapedGridAmountsAndSemanticTagInputs(MinecraftServer server) {
        var previewSlots = CraftingTableExecutionDriver.resolvePreviewSlotsForTesting(
            List.of(
                Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG), Ingredient.EMPTY,
                Ingredient.of(Items.OAK_LOG), Ingredient.of(Items.FURNACE), Ingredient.of(Items.OAK_LOG),
                Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG), Ingredient.EMPTY
            ),
            3,
            3,
            Lists.immutable.of(
                new PlanAction.InputBinding(InputContent.fromItemStack(stack(Items.FURNACE, 1), 1), 16),
                new PlanAction.InputBinding(InputContent.fromItemTag(ItemTags.LOGS, 1), 64)
            )
        );

        assertEquals(5, previewSlots.size());
        assertTagPreviewSlot(previewSlots, 1);
        assertTagPreviewSlot(previewSlots, 3);
        assertExactPreviewSlot(previewSlots, 4);
        assertTagPreviewSlot(previewSlots, 5);
        assertTagPreviewSlot(previewSlots, 7);
    }

    @Test
    void furnacePlacementForEightCraftBatchUsesEightPerOccupiedSlot(MinecraftServer server) {
        TestAccess access = new TestAccess();
        access.put(10, stack(Items.COBBLESTONE, 64));

        var placements = CraftingTableExecutionDriver.resolvePlacementsForTesting(
            List.of(
                Ingredient.of(Items.COBBLESTONE), Ingredient.of(Items.COBBLESTONE), Ingredient.of(Items.COBBLESTONE),
                Ingredient.of(Items.COBBLESTONE), Ingredient.EMPTY, Ingredient.of(Items.COBBLESTONE),
                Ingredient.of(Items.COBBLESTONE), Ingredient.of(Items.COBBLESTONE), Ingredient.of(Items.COBBLESTONE)
            ),
            3,
            3,
            context(access),
            Lists.immutable.of(
                new PlanAction.InputBinding(
                    InputContent.fromItemStack(stack(Items.COBBLESTONE, 1), 1),
                    64
                )
            )
        );

        assertEquals(8, placements.size());
        for (SlotFlow.Placement placement : placements) {
            assertEquals(8L, placement.amount());
            assertEquals(Items.COBBLESTONE, placement.template().getItem());
        }
    }

    private static SlotFlow.Context context(TestAccess access) {
        return new SlotFlow.Context(layout(), access, null, null);
    }

    private static void assertPlacementAmount(
        ImmutableList<SlotFlow.Placement> placements,
        int slotId,
        Item item,
        long expectedAmount
    ) {
        SlotFlow.Placement placement = placements.stream()
            .filter(candidate -> candidate.slot() instanceof SlotLayout.Reference.Index(int index) && index  == slotId)
            .findFirst()
            .orElseThrow();
        assertEquals(expectedAmount, placement.amount());
        assertEquals(item, placement.template().getItem());
        assertTrue(placement.input() == null || placement.matches(stack(item, 1)));
    }

    private static void assertTagPreviewSlot(
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots,
        int gridIndex
    ) {
        CraftingTableExecutionDriver.RecipePreviewSlot slot = previewSlots.detect(candidate -> candidate.gridIndex() == gridIndex);
        assertNotNull(slot);
        assertEquals(16L, slot.amount());
        assertTrue(slot.input() instanceof TagStackContent<?> tag && tag.tagKey() == ItemTags.LOGS);
    }

    private static void assertExactPreviewSlot(
        ImmutableList<CraftingTableExecutionDriver.RecipePreviewSlot> previewSlots,
        int gridIndex
    ) {
        CraftingTableExecutionDriver.RecipePreviewSlot slot = previewSlots.detect(candidate -> candidate.gridIndex() == gridIndex);
        assertNotNull(slot);
        assertEquals(16L, slot.amount());
        assertNotNull(slot.input());
        assertTrue(slot.input().satisfy(ContentStack.fromStack(stack(Items.FURNACE, 1))));
    }

    private static SlotLayout layout() {
        SlotLayout.Builder builder = SlotLayout.builder();
        for (int index = 0; index < 9; index++) {
            builder.add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input/" + index), index),
                new SlotLayout.Reference.Index(index + 1)
            );
        }
        for (int index = 0; index < 9; index++) {
            builder.add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory/" + index), index),
                new SlotLayout.Reference.Index(index + 10)
            );
        }
        return builder.build();
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(count);
        return stack;
    }

    private static final class TestAccess implements SlotAccess {
        private final Map<SlotLayout.Reference, ItemStack> stacks = new ConcurrentHashMap<>();

        void put(int slotId, ItemStack stack) {
            stacks.put(new SlotLayout.Reference.Index(slotId), stack.copy());
        }

        @Override
        public State state() {
            return new State(true, testInteractionId, ExecutionSideRequirement.environmentAgnostic);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            ItemStack stack = stacks.get(slot);
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }
}
