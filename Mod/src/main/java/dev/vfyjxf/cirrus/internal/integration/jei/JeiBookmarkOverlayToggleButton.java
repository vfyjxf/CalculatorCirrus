package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

public final class JeiBookmarkOverlayToggleButton {
    public static final int buttonSize = 20;
    public static final int buttonGap = 2;
    public static final int iconSize = 16;

    private JeiBookmarkOverlayToggleButton() {}

    public static Rect2i calculatorButtonAreaAfterHistory(Rect2i historyButton) {
        return new Rect2i(
            historyButton.getX() + historyButton.getWidth() + buttonGap,
            historyButton.getY(),
            buttonSize,
            buttonSize
        );
    }

    public static Rect2i overlayButtonAreaAfterCalculator(Rect2i calculatorButton) {
        return new Rect2i(
            calculatorButton.getX() + calculatorButton.getWidth() + buttonGap,
            calculatorButton.getY(),
            buttonSize,
            buttonSize
        );
    }

    public static Rect2i iconArea(Rect2i button) {
        return new Rect2i(
            button.getX() + Math.max(0, (button.getWidth() - iconSize) / 2),
            button.getY() + Math.max(0, (button.getHeight() - iconSize) / 2),
            iconSize,
            iconSize
        );
    }

    public static void renderCalculator(
        GuiGraphics guiGraphics,
        Rect2i button,
        int mouseX,
        int mouseY,
        boolean pressed
    ) {
        boolean hovered = contains(button, mouseX, mouseY);
        var icon = pressed ? CirrusTextures.Icon.calculatorJeiPressed : CirrusTextures.Icon.calculatorJei;
        JeiTargetButtonHelper.renderIconButton(guiGraphics, button, hovered, pressed, icon);
    }

    public static void renderOverlayToggle(
        GuiGraphics guiGraphics,
        Rect2i button,
        int mouseX,
        int mouseY,
        boolean pressed
    ) {
        boolean hovered = contains(button, mouseX, mouseY);
        boolean open = ExecutionOverlayOpenState.open();
        var icon = open
                ? pressed
                        ? CirrusTextures.Icon.Execution.executionEnabledJeiPressed
                        : CirrusTextures.Icon.Execution.executionEnabledJei
                : pressed
                        ? CirrusTextures.Icon.Execution.executionDisabledJeiPressed
                        : CirrusTextures.Icon.Execution.executionDisabledJei;
        JeiTargetButtonHelper.renderIconButton(guiGraphics, button, hovered, pressed || open, icon);
    }

    public static Component calculatorTooltip() {
        return CirrusLangs.Calculator.Jei.Open.tooltip.component();
    }

    public static Component overlayTooltip() {
        return (ExecutionOverlayOpenState.open()
                ? CirrusLangs.Execution.Overlay.Control.panelClose
                : CirrusLangs.Execution.Overlay.Control.panelOpen).component();
    }

    public static boolean contains(Rect2i area, double x, double y) {
        return x >= area.getX()
                && x < area.getX() + area.getWidth()
                && y >= area.getY()
                && y < area.getY() + area.getHeight();
    }

    public static boolean hasArea(Rect2i area) {
        return area.getWidth() > 0 && area.getHeight() > 0;
    }
}
