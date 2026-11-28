package dev.vfyjxf.cirrus.ui.workbench.tooltip;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures and paints custom square OreUI floating rich-content tooltips directly onto {@link SceneCanvas} with full word-wrapping and zero truncation.
 */
public final class OreUiRichTooltipRenderer {

    public static final int minWidth = 158;
    public static final int maxWidth = 316;
    public static final int padX = 8;
    public static final int headerPadY = 6;
    public static final int bodyPadY = 5;
    public static final int blockGap = 4;
    public static final int lineHeight = 11;
    public static final int meterHeight = 19;
    public static final int pillsHeight = 14;
    public static final int pillRowGap = 3;
    public static final int flowSlotSize = 18;
    public static final int gridCellSize = 18;
    public static final int gridOuterPad = 3;
    public static final int gridInnerPad = 1;
    public static final int gridScrollbarGap = 2;
    public static final int gridScrollbarWidth = 6;
    public static final int listRowHeight = 16;
    public static final int footerPadY = 4;
    public static final int cursorOffset = 12;
    public static final int viewportMargin = 6;
    public static final int pinnedFramePad = 2;

    private OreUiRichTooltipRenderer() {}

    public record Placement(int x, int y, int width, int height) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public record GridGeometry(
        Rect frameRect,
        Rect gridRect,
        @Nullable Rect scrollbarRect,
        int columns,
        int visibleRows,
        int totalRows,
        int maxRowOffset,
        int cellSize,
        int itemCount
    ) {

        public int itemIndexAt(double px, double py, int scrollRow) {
            if (columns <= 0 || visibleRows <= 0 || cellSize <= 0 || !gridRect.contains(px, py)) {
                return -1;
            }
            int col = (int) ((px - gridRect.x()) / cellSize);
            int row = (int) ((py - gridRect.y()) / cellSize);
            if (col < 0 || col >= columns || row < 0 || row >= visibleRows) {
                return -1;
            }
            int clampedScroll = Math.max(0, Math.min(maxRowOffset, scrollRow));
            int index = (clampedScroll + row) * columns + col;
            return index >= 0 && index < itemCount ? index : -1;
        }

        public boolean isOverScrollbar(double px, double py) {
            Rect sb = scrollbarRect;
            return sb != null && sb.contains(px, py);
        }

        public int scrollRowForPointerY(double py) {
            Rect sb = scrollbarRect;
            if (sb == null || maxRowOffset <= 0) {
                return 0;
            }
            int innerTop = sb.y() + 1;
            int innerH = Math.max(1, sb.height() - 2);
            int thumbH = Math.max(12, Math.round(innerH * (visibleRows / (float) Math.max(1, totalRows))));
            int travel = Math.max(1, innerH - thumbH);
            double ratio = Math.max(0.0, Math.min(1.0, (py - innerTop - thumbH * 0.5) / travel));
            return Math.max(0, Math.min(maxRowOffset, (int) Math.round(ratio * maxRowOffset)));
        }
    }

    public record GridRenderState(int scrollRow, int hoveredIndex, boolean scrollbarHovered, boolean scrollbarDragging) {

        public static final GridRenderState passive = new GridRenderState(0, -1, false, false);
    }

    public record RenderResult(Placement placement, @Nullable GridGeometry interactiveGrid) {}

    public record Measured(
        int width,
        int height,
        int headerHeight,
        List<Integer> blockHeights,
        boolean hasFooter,
        int footerHeight
    ) {}

    public static Placement position(
        int anchorX,
        int anchorY,
        int tipWidth,
        int tipHeight,
        int viewWidth,
        int viewHeight
    ) {
        int x = anchorX + cursorOffset;
        int y = anchorY + cursorOffset;
        if (x + tipWidth > viewWidth - viewportMargin) {
            x = anchorX - tipWidth - cursorOffset;
        }
        if (y + tipHeight > viewHeight - viewportMargin) {
            y = anchorY - tipHeight - cursorOffset;
        }
        x = Math.max(viewportMargin, Math.min(Math.max(viewportMargin, viewWidth - tipWidth - viewportMargin), x));
        y = Math.max(viewportMargin, Math.min(Math.max(viewportMargin, viewHeight - tipHeight - viewportMargin), y));
        return new Placement(x, y, tipWidth, tipHeight);
    }

    public static Measured measure(OreUiRichTooltip tooltip, @Nullable Font font) {
        return measure(tooltip, font, maxWidth);
    }

