package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLodLevel;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-20: three semantic zoom levels and LOD-2 inline slot hit testing.
 */
class SpatialDagLodTest {

    @Test
    void zoomThresholdsSwitchLodLevels() {
        assertSame(DagLodLevel.macro, DagLodLevel.forZoom(0.35f));
        assertSame(DagLodLevel.blueprint, DagLodLevel.forZoom(0.85f));
        assertSame(DagLodLevel.microRecipe, DagLodLevel.forZoom(1.50f));
        assertSame(DagLodLevel.macro, DagLodLevel.forZoom(DagViewport.minZoom));
        assertSame(DagLodLevel.microRecipe, DagLodLevel.forZoom(DagViewport.maxZoom));
    }

    @Test
    void lod2InlineSlotsAreHitTestableBelowTheCard() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        DagNodeBox node = Objects.requireNonNull(nodeOf(layout, "c2"));
        assertNotNull(node);

        List<DagHitTester.InlineSlot> slots = DagHitTester.inlineSlots(node, 1, 1);
        assertEquals(2, slots.size());
        DagHitTester.InlineSlot input = slots.get(0);
        DagHitTester.InlineSlot output = slots.get(1);
        assertEquals(DagHitTester.SlotSide.input, input.side());
        assertEquals(DagHitTester.SlotSide.output, output.side());
        assertTrue(input.bounds().y() >= node.maxY(), "inline slots live under the card body");
        assertEquals(DagHitTester.slotSize, Math.round(input.bounds().width()));

        DagHitTester.InlineSlot hit = DagHitTester.slotAt(input.bounds().centerX(), input.bounds().centerY(), slots);
        assertEquals(input, hit);
        assertNull(DagHitTester.slotAt(input.bounds().x() - 20.0f, input.bounds().y(), slots));

        DagHitTester.RectF panel = DagHitTester.inlinePanelBounds(node);
        assertTrue(panel.y() >= node.maxY());
        assertEquals(node.width(), panel.width(), 0.001f);
        DagHitTester.RectF machine = DagHitTester.machineBounds(node);
        assertTrue(machine.centerX() > panel.x() && machine.centerX() < panel.maxX());
    }

    @Test
    void lod2SlotsResolveRealRecipeInputsAndOutputs() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        DagNodeBox node = Objects.requireNonNull(nodeOf(layout, "c2"));
        StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer
                .resolve(node.step(), SpatialDagFixtures.graph(), null, false);

        assertEquals(List.of(SpatialDagFixtures.ore), visuals.inputs());
        assertEquals(List.of(SpatialDagFixtures.block), visuals.outputs());
        assertEquals(SpatialDagFixtures.block, visuals.primaryOutput());
        assertEquals("minecraft:furnace", visuals.machineId());

        List<DagHitTester.InlineSlot> slots = DagHitTester.inlineSlots(node, visuals.inputs(), visuals.outputs());
        assertEquals(2, slots.size());
    }

    @Test
    void macroAndBlueprintKeepCardBoundsFromLayout() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        DagNodeBox node = nodeOf(layout, "c1");
        assertNotNull(node);
        assertTrue(node.step() instanceof CraftStep);
        assertEquals(208.0f, node.width(), 0.001f);
        assertEquals(86.0f, node.height(), 0.001f);
        assertFalse(node.expandedLoop());
        assertTrue(node.innerLoopNodes().isEmpty());
    }

    private static @Nullable DagNodeBox nodeOf(PlanDagLayoutResult layout, String stepId) {
        for (DagNodeBox node : layout.nodes()) {
            if (node.stepId().equals(stepId)) {
                return node;
            }
        }
        return null;
    }
}
