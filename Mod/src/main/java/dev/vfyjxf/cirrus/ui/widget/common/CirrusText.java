package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import java.util.function.ToIntFunction;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

public final class CirrusText {
    private CirrusText() {}

    public static LabelWidget primary(Component text) {
        return label(text, CirrusPalette::textPrimary);
    }

    public static LabelWidget primary(LangEntry text, Object... args) {
        return label(text.component(args), CirrusPalette::textPrimary);
    }

    public static LabelWidget muted(Component text) {
        return label(text, CirrusPalette::textMuted);
    }

    public static LabelWidget muted(LangEntry text, Object... args) {
        return label(text.component(args), CirrusPalette::textMuted);
    }

    public static LabelWidget label(Component text, int color) {
        return LabelWidget.of(text).setColor(color).setShadow(false);
    }

    public static LabelWidget label(Component text, ToIntFunction<CirrusPalette> paletteColor) {
        LabelWidget widget = LabelWidget.of(text).setColor(paletteColor.applyAsInt(CirrusUiTheme.palette(null)))
                .setShadow(false);
        widget.onMount(
            (scene, context, handle) -> CirrusUiTheme.bindThemeListener(
                widget,
                handle,
                () -> widget.setColor(paletteColor.applyAsInt(CirrusUiTheme.palette(widget)))
            )
        );
        return widget;
    }

    public static LabelWidget fill(Component text, int color) {
        LabelWidget label = label(text, color);
        label.useStyle(flexGrow(1), minWidth(0));
        return label;
    }

    public static LabelWidget fill(Component text, ToIntFunction<CirrusPalette> paletteColor) {
        LabelWidget label = label(text, paletteColor);
        label.useStyle(flexGrow(1), minWidth(0));
        return label;
    }

    public static LabelWidget fill(LangEntry text, int color, Object... args) {
        return fill(text.component(args), color);
    }

    public static LabelWidget fillPrimary(Component text) {
        return fill(text, CirrusPalette::textPrimary);
    }

    public static LabelWidget fillPrimary(LangEntry text, Object... args) {
        return fill(text.component(args), CirrusPalette::textPrimary);
    }

    public static LabelWidget fillMuted(Component text) {
        return fill(text, CirrusPalette::textMuted);
    }

    public static LabelWidget fillMuted(LangEntry text, Object... args) {
        return fill(text.component(args), CirrusPalette::textMuted);
    }
}