    public static Measured measure(OreUiRichTooltip tooltip, @Nullable Font font, int maxBoxWidth) {
        int effectiveMaxBox = Math.max(minWidth, Math.min(maxWidth, maxBoxWidth));
        int maxContentW = Math.max(80, effectiveMaxBox - padX * 2);

        boolean hasIcon = tooltip.iconStack() != null || tooltip.iconTexture() != null;
        boolean hasSubtitle = tooltip.subtitle() != null && !tooltip.subtitle().getString().isEmpty();
        int iconReserve = hasIcon ? 24 : 0;
        int badgeReserve = tooltip.badge() != null && !tooltip.badge().getString().isEmpty()
                ? textWidth(font, tooltip.badge()) + 16
                : 0;

        int contentWidth = minWidth - padX * 2;
        int titleLineW = iconReserve + textWidth(font, tooltip.title()) + badgeReserve;
        int subtitleLineW = hasSubtitle ? iconReserve + textWidth(font, tooltip.subtitle()) : 0;
        contentWidth = Math.max(contentWidth, Math.min(maxContentW, Math.max(titleLineW, subtitleLineW)));

        for (OreUiRichTooltip.Block block : tooltip.blocks()) {
            int wanted = switch (block) {
                case OreUiRichTooltip.Block.Divider ignored -> 0;
                case OreUiRichTooltip.Block.TextLine line -> textWidth(font, line.text());
                case OreUiRichTooltip.Block.KeyValue kv -> textWidth(font, kv.key()) + textWidth(font, kv.value()) + 16;
                case OreUiRichTooltip.Block.MetricPills pills -> {
                    int rowW = 0;
                    for (int i = 0; i < pills.pills().size(); i++) {
                        if (i > 0) {
                            rowW += 4;
                        }
                        rowW += textWidth(font, pills.pills().get(i).label()) + 10;
                    }
                    yield rowW;
                }
                case OreUiRichTooltip.Block.Meter meter ->
                    textWidth(font, meter.label()) + textWidth(font, meter.readout()) + 16;
                case OreUiRichTooltip.Block.ItemFlow flow -> measureFlowGrid(flow, maxContentW).width() + 8;
                case OreUiRichTooltip.Block.ItemList list -> {
                    int visible = Math.min(list.maxVisible(), list.entries().size());
                    int maxRow = 0;
                    for (int i = 0; i < visible; i++) {
                        OreUiRichTooltip.ItemEntry entry = list.entries().get(i);
                        int rowW = 18
                                + textWidth(font, entry.name())
                                + (entry.trailing() == null ? 0 : textWidth(font, entry.trailing()) + 12);
                        maxRow = Math.max(maxRow, rowW);
                    }
                    yield maxRow;
                }
                case OreUiRichTooltip.Block.ItemGrid grid -> {
                    GridMetrics gm = measureItemGrid(grid, maxContentW);
                    int headingW = grid.heading() == null ? 0 : textWidth(font, grid.heading());
                    yield Math.max(gm.frameWidth(), headingW);
                }
                case OreUiRichTooltip.Block.FooterHint footer -> textWidth(font, footer.hint());
            };
            contentWidth = Math.max(contentWidth, Math.min(maxContentW, wanted));
        }

        int finalWidth = Math.min(effectiveMaxBox, Math.max(minWidth, contentWidth + padX * 2));
        int innerContentW = Math.max(80, finalWidth - padX * 2);

        int titleAvail = Math.max(40, innerContentW - iconReserve - badgeReserve);
        int titleLines = wrapCount(font, tooltip.title(), titleAvail);
        int subAvail = Math.max(40, innerContentW - iconReserve);
        int subLines = hasSubtitle ? wrapCount(font, tooltip.subtitle(), subAvail) : 0;
        int headerTextH = titleLines * lineHeight + (subLines > 0 ? 1 + subLines * 10 : 0);
        int headerH = Math.max(hasIcon || hasSubtitle ? 30 : 21, headerTextH + 8);

        List<Integer> heights = new ArrayList<>(tooltip.blocks().size());
        int bodyH = 0;
        boolean hasFooter = false;
        int footerH = 0;
        int nonFooterCount = 0;

        for (OreUiRichTooltip.Block block : tooltip.blocks()) {
            int bh = switch (block) {
                case OreUiRichTooltip.Block.Divider ignored -> 5;
                case OreUiRichTooltip.Block.TextLine line ->
                    Math.max(1, wrapCount(font, line.text(), innerContentW)) * lineHeight;
                case OreUiRichTooltip.Block.KeyValue kv -> {
                    int keyW = textWidth(font, kv.key());
                    int valW = textWidth(font, kv.value());
                    if (keyW + valW + 10 <= innerContentW) {
                        yield lineHeight;
                    }
                    int valLines = wrapCount(font, kv.value(), Math.max(40, innerContentW - 8));
                    yield lineHeight + valLines * 10;
                }
                case OreUiRichTooltip.Block.MetricPills pills -> measurePillRowsHeight(pills, font, innerContentW);
                case OreUiRichTooltip.Block.Meter ignored -> meterHeight;
                case OreUiRichTooltip.Block.ItemFlow flow -> {
                    OreUiRecipeGrid.Layout grid = measureFlowGrid(flow, innerContentW);
                    yield (flow.label() != null && !flow.label().getString().isEmpty() ? 11 : 0) + grid.height() + 6;
                }
                case OreUiRichTooltip.Block.ItemList list -> {
                    int visible = Math.min(list.maxVisible(), list.entries().size());
                    boolean overflow = list.entries().size() > visible;
                    int headingH = list.heading() != null && !list.heading().getString().isEmpty() ? 11 : 0;
                    yield headingH + visible * listRowHeight + (overflow ? 10 : 0);
                }
                case OreUiRichTooltip.Block.ItemGrid grid -> {
                    GridMetrics gm = measureItemGrid(grid, innerContentW);
                    int headingH = grid.heading() != null && !grid.heading().getString().isEmpty() ? 11 : 0;
                    yield headingH + gm.frameHeight();
                }
                case OreUiRichTooltip.Block.FooterHint footer -> {
                    hasFooter = true;
                    int lines = Math.max(1, wrapCount(font, footer.hint(), innerContentW));
                    footerH = lines * 10 + footerPadY * 2;
                    yield footerH;
                }
            };
            heights.add(bh);
            if (!(block instanceof OreUiRichTooltip.Block.FooterHint)) {
                if (nonFooterCount > 0) {
                    bodyH += blockGap;
                }
                bodyH += bh;
                nonFooterCount++;
            }
        }

        int totalH = headerH + (nonFooterCount > 0 ? bodyPadY * 2 + bodyH : 2) + (hasFooter ? footerH : 0) + 3;
        return new Measured(finalWidth, totalH, headerH, List.copyOf(heights), hasFooter, footerH);
    }

