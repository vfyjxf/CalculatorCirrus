package dev.vfyjxf.cirrus.mixin.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiBookmarkOverlayToggleButton;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.overlay.ScreenPropertiesCache;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(BookmarkOverlay.class)
public abstract class BookmarkOverlayMixin {

    @Shadow
    @Final
    private ScreenPropertiesCache screenPropertiesCache;

    @Shadow
    @Final
    private IconButton historyButton;

    @Unique
    private Rect2i cirrus$calculatorButtonArea = new Rect2i(0, 0, 0, 0);

    @Unique
    private Rect2i cirrus$overlayToggleButtonArea = new Rect2i(0, 0, 0, 0);

    @Unique
    private boolean cirrus$calculatorButtonPressed;

    @Unique
    private boolean cirrus$overlayToggleButtonPressed;

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void cirrus$updateOverlayToggleButtonBounds(IGuiProperties guiProperties, CallbackInfo ci) {
        ImmutableRect2i historyArea = historyButton.getArea();
        Rect2i history = new Rect2i(historyArea.getX(), historyArea.getY(), historyArea.getWidth(), historyArea.getHeight());
        cirrus$calculatorButtonArea = JeiBookmarkOverlayToggleButton.calculatorButtonAreaAfterHistory(history);
        cirrus$overlayToggleButtonArea = JeiBookmarkOverlayToggleButton.overlayButtonAreaAfterCalculator(cirrus$calculatorButtonArea);
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void cirrus$drawOverlayToggleButton(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (cirrus$calculatorButtonVisible()) {
            JeiBookmarkOverlayToggleButton.renderCalculator(guiGraphics, cirrus$calculatorButtonArea, mouseX, mouseY, cirrus$calculatorButtonPressed);
        }
        if (cirrus$overlayToggleButtonVisible()) {
            JeiBookmarkOverlayToggleButton.renderOverlayToggle(guiGraphics, cirrus$overlayToggleButtonArea, mouseX, mouseY, cirrus$overlayToggleButtonPressed);
        }
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"))
    private void cirrus$drawOverlayToggleButtonTooltip(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (cirrus$calculatorButtonVisible() && JeiBookmarkOverlayToggleButton.contains(cirrus$calculatorButtonArea, mouseX, mouseY)) {
            guiGraphics.renderTooltip(minecraft.font, JeiBookmarkOverlayToggleButton.calculatorTooltip(), mouseX, mouseY);
            return;
        }
        if (cirrus$overlayToggleButtonVisible() && JeiBookmarkOverlayToggleButton.contains(cirrus$overlayToggleButtonArea, mouseX, mouseY)) {
            guiGraphics.renderTooltip(minecraft.font, JeiBookmarkOverlayToggleButton.overlayTooltip(), mouseX, mouseY);
        }
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true)
    private void cirrus$appendOverlayToggleButtonInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(new CombinedInputHandler(
            "CirrusBookmarkOverlay",
            cir.getReturnValue(),
            new OverlayToggleButtonInputHandler()
        ));
    }

    @Unique
    private boolean cirrus$calculatorButtonVisible() {
        return screenPropertiesCache.hasValidScreen() && JeiBookmarkOverlayToggleButton.hasArea(cirrus$calculatorButtonArea);
    }

    @Unique
    private boolean cirrus$overlayToggleButtonVisible() {
        return screenPropertiesCache.hasValidScreen() && JeiBookmarkOverlayToggleButton.hasArea(cirrus$overlayToggleButtonArea);
    }

    @Unique
    private void cirrus$activateCalculatorButton() {
        cirrus$calculatorButtonPressed = false;
        cirrus$overlayToggleButtonPressed = false;
        CirrusUiSound.playClickSound();
        Minecraft.getInstance().setScreen(new CalculatorScreen());
    }

    @Unique
    private void cirrus$activateOverlayToggleButton() {
        cirrus$calculatorButtonPressed = false;
        cirrus$overlayToggleButtonPressed = false;
        ExecutionOverlayOpenState.toggle();
        CirrusUiSound.playClickSound();
    }

    @Unique
    private boolean cirrus$isLeftMouse(UserInput input) {
        return input.getKey().getType() == InputConstants.Type.MOUSE && input.getKey().getValue() == 0;
    }

    private class OverlayToggleButtonInputHandler implements IUserInputHandler {
        @Override
        public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
            if (!cirrus$isLeftMouse(input)) {
                cirrus$calculatorButtonPressed = false;
                cirrus$overlayToggleButtonPressed = false;
                return Optional.empty();
            }
            boolean calculatorHit = cirrus$calculatorButtonVisible()
                && JeiBookmarkOverlayToggleButton.contains(cirrus$calculatorButtonArea, input.getMouseX(), input.getMouseY());
            boolean overlayHit = cirrus$overlayToggleButtonVisible()
                && JeiBookmarkOverlayToggleButton.contains(cirrus$overlayToggleButtonArea, input.getMouseX(), input.getMouseY());
            if (!calculatorHit && !overlayHit) {
                cirrus$calculatorButtonPressed = false;
                cirrus$overlayToggleButtonPressed = false;
                return Optional.empty();
            }
            if (input.isSimulate()) {
                cirrus$calculatorButtonPressed = calculatorHit;
                cirrus$overlayToggleButtonPressed = overlayHit;
                return Optional.of(this);
            }
            if (input.getInputType() == InputType.EXECUTE && calculatorHit && cirrus$calculatorButtonPressed) {
                cirrus$activateCalculatorButton();
                return Optional.of(this);
            }
            if (input.getInputType() == InputType.EXECUTE && overlayHit && cirrus$overlayToggleButtonPressed) {
                cirrus$activateOverlayToggleButton();
                return Optional.of(this);
            }
            if (input.getInputType() == InputType.EXECUTE) {
                cirrus$calculatorButtonPressed = false;
                cirrus$overlayToggleButtonPressed = false;
                return Optional.empty();
            }
            return Optional.of(this);
        }

        @Override
        public void unfocus() {
            cirrus$calculatorButtonPressed = false;
            cirrus$overlayToggleButtonPressed = false;
        }
    }
}
