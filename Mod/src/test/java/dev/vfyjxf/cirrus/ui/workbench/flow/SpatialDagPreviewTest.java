package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Co-Pilot hover rehearsal overlay: outline-only preview state on the DAG canvas.
 */
class SpatialDagPreviewTest {

    private static SpatialDagCanvasWidget canvas() {
        SpatialDagFixtures.RecordingHost host = new SpatialDagFixtures.RecordingHost(
            SpatialDagFixtures.snapshot(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr, Set.of())
        );
        return SpatialDagCanvasWidget.create().host(host).graph(SpatialDagFixtures.graph());
    }

    @Test
    void previewStepsSetReadAndClear() {
        SpatialDagCanvasWidget canvas = canvas();
        assertTrue(canvas.dagLayout().nodes().size() >= 3);
        assertNull(canvas.previewedStepIds(), "no preview is active initially");

        canvas.previewSteps(Set.of("c1", "c2"), SpatialDagCanvasWidget.PreviewKind.banRecipe);
        assertEquals(Set.of("c1", "c2"), canvas.previewedStepIds());
        assertSame(SpatialDagCanvasWidget.PreviewKind.banRecipe, canvas.previewKind());

        canvas.previewSteps(Set.of("c3"), SpatialDagCanvasWidget.PreviewKind.ownMachine);
        assertEquals(Set.of("c3"), canvas.previewedStepIds());
        assertSame(SpatialDagCanvasWidget.PreviewKind.ownMachine, canvas.previewKind());

        canvas.clearPreview();
        assertNull(canvas.previewedStepIds());
        assertNull(canvas.previewKind());
    }

    @Test
    void unknownStepIdsAreIgnoredAndPreviewDoesNotTouchSelection() {
        SpatialDagCanvasWidget canvas = canvas();
        canvas.applySelection(Set.of("c1"));

        canvas.previewSteps(Set.of("c2", "missing-step"), SpatialDagCanvasWidget.PreviewKind.buildMachine);
        assertEquals(Set.of("c2"), canvas.previewedStepIds(), "unknown step ids never render");
        assertSame(SpatialDagCanvasWidget.PreviewKind.buildMachine, canvas.previewKind());
        assertEquals(Set.of("c1"), canvas.selectedStepIds(), "a rehearsal never changes selection");

        canvas.previewSteps(Set.of("missing-step"), SpatialDagCanvasWidget.PreviewKind.pickVariant);
        assertEquals(Set.of(), canvas.previewedStepIds(), "an unknown-only preview stays empty");

        canvas.previewSteps(null, SpatialDagCanvasWidget.PreviewKind.treatFree);
        assertNull(canvas.previewedStepIds(), "null step ids clear the preview");
        canvas.previewSteps(Set.of("c1"), null);
        assertNull(canvas.previewedStepIds(), "null kind clears the preview");
    }
}