    public static Placement render(
        SceneCanvas canvas,
        OreUiRichTooltip tooltip,
        int anchorX,
        int anchorY,
        int viewWidth,
        int viewHeight,
        CirrusPalette palette
    ) {
        return renderInternal(canvas, tooltip, anchorX, anchorY, viewWidth, viewHeight, null, GridRenderState.passive, null, false, palette)
                .placement();
    }

    /**
     * Renders a JEI-style pinned interactive tooltip at its exact locked {@link Placement} with zero
     * positional or dimensional shift, drawing the subtle outer pinned highlight frame, interactive
     * grid hover/scroll state, and returning the exact laid-out {@link GridGeometry} for hit-testing.
     */
    public static RenderResult renderPinned(
        SceneCanvas canvas,
        OreUiRichTooltip tooltip,
        Placement lockedBox,
        GridRenderState gridState,
        @Nullable Component footerOverride,
        CirrusPalette palette
    ) {
        return renderInternal(
            canvas,
            tooltip,
            lockedBox.x(),
            lockedBox.y(),
            lockedBox.x() + lockedBox.width() + viewportMargin,
            lockedBox.y() + lockedBox.height() + viewportMargin,
            lockedBox,
            gridState == null ? GridRenderState.passive : gridState,
            footerOverride,
            true,
            palette
        );
    }

