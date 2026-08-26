package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Renders the calculator and execution-overlay toggle buttons next to JEI's
 * bookmark overlay buttons, without mixing into JEI internals.
 *
 * <p>Layout mirrors JEI's {@code BookmarkOverlay.updateBounds}: the bookmark
 * button column sits in the bottom-left corner of the strip left of the open
 * gui (border margin 6, button size 20, gap 2), with JEI's history button 22px
 * right of the bookmark button. Our calculator button follows the history
 * button, the overlay toggle follows the calculator button, so the row reads
 * bookmark / history / calculator / overlay toggle along the bottom edge.
 *
 * <p>Visibility mirrors JEI's own check ("has valid screen properties for this
 * screen") via {@link IJeiRuntime#getScreenHelper()}; on screens where JEI
 * shows no overlay the buttons disappear with it.
 */
public final class JeiBookmarkOverlayButtons {

    // mirrored from JEI's BookmarkOverlay
    private static final int borderMargin = 6;
    private static final int jeiButtonSize = 20;
    private static final int jeiButtonGap = 2;
    private static final int historyButtonX = borderMargin + jeiButtonSize + jeiButtonGap;

    private static boolean calculatorPressed;
    private static boolean overlayPressed;

    private JeiBookmarkOverlayButtons() {
    }

    public static void render(ScreenEvent.Render.Post event) {
        Rect2i calculator = calculatorButtonArea(event.getScreen());
        if (calculator == null) {
            resetPressed();
            return;
        }
        Rect2i overlay = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator);
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        JeiBookmarkOverlayToggleButton.renderCalculator(guiGraphics, calculator, mouseX, mouseY, calculatorPressed);
        JeiBookmarkOverlayToggleButton.renderOverlayToggle(guiGraphics, overlay, mouseX, mouseY, overlayPressed);
        if (JeiBookmarkOverlayToggleButton.contains(calculator, mouseX, mouseY)) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, JeiBookmarkOverlayToggleButton.calculatorTooltip(), mouseX, mouseY);
        } else if (JeiBookmarkOverlayToggleButton.contains(overlay, mouseX, mouseY)) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, JeiBookmarkOverlayToggleButton.overlayTooltip(), mouseX, mouseY);
        }
    }

    public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        Rect2i calculator = calculatorButtonArea(event.getScreen());
        if (calculator == null) {
            resetPressed();
            return;
        }
        Rect2i overlay = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator);
        boolean calculatorHit = JeiBookmarkOverlayToggleButton.contains(calculator, event.getMouseX(), event.getMouseY());
        boolean overlayHit = JeiBookmarkOverlayToggleButton.contains(overlay, event.getMouseX(), event.getMouseY());
        if (!calculatorHit && !overlayHit) {
            resetPressed();
            return;
        }
        calculatorPressed = calculatorHit;
        overlayPressed = overlayHit;
        event.setCanceled(true);
    }

    public static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (!calculatorPressed && !overlayPressed) {
            return;
        }
        Rect2i calculator = calculatorButtonArea(event.getScreen());
        boolean calculatorHit = calculator != null
            && JeiBookmarkOverlayToggleButton.contains(calculator, event.getMouseX(), event.getMouseY());
        boolean overlayHit = calculator != null
            && JeiBookmarkOverlayToggleButton.contains(
                JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(calculator),
                event.getMouseX(),
                event.getMouseY()
            );
        if (calculatorHit && calculatorPressed) {
            activateCalculatorButton();
        } else if (overlayHit && overlayPressed) {
            activateOverlayToggleButton();
        } else {
            resetPressed();
        }
        event.setCanceled(true);
    }

    private static void activateCalculatorButton() {
        resetPressed();
        CirrusUiSound.playClickSound();
        Minecraft.getInstance().setScreen(new CalculatorScreen());
    }

    private static void activateOverlayToggleButton() {
        resetPressed();
        ExecutionOverlayOpenState.toggle();
        CirrusUiSound.playClickSound();
    }

    private static void resetPressed() {
        calculatorPressed = false;
        overlayPressed = false;
    }

    /**
     * The calculator button area for the current screen, or {@code null} when JEI
     * shows no overlay for it (no valid gui properties or no room for the button
     * column in the strip left of the gui).
     */
    private static @Nullable Rect2i calculatorButtonArea(Screen screen) {
        IJeiRuntime runtime = CirrusJeiPlugin.jeiRuntimeOrNull();
        if (runtime == null) {
            return null;
        }
        Optional<IGuiProperties> properties = runtime.getScreenHelper().getGuiProperties(screen);
        if (properties.isEmpty()) {
            return null;
        }
        IGuiProperties gui = properties.get();
        return calculatorButtonArea(Math.max(0, gui.guiLeft()), gui.screenHeight());
    }

    static @Nullable Rect2i calculatorButtonArea(int stripWidth, int screenHeight) {
        int rowWidth = overlayToggleX() + JeiBookmarkOverlayToggleButton.buttonSize;
        if (stripWidth < rowWidth || screenHeight < borderMargin * 2 + jeiButtonSize) {
            return null;
        }
        Rect2i history = new Rect2i(
            historyButtonX,
            screenHeight - borderMargin - jeiButtonSize,
            jeiButtonSize,
            jeiButtonSize
        );
        return JeiBookmarkOverlayToggleButton.calculatorButtonAreaAfterHistory(history);
    }

    private static int overlayToggleX() {
        return historyButtonX + jeiButtonSize + JeiBookmarkOverlayToggleButton.buttonGap
            + JeiBookmarkOverlayToggleButton.buttonSize + JeiBookmarkOverlayToggleButton.buttonGap;
    }
}
