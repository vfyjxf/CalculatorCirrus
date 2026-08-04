package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.execution.layout.ExecutionOverlayLayout;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayLayoutTest {

    @Test
    void jeiPanelLeavesBottomControlSpace() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(478, 768, true, 162, 148, 162);

        assertEquals(8, bounds.getX());
        assertEquals(18, bounds.getY());
        assertEquals(162, bounds.getWidth());
        assertEquals(722, bounds.getHeight());
        assertEquals(740, bounds.getY() + bounds.getHeight());
    }

    @Test
    void preferredWidthSnapsToJeiSlotGridWidth() {
        assertEquals(162, MainPanel.preferredPanelWidth(400));
        assertEquals(144, MainPanel.preferredPanelWidth(150));
        assertEquals(108, MainPanel.preferredPanelWidth(111));
    }

    @Test
    void panelUsesPreferredWidthWhenWithinConfiguredRange() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(320, 300, true, 150, 148, 162);

        assertEquals(150, bounds.getWidth());
        assertEquals(8, bounds.getX());
    }

    @Test
    void panelWidthNeverShrinksBelowAvailableSpaceOnNarrowScreens() {
        Rect2i bounds = ExecutionOverlayLayout.panelBounds(120, 300, true, 162, 148, 162);

        assertEquals(110, bounds.getWidth());
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