    private static RenderResult renderInternal(
        SceneCanvas canvas,
        OreUiRichTooltip tooltip,
        int anchorX,
        int anchorY,
        int viewWidth,
        int viewHeight,
        @Nullable Placement forcedBox,
        GridRenderState gridState,
        @Nullable Component footerOverride,
        boolean pinned,
        CirrusPalette palette
    ) {
        if (canvas == null || tooltip == null || tooltip.isEmpty() || palette == null) {
            return new RenderResult(new Placement(0, 0, 0, 0), null);
        }
        int dynamicMaxW = forcedBox != null
                ? Math.max(minWidth, Math.min(maxWidth, forcedBox.width()))
                : Math.min(maxWidth, Math.max(minWidth, viewWidth - viewportMargin * 2));
        Measured measured = measure(tooltip, ChromeText.font(canvas), dynamicMaxW);
        Placement box = forcedBox != null
                ? forcedBox
                : position(anchorX, anchorY, measured.width(), measured.height(), viewWidth, viewHeight);
        int x = box.x();
        int y = box.y();
        int w = box.width();
        int h = box.height();
        int accent = tooltip.accent(palette);
        boolean dark = OreUiPaint.isDark(palette);

        canvas.shadow(x, y, w, h, 3f, 4f, 9f, palette.dropShadow());
        if (pinned) {
            int fx = x - pinnedFramePad;
            int fy = y - pinnedFramePad;
            int fw = w + pinnedFramePad * 2;
            int fh = h + pinnedFramePad * 2;
            canvas.fill(fx, fy, fw, fh, OreUiPaint.withAlpha(palette.focusRing(), 0.16f));
            canvas.strokeRect(fx, fy, fw, fh, OreUiPaint.withAlpha(palette.focusRing(), 0.92f), 1);
        }
        canvas.fill(x, y, w, h, OreUiPaint.outerInk(palette));
        int innerW = Math.max(1, w - 2);
        int innerH = Math.max(1, h - 2);
        canvas.fill(x + 1, y + 1, innerW, innerH, palette.popoverSurface());
        int bevelHighlight = dark
                ? OreUiPaint.lighten(palette.popoverSurface(), 0.14f)
                : OreUiPaint.lighten(palette.cardSurface(), 0.35f);
        canvas.fill(x + 1, y + 1, innerW, 1, bevelHighlight);
        canvas.fill(x + 1, y + 1, 1, Math.max(1, innerH - 2), bevelHighlight);
        int ledgeColor = dark
                ? OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.72f)
                : OreUiPaint.withAlpha(palette.hairlineBorder(), 0.72f);
        canvas.fill(x + 1, y + h - 3, innerW, 2, ledgeColor);

        int headerY = y + 2;
        int headerH = measured.headerHeight();
        int headerFill = dark ? OreUiPaint.cardFooter(palette) : OreUiPaint.withAlpha(accent, 0.08f);
        canvas.fill(x + 2, headerY, Math.max(1, innerW - 1), headerH, headerFill);
        canvas.fill(x + 1, headerY + headerH, innerW, 1, OreUiPaint.cardDivider(palette));

        int textX = x + padX;
        int rightX = x + w - padX;
        if (tooltip.iconStack() != null) {
            int slotY = headerY + Math.max(2, (headerH - 20) / 2);
            CoPilotPaint.renderSlotItem(canvas, tooltip.iconStack(), textX, slotY, 20, palette);
            textX += 24;
        } else if (tooltip.iconTexture() != null) {
            int slotY = headerY + Math.max(2, (headerH - 18) / 2);
            OreUiPaint.slot(canvas, textX, slotY, 18, palette);
            var resolved = CirrusThemeIcons.resolve(tooltip.iconTexture(), OreUiPaint.themeOf(palette));
            canvas.texture(resolved, textX + 2, slotY + 2, 14, 14);
            textX += 22;
        }

        Component badge = tooltip.badge();
        Component subtitle = tooltip.subtitle();
        boolean hasSubtitle = subtitle != null && !subtitle.getString().isEmpty();
        int badgeWidth = 0;
        if (badge != null && !badge.getString().isEmpty()) {
            int estimated = ChromeText.width(canvas, badge) + 10;
            int badgeX = Math.max(textX + 24, rightX - estimated);
            int badgeY = hasSubtitle ? headerY + 3 : headerY + Math.max(2, (headerH - OreUiPaint.minPillHeight) / 2);
            badgeWidth = OreUiPaint.tagPill(canvas, badgeX, badgeY, OreUiPaint.minPillHeight, badge, accent, palette)
                    + 4;
        }

        int titleAvail = Math.max(40, rightX - textX - badgeWidth);
        List<FormattedCharSequence> titleLines = splitLines(canvas, tooltip.title(), titleAvail);
        int subAvail = Math.max(40, rightX - textX);
        List<FormattedCharSequence> subLines = hasSubtitle ? splitLines(canvas, subtitle, subAvail) : List.of();
        int totalHeaderTextH = titleLines.size() * lineHeight + (subLines.isEmpty() ? 0 : 1 + subLines.size() * 10);
        int ty = headerY + Math.max(4, (headerH - totalHeaderTextH) / 2);
        for (FormattedCharSequence line : titleLines) {
            canvas.drawString(line, textX, ty, palette.textPrimary(), false);
            ty += lineHeight;
        }
        if (!subLines.isEmpty()) {
            ty += 1;
            for (FormattedCharSequence line : subLines) {
                canvas.drawString(line, textX, ty, palette.textMuted(), false);
                ty += 10;
            }
        }

