package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.execution.layout.ExecutionOverlayLayout;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayLayoutTest {

    @Test
    void jeiPanelMirrorsBookmarkOverlayAreaAndLeavesBottomControlSpace() {
        Rect2i bounds = ExecutionOverlayLayout.panelBoundsForStrip(640, 360, 232, true, 220, 172, 280);

        assertEquals(6, bounds.getX());
        assertEquals(6, bounds.getY());
        assertEquals(220, bounds.getWidth());
        assertEquals(326, bounds.getHeight());
        assertEquals(332, bounds.getY() + bounds.getHeight());
    }

    @Test
    void jeiPanelNeverSpillsPastContainerGuiLeftOnNarrowStrip() {
        Rect2i bounds = ExecutionOverlayLayout.panelBoundsForStrip(427, 240, 125, true, 113, 172, 280);

        assertEquals(6, bounds.getX());
        assertEquals(113, bounds.getWidth());
        assertTrue(bounds.getX() + bounds.getWidth() <= 125 - ExecutionOverlayLayout.jeiRightGap);
    }

    @Test
    void jeiExclusionBoundsClaimFullBookmarkStripAboveBottomButtons() {
        Rect2i panel = new Rect2i(6, 6, 220, 326);
        Rect2i exclusion = ExecutionOverlayLayout.jeiExclusionBounds(null, panel);

        assertEquals(0, exclusion.getX());
        assertEquals(0, exclusion.getY());
        assertEquals(232, exclusion.getWidth());
        assertEquals(332, exclusion.getHeight());
    }

    @Test
    void preferredWidthUsesResponsiveCockpitRange() {
        assertEquals(MainPanel.maxPanelWidth, MainPanel.preferredPanelWidth(400));
        assertEquals(220, MainPanel.preferredPanelWidth(220));
        assertEquals(150, MainPanel.preferredPanelWidth(150));
    }

    @Test
    void panelUsesPreferredWidthWhenWithinConfiguredRange() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(320, 300, true, 150, 148, 162);

        assertEquals(150, bounds.getWidth());
        assertEquals(6, bounds.getX());
    }

    @Test
    void panelWidthNeverShrinksBelowAvailableSpaceOnNarrowScreens() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(120, 300, true, 162, 148, 162);

        assertEquals(108, bounds.getWidth());
        assertTrue(bounds.getX() >= 0);
        assertTrue(bounds.getX() + bounds.getWidth() <= 120);
    }

    @Test
    void nonJeiPanelUsesCompactInsets() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(320, 240, false, 162, 148, 162);

        assertEquals(2, bounds.getX());
        assertEquals(2, bounds.getY());
        assertEquals(162, bounds.getWidth());
        assertEquals(236, bounds.getHeight());
    }
}
