package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class CirrusButtonWidget extends Widget {
    private static final int labelPadding = 10;
    private static final int iconLabelGap = 4;
    private static final int minLabelButtonWidth = 20;

    private @Nullable Supplier<? extends SizedTexture> iconSupplier;
    private @Nullable Supplier<? extends SizedTexture> hoverIconSupplier;
    private @Nullable Component label;
    private @Nullable Consumer<InputContext> action;
    private @Nullable Supplier<Tooltip> tooltipSupplier;
    private BooleanSupplier selectedSupplier = () -> false;
    private final int baseWidth;
    private @Nullable Integer explicitTextColor;
    private int preferredMinWidth;
    private boolean enabled = true;
    private boolean pressed;
    private boolean clickSound;
    private boolean compressLabelToBaseWidth;

    public CirrusButtonWidget(int width, int height) {
        this.baseWidth = width;
        this.preferredMinWidth = width;
        addStyleClass("cirrus-button");
        useStyle(sizeOf(width, height));
        onMouseClicked((input, context) -> {
            if (!enabled || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            pressed = true;
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            pressed = false;
            return EventDispatch.pass;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!enabled || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            if (action != null) {
                action.accept(input);
            }
            if (clickSound) {
                CirrusUiSound.playClickSound();
            }
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseLeave((mouseX, mouseY, context) -> pressed = false);
        onHoverTooltip((mouseX, mouseY) -> tooltipSupplier == null ? null : tooltipSupplier.get());
    }

    public CirrusButtonWidget icon(@Nullable SizedTexture icon) {
        this.iconSupplier = icon == null ? null : () -> icon;
        this.hoverIconSupplier = null;
        updatePreferredMinWidth();
        return this;
    }

    public CirrusButtonWidget icon(@Nullable SizedTexture icon, @Nullable SizedTexture hoverIcon) {
        this.iconSupplier = icon == null ? null : () -> icon;
        this.hoverIconSupplier = icon == null || hoverIcon == null ? null : () -> hoverIcon;
        updatePreferredMinWidth();
        return this;
    }

    public CirrusButtonWidget iconSupplier(@Nullable Supplier<? extends SizedTexture> iconSupplier) {
        this.iconSupplier = iconSupplier;
        this.hoverIconSupplier = null;
        updatePreferredMinWidth();
        return this;
    }

    public CirrusButtonWidget label(@Nullable Component label) {
        this.label = label;
        updatePreferredMinWidth();
        return this;
    }

    public CirrusButtonWidget label(@Nullable LangEntry label, Object... args) {
        return label(label == null ? null : label.component(args));
    }

    public CirrusButtonWidget textColor(int textColor) {
        this.explicitTextColor = textColor;
        return this;
    }

    public int resolvedTextColor() {
        CirrusPalette palette = CirrusUiTheme.palette(this);
        if (!enabled) {
            return palette.textDisabled();
        }
        return explicitTextColor != null ? explicitTextColor : palette.textPrimary();
    }

    public CirrusButtonWidget selected(BooleanSupplier selectedSupplier) {
        this.selectedSupplier = selectedSupplier == null ? () -> false : selectedSupplier;
        return this;
    }

    public CirrusButtonWidget enabled(boolean enabled) {
        this.enabled = enabled;
        setActive(enabled);
        setInteractive(enabled);
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public CirrusButtonWidget onPress(@Nullable Consumer<InputContext> action) {
        this.action = action;
        return this;
    }

    public CirrusButtonWidget onPress(@Nullable Runnable action) {
        this.action = action == null ? null : ignored -> action.run();
        return this;
    }

    public CirrusButtonWidget tooltip(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public CirrusButtonWidget clickSound(boolean clickSound) {
        this.clickSound = clickSound;
        return this;
    }

    public CirrusButtonWidget compressLabelToBaseWidth(boolean compressLabelToBaseWidth) {
        this.compressLabelToBaseWidth = compressLabelToBaseWidth;
        updatePreferredMinWidth();
        return this;
    }

    public boolean usesStandardButtonTextures() {
        return true;
    }

    public int preferredMinWidth() {
        return preferredMinWidth;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        boolean selected = selectedSupplier.getAsBoolean();
        boolean down = pressed || selected;
        CirrusControlChrome.renderButtonBackground(canvas, this, 0, 0, width(), height(), down, hovered(), enabled);

        SizedTexture rawIcon = currentIcon();
        SizedTexture icon = rawIcon == null ? null : CirrusThemeIcons.resolveSized(rawIcon, this);
        int iconOffset = 0;
        if (icon != null) {
            int iconX;
            int iconY = CirrusControlChrome.centeredIconY(height(), icon.height(), down);
            if (label == null) {
                iconX = CirrusControlChrome.centeredIconX(width(), icon.width());
            } else {
                iconX = 4;
                iconOffset = icon.width() + iconLabelGap;
            }
            CirrusPalette palette = CirrusUiTheme.palette(this);
            boolean light = !OreUiPaint.isDark(palette);
            if (enabled) {
                if (light) {
                    canvas.color(OreUiPaint.lighten(palette.textSecondary(), 0.08f))
                            .texture(icon, iconX, iconY, icon.width(), icon.height());
                    canvas.resetColor();
                } else {
                    canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
                }
            } else {
                int disabledTint = light ? OreUiPaint.withAlpha(palette.textMuted(), 0.58f) : 0xAA_FFFFFF;
                canvas.color(disabledTint).texture(icon, iconX, iconY, icon.width(), icon.height());
                canvas.resetColor();
            }
        }

        if (label != null) {
            var font = context().font();
            int availableTextWidth = Math.max(0, width() - iconOffset - 4);
            Component renderedLabel = fitLabel(font, label, availableTextWidth);
            int textWidth = font.width(renderedLabel);
            int textX = iconOffset == 0
                    ? (width() - textWidth) / 2
                    : iconOffset + (width() - iconOffset - textWidth) / 2;
            int textY = CirrusControlChrome.centeredTextY(0, height(), font) + (down ? 1 : 0);
            canvas.text(renderedLabel, Math.max(1, textX), textY, resolvedTextColor(), false);
        }
    }

    private void updatePreferredMinWidth() {
        int preferred = baseWidth;
        if (label != null && !compressLabelToBaseWidth) {
            preferred = Math.max(preferred, preferredLabelButtonWidth(label, currentIcon()));
        }
        preferredMinWidth = preferred;
        useStyle(minWidth(preferredMinWidth));
    }

    public static int preferredLabelButtonWidth(Component label) {
        return preferredLabelButtonWidth(label, null);
    }

    private static int preferredLabelButtonWidth(Component label, @Nullable SizedTexture icon) {
        int width = Math
                .max(minLabelButtonWidth, estimatedTextWidth(label == null ? "" : label.getString()) + labelPadding);
        if (icon != null) {
            width += icon.width() + iconLabelGap;
        }
        return width;
    }

    private static int estimatedTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            width += estimatedGlyphWidth(codePoint);
            i += Character.charCount(codePoint);
        }
        return width;
    }

    private static int estimatedGlyphWidth(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return 4;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
            return 9;
        }
        return 6;
    }

    private @Nullable SizedTexture currentIcon() {
        if (iconSupplier == null) {
            return null;
        }
        if (enabled && hovered() && hoverIconSupplier != null) {
            return hoverIconSupplier.get();
        }
        return iconSupplier.get();
    }

    public static int iconYOffset() {
        return CirrusControlChrome.iconYOffset();
    }

    private static Component fitLabel(Font font, Component label, int maxWidth) {
        if (font.width(label) <= maxWidth) {
            return label;
        }
        if (maxWidth <= font.width("...")) {
            return Component.literal("");
        }
        String text = label.getString();
        String prefix = font.plainSubstrByWidth(text, maxWidth - font.width("...")).stripTrailing();
        return Component.literal(prefix + "...");
    }
}
