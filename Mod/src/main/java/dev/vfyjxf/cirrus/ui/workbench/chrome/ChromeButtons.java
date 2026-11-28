package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Shared chrome painting: sharp 90-degree OreUI button plates, theme-routed icons and badges.
 */
public final class ChromeButtons {

    public static final int iconSize = 14;
    public static final int smallIconSize = 10;
    public static final int rowHeight = 18;
    public static final int indicatorWidth = 2;

    private ChromeButtons() {}

    public static void plate(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.raisedButton(canvas, x, y, width, height, palette, false, false, false, false);
    }

    public static void hoverPlate(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.raisedButton(canvas, x, y, width, height, palette, false, true, false, false);
    }

    public static void selectedPlate(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.raisedCard(canvas, x, y, width, height, palette, false, true);
    }

    public static void icon(SceneCanvas canvas, @Nullable VisualTexture texture, int x, int y, int size, int tint) {
        if (texture == null || size <= 0) {
            return;
        }
        canvas.color(tint).texture(texture, x, y, size, size);
        canvas.resetColor();
    }

    public static void icon(
        SceneCanvas canvas,
        @Nullable VisualTexture texture,
        int x,
        int y,
        int size,
        int tint,
        CirrusPalette palette
    ) {
        VisualTexture resolved = texture instanceof ImageTexture image
                ? CirrusThemeIcons.resolve(image, OreUiPaint.themeOf(palette))
                : texture;
        icon(canvas, resolved, x, y, size, tint);
    }

    public static void plus(SceneCanvas canvas, int x, int y, int size, int color) {
        if (size <= 0) {
            return;
        }
        int stroke = (size >= 4 && (size & 1) == 0) ? 2 : 1;
        int offset = (size - stroke) / 2;
        canvas.fill(x, y + offset, size, stroke, color);
        canvas.fill(x + offset, y, stroke, size, color);
    }

    public static void returnArrow(SceneCanvas canvas, int x, int y, int color) {
        // Crisp 8x7 pixel return-key glyph: down stem on right, horizontal shaft, 3-step left arrowhead.
        canvas.fill(x + 6, y, 1, 4, color);
        canvas.fill(x + 1, y + 3, 6, 1, color);
        canvas.fill(x, y + 3, 1, 1, color);
        canvas.fill(x + 1, y + 2, 1, 1, color);
        canvas.fill(x + 2, y + 1, 1, 1, color);
        canvas.fill(x + 1, y + 4, 1, 1, color);
        canvas.fill(x + 2, y + 5, 1, 1, color);
    }

    public static void searchLens(SceneCanvas canvas, int x, int y, int color) {
        // Crisp 8x8 pixel magnifying glass glyph.
        canvas.fill(x + 2, y, 3, 1, color);
        canvas.fill(x + 1, y + 1, 1, 1, color);
        canvas.fill(x + 5, y + 1, 1, 1, color);
        canvas.fill(x, y + 2, 1, 3, color);
        canvas.fill(x + 6, y + 2, 1, 3, color);
        canvas.fill(x + 1, y + 5, 1, 1, color);
        canvas.fill(x + 5, y + 5, 1, 1, color);
        canvas.fill(x + 2, y + 6, 3, 1, color);
        canvas.fill(x + 6, y + 6, 1, 1, color);
        canvas.fill(x + 7, y + 7, 1, 1, color);
    }

    public static void cross(SceneCanvas canvas, int x, int y, int size, int color) {
        for (int offset = 0; offset < size; offset++) {
            canvas.fill(x + offset, y + offset, 1, 1, color);
            canvas.fill(x + size - 1 - offset, y + offset, 1, 1, color);
        }
    }

    public static void chevronLeft(SceneCanvas canvas, int x, int y, int color) {
        canvas.fill(x + 3, y, 1, 1, color);
        canvas.fill(x + 2, y + 1, 1, 1, color);
        canvas.fill(x + 1, y + 2, 1, 1, color);
        canvas.fill(x, y + 3, 1, 1, color);
        canvas.fill(x + 1, y + 4, 1, 1, color);
        canvas.fill(x + 2, y + 5, 1, 1, color);
        canvas.fill(x + 3, y + 6, 1, 1, color);
    }

    public static void chevronRight(SceneCanvas canvas, int x, int y, int color) {
        canvas.fill(x, y, 1, 1, color);
        canvas.fill(x + 1, y + 1, 1, 1, color);
        canvas.fill(x + 2, y + 2, 1, 1, color);
        canvas.fill(x + 3, y + 3, 1, 1, color);
        canvas.fill(x + 2, y + 4, 1, 1, color);
        canvas.fill(x + 1, y + 5, 1, 1, color);
        canvas.fill(x, y + 6, 1, 1, color);
    }

    public static void doubleChevronLeft(SceneCanvas canvas, int x, int y, int color) {
        chevronLeft(canvas, x, y, color);
        chevronLeft(canvas, x + 3, y, color);
    }

    public static void doubleChevronRight(SceneCanvas canvas, int x, int y, int color) {
        chevronRight(canvas, x, y, color);
        chevronRight(canvas, x + 3, y, color);
    }

    public static void caretDown(SceneCanvas canvas, int x, int y, int color) {
        canvas.fill(x, y, 7, 1, color);
        canvas.fill(x + 1, y + 1, 5, 1, color);
        canvas.fill(x + 2, y + 2, 3, 1, color);
        canvas.fill(x + 3, y + 3, 1, 1, color);
    }

