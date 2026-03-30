package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNodeKind;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionOverlayPopupContractTest {

    @Test
    void popupBoundsAllowPartialOverflowWhileKeepingTitleReachable() {
        Rect2i clamped = ExecutionOverlayWidget.clampWorkbenchPopupBounds(
            new Rect2i(-20, 180, 120, 90),
            new Rect2i(4, 32, 300, 170)
        );

        assertEquals(-20, clamped.getX());
        assertEquals(180, clamped.getY());
        assertEquals(120, clamped.getWidth());
        assertEquals(90, clamped.getHeight());
    }

    @Test
    void popupBoundsClampOnlyAfterVisibleGripLeavesWorkbenchBody() {
        Rect2i clamped = ExecutionOverlayWidget.clampWorkbenchPopupBounds(
            new Rect2i(290, 40, 100, 80),
            new Rect2i(4, 32, 300, 170)
        );

        assertEquals(278, clamped.getX());
        assertEquals(40, clamped.getY());
    }

    @Test
    void operationalFocusFallsBackToNearestRecipeForWorkbenchRecipeStrip() {
        ExecutionOverlayNode checkAccess = node("check-access", "Check access", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode checkCapacity = node("check-capacity", "Check capacity", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode recipe = node(
            "craft-planks",
            "Craft Oak Planks",
            ExecutionOverlayNodeKind.RECIPE,
            checkAccess,
            checkCapacity
        );
        ExecutionOverlayNode root = node("root", "Workbench", ExecutionOverlayNodeKind.OPERATIONAL, recipe);

        ExecutionOverlayNode resolved = ExecutionOverlayWidget.resolveWorkbenchRecipeFocus(root, "check-access");

        assertSame(recipe, resolved);
    }

    @Test
    void laneAndStatusRailSeparateRecipeNodesFromOperationalChecks() {
        ExecutionOverlayNode recipeA = node("recipe-a", "Craft Oak Planks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode recipeB = node("recipe-b", "Craft Sticks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode checkAccess = node("check-access", "Check access", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode checkCapacity = node("check-capacity", "Check capacity", ExecutionOverlayNodeKind.OPERATIONAL);
        var stageNodes = Lists.immutable.of(recipeA, checkAccess, checkCapacity, recipeB);

        var laneNodes = ExecutionOverlayWidget.selectWorkbenchLaneRecipeNodes(stageNodes);
        var statusNodes = ExecutionOverlayWidget.selectWorkbenchStatusNodes(stageNodes, 3);

        assertIterableEquals(Lists.mutable.of("recipe-a", "recipe-b"), laneNodes.collect(ExecutionOverlayNode::nodeId).toList());
        assertIterableEquals(Lists.mutable.of("check-access", "check-capacity"), statusNodes.collect(ExecutionOverlayNode::nodeId).toList());
    }

    @Test
    void focusedOperationalCheckIsPrioritizedInStatusRailCollection() {
        ExecutionOverlayNode recipe = node("recipe-a", "Craft Oak Planks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode checkAccess = node("check-access", "Check access", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode checkCapacity = node("check-capacity", "Check capacity", ExecutionOverlayNodeKind.OPERATIONAL);
        var stageNodes = Lists.immutable.of(recipe, checkCapacity, checkAccess);

        var statusNodes = ExecutionOverlayWidget.collectWorkbenchOperationalStatusNodes(stageNodes, checkAccess, 2);

        assertIterableEquals(Lists.mutable.of("check-access", "check-capacity"), statusNodes.collect(ExecutionOverlayNode::nodeId).toList());
    }

    @Test
    void workbenchItemOverlayTextDefersToBuiltInItemDecorations() {
        assertEquals("", ExecutionOverlayWidget.workbenchItemOverlayText(new ItemStack(Items.OAK_LOG, 64), 64));
        assertEquals("", ExecutionOverlayWidget.workbenchItemOverlayText(ItemStack.EMPTY, 0));
    }

    @Test
    void reducedPathTrailKeepsBranchCheckpointsAndCurrentStage() {
        ExecutionOverlayNode focusCheck = node("focus-check", "Check access", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode branchSibling = node("branch-sibling", "Craft Bark", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode stageRoot = node(
            "stage-root",
            "Craft Oak Planks",
            ExecutionOverlayNodeKind.RECIPE,
            focusCheck,
            branchSibling
        );
        ExecutionOverlayNode rootSibling = node("root-sibling", "Craft Stick", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode root = node("root", "Workbench", ExecutionOverlayNodeKind.OPERATIONAL, stageRoot, rootSibling);

        var reduced = ExecutionOverlayWidget.reduceWorkbenchPathTrail(
            Lists.immutable.of(root, stageRoot, focusCheck),
            "stage-root"
        );

        assertIterableEquals(Lists.mutable.of("root", "stage-root"), reduced.collect(ExecutionOverlayNode::nodeId).toList());
    }

    @Test
    void queueRowsStartAtCurrentRecipeAndIgnoreOperationalChecks() {
        ExecutionOverlayNode recipeA = node("recipe-a", "Craft Oak Log", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode checkAccess = node("check-access", "Check access", ExecutionOverlayNodeKind.OPERATIONAL);
        ExecutionOverlayNode recipeB = node("recipe-b", "Craft Oak Planks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode recipeC = node("recipe-c", "Craft Stick", ExecutionOverlayNodeKind.RECIPE);

        var rows = ExecutionOverlayWidget.projectWorkbenchQueueRows(
            Lists.immutable.of(recipeA, checkAccess, recipeB, recipeC),
            "recipe-b",
            3
        );

        assertIterableEquals(Lists.mutable.of("recipe-b", "recipe-c"), rows.collect(row -> row.node().nodeId()).toList());
        assertIterableEquals(
            Lists.mutable.of(
                ExecutionOverlayWidget.WorkbenchQueueRowState.CURRENT,
                ExecutionOverlayWidget.WorkbenchQueueRowState.UPCOMING
            ),
            rows.collect(ExecutionOverlayWidget.WorkbenchQueueRow::state).toList()
        );
    }

    @Test
    void queueRowsMergeAdjacentEquivalentRecipesAndKeepBatchCount() {
        ExecutionOverlayNode recipeA1 = node("recipe-a1", "Craft Oak Planks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode recipeA2 = node("recipe-a2", "Craft Oak Planks", ExecutionOverlayNodeKind.RECIPE);
        ExecutionOverlayNode recipeB = node("recipe-b", "Craft Stick", ExecutionOverlayNodeKind.RECIPE);

        var rows = ExecutionOverlayWidget.projectWorkbenchQueueRows(
            Lists.immutable.of(recipeA1, recipeA2, recipeB),
            "recipe-a2",
            3
        );

        assertIterableEquals(Lists.mutable.of("recipe-a1", "recipe-b"), rows.collect(row -> row.node().nodeId()).toList());
        assertIterableEquals(Lists.mutable.of(2, 1), rows.collect(ExecutionOverlayWidget.WorkbenchQueueRow::batchCount).toList());
        assertIterableEquals(
            Lists.mutable.of(
                ExecutionOverlayWidget.WorkbenchQueueRowState.CURRENT,
                ExecutionOverlayWidget.WorkbenchQueueRowState.UPCOMING
            ),
            rows.collect(ExecutionOverlayWidget.WorkbenchQueueRow::state).toList()
        );
    }

    private static ExecutionOverlayNode node(String id, String label, ExecutionOverlayNodeKind kind, ExecutionOverlayNode... children) {
        return new ExecutionOverlayNode(
            id,
            Component.literal(label),
            Component.literal(kind == ExecutionOverlayNodeKind.RECIPE ? "2/4" : "Quick check"),
            new ItemStack(kind == ExecutionOverlayNodeKind.RECIPE ? Items.OAK_PLANKS : Items.BARREL),
            ExecutionFlowStatus.pending,
            kind,
            0,
            true,
            true,
            Lists.immutable.of(segment(label)),
            Lists.immutable.of(children)
        );
    }

    private static ExecutionOverlayLaneSegment segment(String label) {
        return new ExecutionOverlayLaneSegment("seg:" + label, Component.literal(label), ExecutionFlowStatus.pending, false);
    }
}
