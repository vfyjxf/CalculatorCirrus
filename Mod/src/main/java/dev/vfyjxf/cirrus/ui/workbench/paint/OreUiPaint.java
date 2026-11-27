package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.OreUiTextures;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import net.minecraft.network.chat.Component;

/**
 * Unified sharp 90-degree OreUI canvas painting helpers driven solely by active theme tokens.
 */
public final class OreUiPaint {

    public static final int cardInsetTop = 2;
    public static final int cardInsetBottom = 3;
    public static final int cardInsetX = 2;
    public static final int minPillHeight = 13;

    private OreUiPaint() {}

    public static boolean isDark(CirrusPalette palette) {
        int bg = palette.cardSurface();
        int r = (bg >>> 16) & 0xFF;
        int g = (bg >>> 8) & 0xFF;
        int b = bg & 0xFF;
        return (r * 299 + g * 587 + b * 114) < 128_000;
    }

    public static WorkbenchThemeMode themeOf(CirrusPalette palette) {
        return isDark(palette) ? WorkbenchThemeMode.obsidianBlueprint : WorkbenchThemeMode.atelierStone;
    }

    public static int outerInk(CirrusPalette palette) {
        return isDark(palette) ? OreUiTextures.Dark.outerInk : OreUiTextures.Light.outerInk;
    }

    public static int cardFooter(CirrusPalette palette) {
        return isDark(palette) ? OreUiTextures.Dark.cardFooter : OreUiTextures.Light.cardFooter;
    }

    public static int cardDivider(CirrusPalette palette) {
        return isDark(palette) ? OreUiTextures.Dark.cardDivider : OreUiTextures.Light.cardDivider;
    }

    public static int meterTrack(CirrusPalette palette) {
        return isDark(palette) ? OreUiTextures.Dark.meterTrack : OreUiTextures.Light.meterTrack;
    }

    public static int meterBorder(CirrusPalette palette) {
        return isDark(palette) ? OreUiTextures.Dark.meterBorder : OreUiTextures.Light.meterBorder;
    }

    public static void raisedCard(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean hovered,
        boolean selected
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        CirrusThemeTextures textures = CirrusThemeTextures.declared(themeOf(palette));
        VisualTexture surface = selected
                ? textures.cardSelected()
                : hovered ? textures.cardHover() : textures.cardSurface();
        canvas.texture(surface, x, y, width, height);
    }

    public static void raisedCardWithAlpha(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean hovered,
        boolean selected,
        float alpha
    ) {
        if (width <= 0 || height <= 0 || alpha <= 0.01f) {
            return;
        }
        if (alpha < 0.99f) {
            canvas.color(withAlpha(0xFFFFFFFF, alpha));
        }
        raisedCard(canvas, x, y, width, height, palette, hovered, selected);
        if (alpha < 0.99f) {
            canvas.resetColor();
        }
    }

    public static void raisedButton(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean down,
        boolean hovered,
        boolean primary,
        boolean danger
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        CirrusThemeTextures textures = CirrusThemeTextures.declared(themeOf(palette));
        VisualTexture surface;
        if (danger) {
            surface = textures.ctaDanger();
        } else if (primary) {
            surface = textures.ctaPrimary();
        } else if (down) {
            surface = textures.buttonDown();
        } else if (hovered) {
            surface = textures.buttonHover();
        } else {
            surface = textures.buttonUp();
        }
        canvas.texture(surface, x, y, width, height);
    }