    public static void arrowUp(SceneCanvas canvas, int x, int y, int size, int color) {
        int half = Math.max(1, size / 2);
        canvas.fill(x + half, y, 1, size, color);
        for (int step = 0; step < half; step++) {
            canvas.fill(x + half - step, y + step, 1, 1, color);
            canvas.fill(x + half + step, y + step, 1, 1, color);
        }
    }

    public static void lock(SceneCanvas canvas, int x, int y, int size, int color) {
        int body = Math.max(3, size - 2);
        canvas.fill(x + 1, y + 3, body, size - 3, color);
        canvas.fill(x + 2, y + 1, Math.max(1, body - 2), 1, color);
        canvas.fill(x + 2, y + 2, 1, 1, color);
        canvas.fill(x + body, y + 2, 1, 1, color);
    }

    public static void themeGlyph(SceneCanvas canvas, int x, int y, boolean dark, int color) {
        if (dark) {
            // Crescent moon (8x8)
            canvas.fill(x + 3, y + 1, 3, 1, color);
            canvas.fill(x + 2, y + 2, 2, 1, color);
            canvas.fill(x + 1, y + 3, 2, 2, color);
            canvas.fill(x + 2, y + 5, 2, 1, color);
            canvas.fill(x + 3, y + 6, 3, 1, color);
        } else {
            // Crisp sun (8x8)
            canvas.fill(x + 2, y + 2, 4, 4, color);
            canvas.fill(x + 3, y, 2, 1, color);
            canvas.fill(x + 3, y + 7, 2, 1, color);
            canvas.fill(x, y + 3, 1, 2, color);
            canvas.fill(x + 7, y + 3, 1, 2, color);
            canvas.fill(x + 1, y + 1, 1, 1, color);
            canvas.fill(x + 6, y + 1, 1, 1, color);
            canvas.fill(x + 1, y + 6, 1, 1, color);
            canvas.fill(x + 6, y + 6, 1, 1, color);
        }
    }

    public static void zenGlyph(SceneCanvas canvas, int x, int y, int color) {
        // Four corner expand brackets (8x8)
        canvas.fill(x, y, 3, 1, color);
        canvas.fill(x, y, 1, 3, color);
        canvas.fill(x + 5, y, 3, 1, color);
        canvas.fill(x + 7, y, 1, 3, color);
        canvas.fill(x, y + 7, 3, 1, color);
        canvas.fill(x, y + 5, 1, 3, color);
        canvas.fill(x + 5, y + 7, 3, 1, color);
        canvas.fill(x + 7, y + 5, 1, 3, color);
    }

    public static void drawMiniItem(
        SceneCanvas canvas,
        @Nullable ComponentStackKey resource,
        CirrusPalette palette,
        int x,
        int y,
        int size
    ) {
        if (resource == null || size <= 0) {
            return;
        }
        var stack = CoPilotPaint.stackOf(resource, 1L);
        if (stack == null || stack.isEmpty()) {
            canvas.fill(x, y, size, size, palette.recessedSurface());
            canvas.strokeRect(x, y, size, size, palette.hairlineBorder(), 1);
            return;
        }
        CoPilotPaint.renderStack(canvas, stack, x, y, size);
    }

    public static int countChipWidth(SceneCanvas canvas, long count) {
        String label = "×" + ChromeText.abbreviated(count);
        return ChromeText.width(canvas, Component.literal(label)) + 8;
    }

    public static int countChip(
        SceneCanvas canvas,
        long count,
        int x,
        int y,
        CirrusPalette palette,
        int ink,
        int plate
    ) {
        String label = "×" + ChromeText.abbreviated(count);
        int width = ChromeText.width(canvas, Component.literal(label)) + 8;
        int height = 11;
        boolean dark = OreUiPaint.isDark(palette);
        int fill = OreUiPaint.withAlpha(plate, dark ? 0.22f : 0.12f);
        int border = OreUiPaint.withAlpha(plate, dark ? 0.58f : 0.42f);
        int textInk = dark ? palette.textPrimary() : plate;
        canvas.fill(x, y, width, height, fill);
        canvas.strokeRect(x, y, width, height, border, 1);
        canvas.drawString(label, x + 4, ChromeText.centeredY(y, height, canvas), textInk, false);
        return width;
    }

    public static int basketChipWidth(SceneCanvas canvas, long extraTargets) {
        String label = "+" + ChromeText.abbreviated(extraTargets);
        return ChromeText.width(canvas, Component.literal(label)) + 8;
    }

    public static int basketChip(SceneCanvas canvas, long extraTargets, int x, int y, CirrusPalette palette) {
        String label = "+" + ChromeText.abbreviated(extraTargets);
        int width = ChromeText.width(canvas, Component.literal(label)) + 8;
        int height = 11;
        boolean dark = OreUiPaint.isDark(palette);
        int accent = palette.focusRing();
        int fill = OreUiPaint.withAlpha(accent, dark ? 0.24f : 0.14f);
        int border = OreUiPaint.withAlpha(accent, dark ? 0.62f : 0.45f);
        canvas.fill(x, y, width, height, fill);
        canvas.strokeRect(x, y, width, height, border, 1);
        canvas.drawString(label, x + 4, ChromeText.centeredY(y, height, canvas), accent, false);
        return width;
    }

    public static void separator(SceneCanvas canvas, int x, int y, int width, CirrusPalette palette) {
        canvas.fill(x, y, width, 1, palette.hairlineBorder());
    }
}
