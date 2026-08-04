package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

public final class CirrusText {
    private CirrusText() {
    }

    public static LabelWidget primary(Component text) {
        return label(text, textPrimary);
    }

    public static LabelWidget primary(LangEntry text, Object... args) {
        return label(text.component(args), textPrimary);
    }

    public static LabelWidget muted(Component text) {
        return label(text, textMuted);
    }

    public static LabelWidget muted(LangEntry text, Object... args) {
        return label(text.component(args), textMuted);
    }

    public static LabelWidget label(Component text, int color) {
        return LabelWidget.of(text)
            .setColor(color)
            .setShadow(false);
    }

    public static LabelWidget fill(Component text, int color) {
        LabelWidget label = label(text, color);
        label.useStyle(flexGrow(1), minWidth(0));
        return label;
    }

    public static LabelWidget fill(LangEntry text, int color, Object... args) {
        return fill(text.component(args), color);
    }

    public static LabelWidget fillPrimary(Component text) {
        return fill(text, textPrimary);
    }

    public static LabelWidget fillPrimary(LangEntry text, Object... args) {
        return fill(text.component(args), textPrimary);
    }

    public static LabelWidget fillMuted(Component text) {
        return fill(text, textMuted);
    }

    public static LabelWidget fillMuted(LangEntry text, Object... args) {
        return fill(text.component(args), textMuted);
    }
}
