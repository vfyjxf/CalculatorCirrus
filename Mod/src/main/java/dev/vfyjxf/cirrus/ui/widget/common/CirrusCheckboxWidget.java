package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
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
        CirrusControlChrome.renderButtonBackground(canvas, 0, 0, width(), height(), false, hovered(), true);
        var icon = checked
            ? hovered() ? CirrusTextures.Toggle.Checked.selectHover : CirrusTextures.Toggle.Checked.select
            : hovered() ? CirrusTextures.Toggle.Checked.unselectHover : CirrusTextures.Toggle.Checked.unselect;
        int iconWidth = Math.min(icon.width(), Math.max(1, width() - iconPadding));
        int iconHeight = Math.min(icon.height(), Math.max(1, height() - iconPadding));
        canvas.texture(
            icon,
            Math.max(0, CirrusControlChrome.centeredIconX(width(), iconWidth)),
            Math.max(0, CirrusControlChrome.centeredCheckboxIconY(height(), iconHeight)),
            iconWidth,
            iconHeight
        );
    }
}
