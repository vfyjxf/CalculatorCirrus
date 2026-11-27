package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-23: minimap viewport drag camera inversion and hover trace partitioning.
 */
class SpatialDagMinimapTest {

    private static final int minimapW = DagMinimapWidget.wideWidth;
    private static final int minimapH = DagMinimapWidget.wideHeight;
    private static final int viewW = 800;
    private static final int viewH = 400;
    private static final float padding = DagMinimapWidget.padding;
    private static final float contentMinX = 0.0f;
    private static final float contentMinY = 0.0f;
    private static final float contentMaxX = 400.0f;
    private static final float contentMaxY = 200.0f;

    @Test
    void whiteFrameDragMapsProportionallyToCamera() {
        double scale = DagViewport
                .minimapContentScale(minimapW, minimapH, contentMinX, contentMinY, contentMaxX, contentMaxY, padding);
        double contentWidth = (contentMaxX - contentMinX) * scale;

        DagViewport center = new DagViewport();
        center.centerOnMinimapPoint(
            padding + contentWidth * 0.5,
            padding,
            minimapW,
            minimapH,
            contentMinX,
            contentMinY,
            contentMaxX,
            contentMaxY,
            viewW,
            viewH,
            padding
        );
        assertEquals(
            200.0f - viewW * 0.5,
            center.camX(),
            0.001f,
            "u=0.5 puts the content midpoint under the viewport center"
        );
        assertEquals(0.0f - viewH * 0.5, center.camY(), 0.5f);

        DagViewport quarter = new DagViewport();
        quarter.centerOnMinimapPoint(
            padding + contentWidth * 0.25,
            padding,
            minimapW,
            minimapH,
            contentMinX,
            contentMinY,
            contentMaxX,
            contentMaxY,
            viewW,
            viewH,
            padding
        );
        assertEquals(100.0f - viewW * 0.5, quarter.camX(), 0.001f);

        assertEquals(100.0f, center.camX() - quarter.camX(), 0.001f, "camera moves 1:1 with the dragged world point");
    }

    @Test
    void draggingBeyondTheMinimapClampsToContentBounds() {
        DagViewport viewport = new DagViewport();
        viewport.centerOnMinimapPoint(
            -500.0,
            -500.0,
            minimapW,
            minimapH,
            contentMinX,
            contentMinY,
            contentMaxX,
            contentMaxY,
            viewW,
            viewH,
            padding
        );
        assertEquals(contentMinX - viewW * 0.5, viewport.camX(), 0.001f);
        viewport.centerOnMinimapPoint(
            4000.0,
            4000.0,
            minimapW,
            minimapH,
            contentMinX,
            contentMinY,
            contentMaxX,
            contentMaxY,
            viewW,
            viewH,
            padding
        );
        assertEquals(contentMaxX - viewW * 0.5, viewport.camX(), 0.001f);
    }

    @Test
    void hoveringTheMiddleNodePartitionsUpstreamDownstreamAndUnrelated() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.tracePlan(), DagLayoutMode.horizontalLr);
        Set<String> stepIds = new TreeSet<>();
        layout.nodes().forEach(node -> stepIds.add(node.stepId()));
        DagSelectionMath.TraceSets trace = DagSelectionMath.traceSets("c1", layout.edges(), stepIds);

        assertEquals(Set.of("x0"), trace.upstream());
        assertEquals(Set.of("c2"), trace.downstream());
        assertEquals(Set.of("c3"), trace.unrelated());

        DagSelectionMath.TraceSets leaf = DagSelectionMath.traceSets("c2", layout.edges(), stepIds);
        assertEquals(Set.of("x0", "c1"), leaf.upstream());
        assertTrue(leaf.downstream().isEmpty());
        assertTrue(leaf.unrelated().contains("c3"));
    }

    @Test
    void visibleWorldRectFollowsZoom() {
        DagViewport viewport = new DagViewport(0.0f, 0.0f, 2.0f);
        assertEquals(0.0f, viewport.visibleWorldX0(), 0.001f);
        assertEquals(viewW / 2.0f, viewport.visibleWorldX1(viewW), 0.001f);
        assertEquals(viewH / 2.0f, viewport.visibleWorldY1(viewH), 0.001f);
        viewport.zoomAt(viewW * 0.5, viewH * 0.5, 3.0);
        assertEquals(DagViewport.maxZoom, viewport.zoom(), 0.0001f, "zoom clamps to 2.40x");
    }
}
