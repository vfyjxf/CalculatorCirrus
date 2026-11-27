package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-21: 3px drag threshold, 8px grid snap, 4px magnetic guides, Alt bypass and Esc restore.
 */
class SpatialDagDragTest {

    @Test
    void dragStartsOnlyAfterThreePixelThreshold() {
        DagDragState state = new DagDragState();
        state.press(DagDragState.Mode.nodeDrag, 100.0, 100.0);
        assertFalse(state.move(102.0, 101.0, 0.0), "2px still counts as a click");
        assertTrue(state.isClick());
        assertTrue(state.move(104.0, 100.0, 16.0), "4px promotes to a drag");
        assertFalse(state.isClick());
        assertTrue(state.dragging());
    }

    @Test
    void gridSnapRoundsToEightPixelsAndAltDisablesIt() {
        assertEquals(16.0f, DagSelectionMath.applyGridSnap(13.0f, DagSelectionMath.grid, true), 0.001f);
        assertEquals(8.0f, DagSelectionMath.applyGridSnap(9.0f, DagSelectionMath.grid, true), 0.001f);
        assertEquals(
            13.0f,
            DagSelectionMath.applyGridSnap(13.0f, DagSelectionMath.grid, false),
            0.001f,
            "Alt keeps the free pixel offset"
        );
        FloatPos snapped = DagSelectionMath.snappedOffset(13.4, -9.6, DagSelectionMath.grid, true);
        assertEquals(16.0, snapped.x, 0.001);
        assertEquals(-8.0, snapped.y, 0.001);
        FloatPos free = DagSelectionMath.snappedOffset(13.4, -9.6, DagSelectionMath.grid, false);
        assertEquals(13.4, free.x, 0.001);
    }

    @Test
    void magneticGuidesTriggerWithinFourPixels() {
        DagHitTester.RectF dragged = new DagHitTester.RectF(0.0f, 0.0f, 40.0f, 20.0f);
        DagHitTester.RectF near = new DagHitTester.RectF(43.0f, 120.0f, 40.0f, 20.0f);
        DagHitTester.RectF far = new DagHitTester.RectF(68.0f, 120.0f, 40.0f, 20.0f);

        List<DagSelectionMath.GuideLine> nearGuides = DagSelectionMath
                .guideLines(dragged, List.of(near), DagSelectionMath.guideThreshold);
        assertFalse(nearGuides.isEmpty(), "a 3px gap must produce a magnetic guide");
        assertTrue(nearGuides.stream().allMatch(DagSelectionMath.GuideLine::vertical));
        assertEquals(43.0f, nearGuides.get(0).position(), 0.001f);

        List<DagSelectionMath.GuideLine> farGuides = DagSelectionMath
                .guideLines(dragged, List.of(far), DagSelectionMath.guideThreshold);
        assertTrue(farGuides.isEmpty(), "28px apart never snaps");

        FloatPos snapped = DagSelectionMath
                .magneticSnap(dragged, List.of(near), new FloatPos(0.0, 0.0), DagSelectionMath.guideThreshold);
        assertEquals(3.0, snapped.x, 0.001, "the offset absorbs the 3px residual so edges align");

        FloatPos untouched = DagSelectionMath
                .magneticSnap(dragged, List.of(far), new FloatPos(0.0, 0.0), DagSelectionMath.guideThreshold);
        assertEquals(0.0, untouched.x, 0.001);
    }

    @Test
    void escCancelsDragAndRestoresOriginalOffsets() {
        DagDragState state = new DagDragState();
        state.press(DagDragState.Mode.nodeDrag, 10.0, 10.0);
        state.anchor("c2");
        state.captureOriginal("c2", new FloatPos(4.0, 8.0));
        state.move(40.0, 40.0, 32.0);
        state.previewOffset("c2", new FloatPos(40.0, 48.0));
        assertTrue(state.escape());
        assertEquals(DagDragState.Mode.none, state.mode());
        FloatPos restored = state.previewOffset("c2");
        assertNotNull(restored);
        assertEquals(4.0, restored.x, 0.001);
        assertEquals(8.0, restored.y, 0.001);
        assertTrue(state.previewOffsets().containsKey("c2"), "cancel keeps the pre-drag offset");
        assertFalse(state.escape(), "a second Esc falls through to the screen");
    }

    @Test
    void releaseWithoutThresholdIsAClickNotADrag() {
        DagDragState state = new DagDragState();
        state.press(DagDragState.Mode.panning, 5.0, 5.0);
        state.move(7.0, 6.0, 8.0);
        assertFalse(state.dragging());
        assertTrue(state.release());
        assertEquals(DagDragState.Mode.none, state.mode());
        assertNull(state.anchorStepId());
    }

    @Test
    void autoLayoutShortcutClearsManualOffsetsOnTheHost() {
        SpatialDagFixtures.RecordingHost host = new SpatialDagFixtures.RecordingHost(
            SpatialDagFixtures.snapshot(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr, Set.of())
        );
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().host(host);
        canvas.clearManualOffsets();
        assertEquals(1, host.clearOffsetsCount);
    }

    @Test
    void repeatedNodeDragPreservesOriginalOffsetWithoutDoubleCounting() {
        DagDragState state = new DagDragState();
        // First drag commits an offset of (48, 24)
        state.press(DagDragState.Mode.nodeDrag, 100.0, 100.0);
        state.anchor("c2");
        state.captureOriginal("c2", new FloatPos(0.0, 0.0));
        assertTrue(state.move(148.0, 124.0, 64.0));
        state.previewOffset("c2", new FloatPos(48.0, 24.0));
        assertTrue(state.release());

        // Second drag starts from existing (48, 24) — press() clears previous map before captureOriginal()
        state.press(DagDragState.Mode.nodeDrag, 200.0, 200.0);
        state.anchor("c2");
        state.captureOriginal("c2", new FloatPos(48.0, 24.0));
        FloatPos base = state.originalOffsets().get("c2");
        assertNotNull(base);
        assertEquals(48.0, base.x, 0.001);
        assertEquals(24.0, base.y, 0.001);
        assertTrue(state.move(216.0, 208.0, 64.0));
        FloatPos total = new FloatPos(64.0, 32.0);
        FloatPos visualDelta = new FloatPos(total.x - base.x, total.y - base.y);
        assertEquals(16.0, visualDelta.x, 0.001, "visual delta must not double-count base offset");
        assertEquals(8.0, visualDelta.y, 0.001, "visual delta must not double-count base offset");
    }
}