    public static void selectedToggle(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean hovered
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean dark = isDark(palette);
        int accent = palette.focusRing();
        int face = dark ? withAlpha(accent, hovered ? 0.28f : 0.22f) : withAlpha(accent, hovered ? 0.18f : 0.13f);
        int border = withAlpha(accent, dark ? 0.88f : 0.78f);
        int topSpec = lighten(accent, dark ? 0.42f : 0.30f);
        int ledge = withAlpha(accent, dark ? 0.38f : 0.26f);
        canvas.fill(x, y, width, height, palette.cardSurface());
        canvas.fill(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), face);
        if (width > 2 && height > 3) {
            canvas.fill(x + 1, y + 1, width - 2, 1, withAlpha(topSpec, dark ? 0.45f : 0.55f));
            canvas.fill(x + 1, y + height - 2, width - 2, 1, ledge);
        }
        canvas.strokeRect(x, y, width, height, border, 1);
    }

    public static void popover(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.shadow(x, y, width, height, 3f, 5f, 8f, palette.dropShadow());
        CirrusThemeTextures textures = CirrusThemeTextures.declared(themeOf(palette));
        canvas.texture(textures.dockIsland(), x, y, width, height);
        canvas.strokeRect(x, y, width, height, outerInk(palette), 1);
    }

    public static void searchField(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        boolean focused
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        CirrusThemeTextures textures = CirrusThemeTextures.declared(themeOf(palette));
        canvas.texture(textures.recessed(), x, y, width, height);
        if (focused) {
            canvas.strokeRect(x, y, width, height, palette.focusRing(), 1);
        }
    }

    public static void slot(SceneCanvas canvas, int x, int y, int size, CirrusPalette palette) {
        if (size <= 0) {
            return;
        }
        CirrusThemeTextures textures = CirrusThemeTextures.declared(themeOf(palette));
        canvas.texture(textures.slotBase(), x, y, size, size);
    }

    public static void headerBand(
        SceneCanvas canvas,
        int cardX,
        int cardY,
        int cardWidth,
        int bandHeight,
        CirrusPalette palette,
        boolean selected
    ) {
        int inset = (selected && isDark(palette)) ? 2 : 1;
        int innerW = cardWidth - inset * 2;
        if (innerW <= 0 || bandHeight <= 0) {
            return;
        }
        canvas.fill(cardX + inset, cardY + inset, innerW, bandHeight, cardFooter(palette));
        canvas.fill(cardX + inset, cardY + inset + bandHeight, innerW, 1, cardDivider(palette));
    }

    public static void footerBand(
        SceneCanvas canvas,
        int cardX,
        int footerY,
        int cardWidth,
        int footerHeight,
        CirrusPalette palette,
        boolean selected
    ) {
        int inset = (selected && isDark(palette)) ? 2 : 1;
        int innerW = cardWidth - inset * 2;
        if (innerW <= 0 || footerHeight <= 0) {
            return;
        }
        canvas.fill(cardX + inset, footerY, innerW, 1, cardDivider(palette));
        if (footerHeight > 1) {
            canvas.fill(cardX + inset, footerY + 1, innerW, footerHeight - 1, cardFooter(palette));
        }
    }

    public static void flatMeter(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        double fraction,
        int fillColor,
        CirrusPalette palette
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.fill(x, y, width, height, meterBorder(palette));
        if (width <= 2 || height <= 2) {
            return;
        }
        canvas.fill(x + 1, y + 1, width - 2, height - 2, meterTrack(palette));
        double clamped = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
        int fillW = (int) Math.round((width - 2) * clamped);
        if (fillW > 0) {
            canvas.fill(x + 1, y + 1, fillW, height - 2, fillColor);
            if (height >= 5) {
                canvas.fill(x + 1, y + 1, fillW, 1, lighten(fillColor, 0.22f));
            }
        }
    }

    public static int tagPill(
        SceneCanvas canvas,
        int x,
        int y,
        int height,
        Component label,
        int inkColor,
        CirrusPalette palette
    ) {
        int pillH = Math.max(minPillHeight, height);
        int textW = label == null ? 0 : canvas.font().width(label);
        int width = textW + 10;
        int fill = isDark(palette) ? withAlpha(inkColor, 0.20f) : withAlpha(inkColor, 0.12f);
        int border = withAlpha(inkColor, isDark(palette) ? 0.62f : 0.45f);
        canvas.fill(x, y, width, pillH, fill);
        canvas.strokeRect(x, y, width, pillH, border, 1);
        if (label != null && !label.getString().isEmpty()) {
            canvas.drawString(label, x + 5, ChromeText.centeredY(y, pillH, canvas), inkColor, false);
        }
        return width;
    }

    public static void checkbox(
        SceneCanvas canvas,
        int x,
        int y,
        int size,
        boolean checked,
        boolean hovered,
        CirrusPalette palette
    ) {
        if (size <= 0) {
            return;
        }
        if (checked) {
            raisedButton(canvas, x, y, size, size, palette, false, hovered, true, false);
            int ox = x + Math.max(2, (size - 6) / 2);
            int oy = y + Math.max(2, (size - 2 - 5) / 2);
            int ink = palette.textOnAccent();
            canvas.fill(ox, oy + 2, 1, 2, ink);
            canvas.fill(ox + 1, oy + 3, 1, 2, ink);
            canvas.fill(ox + 2, oy + 3, 1, 2, ink);
            canvas.fill(ox + 3, oy + 2, 1, 2, ink);
            canvas.fill(ox + 4, oy + 1, 1, 2, ink);
            canvas.fill(ox + 5, oy, 1, 2, ink);
        } else {
            searchField(canvas, x, y, size, size, palette, hovered);
        }
    }

    public static int withAlpha(int argb, float alpha) {
        float clamped = Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 1f;
        int base = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(base * clamped)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    public static int lighten(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
