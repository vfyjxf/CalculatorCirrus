package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Dedicated OreUI slot presentation for {@link DurabilityContent}:
 * draws a subtle warm-amber durability requirement frame behind the clean tool sprite and a
 * crisp bottom-right {@code -N} durability-cost badge (plus top-right {@code ×N} when multiple
 * tools are required), replacing legacy {@code 0%} chance badges and mutated damage bars.
 */
public final class DurabilityContentVisual {

    private static final int amberAccent = 0xFFE6A832;
    private static final int amberWash = 0x22E6A832;
    private static final int amberBracket = 0xDDE6A832;
    private static final int amberText = 0xFFFFD166;

    private static final int consumeAllAccent = 0xFFE06452;
    private static final int consumeAllWash = 0x26E06452;
    private static final int consumeAllBracket = 0xDDE06452;
    private static final int consumeAllText = 0xFFFF9B8C;

    private static final int badgePlateBg = 0xDC11151B;
    private static final int countText = 0xFFF2F6FC;

    private DurabilityContentVisual() {}

    /**
     * Compact durability cost text rendered inside a slot corner (e.g. {@code -1}, {@code -4}, {@code -1.2K}).
     */
    public static String formatCompactCost(int durabilityCost) {
        if (durabilityCost < 1_000) {
            return "-" + durabilityCost;
        }
        if (durabilityCost < 1_000_000) {
            return "-" + formatScaled(durabilityCost, 1_000.0, "K");
        }
        return "-" + formatScaled(durabilityCost, 1_000_000.0, "M");
    }

    /**
     * Draws the behind-icon durability requirement wash and crisp OreUI corner brackets.
     */
    public static void renderUnderlay(
        GuiGraphics graphics,
        DurabilityContent content,
        int x,
        int y,
        int width,
        int height
    ) {
        if (width < 8 || height < 8) {
            return;
        }
        boolean whole = content.consumesWholeTool();
        int wash = whole ? consumeAllWash : amberWash;
        int bracket = whole ? consumeAllBracket : amberBracket;

        graphics.fill(x, y, x + width, y + height, wash);

        int arm = Math.max(2, Math.min(4, width / 4));
        // Top-left L-bracket
        graphics.fill(x, y, x + arm, y + 1, bracket);
        graphics.fill(x, y, x + 1, y + arm, bracket);
        // Top-right L-bracket
        graphics.fill(x + width - arm, y, x + width, y + 1, bracket);
        graphics.fill(x + width - 1, y, x + width, y + arm, bracket);
        // Bottom-left L-bracket (bottom-right is owned by the -N cost badge)
        graphics.fill(x, y + height - 1, x + arm, y + height, bracket);
        graphics.fill(x, y + height - arm, x + 1, y + height, bracket);
    }

    /**
     * Draws the foreground {@code -N} durability-cost badge (and optional top-right tool count when {@code amount > 1}).
     */
    public static void renderOverlay(
        GuiGraphics graphics,
        DurabilityContent content,
        int x,
        int y,
        int width,
        int height
    ) {
        if (width < 10 || height < 10) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc == null ? null : mc.font;
        if (font == null) {
            return;
        }

        boolean whole = content.consumesWholeTool();
        int accent = whole ? consumeAllAccent : amberAccent;
        int ink = whole ? consumeAllText : amberText;
        String text = formatCompactCost(content.durabilityCost());

        int rawW = Math.max(1, font.width(text));
        float preferred = text.length() <= 2 ? 0.72f : (text.length() == 3 ? 0.62f : 0.52f);
        float scale = Math.max(0.45f, Math.min(preferred, (width - 2.0f) / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8.0f * scale;

        float tx = x + width - 0.5f - scaledW;
        float ty = y + height - 1.5f - scaledH;
        int bgX = Math.max(x, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(y, (int) Math.floor(ty - 0.5f));
        int bgRight = x + width;
        int bgBottom = y + height;

        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 210f);
        if (bgRight > bgX && bgBottom > bgY) {
            graphics.fill(bgX, bgY, bgRight, bgBottom, badgePlateBg);
            graphics.fill(bgX, bgBottom - 1, bgRight, bgBottom, accent);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(tx, ty, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, ink, true);
        graphics.pose().popPose();

        // When multiple tools are simultaneously required, tuck the stack count into top-right so it never clashes with -N.
        if (content.amount() > 1L) {
            String countStr = "\u00d7" + content.amount();
            int cRawW = Math.max(1, font.width(countStr));
            float cScale = Math.max(0.42f, Math.min(0.56f, (width - 3.0f) / cRawW));
            float cScaledW = cRawW * cScale;
            float cScaledH = 8.0f * cScale;
            float cx = x + width - 0.5f - cScaledW;
            float cy = y + 0.5f;
            int cBgX = Math.max(x, (int) Math.floor(cx - 1.0f));
            int cBgBottom = Math.min(y + height, (int) Math.ceil(cy + cScaledH + 0.5f));
            if (bgRight > cBgX && cBgBottom > y) {
                graphics.fill(cBgX, y, bgRight, cBgBottom, badgePlateBg);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0f);
            graphics.pose().scale(cScale, cScale, 1f);
            graphics.drawString(font, countStr, 0, 0, countText, true);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    private static String formatScaled(int value, double unit, String suffix) {
        double scaled = value / unit;
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
