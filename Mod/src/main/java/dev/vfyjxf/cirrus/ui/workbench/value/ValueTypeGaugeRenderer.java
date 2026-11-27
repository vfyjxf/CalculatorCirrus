package dev.vfyjxf.cirrus.ui.workbench.value;

import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class ValueTypeGaugeRenderer {

    public static final int databaseMeterHeight = 20;
    public static final int databaseMeterMinWidth = 92;
    public static final int databaseMeterGap = 4;

    public static final int dagPillHeight = 13;
    public static final int dagPillGap = 4;
    public static final int dagInlineStripHeight = 15;

    private ValueTypeGaugeRenderer() {}

    public enum Direction {
        input, output, neutral
    }

    public record ValueEntry(String typeId, long amount, Direction direction, float chance) {
        public ValueEntry {
            typeId = Objects.requireNonNull(typeId, "typeId");
            amount = Math.max(0L, amount);
            direction = direction == null ? Direction.neutral : direction;
        }

        public static ValueEntry fromContent(ValueContent<?> content, Direction direction) {
            return new ValueEntry(content.type().typeId().toString(), content.amount(), direction, content.chance());
        }

        public ValueTypeVisual visual() {
            return ValueTypeVisualRegistry.resolve(typeId);
        }
    }

    /**
     * Database style: technical instrument bar with icon well, type tag, formatted readout, and segmented gauge.
     */
    public static void drawDatabaseMeter(
        SceneCanvas canvas,
        ValueEntry entry,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        boolean hovered
    ) {
        if (width <= 8 || height <= 8) {
            return;
        }
        ValueTypeVisual visual = entry.visual();
        int accent = visual.accentColor(palette);
        boolean dark = OreUiPaint.isDark(palette);
        int bg = hovered
                ? mix(palette.cardSurfaceHover(), accent, dark ? 0.14f : 0.10f)
                : mix(palette.recessedSurface(), accent, dark ? 0.08f : 0.06f);
        int border = hovered
                ? OreUiPaint.withAlpha(accent, 0.85f)
                : mix(palette.hairlineBorder(), accent, dark ? 0.34f : 0.28f);

        canvas.fill(x, y, width, height, bg);
        canvas.strokeRect(x, y, width, height, border, 1);
        canvas.fill(x, y, 2, height, accent);

        int iconWellSize = Math.min(14, height - 4);
        int iconWellX = x + 4;
        int iconWellY = y + (height - iconWellSize) / 2;
        canvas.fill(
            iconWellX,
            iconWellY,
            iconWellSize,
            iconWellSize,
            OreUiPaint.withAlpha(accent, dark ? 0.16f : 0.13f)
        );
        canvas.strokeRect(
            iconWellX,
            iconWellY,
            iconWellSize,
            iconWellSize,
            OreUiPaint.withAlpha(accent, dark ? 0.44f : 0.38f),
            1
        );
        drawIcon(
            canvas,
            visual,
            iconWellX + (iconWellSize - 9) / 2,
            iconWellY + (iconWellSize - 9) / 2,
            9,
            accent,
            theme,
            1.0f
        );

        int contentX = iconWellX + iconWellSize + 4;
        int contentW = Math.max(8, x + width - 4 - contentX);
        int topRowY = y + 2;

        String dirPrefix = entry.direction() == Direction.output
                ? "+"
                : entry.direction() == Direction.input ? "-" : "";
        String valueText = dirPrefix + visual.formatCompact(entry.amount());
        String unitTag = visual.unit();
        int valueW = ChromeText.width(canvas, Component.literal(valueText));
        int labelInk = dark ? OreUiPaint.lighten(accent, 0.28f) : darken(accent, 0.22f);

        if (contentW - valueW >= 18) {
            Component tagComp = Component.literal(unitTag);
            ChromeText.draw(
                canvas,
                ChromeText.fit(canvas, tagComp, Math.max(8, contentW - valueW - 4)),
                contentX,
                topRowY,
                palette.textMuted()
            );
            ChromeText.drawRight(canvas, Component.literal(valueText), x + width - 4, topRowY, labelInk);
        } else {
            ChromeText.draw(
                canvas,
                ChromeText.fit(canvas, Component.literal(valueText), contentW),
                contentX,
                topRowY,
                labelInk
            );
        }

        int barH = 4;
        int barY = y + height - barH - 2;
        float frac = visual.gaugeFraction(entry.amount());
        drawSegmentedBar(canvas, contentX, barY, contentW, barH, frac, accent, palette, 1.0f);
    }

    /**
     * DAG style: compact horizontal telemetry capsule with icon, value text, and thin bottom progress bar.
     */
    public static int measureDagPillWidth(Font font, ValueEntry entry) {
        ValueTypeVisual visual = entry.visual();
        String text = visual.formatCompact(entry.amount());
        return 4 + 9 + 3 + Math.max(12, font.width(text)) + 5;
    }

    public static void drawDagPill(
        SceneCanvas canvas,
        ValueEntry entry,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        float fade
    ) {
        if (width <= 10 || height <= 6 || fade <= 0.01f) {
            return;
        }
        ValueTypeVisual visual = entry.visual();
        int accent = visual.accentColor(palette);
        boolean dark = OreUiPaint.isDark(palette);

        int bg = OreUiPaint.withAlpha(mix(palette.recessedSurface(), accent, dark ? 0.12f : 0.08f), fade);
        int border = OreUiPaint.withAlpha(mix(palette.hairlineBorder(), accent, 0.45f), fade);
        canvas.fill(x, y, width, height, bg);
        canvas.strokeRect(x, y, width, height, border, 1);
        canvas.fill(x, y + 1, 2, Math.max(1, height - 2), OreUiPaint.withAlpha(accent, fade));

        int iconY = y + Math.max(1, (height - 2 - 9) / 2);
        drawIcon(canvas, visual, x + 4, iconY, 9, accent, theme, fade);

        int textX = x + 15;
        int textMaxW = Math.max(6, x + width - 3 - textX);
        String text = visual.formatCompact(entry.amount());
        int ink = dark ? OreUiPaint.lighten(accent, 0.25f) : darken(accent, 0.18f);
        ChromeText.draw(
            canvas,
            ChromeText.fit(canvas, Component.literal(text), textMaxW),
            textX,
            y + Math.max(1, (height - 2 - 8) / 2),
            OreUiPaint.withAlpha(ink, fade)
        );

        int barX = x + 2;
        int barW = Math.max(2, width - 3);
        int barY = y + height - 2;
        canvas.fill(barX, barY, barW, 2, OreUiPaint.withAlpha(OreUiPaint.meterTrack(palette), fade));
        int fillW = Math.max(2, Math.round(barW * visual.gaugeFraction(entry.amount())));
        canvas.fill(barX, barY, Math.min(barW, fillW), 2, OreUiPaint.withAlpha(accent, fade));
    }

    /**
     * Compact square emblem for slot-sized fallbacks so ValueType resources never render as flat color blocks.
     */
    public static void drawMiniEmblem(
        SceneCanvas canvas,
        String typeId,
        int x,
        int y,
        int size,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        float fade
    ) {
        if (size <= 4 || fade <= 0.01f) {
            return;
        }
        ValueTypeVisual visual = ValueTypeVisualRegistry.resolve(typeId);
        int accent = visual.accentColor(palette);
        boolean dark = OreUiPaint.isDark(palette);

        canvas.fill(
            x,
            y,
            size,
            size,
            OreUiPaint.withAlpha(mix(palette.recessedSurface(), accent, dark ? 0.18f : 0.12f), fade)
        );
        canvas.strokeRect(x, y, size, size, OreUiPaint.withAlpha(accent, fade * 0.72f), 1);
        canvas.fill(x + 1, y + 1, Math.max(1, size - 2), 1, OreUiPaint.withAlpha(accent, fade));

        int iconSize = Math.min(9, Math.max(5, size - 4));
        drawIcon(
            canvas,
            visual,
            x + (size - iconSize) / 2,
            y + Math.max(1, (size - 2 - iconSize) / 2),
            iconSize,
            accent,
            theme,
            fade
        );

        int barW = Math.max(2, size - 2);
        canvas.fill(x + 1, y + size - 2, barW, 1, OreUiPaint.withAlpha(OreUiPaint.meterTrack(palette), fade));
        canvas.fill(x + 1, y + size - 2, Math.max(2, (barW * 2) / 3), 1, OreUiPaint.withAlpha(accent, fade));
    }

    public static void drawSegmentedBar(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        float fraction,
        int accent,
        CirrusPalette palette,
        float fade
    ) {
        if (width <= 2 || height <= 0) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, fraction));
        canvas.fill(x, y, width, height, OreUiPaint.withAlpha(OreUiPaint.meterTrack(palette), fade));
        int fillW = clamped <= 0.0f ? 0 : Math.max(2, Math.round(width * clamped));
        if (fillW > 0) {
            int drawnW = Math.min(width, fillW);
            canvas.fill(x, y, drawnW, height, OreUiPaint.withAlpha(accent, fade));
            if (height >= 3) {
                canvas.fill(x, y, drawnW, 1, OreUiPaint.withAlpha(OreUiPaint.lighten(accent, 0.34f), fade));
            }
        }
        if (width >= 24) {
            int notchColor = OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), fade * 0.45f);
            for (int q = 1; q <= 3; q++) {
                int nx = x + (width * q) / 4;
                canvas.fill(nx, y, 1, height, notchColor);
            }
        }
    }

    public static void drawIcon(
        SceneCanvas canvas,
        ValueTypeVisual visual,
        int x,
        int y,
        int size,
        int accent,
        WorkbenchThemeMode theme,
        float fade
    ) {
        ImageTexture sprite = visual.icon();
        if (sprite != null) {
            ImageTexture themed = CirrusThemeIcons.resolve(sprite, theme);
            if (fade < 0.99f) {
                canvas.color(OreUiPaint.withAlpha(0xFFFFFFFF, fade));
            }
            canvas.texture(themed, x, y, size, size);
            if (fade < 0.99f) {
                canvas.resetColor();
            }
            return;
        }
        drawProceduralGlyph(canvas, visual.fallbackGlyph(), x, y, size, OreUiPaint.withAlpha(accent, fade));
    }

    private static void drawProceduralGlyph(
        SceneCanvas canvas,
        ValueTypeVisual.GlyphKind kind,
        int x,
        int y,
        int size,
        int color
    ) {
        int midX = x + size / 2;
        int midY = y + size / 2;
        switch (kind) {
            case bolt -> {
                canvas.fill(midX, y + 1, 2, 3, color);
                canvas.fill(midX - 2, midY, 5, 2, color);
                canvas.fill(midX - 1, midY + 1, 2, 3, color);
            }
            case clock -> {
                canvas.strokeRect(x + 1, y + 1, Math.max(3, size - 2), Math.max(3, size - 2), color, 1);
                canvas.fill(midX, y + 2, 1, Math.max(2, size / 2 - 1), color);
                canvas.fill(midX, midY, Math.max(2, size / 2 - 2), 1, color);
            }
            case flame -> {
                canvas.fill(midX, y + 1, 1, 2, color);
                canvas.fill(midX - 1, y + 3, 3, 2, color);
                canvas.fill(midX - 2, y + 5, 5, 2, color);
                canvas.fill(midX - 1, y + 7, 3, 1, color);
            }
            case gear -> {
                canvas.strokeRect(x + 2, y + 2, Math.max(3, size - 4), Math.max(3, size - 4), color, 1);
                canvas.fill(midX, y + 1, 1, 1, color);
                canvas.fill(midX, y + size - 2, 1, 1, color);
                canvas.fill(x + 1, midY, 1, 1, color);
                canvas.fill(x + size - 2, midY, 1, 1, color);
            }
            case crystal -> {
                canvas.fill(midX, y + 1, 1, 1, color);
                canvas.fill(midX - 1, y + 2, 3, 2, color);
                canvas.fill(midX - 2, midY, 5, 2, color);
                canvas.fill(midX - 1, midY + 2, 3, 1, color);
                canvas.fill(midX, midY + 3, 1, 1, color);
            }
            case wave -> {
                canvas.fill(x + 1, midY - 1, 2, 1, color);
                canvas.fill(x + 3, midY, 2, 1, color);
                canvas.fill(x + 5, midY - 1, 2, 1, color);
                canvas.fill(x + 2, midY + 2, 5, 1, color);
            }
            case gauge -> {
                canvas.strokeRect(x + 1, y + 2, Math.max(3, size - 2), Math.max(3, size - 3), color, 1);
                canvas.fill(x + 2, y + size - 3, Math.max(2, size - 4), 1, color);
                canvas.fill(midX, y + 3, 1, Math.max(2, size / 2 - 1), color);
            }
        }
    }

    public static Tooltip tooltipFor(ValueEntry entry) {
        ValueTypeVisual visual = entry.visual();
        Tooltip tooltip = Tooltip.create();
        tooltip.add(visual.displayName().copy().withStyle(ChatFormatting.WHITE));
        tooltip.add(
            Component.translatable("cirrus.ui.value_type.tooltip.amount", visual.formatDetailed(entry.amount()))
                    .withStyle(ChatFormatting.GOLD)
        );
        if (entry.direction() != Direction.neutral) {
            String dirKey = entry.direction() == Direction.output
                    ? "cirrus.ui.value_type.direction.output"
                    : "cirrus.ui.value_type.direction.input";
            tooltip.add(Component.translatable(dirKey).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal(entry.typeId()).withStyle(ChatFormatting.DARK_GRAY));
        return tooltip;
    }

    public static OreUiRichTooltip richTooltipFor(ValueEntry entry, CirrusPalette palette) {
        ValueTypeVisual visual = entry.visual();
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(visual.displayName()).subtitle(entry.typeId())
                .badge(Component.literal(visual.unit()), visual.accentColor(palette));
        builder.keyValue(
            Component.translatable("cirrus.ui.value_type.label.value"),
            Component.literal(visual.formatDetailed(entry.amount())),
            OreUiRichTooltip.Tone.costH
        );
        if (entry.direction() != Direction.neutral) {
            String dirKey = entry.direction() == Direction.output
                    ? "cirrus.ui.value_type.direction.output"
                    : "cirrus.ui.value_type.direction.input";
            builder.keyValue(
                Component.translatable("cirrus.ui.value_type.label.role"),
                Component.translatable(dirKey),
                entry.direction() == Direction.output ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
            );
        }
        if (entry.chance() < 0.999f) {
            int pct = Math.max(1, Math.round(entry.chance() * 100f));
            builder.keyValue(
                Component.translatable("cirrus.ui.value_type.label.chance"),
                Component.literal(pct + "%"),
                OreUiRichTooltip.Tone.costC
            );
        }
        return builder.build();
    }

    private static int mix(int base, int target, float ratio) {
        float t = Math.max(0.0f, Math.min(1.0f, ratio));
        int a = Math.round(((base >>> 24) & 0xFF) * (1.0f - t) + ((target >>> 24) & 0xFF) * t);
        int r = Math.round(((base >>> 16) & 0xFF) * (1.0f - t) + ((target >>> 16) & 0xFF) * t);
        int g = Math.round(((base >>> 8) & 0xFF) * (1.0f - t) + ((target >>> 8) & 0xFF) * t);
        int b = Math.round((base & 0xFF) * (1.0f - t) + (target & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, float amount) {
        float keep = Math.max(0.0f, Math.min(1.0f, 1.0f - amount));
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >>> 16) & 0xFF) * keep);
        int g = Math.round(((argb >>> 8) & 0xFF) * keep);
        int b = Math.round((argb & 0xFF) * keep);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
