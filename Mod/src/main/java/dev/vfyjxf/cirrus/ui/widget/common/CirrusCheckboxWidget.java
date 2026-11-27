package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class CirrusCheckboxWidget extends Widget {
    private static final int defaultSize = 14;
    private static final int iconPadding = 4;

    private final BooleanSupplier selected;
    private final Consumer<Boolean> setter;

    public CirrusCheckboxWidget(BooleanSupplier selected, Consumer<Boolean> setter) {
        this.selected = selected == null ? () -> false : selected;
        this.setter = setter == null ? ignored -> {} : setter;
        addStyleClass("cirrus-button");
        useStyle(sizeOf(defaultSize, defaultSize));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            this.setter.accept(!this.selected.getAsBoolean());
            CirrusUiSound.playClickSound();
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public boolean selected() {
        return selected.getAsBoolean();
    }

    public static int defaultSize() {
        return defaultSize;
    }

    public static int iconPadding() {
        return iconPadding;
    }

    public static int iconYOffset() {
        return CirrusControlChrome.checkboxIconYOffset();
    }

    public boolean usesStandardButtonTextures() {
        return true;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        boolean checked = selected();
        CirrusPalette palette = CirrusUiTheme.palette(this);
        if (checked) {
            canvas.texture(CirrusUiTheme.textures(this).ctaPrimary(), 0, 0, width(), height());
            int cx = width() / 2;
            int cy = height() / 2;
            canvas.fill(cx - 3, cy, 2, 1, palette.textOnAccent());
            canvas.fill(cx - 2, cy + 1, 2, 1, palette.textOnAccent());
            canvas.fill(cx - 1, cy + 2, 2, 1, palette.textOnAccent());
            canvas.fill(cx, cy, 2, 2, palette.textOnAccent());
            canvas.fill(cx + 1, cy - 1, 2, 2, palette.textOnAccent());
            canvas.fill(cx + 2, cy - 2, 2, 2, palette.textOnAccent());
        } else {
            canvas.texture(CirrusUiTheme.textures(this).recessed(), 0, 0, width(), height());
            if (hovered()) {
                canvas.strokeRect(0, 0, width(), height(), palette.focusRing(), 1);
            }
        }
    }
}