        int cursorY = headerY + headerH + 1 + bodyPadY;
        int contentW = Math.max(16, w - padX * 2);
        OreUiRichTooltip.Block.FooterHint footer = null;
        int drawnNonFooter = 0;
        GridGeometry interactiveGeometry = null;

        for (int i = 0; i < tooltip.blocks().size(); i++) {
            OreUiRichTooltip.Block block = tooltip.blocks().get(i);
            int bh = measured.blockHeights().get(i);
            if (block instanceof OreUiRichTooltip.Block.FooterHint hint) {
                footer = hint;
                continue;
            }
            if (drawnNonFooter > 0) {
                cursorY += blockGap;
            }
            if (block instanceof OreUiRichTooltip.Block.ItemGrid grid) {
                GridGeometry geom = drawItemGrid(
                    canvas,
                    grid,
                    x + padX,
                    cursorY,
                    contentW,
                    gridState,
                    pinned,
                    palette
                );
                if (grid.isInteractive() && interactiveGeometry == null) {
                    interactiveGeometry = geom;
                }
            } else {
                drawBlock(canvas, block, x + padX, cursorY, contentW, bh, palette);
            }
            cursorY += bh;
            drawnNonFooter++;
        }

        if (footer != null && measured.footerHeight() > 0) {
            int fH = measured.footerHeight();
            int footY = y + h - 3 - fH;
            canvas.fill(x + 1, footY, innerW, 1, OreUiPaint.cardDivider(palette));
            canvas.fill(x + 1, footY + 1, innerW, fH - 1, OreUiPaint.cardFooter(palette));
            if (pinned && footerOverride != null && !footerOverride.getString().isEmpty()) {
                Component fitted = ChromeText.fit(canvas, footerOverride, contentW);
                ChromeText.draw(
                    canvas,
                    fitted,
                    x + padX,
                    ChromeText.centeredY(footY + 1, Math.max(10, fH - 1), canvas),
                    palette.textSecondary()
                );
            } else {
                List<FormattedCharSequence> footLines = splitLines(canvas, footer.hint(), contentW);
                int fy = footY + footerPadY + 1;
                for (FormattedCharSequence line : footLines) {
                    canvas.drawString(line, x + padX, fy, palette.textMuted(), false);
                    fy += 10;
                }
            }
        }

