package dev.vfyjxf.cirrus.ui.widget.toggle.sw;

import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle.Switch.Slide.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class Slide extends Widget {

    private final ToggleStateBinding stateBinding;
    private final Supplier<Tooltip> tooltipSupplier;

    /**
     * `getter` / `setter` use left=false, right=true.
     */
    public Slide(
        BooleanSupplier getter,
        Consumer<Boolean> setter,
        Supplier<Tooltip> tooltipSupplier
    ) {
        this.stateBinding = ToggleStateBinding.of(getter, setter);
        this.tooltipSupplier = tooltipSupplier;
        onHoverTooltip((mouseX, mouseY) -> tooltipSupplier.get());

        useStyle(sizeOf(20, 13));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            stateBinding.toggle();
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });

        onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
            boolean currentState = stateBinding.getAsBoolean();
            ImageTexture texture = textureFor(currentState, hovered());
            canvas.texture(texture, 0, currentState ? 0 : 1);
        });
    }

    static ImageTexture textureFor(boolean state, boolean hover) {
        // Slide assets are authored by visual position: "off" is the right-side knob.
        if (state) {
            return hover ? offHover : off;
        }
        return hover ? onHover : on;
    }
}
