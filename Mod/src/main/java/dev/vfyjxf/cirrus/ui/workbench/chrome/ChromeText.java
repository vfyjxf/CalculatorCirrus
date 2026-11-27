package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchCountBadge;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * Shared chrome text painting and clipping helpers.
 */
public final class ChromeText {

    public static final int pad = 4;
    public static final int gap = 3;
    private static final String ellipsis = "...";

    private ChromeText() {}

    public static void draw(SceneCanvas canvas, Component label, int x, int y, int color) {
        if (label == null || label.getString().isEmpty()) {
            return;
        }
        canvas.drawString(label, x, y, color, false);
    }

    public static void drawRight(SceneCanvas canvas, Component label, int rightX, int y, int color) {
        if (label == null || label.getString().isEmpty()) {
            return;
        }
        canvas.drawString(label, rightX - width(canvas, label), y, color, false);
    }

    public static void drawFitted(SceneCanvas canvas, Component label, int x, int y, int maxWidth, int color) {
        draw(canvas, fit(canvas, label, maxWidth), x, y, color);
    }

    public static Component fit(SceneCanvas canvas, Component label, int maxWidth) {
        if (label == null) {
            return Component.empty();
        }
        String text = label.getString();
        if (maxWidth <= 0 || text.isEmpty() || width(canvas, label) <= maxWidth) {
            return label;
        }
        Font font = font(canvas);
        if (font == null) {
            return Component.empty();
        }
        if (maxWidth <= font.width(ellipsis)) {
            return Component.empty();
        }
        String clipped = font.plainSubstrByWidth(text, maxWidth - font.width(ellipsis));
        return Component.literal(clipped + ellipsis);
    }

    public static String fitText(
        @Nullable String text,
        int maxWidth,
        @Nullable ToIntFunction<String> widthOf,
        String ellipsis
    ) {
        if (text == null || text.isEmpty() || widthOf == null || maxWidth <= 0) {
            return "";
        }
        if (widthOf.applyAsInt(text) <= maxWidth) {
            return text;
        }
        String suffix = ellipsis == null ? "" : ellipsis;
        int ellipsisWidth = suffix.isEmpty() ? 0 : widthOf.applyAsInt(suffix);
        if (ellipsisWidth > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (widthOf.applyAsInt(text.substring(0, mid)) + ellipsisWidth <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        if (low <= 0) {
            return ellipsisWidth <= maxWidth ? suffix : "";
        }
        return text.substring(0, low) + suffix;
    }

    public static int width(SceneCanvas canvas, Component label) {
        Font font = font(canvas);
        return font == null || label == null ? 0 : font.width(label);
    }

    public static int lineHeight(SceneCanvas canvas) {
        Font font = font(canvas);
        return font == null ? 9 : font.lineHeight;
    }

    public static int centeredY(int rowY, int rowHeight, SceneCanvas canvas) {
        int glyphHeight = Math.max(7, lineHeight(canvas) - 2);
        return rowY + Math.max(0, (rowHeight - glyphHeight) / 2);
    }

    public static int centeredButtonY(int buttonY, int buttonHeight, SceneCanvas canvas) {
        return centeredY(buttonY, Math.max(7, buttonHeight - 2), canvas);
    }

    public static @Nullable Font font(SceneCanvas canvas) {
        if (canvas == null) {
            return null;
        }
        try {
            return canvas.font();
        } catch (RuntimeException absentHost) {
            return null;
        }
    }

    public static boolean hit(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static String abbreviated(long count) {
        return WorkbenchCountBadge.abbreviate(Math.max(0L, count));
    }
}
