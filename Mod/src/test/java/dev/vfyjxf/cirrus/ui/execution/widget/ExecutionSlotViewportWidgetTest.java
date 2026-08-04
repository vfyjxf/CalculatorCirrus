package dev.vfyjxf.cirrus.ui.execution.widget;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionSlotViewportWidgetTest {

    @Test
    void viewportDoesNotClipSlotPixelsWhenScrollbarIsHidden() {
        ExecutionSlotViewportWidget viewport = new ExecutionSlotViewportWidget(3, 1);
        viewport.setSlots(List.of(new ExecutionSlotWidget(), new ExecutionSlotWidget(), new ExecutionSlotWidget()));

        assertEquals(0, viewport.debugViewportInsetTop());
        assertEquals(0, viewport.debugViewportInsetRight());
        assertEquals(0, viewport.debugViewportInsetBottom());
        assertEquals(0, viewport.debugViewportInsetLeft());
    }

    @Test
    void viewportDoesNotClipSlotPixelsWhenScrollbarIsVisible() {
        ExecutionSlotViewportWidget viewport = new ExecutionSlotViewportWidget(3, 1);
        viewport.setSlots(List.of(
            new ExecutionSlotWidget(),
            new ExecutionSlotWidget(),
            new ExecutionSlotWidget(),
            new ExecutionSlotWidget()
        ));

        assertTrue(viewport.debugShowsScrollbar());
        assertEquals(0, viewport.debugViewportInsetTop());
        assertEquals(0, viewport.debugViewportInsetRight());
        assertEquals(0, viewport.debugViewportInsetBottom());
        assertEquals(0, viewport.debugViewportInsetLeft());
    }
}
