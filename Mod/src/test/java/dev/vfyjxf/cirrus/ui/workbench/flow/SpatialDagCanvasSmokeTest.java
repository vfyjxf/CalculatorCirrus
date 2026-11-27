package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cloudlib.ui.widget.BoxWidget;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless smoke: widgets construct, wire and answer geometry without a live scene.
 */
class SpatialDagCanvasSmokeTest {

    @Test
    void canvasWorksWithoutAHost() {
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create();
        assertTrue(canvas.dagLayout().isEmpty());
        assertTrue(canvas.dragPreviewOffsets().isEmpty());
        assertEquals(DagViewport.defaultZoom, canvas.camera().zoom(), 0.0001f);
        canvas.clearManualOffsets();
        canvas.applySelection(Set.of("c1"));
        assertEquals(Set.of("c1"), canvas.selectedStepIds());
        assertFalse(canvas.actionBar().activeSelection(), "no selection hides the action bar");
    }

    @Test
    void canvasBuildsLayoutAndVisualsFromFakeHost() {
        SpatialDagFixtures.RecordingHost host = new SpatialDagFixtures.RecordingHost(
            SpatialDagFixtures.snapshot(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr, Set.of("c2"))
        );
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().host(host).graph(SpatialDagFixtures.graph());
        assertEquals(3, canvas.dagLayout().nodes().size());
        assertEquals(3, canvas.visuals().size());
        assertNotNull(canvas.visuals().get("c2"));

        canvas.applySelection(Set.of("c1", "c2"));
        assertEquals(1, host.selections.size());
        assertTrue(host.selections.get(0).contains("c2"));
    }

    @Test
    void flowStageSwitchesViewsWithInjectedWidgets() {
        FlowStageWidget stage = FlowStageWidget.create();
        assertEquals(FlowViewMode.spatialDag, stage.currentView());
        assertTrue(stage.dag().visible());

        BoxWidget ledger = BoxWidget.create();
        stage.setViewWidget(FlowViewMode.preparationLedger, ledger);
        assertTrue(ledger.invisible(), "injected views start hidden");

        stage.showView(FlowViewMode.preparationLedger);
        assertEquals(FlowViewMode.preparationLedger, stage.currentView());
        assertTrue(ledger.visible());
        assertFalse(stage.dag().visible());

        stage.showView(FlowViewMode.spatialDag);
        assertTrue(stage.dag().visible());
        assertTrue(ledger.invisible());

        stage.setViewWidget(FlowViewMode.preparationLedger, null);
        assertTrue(stage.injectedViews().isEmpty());
    }

    @Test
    void canvasCyclesLayoutModeThroughTheHost() {
        SpatialDagFixtures.RecordingHost host = new SpatialDagFixtures.RecordingHost(
            SpatialDagFixtures.snapshot(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr, Set.of())
        );
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().host(host);
        canvas.cycleLayoutMode();
        assertEquals(DagLayoutMode.verticalTb, host.layoutModes.get(0));
    }
}
