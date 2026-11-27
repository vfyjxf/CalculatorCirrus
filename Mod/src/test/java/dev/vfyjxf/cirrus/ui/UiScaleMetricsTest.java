package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cirrus.ui.execution.layout.ExecutionOverlayLayout;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScaleMetricsTest {

    @Test
    void fitWithinNeverExceedsAvailableSpaceAcrossScales() {
        int[] viewports = {180, 240, 270, 320, 427, 480, 640, 1280};
        for (int v : viewports) {
            float fitted = UiScaleMetrics.fitWithin(620f, 240f, v, 8f);
            assertTrue(fitted >= 0f, "fitted must be non-negative for viewport=" + v);
            assertTrue(fitted <= Math.max(0f, v - 16f), "fitted overflowed viewport=" + v + ": " + fitted);
        }
    }

    @Test
    void distributeRowNeverOverflowsAvailableWidth() {
        int[] widths = {60, 90, 113, 140, 210, 280};
        for (int avail : widths) {
            int[] row = UiScaleMetrics.distributeRow(
                avail,
                3,
                new int[]{42, 22, 22, 18},
                new int[]{24, 16, 16, 14},
                new int[]{avail, 44, 44, 38}
            );
            int sum = 3 * 3;
            for (int w : row) {
                assertTrue(w >= 0);
                sum += w;
            }
            assertTrue(sum <= avail, "row overflowed avail=" + avail + " with sum=" + sum);
        }
    }

    @Test
    void workbenchShellLayoutNeverOverflowsAtHighGuiScale() {
        int[][] viewports = {{320, 180}, {427, 240}, {480, 270}, {640, 360}, {1280, 720}};
        for (int[] vp : viewports) {
            int w = vp[0];
            int h = vp[1];
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(w, h);
            assertTrue(layout.leftRail().within(w, h), "leftRail overflowed " + w + "x" + h);
            assertTrue(layout.sessionTabBar().within(w, h), "sessionTabBar overflowed " + w + "x" + h);
            assertTrue(layout.topDock().within(w, h), "topDock overflowed " + w + "x" + h);
            assertTrue(layout.leftDock().within(w, h), "leftDock overflowed " + w + "x" + h);
            assertTrue(layout.stage().within(w, h), "stage overflowed " + w + "x" + h);
            assertTrue(layout.rightDock().within(w, h), "rightDock overflowed " + w + "x" + h);
            assertTrue(layout.bottomStrip().within(w, h), "bottomStrip overflowed " + w + "x" + h);
            assertTrue(layout.commandPalette().within(w, h), "commandPalette overflowed " + w + "x" + h);
            assertTrue(layout.toastStack().within(w, h), "toastStack overflowed " + w + "x" + h);
        }
    }

    @Test
    void executionOverlayBoundsStayInsideViewportAtHighGuiScale() {
        int[][] viewports = {{320, 180, 72}, {427, 240, 125}, {480, 270, 152}, {640, 360, 232}};
        for (int[] vp : viewports) {
            Rect2i bounds = ExecutionOverlayLayout.panelBoundsForStrip(vp[0], vp[1], vp[2], true, 220, 172, 280);
            assertTrue(bounds.getX() >= 0);
            assertTrue(bounds.getY() >= 0);
            assertTrue(bounds.getX() + bounds.getWidth() <= vp[2], "overflowed strip for " + vp[0] + "x" + vp[1]);
            assertTrue(bounds.getY() + bounds.getHeight() <= vp[1], "overflowed height for " + vp[0] + "x" + vp[1]);
        }
        assertEquals(UiScaleMetrics.Density.tight, UiScaleMetrics.classifyWidth(120, 210, 165));
        assertEquals(UiScaleMetrics.Density.compact, UiScaleMetrics.classifyWidth(180, 210, 165));
        assertEquals(UiScaleMetrics.Density.comfortable, UiScaleMetrics.classifyWidth(240, 210, 165));
    }
}
