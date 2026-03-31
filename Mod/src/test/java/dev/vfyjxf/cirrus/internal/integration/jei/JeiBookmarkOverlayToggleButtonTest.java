package dev.vfyjxf.cirrus.internal.integration.jei;

import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiBookmarkOverlayToggleButtonTest {

    @Test
    void buttonsSitToRightOfHistoryButtonWithJeiGap() {
        Rect2i history = new Rect2i(28, 140, 20, 20);
        Rect2i calculator = JeiBookmarkOverlayToggleButton.calculatorButtonAreaAfterHistory(history);
        Rect2i overlay = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator);

        assertEquals(50, calculator.getX());
        assertEquals(140, calculator.getY());
        assertEquals(20, calculator.getWidth());
        assertEquals(20, calculator.getHeight());
        assertEquals(72, overlay.getX());
        assertEquals(140, overlay.getY());
        assertEquals(20, overlay.getWidth());
        assertEquals(20, overlay.getHeight());
    }

    @Test
    void executionButtonSitsAfterCalculatorButton() {
        Rect2i calculator = new Rect2i(50, 140, 20, 20);
        Rect2i overlay = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator);

        assertEquals(72, overlay.getX());
        assertEquals(140, overlay.getY());
        assertEquals(20, overlay.getWidth());
        assertEquals(20, overlay.getHeight());
    }

    @Test
    void calculatorButtonSitsAfterHistoryButton() {
        Rect2i history = new Rect2i(28, 140, 20, 20);
        Rect2i calculator = JeiBookmarkOverlayToggleButton.calculatorButtonAreaAfterHistory(history);

        assertEquals(50, calculator.getX());
        assertEquals(140, calculator.getY());
        assertEquals(20, calculator.getWidth());
        assertEquals(20, calculator.getHeight());
    }

    @Test
    void iconIsCenteredInsideBookmarkOverlayButton() {
        Rect2i icon = JeiBookmarkOverlayToggleButton.iconArea(new Rect2i(50, 140, 20, 20));

        assertEquals(52, icon.getX());
        assertEquals(142, icon.getY());
        assertEquals(16, icon.getWidth());
        assertEquals(16, icon.getHeight());
    }

    @Test
    void hitTestUsesHalfOpenButtonBounds() {
        Rect2i button = new Rect2i(50, 140, 20, 20);

        assertTrue(JeiBookmarkOverlayToggleButton.contains(button, 50, 140));
        assertTrue(JeiBookmarkOverlayToggleButton.contains(button, 69, 159));
        assertFalse(JeiBookmarkOverlayToggleButton.contains(button, 70, 159));
        assertFalse(JeiBookmarkOverlayToggleButton.contains(button, 69, 160));
    }
}
