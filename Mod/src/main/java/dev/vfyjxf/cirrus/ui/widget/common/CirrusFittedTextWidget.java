package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.ToIntFunction;

public class CirrusFittedTextWidget extends Widget {
    public enum Align {
        left, center, right
    }

    private Component text;
    private int color;
    private @Nullable ToIntFunction<CirrusPalette> paletteColor;
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

    public CirrusFittedTextWidget(Component text, ToIntFunction<CirrusPalette> paletteColor) {
        this.text = text == null ? Component.empty() : text;
        this.paletteColor = paletteColor;
        CirrusPalette fallback = CirrusUiTheme.palette(null);
        this.color = paletteColor == null ? fallback.textPrimary() : paletteColor.applyAsInt(fallback);
    }

    public CirrusFittedTextWidget(LangEntry text, ToIntFunction<CirrusPalette> paletteColor, Object... args) {
        this(text == null ? Component.empty() : text.component(args), paletteColor);
    }

    public static CirrusFittedTextWidget primary(Component text) {
        return new CirrusFittedTextWidget(text, CirrusPalette::textPrimary);
    }

    public static CirrusFittedTextWidget primary(LangEntry text, Object... args) {
        return new CirrusFittedTextWidget(text, CirrusPalette::textPrimary, args);
    }

    public static CirrusFittedTextWidget secondary(Component text) {
        return new CirrusFittedTextWidget(text, CirrusPalette::textSecondary);
    }

    public static CirrusFittedTextWidget muted(Component text) {
        return new CirrusFittedTextWidget(text, CirrusPalette::textMuted);
    }

    public static CirrusFittedTextWidget muted(LangEntry text, Object... args) {
        return new CirrusFittedTextWidget(text, CirrusPalette::textMuted, args);
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
        this.paletteColor = null;
        return this;
    }

    public CirrusFittedTextWidget color(ToIntFunction<CirrusPalette> paletteColor) {
        this.paletteColor = paletteColor;
        if (paletteColor != null) {
            this.color = paletteColor.applyAsInt(CirrusUiTheme.palette(this));
        }
        return this;
    }

    public int resolvedColor() {
        return paletteColor != null ? paletteColor.applyAsInt(CirrusUiTheme.palette(this)) : color;
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
        int y = CirrusControlChrome.centeredTextY(0, height(), font);
        canvas.drawString(fitted, x, y, resolvedColor(), shadow);
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
