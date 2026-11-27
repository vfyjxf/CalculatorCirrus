package dev.vfyjxf.cirrus.ui.widget.toggle.sw;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.Bg;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.leftOff;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.leftOffHover;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.leftOn;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.leftOnHover;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.rightOff;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.rightOffHover;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.rightOn;
import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Rock.rightOnHover;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class Rock extends Widget {

    private final ToggleStateBinding stateBinding;
    private final Supplier<Tooltip> tooltipSupplier;

    /**
     * `getter` / `setter` use left=false, right=true.
     */
    public Rock(BooleanSupplier getter, Consumer<Boolean> setter, Supplier<Tooltip> tooltipSupplier) {
        this.stateBinding = ToggleStateBinding.of(getter, setter);
        this.tooltipSupplier = tooltipSupplier;
        onHoverTooltip((mouseX, mouseY) -> this.tooltipSupplier.get());

        useStyle(sizeOf(20, 13));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            this.stateBinding.toggle();
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });

        onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
            boolean rightSelected = this.stateBinding.getAsBoolean();
            boolean hover = hovered();

            ImageTexture leftBg = rightSelected
                    ? (hover ? Bg.offHoverLeft : Bg.offLeft)
                    : (hover ? Bg.onHoverLeft : Bg.onLeft);
            ImageTexture rightBg = rightSelected
                    ? (hover ? Bg.offHoverRight : Bg.offRight)
                    : (hover ? Bg.onHoverRight : Bg.onRight);

            ImageTexture leftIcon = rightSelected ? (hover ? leftOffHover : leftOff) : (hover ? leftOnHover : leftOn);
            ImageTexture rightIcon = rightSelected
                    ? (hover ? rightOnHover : rightOn)
                    : (hover ? rightOffHover : rightOff);

            int leftBgX = 0;
            int leftBgY = alignedBottom(leftBg);
            int rightBgX = alignedRight(rightBg);
            int rightBgY = alignedBottom(rightBg);
            int leftIconX = leftBgX + 1;
            int leftIconY = leftBgY + 1;
            int rightIconX = rightBgX + rightBg.width() - rightIcon.width() - 1;
            int rightIconY = rightBgY + 1;

            boolean dark = CirrusUiTheme.isDark(this);
            if (!dark) {
                canvas.texture(leftBg, leftBgX, leftBgY);
                canvas.texture(leftIcon, leftIconX, leftIconY);
                canvas.texture(rightBg, rightBgX, rightBgY);
                canvas.texture(rightIcon, rightIconX, rightIconY);
                return;
            }
            CirrusPalette palette = CirrusUiTheme.palette(this);
            int inactiveBgTint = palette.textMuted();
            int activeBgTint = palette.focusRing();
            canvas.color(rightSelected ? inactiveBgTint : activeBgTint).texture(leftBg, leftBgX, leftBgY);
            canvas.color(rightSelected ? palette.textSecondary() : palette.textPrimary())
                    .texture(leftIcon, leftIconX, leftIconY);
            canvas.color(rightSelected ? activeBgTint : inactiveBgTint).texture(rightBg, rightBgX, rightBgY);
            canvas.color(rightSelected ? palette.textPrimary() : palette.textSecondary())
                    .texture(rightIcon, rightIconX, rightIconY);
            canvas.resetColor();
        });
    }

    private int alignedRight(ImageTexture texture) {
        return width() - texture.width();
    }

    private int alignedBottom(ImageTexture texture) {
        return height() - texture.height();
    }
}
