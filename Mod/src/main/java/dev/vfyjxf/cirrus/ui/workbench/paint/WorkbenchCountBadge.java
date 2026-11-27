package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;

import java.util.Locale;

public final class WorkbenchCountBadge {
    private static final int paddingX = 3;
    private static final int plateHeight = 9;
    private static final int textYOffset = 1;

    private WorkbenchCountBadge() {}

    public static String abbreviate(long count) {
        if (count < 1000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            double thousands = count / 1000.0;
            if (thousands < 999.95) {
                return oneDecimal(thousands) + "k";
            }
        }
        return oneDecimal(count / 1_000_000.0) + "M";
    }

    public static int plateWidth(Font font, long count) {
        return font.width(abbreviate(count)) + paddingX * 2;
    }

    public static void draw(SceneCanvas canvas, long count, int x, int y, int argbInk, int argbPlate) {
        String label = abbreviate(count);
        Font font = canvas.font();
        int width = font.width(label) + paddingX * 2;
        canvas.fill(x, y, width, plateHeight, argbPlate);
        int textX = x + width - paddingX - font.width(label);
        canvas.drawString(label, textX, y + textYOffset, argbInk, false);
    }

    private static String oneDecimal(double value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }
}
