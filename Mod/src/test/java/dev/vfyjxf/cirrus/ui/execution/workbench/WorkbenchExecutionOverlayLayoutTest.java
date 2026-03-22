package dev.vfyjxf.cirrus.ui.execution.workbench;

import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchExecutionOverlayLayoutTest {

    @Test
    void overlayBoundsStayPinnedToLeftEdgeWithClampedWidth() {
        assertRect(WorkbenchExecutionOverlayLayout.overlayBounds(240, 240), 8, 12, 132, 192);
        assertRect(WorkbenchExecutionOverlayLayout.overlayBounds(520, 240), 8, 12, 166, 192);
        assertRect(WorkbenchExecutionOverlayLayout.overlayBounds(960, 240), 8, 12, 220, 192);
    }

    @Test
    void controlsRowReservesDedicatedHeightAtPanelBottom() {
        assertEquals(24, WorkbenchExecutionOverlayLayout.controlsHeight);
        assertEquals(164, WorkbenchExecutionOverlayLayout.contentHeight(240));
    }

    private static void assertRect(Rect2i rect, int x, int y, int width, int height) {
        assertEquals(x, rect.getX());
        assertEquals(y, rect.getY());
        assertEquals(width, rect.getWidth());
        assertEquals(height, rect.getHeight());
    }
}
