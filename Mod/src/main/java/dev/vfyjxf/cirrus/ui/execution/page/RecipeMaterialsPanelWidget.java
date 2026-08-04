package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

final class RecipeMaterialsPanelWidget extends Widget {
    private static final int padding = 3;
    private static final int paddingCompact = 2;
    private static final int paddingDense = 2;
    private static final int toolbarHeight = 22;
    private static final int toolbarHeightDense = 22;
    private static final int displayModeIconSize = 16;
    private static final int displayModeButtonPadding = 3;
    private static final int buttonSize = displayModeIconSize + displayModeButtonPadding * 2;
    private static final int buttonSizeDense = buttonSize;
    private static final int gridTopGap = 1;
    private static final int gridGap = 0;
    private static final int gridGapDense = 0;
    private static final int cellWidth = 36;
    private static final int cellWidthCompact = 34;
    private static final int cellWidthDense = 32;
    private static final int cellHeight = 22;
    private static final int cellHeightDense = 22;
    private static final int contentVisualSize = 16;
    private static final int cellHorizontalPadding = 4;
    private static final int iconMetricGap = 2;
    private static final int minMetricWidth = 6;
    private static final int scrollbarWidth = 4;
    private static final int scrollbarWidthDense = 3;
    private static final int scrollbarGap = 2;

    private final ExecutionOverlayViewState state;
    private final ArrayList<InteractiveArea> interactiveAreas = new ArrayList<>();
    private DisplayMode displayMode = DisplayMode.missing;
    private RecipePage.LayoutDensity layoutDensity = RecipePage.LayoutDensity.normal;

    private final ScrollState materialsScrollState = CirrusScroll.longVerticalState(10);
    private float scrollY;
    private float maxScrollY;