        return new RenderResult(box, interactiveGeometry);
    }

    private static void drawBlock(
        SceneCanvas canvas,
        OreUiRichTooltip.Block block,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette
    ) {
        switch (block) {
            case OreUiRichTooltip.Block.Divider ignored ->
                canvas.fill(x, y + 2, width, 1, OreUiPaint.cardDivider(palette));
            case OreUiRichTooltip.Block.TextLine line -> {
                List<FormattedCharSequence> lines = splitLines(canvas, line.text(), width);
                int ly = y + Math.max(0, (height - lines.size() * lineHeight) / 2) + 1;
                int ink = line.color(palette);
                for (FormattedCharSequence seq : lines) {
                    canvas.drawString(seq, x, ly, ink, false);
                    ly += lineHeight;
                }
            }
            case OreUiRichTooltip.Block.KeyValue kv -> {
                int keyW = ChromeText.width(canvas, kv.key());
                int valW = ChromeText.width(canvas, kv.value());
                if (keyW + valW + 10 <= width) {
                    int ty = ChromeText.centeredY(y, height, canvas);
                    ChromeText.draw(canvas, kv.key(), x, ty, palette.textMuted());
                    ChromeText.drawRight(canvas, kv.value(), x + width, ty, kv.valueColor(palette));
                } else {
                    ChromeText.draw(canvas, kv.key(), x, y + 1, palette.textMuted());
                    List<FormattedCharSequence> valLines = splitLines(canvas, kv.value(), Math.max(40, width - 8));
                    int vy = y + lineHeight + 1;
                    int ink = kv.valueColor(palette);
                    for (FormattedCharSequence seq : valLines) {
                        canvas.drawString(seq, x + 8, vy, ink, false);
                        vy += 10;
                    }
                }
            }
            case OreUiRichTooltip.Block.MetricPills pills -> {
                int cursorX = x;
                int cursorY = y;
                int limitX = x + width;
                for (OreUiRichTooltip.Pill pill : pills.pills()) {
                    int estW = ChromeText.width(canvas, pill.label()) + 10;
                    if (cursorX + estW > limitX && cursorX > x) {
                        cursorX = x;
                        cursorY += OreUiPaint.minPillHeight + pillRowGap;
                    }
                    int used = OreUiPaint.tagPill(
                        canvas,
                        cursorX,
                        cursorY,
                        OreUiPaint.minPillHeight,
                        pill.label(),
                        pill.color(palette),
                        palette
                    );
                    cursorX += used + 4;
                }
            }
            case OreUiRichTooltip.Block.Meter meter -> {
                int readoutW = ChromeText.width(canvas, meter.readout());
                Component fittedLabel = ChromeText.fit(canvas, meter.label(), Math.max(16, width - readoutW - 8));
                ChromeText.draw(canvas, fittedLabel, x, y + 1, palette.textSecondary());
                ChromeText.drawRight(canvas, meter.readout(), x + width, y + 1, meter.fillColor(palette));
                int barY = y + 12;
                int barH = 6;
                OreUiPaint.flatMeter(canvas, x, barY, width, barH, meter.fraction(), meter.fillColor(palette), palette);
                int tickColor = OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.35f);
                for (int q = 1; q <= 3; q++) {
                    int tx = x + 1 + (width - 2) * q / 4;
                    canvas.fill(tx, barY + 1, 1, barH - 2, tickColor);
                }
            }
            case OreUiRichTooltip.Block.ItemFlow flow -> drawItemFlow(canvas, flow, x, y, width, palette);
            case OreUiRichTooltip.Block.ItemList list -> drawItemList(canvas, list, x, y, width, palette);
            case OreUiRichTooltip.Block.ItemGrid ignored -> {}
            case OreUiRichTooltip.Block.FooterHint ignored -> {}
        }
    }

    private record GridMetrics(
        int columns,
        int visibleRows,
        int totalRows,
        int maxRowOffset,
        int frameWidth,
        int frameHeight
    ) {}

    private static GridMetrics measureItemGrid(OreUiRichTooltip.Block.ItemGrid grid, int maxContentW) {
        int count = grid.items().size();
        int chromeW = gridOuterPad * 2 + gridInnerPad * 2;
        int maxColsFit = Math.max(1, (maxContentW - chromeW - gridScrollbarGap - gridScrollbarWidth) / gridCellSize);
        int cols = Math.min(Math.min(grid.maxColumns(), maxColsFit), Math.max(1, count));
        int totalRows = count == 0 ? 1 : (count + cols - 1) / cols;
        int visibleRows = Math.min(grid.maxRows(), Math.max(1, totalRows));
        int maxRowOffset = Math.max(0, totalRows - visibleRows);
        int scrollbarSpace = maxRowOffset > 0 ? gridScrollbarGap + gridScrollbarWidth : 0;
        int frameW = chromeW + cols * gridCellSize + scrollbarSpace;
        int frameH = gridOuterPad * 2 + gridInnerPad * 2 + visibleRows * gridCellSize;
        return new GridMetrics(cols, visibleRows, totalRows, maxRowOffset, frameW, frameH);
    }

    private static GridGeometry drawItemGrid(
        SceneCanvas canvas,
        OreUiRichTooltip.Block.ItemGrid grid,
        int x,
        int y,
        int width,
        GridRenderState state,
        boolean pinned,
        CirrusPalette palette
    ) {
        int cursorY = y;
        if (grid.heading() != null && !grid.heading().getString().isEmpty()) {
            ChromeText.draw(canvas, grid.heading(), x, cursorY, palette.textMuted());
            cursorY += 11;
        }
        GridMetrics gm = measureItemGrid(grid, width);
        int frameW = gm.frameWidth();
        int frameH = gm.frameHeight();
        Rect frameRect = new Rect(x, cursorY, frameW, frameH);
        canvas.fill(frameRect.x(), frameRect.y(), frameRect.width(), frameRect.height(), palette.recessedSurface());
        canvas.strokeRect(
            frameRect.x(),
            frameRect.y(),
            frameRect.width(),
            frameRect.height(),
            OreUiPaint.cardDivider(palette),
            1
        );

        int gridX = x + gridOuterPad + gridInnerPad;
        int gridY = cursorY + gridOuterPad + gridInnerPad;
        int gridW = gm.columns() * gridCellSize;
        int gridH = gm.visibleRows() * gridCellSize;
        Rect gridRect = new Rect(gridX, gridY, gridW, gridH);

        int clampedScroll = Math.max(0, Math.min(gm.maxRowOffset(), state.scrollRow()));
        int count = grid.items().size();
        int hoveredIdx = pinned ? state.hoveredIndex() : -1;

        for (int r = 0; r < gm.visibleRows(); r++) {
            for (int c = 0; c < gm.columns(); c++) {
                int idx = (clampedScroll + r) * gm.columns() + c;
                if (idx >= count) {
                    break;
                }
                int cx = gridX + c * gridCellSize;
                int cy = gridY + r * gridCellSize;
                OreUiPaint.slot(canvas, cx, cy, gridCellSize, palette);
                boolean isHover = idx == hoveredIdx;
                boolean isActive = idx == grid.activeIndex();
                if (isHover) {
                    canvas.fill(
                        cx + 1,
                        cy + 1,
                        gridCellSize - 2,
                        gridCellSize - 2,
                        OreUiPaint.withAlpha(palette.focusRing(), 0.24f)
                    );
                }
                ContentStack<?, ?> stack = grid.items().get(idx);
                CoPilotPaint.icon(canvas, stack, cx + 1, cy + 1, 16, palette);
                if (isHover) {
                    canvas.strokeRect(cx, cy, gridCellSize, gridCellSize, palette.focusRing(), 1);
                } else if (isActive) {
                    canvas.strokeRect(
                        cx,
                        cy,
                        gridCellSize,
                        gridCellSize,
                        OreUiPaint.withAlpha(palette.costS(), 0.65f),
                        1
                    );
                }
            }
        }

        Rect scrollbarRect = null;
        if (gm.maxRowOffset() > 0) {
            int sbX = gridX + gridW + gridScrollbarGap;
            scrollbarRect = new Rect(sbX, gridY, gridScrollbarWidth, gridH);
            canvas.fill(sbX, gridY, gridScrollbarWidth, gridH, OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.35f));
            canvas.strokeRect(sbX, gridY, gridScrollbarWidth, gridH, OreUiPaint.cardDivider(palette), 1);
            int innerH = Math.max(4, gridH - 2);
            int thumbH = Math.max(12, Math.round(innerH * (gm.visibleRows() / (float) Math.max(1, gm.totalRows()))));
            int travel = Math.max(0, innerH - thumbH);
            int thumbY = gridY + 1 + Math.round(travel * (clampedScroll / (float) gm.maxRowOffset()));
            boolean thumbActive = pinned && (state.scrollbarDragging() || state.scrollbarHovered());
            int thumbColor = thumbActive
                    ? palette.focusRing()
                    : OreUiPaint.withAlpha(palette.textMuted(), 0.85f);
            canvas.fill(sbX + 1, thumbY, Math.max(2, gridScrollbarWidth - 2), thumbH, thumbColor);
        }

        return new GridGeometry(
            frameRect,
            gridRect,
            scrollbarRect,
            gm.columns(),
            gm.visibleRows(),
            gm.totalRows(),
            gm.maxRowOffset(),
            gridCellSize,
            count
        );
    }

    private static int measurePillRowsHeight(OreUiRichTooltip.Block.MetricPills pills, @Nullable Font font, int width) {
        if (pills.pills().isEmpty()) {
            return pillsHeight;
        }
        int rows = 1;
        int cursorX = 0;
        for (OreUiRichTooltip.Pill pill : pills.pills()) {
            int estW = textWidth(font, pill.label()) + 10;
            if (cursorX > 0 && cursorX + estW > width) {
                rows++;
                cursorX = estW + 4;
            } else {
                cursorX += estW + 4;
            }
        }
        return rows * OreUiPaint.minPillHeight + (rows - 1) * pillRowGap;
    }

    private static void drawItemFlow(
        SceneCanvas canvas,
        OreUiRichTooltip.Block.ItemFlow flow,
        int x,
        int y,
        int width,
        CirrusPalette palette
    ) {
        int rowY = y;
        if (flow.label() != null && !flow.label().getString().isEmpty()) {
            ChromeText.draw(canvas, flow.label(), x, rowY, palette.textMuted());
            rowY += 11;
        }
        List<OreUiRecipeGrid.SlotEntry> ins = toGridEntries(flow.inputs());
        List<OreUiRecipeGrid.SlotEntry> outs = toGridEntries(flow.outputs());
        OreUiRecipeGrid.Layout grid = OreUiRecipeGrid
                .measure(ins.size(), outs.size(), flowSlotSize, Math.max(48, width - 8), 3);
        int boxH = grid.height() + 6;
        canvas.fill(x, rowY, width, boxH, palette.recessedSurface());
        canvas.strokeRect(x, rowY, width, boxH, OreUiPaint.cardDivider(palette), 1);
        OreUiRecipeGrid.render(canvas, ins, outs, grid, x + 4, rowY + 3, palette);
    }

    private static OreUiRecipeGrid.Layout measureFlowGrid(OreUiRichTooltip.Block.ItemFlow flow, int maxContentW) {
        return OreUiRecipeGrid
                .measure(flow.inputs().size(), flow.outputs().size(), flowSlotSize, Math.max(48, maxContentW - 8), 3);
    }

    private static List<OreUiRecipeGrid.SlotEntry> toGridEntries(List<OreUiRichTooltip.ItemBadge> badges) {
        List<OreUiRecipeGrid.SlotEntry> out = new ArrayList<>(badges.size());
        for (OreUiRichTooltip.ItemBadge badge : badges) {
            long count = badge.countLabel() == null ? 1L : OreUiSlotAmount.parseCount(badge.countLabel().getString());
            out.add(OreUiRecipeGrid.SlotEntry.ofStack(badge.stack(), Math.max(1L, count)));
        }
        return List.copyOf(out);
    }

    private static void drawItemList(
        SceneCanvas canvas,
        OreUiRichTooltip.Block.ItemList list,
        int x,
        int y,
        int width,
        CirrusPalette palette
    ) {
        int cursorY = y;
        if (list.heading() != null && !list.heading().getString().isEmpty()) {
            ChromeText.draw(canvas, list.heading(), x, cursorY, palette.textMuted());
            cursorY += 11;
        }
        int visible = Math.min(list.maxVisible(), list.entries().size());
        for (int i = 0; i < visible; i++) {
            OreUiRichTooltip.ItemEntry entry = list.entries().get(i);
            if (i % 2 == 1) {
                canvas.fill(x, cursorY, width, listRowHeight, OreUiPaint.withAlpha(palette.recessedSurface(), 0.65f));
            }
            CoPilotPaint.renderSlotItem(canvas, entry.stack(), x, cursorY + 1, 14, palette);
            int trailingW = entry.trailing() == null ? 0 : ChromeText.width(canvas, entry.trailing()) + 6;
            Component fitted = ChromeText.fit(canvas, entry.name(), Math.max(16, width - 18 - trailingW));
            int ty = ChromeText.centeredY(cursorY, listRowHeight, canvas);
            ChromeText.draw(canvas, fitted, x + 18, ty, palette.textPrimary());
            if (entry.trailing() != null && !entry.trailing().getString().isEmpty()) {
                ChromeText.drawRight(canvas, entry.trailing(), x + width - 2, ty, entry.trailingColor(palette));
            }
            cursorY += listRowHeight;
        }
        if (list.entries().size() > visible) {
            Component more = Component.literal("+" + (list.entries().size() - visible) + " more");
            ChromeText.draw(canvas, more, x + 18, cursorY + 1, palette.textMuted());
        }
    }

    private static List<FormattedCharSequence> splitLines(SceneCanvas canvas, @Nullable Component text, int maxW) {
        if (text == null || text.getString().isEmpty()) {
            return List.of();
        }
        Font font = ChromeText.font(canvas);
        if (font != null) {
            List<FormattedCharSequence> split = font.split(text, Math.max(32, maxW));
            return split.isEmpty() ? List.of(text.getVisualOrderText()) : split;
        }
        return List.of(text.getVisualOrderText());
    }

    private static int wrapCount(@Nullable Font font, @Nullable Component text, int maxW) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        int safeW = Math.max(32, maxW);
        if (font != null) {
            return Math.max(1, font.split(text, safeW).size());
        }
        return Math.max(1, (int) Math.ceil(text.getString().length() * 6.0 / safeW));
    }

    private static int textWidth(@Nullable Font font, @Nullable Component text) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        return font != null ? font.width(text) : text.getString().length() * 6;
    }
}
