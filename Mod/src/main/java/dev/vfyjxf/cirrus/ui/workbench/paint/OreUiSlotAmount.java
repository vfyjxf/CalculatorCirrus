package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Minecraft-native in-slot item count formatter and scaled corner overlay supporting values up to trillions.
 */
public final class OreUiSlotAmount {

    private OreUiSlotAmount() {}

    /**
     * Formats an item count for display inside a slot; counts of 1 or less return empty string like vanilla Minecraft.
     */
    public static String format(long amount) {
        if (amount <= 1L) {
            return "";
        }
        if (amount < 1_000L) {
            return Long.toString(amount);
        }
        if (amount < 1_000_000L) {
            return formatScaled(amount, 1_000.0, "K");
        }
        if (amount < 1_000_000_000L) {
            return formatScaled(amount, 1_000_000.0, "M");
        }
        if (amount < 1_000_000_000_000L) {
            return formatScaled(amount, 1_000_000_000.0, "G");
        }
        return formatScaled(amount, 1_000_000_000_000.0, "T");
    }

    /**
     * Parses a count label such as "×32", "x16", or "128" into a numeric count, returning 0 when non-numeric.
     */
    public static long parseCount(@Nullable String label) {
        if (label == null || label.isBlank()) {
            return 0L;
        }
        String trimmed = label.trim();
        if (trimmed.startsWith("\u00d7") || trimmed.startsWith("x") || trimmed.startsWith("X")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            return 0L;
        }
        char last = Character.toUpperCase(trimmed.charAt(trimmed.length() - 1));
        double mult = 1.0;
        if (last == 'K') {
            mult = 1_000.0;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (last == 'M') {
            mult = 1_000_000.0;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (last == 'G' || last == 'B') {
            mult = 1_000_000_000.0;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            return Math.round(Double.parseDouble(trimmed) * mult);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /**
     * Renders a recessed OreUI item slot, centered item sprite, and native bottom-right count overlay when amount > 1.
     */
    public static void drawSlot(
        SceneCanvas canvas,
        @Nullable ResourceKey resource,
        long amount,
        int x,
        int y,
        int slotSize,
        CirrusPalette palette
    ) {
        drawSlot(canvas, resource == null ? null : CoPilotPaint.stackOf(resource, 1L), amount, x, y, slotSize, palette);
    }

    public static void drawSlot(
        SceneCanvas canvas,
        @Nullable ContentStack<?, ?> stack,
        long amount,
        int x,
        int y,
        int slotSize,
        CirrusPalette palette
    ) {
        if (canvas == null || slotSize <= 0) {
            return;
        }
        OreUiPaint.slot(canvas, x, y, slotSize, palette);
        int iconSize = Math.max(8, slotSize - 2);
        int pad = Math.max(1, (slotSize - iconSize) / 2);
        if (stack != null && !stack.isEmpty()) {
            CoPilotPaint.renderStack(canvas, stack, x + pad, y + pad, iconSize);
        }
        drawInSlot(canvas, x, y, slotSize, amount, palette);
    }

    /**
     * Renders a compact, dynamically scaled count badge tucked cleanly in the bottom-right corner of a slot.
     */
    public static void drawInSlot(
        SceneCanvas canvas,
        int slotX,
        int slotY,
        int slotSize,
        long amount,
        CirrusPalette palette
    ) {
        String text = format(amount);
        if (text.isEmpty() || canvas == null || slotSize < 12) {
            return;
        }
        Font font = canvas.font();
        int rawW = Math.max(1, font.width(text));
        int rawH = 8;
        float preferredScale = preferredScale(slotSize, text.length());
        float maxW = Math.max(6f, slotSize - 2.5f);
        float scale = Math.min(preferredScale, maxW / rawW);
        scale = Math.max(0.48f, scale);

        float scaledW = rawW * scale;
        float scaledH = rawH * scale;
        float tx = slotX + slotSize - 1.5f - scaledW;
        float ty = slotY + slotSize - 1.5f - scaledH;

        // Subtle dark corner scrim tucked strictly behind the digits so counts pop over bright item sprites.
        int bgX = Math.max(slotX + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(slotY + 1, (int) Math.floor(ty - 0.5f));
        int bgW = Math.min(slotX + slotSize - 1 - bgX, (int) Math.ceil(scaledW + 2.0f));
        int bgH = Math.min(slotY + slotSize - 1 - bgY, (int) Math.ceil(scaledH + 1.5f));
        if (bgW > 0 && bgH > 0) {
            canvas.fill(bgX, bgY, bgW, bgH, OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.64f));
        }

        int ink = tierColor(amount, palette);
        canvas.pushTransform();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);
        try {
            canvas.drawString(text, 0, 0, ink, true);
        } finally {
            canvas.popTransform();
        }
    }

    private static float preferredScale(int slotSize, int len) {
        if (slotSize <= 14) {
            return len <= 2 ? 0.62f : 0.52f;
        }
        if (slotSize <= 18) {
            if (len <= 2) {
                return 0.78f;
            }
            return len == 3 ? 0.66f : 0.58f;
        }
        if (len <= 2) {
            return 0.86f;
        }
        return len == 3 ? 0.74f : 0.64f;
    }

    private static int tierColor(long amount, CirrusPalette palette) {
        if (amount >= 1_000_000L) {
            return OreUiPaint.lighten(palette.focusRing(), 0.25f);
        }
        if (amount >= 1_000L) {
            return OreUiPaint.lighten(palette.costH(), 0.25f);
        }
        return palette.textOnAccent();
    }

    private static String formatScaled(long amount, double unit, String suffix) {
        double scaled = amount / unit;
        if (scaled < 10.0) {
            long tenths = Math.round(scaled * 10.0);
            if (tenths % 10L == 0L) {
                return (tenths / 10L) + suffix;
            }
            return String.format(Locale.ROOT, "%.1f%s", tenths / 10.0, suffix);
        }
        return Math.round(scaled) + suffix;
    }
}
