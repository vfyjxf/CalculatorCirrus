package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class CirrusSegmentedControlWidget<T> extends CompositeWidget<Widget> {
    private final int segmentHeight;
    private final List<Option<T>> options = new ArrayList<>();
    private @Nullable T value;
    private Consumer<T> onChanged = ignored -> {};

    public CirrusSegmentedControlWidget(int height) {
        this.segmentHeight = Math.max(12, height);
        addStyleClass("cirrus-segmented");
        useStyle(flexRow(), gap(2), heightOf(segmentHeight), minWidth(0), flexShrink(0));
    }

    public CirrusSegmentedControlWidget<T> option(T value, Component label, int width) {
        Option<T> option = new Option<>(value, label == null ? Component.empty() : label, width);
        options.add(option);
        addWidget(new Segment(option));
        if (this.value == null) {
            this.value = value;
        }
        return this;
    }

    public CirrusSegmentedControlWidget<T> option(T value, LangEntry label, int width) {
        return option(value, label == null ? Component.empty() : label.component(), width);
    }

    public CirrusSegmentedControlWidget<T> value(T value) {
        this.value = value;
        return this;
    }

    public @Nullable T value() {
        return value;
    }

    public CirrusSegmentedControlWidget<T> onChanged(Consumer<T> onChanged) {
        this.onChanged = onChanged == null ? ignored -> {} : onChanged;
        return this;
    }

    public int optionCount() {
        return options.size();
    }

    private record Option<T>(T value, Component label, int width) {}

    private final class Segment extends Widget {
        private final Option<T> option;

        private Segment(Option<T> option) {
            this.option = option;
            addStyleClass("cirrus-button");
            useStyle(sizeOf(Math.max(18, option.width()), segmentHeight), flexShrink(0));
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                if (!Objects.equals(value, option.value())) {
                    value = option.value();
                    onChanged.accept(value);
                    CirrusUiSound.playClickSound();
                }
                return EventDispatch.consumed;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            boolean selected = Objects.equals(value, option.value());
            CirrusPalette palette = CirrusUiTheme.palette(this);
            CirrusControlChrome
                    .renderButtonBackground(canvas, this, 0, 0, width(), height(), selected, hovered(), true);
            var font = context().font();
            String text = option.label().getString();
            String fitted = font.width(text) <= width() - 6
                    ? text
                    : font.plainSubstrByWidth(text, Math.max(0, width() - 9)).stripTrailing() + "...";
            int textX = Math.max(2, (width() - font.width(fitted)) / 2);
            int textY = CirrusControlChrome.centeredTextY(0, height(), font);
            canvas.drawString(
                fitted,
                textX,
                textY,
                selected || hovered() ? palette.textPrimary() : palette.textMuted(),
                false
            );
        }
    }
}
