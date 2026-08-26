package dev.vfyjxf.cirrus.internal.integration.jei;

import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JeiBookmarkOverlayButtonsTest {

    @Test
    void buttonsLineUpWithJeiBottomLeftButtonColumn() {
        // JEI layout: strip left of the gui, border margin 6, button 20x20, gap 2.
        // bookmark x=6, history x=28, calculator x=50, overlay toggle x=72, y=screenHeight-26.
        Rect2i calculator = JeiBookmarkOverlayButtons.calculatorButtonArea(200, 300);

        assertNotNull(calculator);
        assertEquals(50, calculator.getX());
        assertEquals(274, calculator.getY());
        assertEquals(20, calculator.getWidth());
        assertEquals(20, calculator.getHeight());

        Rect2i overlay = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator);
        assertEquals(72, overlay.getX());
        assertEquals(274, overlay.getY());
    }

    @Test
    void buttonsHideWhenStripIsTooNarrowForTheRow() {
        // row needs bookmark(6..26) history(28..48) calculator(50..70) toggle(72..92)
        assertNull(JeiBookmarkOverlayButtons.calculatorButtonArea(91, 300));
        assertNull(JeiBookmarkOverlayButtons.calculatorButtonArea(40, 300));
        assertNotNull(JeiBookmarkOverlayButtons.calculatorButtonArea(92, 300));
    }

    @Test
    void buttonsHideOnTooShortScreens() {
        assertNull(JeiBookmarkOverlayButtons.calculatorButtonArea(200, 31));
        assertNotNull(JeiBookmarkOverlayButtons.calculatorButtonArea(200, 32));
    }
}
