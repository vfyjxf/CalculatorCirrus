package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeViewWidgetContractTest {

    @Test
    void breadcrumbScrollbarThumbKeepsMinimumDraggableWidth() {
        assertEquals(8, TreeViewWidget.computeThumbWidth(24, 640, 48));
    }

    @Test
    void breadcrumbScrollbarThumbScalesWithViewportCoverage() {
        assertEquals(50, TreeViewWidget.computeThumbWidth(100, 200, 100));
    }

    @Test
    void layerButtonsKeepCompactLabelOnlyConvention() {
        assertEquals("L0", TreeViewWidget.layerLabel(0));
        assertEquals("L7", TreeViewWidget.layerLabel(7));
    }

    @Test
    void treeViewUsesCompactLayerNavAndBottomStripMetrics() {
        assertEquals(36, TreeViewWidget.debugLayerNavWidth());
        assertEquals(24, TreeViewWidget.debugBottomStripHeight());
        assertEquals(18, TreeViewWidget.debugBreadcrumbScrollbarY(24));
        assertEquals(20, TreeViewWidget.debugLayerHeaderHeight());
        assertEquals(16, TreeViewWidget.debugLayerHeaderIconSize());
    }

    @Test
    void breadcrumbScrollbarOnlyAppearsWhenContentOverflows() {
        assertFalse(TreeViewWidget.debugBreadcrumbScrollbarVisible(80, 80));
        assertFalse(TreeViewWidget.debugBreadcrumbScrollbarVisible(72, 80));
        assertTrue(TreeViewWidget.debugBreadcrumbScrollbarVisible(120, 80));
    }

    @Test
    void layerHeaderUsesIconOnlyPresentation() {
        assertEquals(true, TreeViewWidget.debugLayerHeaderUsesIconOnly());
    }

    @Test
    void layerBarUsesHiddenScrollbarWithOverlayIndicators() {
        assertTrue(TreeViewWidget.debugLayerScrollbarHidden());
        assertEquals(0, TreeViewWidget.debugLayerScrollViewportInset());
        assertEquals(10, TreeViewWidget.debugLayerScrollIndicatorSize());
    }
}