    RecipeMaterialsPanelWidget(ExecutionOverlayViewState state) {
        this.state = state;
        useStyle(background(Background.Outlined.flat), minWidth(0), minHeight(0), boxSizingBorderBox());
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());
            for (InteractiveArea area : interactiveAreas) {
                if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY) && area.action() != null) {
                    area.action().run();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
            if (scrollY == 0) {
                return EventDispatch.pass;
            }
            int localX = (int) Math.round(mouseX);
            int localY = (int) Math.round(mouseY);
            Rect2i viewport = materialsViewportArea();
            if (!ExecutionOverlayUi.contains(viewport, localX, localY)) {
                return EventDispatch.pass;
            }
            if (maxScrollY <= 0f) {
                return EventDispatch.pass;
            }
            this.scrollY = clamp(this.scrollY + materialsScrollState.wheelDelta(scrollX, scrollY).y(), 0f, maxScrollY);
            return EventDispatch.consumed;
        });
    }

    void setLayoutDensity(RecipePage.LayoutDensity density) {
        layoutDensity = density == null ? RecipePage.LayoutDensity.normal : density;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        interactiveAreas.clear();
        Font font = context().font();
        int pad = resolvePadding(layoutDensity);
        int toolbarH = resolveToolbarHeight(layoutDensity);
        int contentW = Math.max(0, width() - pad * 2);
        int contentH = Math.max(0, height() - pad * 2);
        if (contentW <= 0 || contentH <= 0) {
            return;
        }

        drawToolbar(canvas, font, pad, pad, contentW, toolbarH, mouseX, mouseY);
        int gridY = pad + toolbarH + gridTopGap;
        int gridH = Math.max(0, height() - pad - gridY);
        drawMaterials(canvas, font, pad, gridY, contentW, gridH, mouseX, mouseY);
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        for (InteractiveArea area : interactiveAreas) {
            if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY)) {
                Tooltip tooltip = area.tooltip().get();
                if (tooltip != null && tooltip.notEmpty()) {
                    return tooltip;
                }
            }
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    private void drawToolbar(SceneCanvas canvas, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int button = resolveButtonSize(layoutDensity);
        Rect2i modeButton = new Rect2i(x + width - button, y, button, height);

        int titleWidth = Math.max(0, modeButton.getX() - x - 4);
        String title = titleText(font, titleWidth);
        int titleY = y + Math.max(0, (height - font.lineHeight) / 2);
        canvas.drawString(title, x, titleY, ExecutionOverlayUi.titleColor, false);

        boolean hovered = hover(modeButton, mouseX, mouseY);
        drawIconButton(canvas, modeButton, displayMode.icon(hovered), displayMode.activeColor(), hovered, true);
        interactiveAreas.add(new InteractiveArea(modeButton, this::displayModeTooltip, this::toggleDisplayMode));
    }

    private String titleText(Font font, int width) {
        List<OverlayMaterialRequirement> entries = visibleRequirements();
        String title = displayMode.label() + " " + entries.size();
        return ExecutionOverlayUi.trim(font, title, width);
    }

    private void drawIconButton(
        SceneCanvas canvas,
        Rect2i area,
        SizedTexture icon,
        int accent,
        boolean hovered,
        boolean enabled
    ) {
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), false, hovered, enabled);
        int iconX = area.getX() + CirrusControlChrome.centeredIconX(area.getWidth(), icon.width());
        int iconY = area.getY() + CirrusControlChrome.centeredIconY(area.getHeight(), icon.height(), false);
        canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
        if (hovered) {
            canvas.fill(area.getX() + 3, area.getY() + area.getHeight() - 2, Math.max(1, area.getWidth() - 6), 1, accent);
        }
    }

    private void drawMaterials(SceneCanvas canvas, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<OverlayMaterialRequirement> requirements = visibleRequirements();
        if (requirements.isEmpty()) {
            maxScrollY = 0f;
            scrollY = 0f;
            String text = state.calculating()
                ? ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculatingShort)
                : ExecutionOverlayUi.tr(displayMode.emptyLabel());
            canvas.drawString(ExecutionOverlayUi.trim(font, text, width), x, y + 2, ExecutionOverlayUi.textSecondary, false);
            return;
        }

        GridMetrics metrics = gridMetrics(font, width, height, requirements);
        maxScrollY = Math.max(0, metrics.contentHeight() - metrics.viewportHeight());
        int gap = metrics.gap();
        int cellH = metrics.cellHeight();
        int columns = metrics.columns();
        int viewportW = metrics.contentWidth();
        int startX = x;

        clampScrollY(maxScrollY);
        int scrolledY = y - Math.round(scrollY);
        Rect2i viewport = new Rect2i(x, y, viewportW, height);
        int absX = absolutePos().x() + viewport.getX();
        int absY = absolutePos().y() + viewport.getY();
        canvas.pushClip(absX, absY, viewport.getWidth(), viewport.getHeight());

        for (int index = 0; index < requirements.size(); index++) {
            int col = index % columns;
            int row = index / columns;
            int cellW = metrics.widthForColumn(col);
            int cellX = startX + metrics.xForColumn(col);
            int cellY = scrolledY + row * (cellH + gap);
            if (cellY + cellH < y) continue;
            if (cellY > y + height) break;
            Rect2i cell = new Rect2i(cellX, cellY, cellW, cellH);
            OverlayMaterialRequirement requirement = requirements.get(index);
            boolean hovered = hover(cell, mouseX, mouseY) && hover(viewport, mouseX, mouseY);
            drawMaterialCell(canvas, font, cell, requirement, hovered);
            Rect2i interactive = intersection(cell, viewport);
            if (interactive != null) {
                interactiveAreas.add(new InteractiveArea(interactive, () -> materialTooltip(requirement), null));
            }
        }
        canvas.popClip();
        drawScrollbar(canvas, x, y, width, height, metrics);
    }

    private void drawMaterialCell(SceneCanvas canvas, Font font, Rect2i cell, OverlayMaterialRequirement requirement, boolean hovered) {
        canvas.texture(Background.Outlined.inset, cell.getX(), cell.getY(), cell.getWidth(), cell.getHeight());
        if (hovered) {
            canvas.fill(cell.getX() + 1, cell.getY() + 1, Math.max(0, cell.getWidth() - 2), Math.max(0, cell.getHeight() - 2), 0x12FFFFFF);
        }

        RecipeContent content = requirement.material();
        int slotX = cell.getX() + cellHorizontalPadding;
        int slotY = cell.getY() + Math.max(1, (cell.getHeight() - contentVisualSize) / 2);
        if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            ExecutionOverlayContentHelper.renderRecipeContent(canvas, content, slotX, slotY, contentVisualSize, contentVisualSize, 0);
        } else {
            String token = ExecutionOverlayContentHelper.fallbackToken(content);
            canvas.drawString(
                token,
                slotX + Math.max(1, (contentVisualSize - font.width(token)) / 2),
                slotY + 4,
                ExecutionOverlayUi.textSecondary,
                false
            );
        }

        int metricX = slotX + contentVisualSize + iconMetricGap;
        int metricY = cell.getY() + Math.max(1, (cell.getHeight() - font.lineHeight) / 2);
        int metricW = Math.max(0, cell.getX() + cell.getWidth() - metricX - cellHorizontalPadding);
        drawMetric(canvas, font, metricX, metricY, metricW, displayMode.activeColor(), displayMode.value(requirement), displayMode.emphasize(requirement));
    }

    private void drawMetric(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        int color,
        long value,
        boolean emphasize
    ) {
        if (width <= 0) {
            return;
        }
        String text = ExecutionOverlayUi.compactNumber(value);
        canvas.drawString(
            ExecutionOverlayUi.trim(font, text, width),
            x,
            y,
            emphasize ? color : ExecutionOverlayUi.textPrimary,
            false
        );
    }

    private void clampScrollY(float maxScrollY) {
        scrollY = clamp(scrollY, 0f, maxScrollY);
    }

    private Rect2i materialsViewportArea() {
        int pad = resolvePadding(layoutDensity);
        int toolbarH = resolveToolbarHeight(layoutDensity);
        int contentW = Math.max(0, width() - pad * 2);
        int gridY = pad + toolbarH + gridTopGap;
        int gridH = Math.max(0, height() - pad - gridY);
        return new Rect2i(pad, gridY, contentW, gridH);
    }

    private GridMetrics gridMetrics(Font font, int width, int height, List<OverlayMaterialRequirement> requirements) {
        int count = requirements.size();
        int gap = resolveGridGap(layoutDensity);
        int cellH = resolveCellHeight(layoutDensity);
        int[] itemWidths = itemWidths(font, requirements);
        int columns = resolveColumns(width, itemWidths, gap);
        int[] columnWidths = columnWidths(itemWidths, columns);
        int contentH = gridContentHeight(count, columns, cellH, gap);
        boolean overflow = contentH > height;
        int barW = overflow ? resolveScrollbarWidth(layoutDensity) : 0;
        int contentW = overflow ? Math.max(0, width - barW - scrollbarGap) : width;
        if (overflow) {
            columns = resolveColumns(contentW, itemWidths, gap);
            columnWidths = columnWidths(itemWidths, columns);
            contentH = gridContentHeight(count, columns, cellH, gap);
            overflow = contentH > height;
            if (!overflow) {
                barW = 0;
                contentW = width;
            }
        }
        return new GridMetrics(
            Math.max(0, contentW),
            Math.max(1, columns),
            gap,
            columnWidths,
            cellH,
            contentH,
            Math.max(0, height),
            overflow,
            barW
        );
    }

    private int[] itemWidths(Font font, List<OverlayMaterialRequirement> requirements) {
        int minWidth = resolveCellWidth(layoutDensity);
        int[] widths = new int[requirements.size()];
        for (int index = 0; index < requirements.size(); index++) {
            String text = ExecutionOverlayUi.compactNumber(displayMode.value(requirements.get(index)));
            int textWidth = Math.max(minMetricWidth, font.width(text));
            widths[index] = Math.max(minWidth, cellHorizontalPadding * 2 + contentVisualSize + iconMetricGap + textWidth);
        }
        return widths;
    }

    private static int resolveColumns(int width, int[] itemWidths, int gap) {
        if (itemWidths.length <= 0 || width <= 0) {
            return 1;
        }
        int best = 1;
        int maxColumns = itemWidths.length;
        for (int columns = 1; columns <= maxColumns; columns++) {
            int used = usedWidth(columnWidths(itemWidths, columns), gap);
            if (used > width) {
                break;
            }
            best = columns;
        }
        return best;
    }

    private static int[] columnWidths(int[] itemWidths, int columns) {
        int safeColumns = Math.max(1, columns);
        int[] out = new int[safeColumns];
        for (int index = 0; index < itemWidths.length; index++) {
            int column = index % safeColumns;
            out[column] = Math.max(out[column], itemWidths[index]);
        }
        return out;
    }

    private static int usedWidth(int[] columnWidths, int gap) {
        if (columnWidths.length == 0) {
            return 0;
        }
        int width = Math.max(0, columnWidths.length - 1) * gap;
        for (int columnWidth : columnWidths) {
            width += columnWidth;
        }
        return width;
    }

    private static int gridContentHeight(int count, int columns, int cellH, int gap) {
        if (count <= 0) {
            return 0;
        }
        int rows = (count + Math.max(1, columns) - 1) / Math.max(1, columns);
        return rows * cellH + Math.max(0, rows - 1) * gap;
    }

    private void drawScrollbar(SceneCanvas canvas, int x, int y, int width, int height, GridMetrics metrics) {
        if (!metrics.overflow() || height <= 0) {
            return;
        }
        int trackW = Math.max(1, metrics.scrollbarWidth());
        int trackX = x + Math.max(0, width - trackW);
        canvas.texture(Background.scroll, trackX, y, trackW, height);
        int maxScroll = Math.max(1, metrics.contentHeight() - metrics.viewportHeight());
        int thumbH = Math.min(height, Math.max(10, (int) ((double) height / Math.max(1, metrics.contentHeight()) * height)));
        int travel = Math.max(0, height - thumbH);
        int thumbY = y + Math.round((scrollY / maxScroll) * travel);
        canvas.texture(CirrusTextures.Icon.scrollBarVertical, trackX, thumbY, trackW, thumbH);
    }

    private static @Nullable Rect2i intersection(Rect2i left, Rect2i right) {
        int x1 = Math.max(left.getX(), right.getX());
        int y1 = Math.max(left.getY(), right.getY());
        int x2 = Math.min(left.getX() + left.getWidth(), right.getX() + right.getWidth());
        int y2 = Math.min(left.getY() + left.getHeight(), right.getY() + right.getHeight());
        if (x2 <= x1 || y2 <= y1) {
            return null;
        }
        return new Rect2i(x1, y1, x2 - x1, y2 - y1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<OverlayMaterialRequirement> visibleRequirements() {
        ExecutionDriverSnapshot snapshot = state.snapshot();
        if (snapshot == null || snapshot.materials().isEmpty()) {
            return List.of();
        }
        ArrayList<OverlayMaterialRequirement> out = new ArrayList<>();
        for (OverlayMaterialRequirement requirement : snapshot.materials()) {
            if (displayMode.accepts(requirement)) {
                out.add(requirement);
            }
        }
        out.sort(displayMode::compare);
        return out;
    }

    private void toggleDisplayMode() {
        displayMode = displayMode == DisplayMode.missing ? DisplayMode.total : DisplayMode.missing;
        materialsScrollState.resetScroll();
        scrollY = 0f;
        ExecutionOverlayUi.playClickSound();
    }

    private Tooltip displayModeTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(displayMode.label()));
        tooltip.add(ExecutionOverlayUi.tooltipBodyText(displayMode.next().label()));
        return tooltip;
    }

    private Tooltip materialTooltip(OverlayMaterialRequirement requirement) {
        Tooltip tooltip = Tooltip.create();
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(requirement.material());
        if (stack != null) {
            tooltip.setStack(stack);
        }
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(label(requirement)));
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.required()),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.available()),
            ExecutionOverlayUi.tooltipSuccess
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.miss),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.missing()),
            requirement.missing() > 0 ? ExecutionOverlayUi.tooltipWarning : ExecutionOverlayUi.tooltipTextSecondary
        );
        tooltip.add(builder.build());
        return tooltip;
    }

    private static String label(OverlayMaterialRequirement requirement) {
        String label = ExecutionOverlayContentHelper.label(requirement.material());
        return label == null || label.isBlank() ? "-" : label;
    }

    private static boolean hover(Rect2i area, int mouseX, int mouseY) {
        return ExecutionOverlayUi.contains(area, mouseX, mouseY);
    }

    private static int resolvePadding(RecipePage.LayoutDensity density) {
        return switch (density) {
            case normal -> padding;
            case compact -> paddingCompact;
            case dense -> paddingDense;
        };
    }

    static int resolveToolbarHeight(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? toolbarHeightDense : toolbarHeight;
    }

    static int resolveButtonSize(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? buttonSizeDense : buttonSize;
    }

    static int resolveGridGap(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? gridGapDense : gridGap;
    }

    static int resolveCellWidth(RecipePage.LayoutDensity density) {
        return switch (density) {
            case normal -> cellWidth;
            case compact -> cellWidthCompact;
            case dense -> cellWidthDense;
        };
    }

    static int resolveCellHeight(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? cellHeightDense : cellHeight;
    }

    private static int resolveScrollbarWidth(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? scrollbarWidthDense : scrollbarWidth;
    }

    private enum DisplayMode {
        missing(
            CirrusLangs.Execution.Overlay.Material.viewMissing,
            CirrusLangs.Execution.Overlay.Material.noneMissing,
            CirrusTextures.Icon.Calc.Sort.missingFirst,
            CirrusTextures.Icon.Calc.Sort.missingFirstPressed,
            ExecutionOverlayUi.warning
        ),
        total(
            CirrusLangs.Execution.Overlay.Material.viewTotal,
            CirrusLangs.Execution.Overlay.Material.none,
            CirrusTextures.Icon.Calc.Sort.number,
            CirrusTextures.Icon.Calc.Sort.numberPressed,
            ExecutionOverlayUi.accent
        );

        private final LangEntry label;
        private final LangEntry emptyLabel;
        private final SizedTexture icon;
        private final SizedTexture hoverIcon;
        private final int color;

        DisplayMode(LangEntry label, LangEntry emptyLabel, SizedTexture icon, SizedTexture hoverIcon, int color) {
            this.label = label;
            this.emptyLabel = emptyLabel;
            this.icon = icon;
            this.hoverIcon = hoverIcon;
            this.color = color;
        }

        private boolean accepts(OverlayMaterialRequirement requirement) {
            return switch (this) {
                case missing -> requirement.missing() > 0L;
                case total -> true;
            };
        }

        private long value(OverlayMaterialRequirement requirement) {
            return switch (this) {
                case missing -> requirement.missing();
                case total -> requirement.required();
            };
        }

        private boolean emphasize(OverlayMaterialRequirement requirement) {
            return this == missing && requirement.missing() > 0L;
        }

        private int compare(OverlayMaterialRequirement left, OverlayMaterialRequirement right) {
            int byValue = Long.compare(value(right), value(left));
            if (byValue != 0) {
                return byValue;
            }
            int byMissing = Long.compare(right.missing(), left.missing());
            if (byMissing != 0) {
                return byMissing;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                RecipeMaterialsPanelWidget.label(left),
                RecipeMaterialsPanelWidget.label(right)
            );
        }

        private DisplayMode next() {
            return switch (this) {
                case missing -> total;
                case total -> missing;
            };
        }

        private LangEntry emptyLabel() {
            return emptyLabel;
        }

        private String label() {
            return ExecutionOverlayUi.tr(label);
        }

        private SizedTexture icon(boolean hovered) {
            return hovered ? hoverIcon : icon;
        }

        private int activeColor() {
            return color;
        }
    }

    private record InteractiveArea(Rect2i bounds, Supplier<Tooltip> tooltip, @Nullable Runnable action) {
    }

    private record GridMetrics(
        int contentWidth,
        int columns,
        int gap,
        int[] columnWidths,
        int cellHeight,
        int contentHeight,
        int viewportHeight,
        boolean overflow,
        int scrollbarWidth
    ) {
        int widthForColumn(int column) {
            if (columnWidths.length == 0) {
                return 0;
            }
            return columnWidths[Math.max(0, Math.min(column, columnWidths.length - 1))];
        }

        int xForColumn(int column) {
            int x = 0;
            int limit = Math.max(0, Math.min(column, columnWidths.length));
            for (int index = 0; index < limit; index++) {
                x += columnWidths[index] + gap;
            }
            return x;
        }
    }
}
