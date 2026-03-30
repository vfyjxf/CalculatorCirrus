package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(22, TreeViewWidget.debugBottomStripHeight());
    }

    @Test
    void layerHeaderUsesIconOnlyPresentation() {
        assertEquals(true, TreeViewWidget.debugLayerHeaderUsesIconOnly());
    }
}
