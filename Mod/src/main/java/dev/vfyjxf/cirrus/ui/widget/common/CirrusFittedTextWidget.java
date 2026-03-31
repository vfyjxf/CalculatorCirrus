package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class CirrusFittedTextWidget extends Widget {
    public enum Align {
        left,
        center,
        right
    }

    private Component text;
    private int color;
    private boolean shadow;
    private int paddingLeft;
    private int paddingRight;
    private Align align = Align.left;

    public CirrusFittedTextWidget(Component text, int color) {
        this.text = text == null ? Component.empty() : text;
        this.color = color;
    }

    public CirrusFittedTextWidget(LangEntry text, int color, Object... args) {
        this(text == null ? Component.empty() : text.component(args), color);
    }

    public CirrusFittedTextWidget text(Component text) {
        this.text = text == null ? Component.empty() : text;
        return this;
    }

    public CirrusFittedTextWidget text(LangEntry text, Object... args) {
        this.text = text == null ? Component.empty() : text.component(args);
        return this;
    }

    public Component text() {
        return text;
    }

    public CirrusFittedTextWidget textPadding(int left, int right) {
        this.paddingLeft = Math.max(0, left);
        this.paddingRight = Math.max(0, right);
        return this;
    }

    public int textPaddingLeft() {
        return paddingLeft;
    }

    public int textPaddingRight() {
        return paddingRight;
    }

    public CirrusFittedTextWidget align(Align align) {
        this.align = align == null ? Align.left : align;
        return this;
    }

    public Align align() {
        return align;
    }

    public CirrusFittedTextWidget color(int color) {
        this.color = color;
        return this;
    }

    public CirrusFittedTextWidget shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        var font = context().font();
        int available = Math.max(0, width() - paddingLeft - paddingRight);
        String fitted = fit(text.getString(), available);
        int x = switch (align) {
            case left -> paddingLeft;
            case center -> paddingLeft + Math.max(0, available - font.width(fitted)) / 2;
            case right -> paddingLeft + Math.max(0, available - font.width(fitted));
        };
        int y = Math.max(0, (height() - font.lineHeight) / 2);
        canvas.drawString(fitted, x, y, color, shadow);
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        String value = text.getString();
        int available = Math.max(0, width() - paddingLeft - paddingRight);
        if (value.isBlank() || context().font().width(value) <= available) {
            return super.hoverTooltip(mouseX, mouseY);
        }
        Tooltip tooltip = Tooltip.create();
        tooltip.add(text);
        return tooltip;
    }

    private String fit(String value, int available) {
        var font = context().font();
        if (font.width(value) <= available) {
            return value;
        }
        int ellipsisWidth = font.width("...");
        if (available <= ellipsisWidth) {
            return "";
        }
        return font.plainSubstrByWidth(value, Math.max(0, available - ellipsisWidth)).stripTrailing() + "...";
    }
}
