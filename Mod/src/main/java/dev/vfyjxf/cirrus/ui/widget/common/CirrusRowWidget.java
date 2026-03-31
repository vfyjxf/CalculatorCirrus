package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

public class CirrusRowWidget extends WidgetGroup<Widget> {
    public enum Chrome {
        flat,
        selectable
    }

    private final Chrome chrome;
    private BooleanSupplier selectedSupplier = () -> false;
    private @Nullable Consumer<InputContext> action;

    public CirrusRowWidget(int height, int rowGap, Chrome chrome) {
        this.chrome = chrome;
        useStyle(flexRow(), alignItemsCenter(), gap(rowGap), heightOf(height), minWidth(0));
        onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
            if (chrome == Chrome.selectable) {
                boolean selected = selectedSupplier.getAsBoolean();
                CirrusControlChrome.renderButtonBackground(canvas, 0, 0, self.width(), self.height(), selected, hovered(), true);
            } else {
                canvas.texture(CirrusTextures.Background.Outlined.flat, 0, 0, self.width(), self.height());
            }
        });
        onMouseClick((input, clickCount, context) -> {
            if (action == null || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            action.accept(input);
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public static CirrusRowWidget flat(int height, int rowGap) {
        return new CirrusRowWidget(height, rowGap, Chrome.flat);
    }

    public static CirrusRowWidget selectable(int height, int rowGap) {
        return new CirrusRowWidget(height, rowGap, Chrome.selectable);
    }

    public CirrusRowWidget selected(BooleanSupplier selectedSupplier) {
        this.selectedSupplier = selectedSupplier == null ? () -> false : selectedSupplier;
        return this;
    }

    public CirrusRowWidget onPress(Consumer<InputContext> action) {
        this.action = action;
        return this;
    }

    public Chrome chrome() {
        return chrome;
    }
}
