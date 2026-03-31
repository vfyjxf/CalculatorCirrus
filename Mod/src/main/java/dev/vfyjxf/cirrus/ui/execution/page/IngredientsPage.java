package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;

public class IngredientsPage extends Widget {

    private static final int pad = ExecutionOverlayUi.bodyPadding;
    private static final int toolbarHeight = 20;
    private static final int toolbarHeightCompact = 20;
    private static final int modeButtonWidth = 20;
    private static final int sortButtonWidth = 20;
    private static final int sortDirectionButtonWidth = 20;
    private static final int segmentGap = 3;
    private static final int listRowHeight = 22;
    private static final int listRowExpandedHeight = 34;
    private static final int listRowGap = 1;
    private static final int listNameMaxLines = 2;
    private static final int sectionGap = 8;
    private static final int sectionGapCompact = 5;
    private static final int sectionHeaderHeight = 18;
    private static final int sectionHeaderHeightCompact = 16;
    private static final int gridCellWidth = 66;
    private static final int gridCellWidthMin = 48;
    private static final int gridCellHeight = 38;
    private static final int gridCellHeightCompact = 34;
    private static final int gridGap = 3;
    private static final int gridGapCompact = 2;
    private static final int gridCellPad = 4;
    private static final int gridCellPadCompact = 3;
    private static final int gridMetricRowGap = 1;
    private static final int metricMarkerWidth = 2;
    private static final int metricMarkerGap = 3;
    private static final int listMetricGap = 4;
    private static final int listMetricValueGap = 2;
    private static final int scrollbarWidth = 6;
    private static final int scrollbarGap = 2;

    private final ExecutionOverlayViewState state;

    private ViewMode viewMode = ViewMode.grid;
    private SortMode sortMode = SortMode.missingFirst;
    private SortDirection sortDirection = SortDirection.ascending;
    private final List<InteractiveArea> interactiveAreas = new ArrayList<>();
    private float scrollY;
    private float targetScrollY;
    private boolean draggingScrollbar;
    private float scrollbarDragOffsetY;
    private final ScrollState wheelScrollState = CirrusScroll.longVerticalState(18);

    public IngredientsPage(ExecutionOverlayViewState state) {
        this.state = state;
        useStyle(background(Background.Outlined.flat));
        setTickable(true);
        onMouseScrolled((mouseX, mouseY, scrollX, scrollDelta, context) -> {
            Rect2i area = contentViewportArea();
            int localX = (int) Math.round(mouseX);
            int localY = (int) Math.round(mouseY);
            if (!ExecutionOverlayUi.contains(area, localX, localY)) {
                return EventDispatch.pass;
            }
            targetScrollY = clamp(targetScrollY + wheelScrollState.wheelDelta(0, scrollDelta).y(), 0f, maxScroll());
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                stopScrollbarDrag();
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());

            Rect2i thumb = scrollbarThumbArea();
            if (thumb != null && ExecutionOverlayUi.contains(thumb, mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarDragOffsetY = mouseY - thumb.getY();
                return EventDispatch.consumed;
            }
            stopScrollbarDrag();
            Rect2i track = scrollbarTrackArea();
            if (track != null && ExecutionOverlayUi.contains(track, mouseX, mouseY)) {
                targetScrollY = jumpScrollTo(mouseY);
                scrollY = targetScrollY;
                return EventDispatch.consumed;
            }

            for (InteractiveArea area : interactiveAreas) {
                if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY)) {
                    if (area.action() != null) {
                        area.action().run();
                        return EventDispatch.consumed;
                    }
                }
            }
            return EventDispatch.pass;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!draggingScrollbar) {
                return EventDispatch.pass;
            }
            if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
                stopScrollbarDrag();
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            targetScrollY = dragScrollTo((float) relative.y());
            scrollY = targetScrollY;
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            if (!draggingScrollbar) {
                return EventDispatch.pass;
            }
            stopScrollbarDrag();
            return EventDispatch.consumed;
        });
        onMouseLeave((mouseX, mouseY, context) -> stopScrollbarDrag());
    }

    @Override
    public void tick() {
        super.tick();
        float delta = targetScrollY - scrollY;
        if (Math.abs(delta) < 0.5f) {
            scrollY = targetScrollY;
        } else {
            scrollY += delta * 0.35f;
        }
        float max = maxScroll();
        if (scrollY > max) {
            scrollY = max;
        }
        if (targetScrollY > max) {
            targetScrollY = max;
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        interactiveAreas.clear();
        Font font = context().font();
        int pagePad = resolvePagePadding(width(), height());
        int x = pagePad;
        int y = pagePad;
        int availableWidth = Math.max(0, width() - pagePad * 2);
        int bottom = height() - pagePad;

        y = drawToolbar(canvas, x, y, availableWidth, mouseX, mouseY);
        y += 4;

        if (state.calculating()) {
            Rect2i viewport = new Rect2i(x, y, availableWidth, Math.max(0, bottom - y));
            canvas.drawString(
                ExecutionOverlayUi.trim(font, ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculating), viewport.getWidth()),
                viewport.getX(),
                viewport.getY(),
                ExecutionOverlayUi.textSecondary,
                false
            );
            return;
        }

        MaterialSnapshot snapshot = buildMaterialSnapshot();
        Rect2i viewport = new Rect2i(x, y, availableWidth, Math.max(0, bottom - y));
        int contentWidth = scrollableContentWidth(viewport.getWidth());
        int contentHeight = measureContentHeight(font, contentWidth, snapshot);
        syncScrollBounds(contentHeight, viewport.getHeight());

        if (snapshot.totalCount() <= 0) {
            String message = ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.none);
            canvas.drawString(ExecutionOverlayUi.trim(font, message, viewport.getWidth()), viewport.getX(), viewport.getY(), ExecutionOverlayUi.textSecondary, false);
            return;
        }

        Rect2i clipArea = new Rect2i(viewport.getX(), viewport.getY(), contentWidth, viewport.getHeight());
        int absX = absolutePos().x() + clipArea.getX();
        int absY = absolutePos().y() + clipArea.getY();
        canvas.pushClip(absX, absY, clipArea.getWidth(), clipArea.getHeight());

        int contentY = viewport.getY() - Math.round(scrollY);
        int viewportTop = viewport.getY();
        int viewportBottom = viewport.getY() + viewport.getHeight();
        contentY = drawSection(canvas, font, viewport.getX(), contentY, contentWidth, viewportTop, viewportBottom, snapshot, MaterialCategory.missing, mouseX, mouseY);
        contentY = drawSection(canvas, font, viewport.getX(), contentY, contentWidth, viewportTop, viewportBottom, snapshot, MaterialCategory.available, mouseX, mouseY);
        drawSection(canvas, font, viewport.getX(), contentY, contentWidth, viewportTop, viewportBottom, snapshot, MaterialCategory.catalyst, mouseX, mouseY);

        canvas.popClip();
        drawScrollbar(canvas, viewport, contentHeight);
    }

    private int drawToolbar(SceneCanvas canvas, int x, int y, int width, int mouseX, int mouseY) {
        int toolbarHeight = resolveToolbarHeight(width(), height());
        Rect2i modeButton = new Rect2i(x, y, resolveModeButtonWidth(), toolbarHeight);
        Rect2i sortButton = new Rect2i(modeButton.getX() + modeButton.getWidth() + segmentGap, y, resolveSortButtonWidth(), toolbarHeight);
        Rect2i directionButton = new Rect2i(sortButton.getX() + sortButton.getWidth() + segmentGap, y, resolveSortDirectionButtonWidth(), toolbarHeight);
        drawViewToggleButton(canvas, modeButton, mouseX, mouseY);
        drawSortButton(canvas, sortButton, mouseX, mouseY);
        drawSortDirectionButton(canvas, directionButton, mouseX, mouseY);

        return y + toolbarHeight;
    }

    private int drawSection(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        int viewportTop,
        int viewportBottom,
        MaterialSnapshot snapshot,
        MaterialCategory category,
        int mouseX,
        int mouseY
    ) {
        List<MaterialEntry> entries = snapshot.entries(category, sortMode, sortDirection);
        if (entries.isEmpty()) {
            return y;
        }
        int sectionGap = resolveSectionGap(width, height());
        int sectionHeaderHeight = resolveSectionHeaderHeight(width, height());
        y += sectionGap;
        if (intersectsViewport(y, sectionHeaderHeight, viewportTop, viewportBottom)) {
            drawSectionHeader(canvas, font, x, y, width, category, entries.size());
        }
        y += sectionHeaderHeight + 4;
        if (viewMode == ViewMode.grid) {
            return drawGridSection(canvas, font, x, y, width, viewportTop, viewportBottom, entries, category, mouseX, mouseY);
        }
        return drawListSection(canvas, font, x, y, width, viewportTop, viewportBottom, entries, category, mouseX, mouseY);
    }

    private void drawSectionHeader(SceneCanvas canvas, Font font, int x, int y, int width, MaterialCategory category, int count) {
        int sectionHeaderHeight = resolveSectionHeaderHeight(width(), height());
        String countText = Integer.toString(count);
        int textY = y + Math.max(0, (sectionHeaderHeight - font.lineHeight) / 2);
        int countWidth = font.width(countText);
        int countX = x + Math.max(0, width - countWidth - 6);

        canvas.texture(Background.Border.light, x, y, width, sectionHeaderHeight);
        canvas.fill(x + 3, y + 3, 2, sectionHeaderHeight - 6, category.accentColor());
        int titleX = x + 8;
        int titleWidth = Math.max(0, countX - titleX - 4);
        if (titleWidth > 0) {
            canvas.drawString(ExecutionOverlayUi.trim(font, category.label(), titleWidth), titleX, textY, ExecutionOverlayUi.textPrimary, false);
        }
        canvas.drawString(countText, countX, textY, category.accentColor(), false);
    }

    private int drawGridSection(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        int viewportTop,
        int viewportBottom,
        List<MaterialEntry> entries,
        MaterialCategory category,
        int mouseX,
        int mouseY
    ) {
        int pageHeight = height();
        int cellGap = resolveGridGap(width, pageHeight);
        int cellWidth = resolveGridCellWidth(width);
        int cellHeight = resolveGridCellHeight(width, pageHeight);
        int columns = resolveGridColumns(width);
        int usedWidth = columns * cellWidth + Math.max(0, columns - 1) * cellGap;
        int startX = x + Math.max(0, (width - usedWidth) / 2);
        for (int index = 0; index < entries.size(); index++) {
            int col = index % columns;
            int row = index / columns;
            int cellX = startX + col * (cellWidth + cellGap);
            int cellY = y + row * (cellHeight + cellGap);
            if (cellY > viewportBottom) {
                break;
            }
            if (!intersectsViewport(cellY, cellHeight, viewportTop, viewportBottom)) {
                continue;
            }
            MaterialEntry entry = entries.get(index);
            Rect2i cell = new Rect2i(cellX, cellY, cellWidth, cellHeight);
            boolean hovered = hover(cell, mouseX, mouseY);
            drawGridCell(canvas, font, cell, entry, category, hovered);
            Tooltip tooltip = materialTooltip(entry, category);
            interactiveAreas.add(new InteractiveArea(cell, () -> tooltip, null));
        }
        int rows = (int) Math.ceil(entries.size() / (double) columns);
        return y + rows * cellHeight + Math.max(0, rows - 1) * cellGap;
    }

    private void drawGridCell(SceneCanvas canvas, Font font, Rect2i cell, MaterialEntry entry, MaterialCategory category, boolean hovered) {
        int x = cell.getX();
        int y = cell.getY();
        int width = cell.getWidth();
        int height = cell.getHeight();

        canvas.texture(Background.Border.light, x, y, width, height);
        canvas.texture(Background.flat, x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2));
        if (hovered) {
            canvas.fill(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), 0x12FFFFFF);
        }

        int cellPad = resolveGridCellPad(width, height);
        int slotX = x + cellPad;
        int slotY = y + Math.max(0, (height - ExecutionOverlayUi.slotSize) / 2);
        RecipeContent content = entry.requirement().material();
        if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            ExecutionOverlayUi.drawSlot(canvas, content, slotX, slotY);
        } else {
            canvas.texture(Background.Slot.dark, slotX, slotY, ExecutionOverlayUi.slotSize, ExecutionOverlayUi.slotSize);
            String token = ExecutionOverlayContentHelper.fallbackToken(content);
            int tokenX = slotX + Math.max(1, (ExecutionOverlayUi.slotSize - font.width(token)) / 2);
            canvas.drawString(token, tokenX, slotY + 5, ExecutionOverlayUi.textSecondary, false);
        }

        ImmutableList<ListMetric> metrics = gridMetrics(entry, category);
        int metricX = slotX + ExecutionOverlayUi.slotSize + 3;
        int metricWidth = Math.max(0, x + width - cellPad - metricX);
        int metricHeight = gridMetricBlockHeight(font, metrics);
        int metricY = y + Math.max(0, (height - metricHeight) / 2);
        drawGridBottomMetrics(canvas, font, metricX, metricY, metricWidth, metrics);
    }

    private void drawGridBottomMetrics(SceneCanvas canvas, Font font, int x, int y, int width, ImmutableList<ListMetric> metrics) {
        if (metrics.isEmpty() || width <= 0) {
            return;
        }
        int cursorY = y;
        for (int index = 0; index < metrics.size(); index++) {
            ListMetric metric = metrics.get(index);
            drawGridBottomMetric(canvas, font, x, cursorY, width, metric);
            cursorY += gridMetricRowHeight(font, metric);
            if (index + 1 < metrics.size()) {
                cursorY += gridMetricRowGap;
            }
        }
    }

    private void drawGridBottomMetric(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        ListMetric metric
    ) {
        if (width <= 0) {
            return;
        }
        int rowHeight = font.lineHeight;
        canvas.fill(x, y + 1, metricMarkerWidth, Math.max(1, rowHeight - 2), metric.markerColor());
        int textX = x + metricMarkerWidth + metricMarkerGap;
        int textWidth = Math.max(0, width - metricMarkerWidth - metricMarkerGap);
        canvas.drawString(
            ExecutionOverlayUi.trim(font, metric.value(), textWidth),
            textX,
            y,
            metric.valueColor(),
            false
        );
    }

    private int gridMetricBlockHeight(Font font, ImmutableList<ListMetric> metrics) {
        int height = 0;
        for (int index = 0; index < metrics.size(); index++) {
            height += gridMetricRowHeight(font, metrics.get(index));
            if (index + 1 < metrics.size()) {
                height += gridMetricRowGap;
            }
        }
        return height;
    }

    private int gridMetricRowHeight(Font font, ListMetric metric) {
        return font.lineHeight;
    }

    private int drawListSection(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        int viewportTop,
        int viewportBottom,
        List<MaterialEntry> entries,
        MaterialCategory category,
        int mouseX,
        int mouseY
    ) {
        for (MaterialEntry entry : entries) {
            int rowHeight = listRowHeight(font, width, entry, category);
            if (y > viewportBottom) {
                return y;
            }
            if (!intersectsViewport(y, rowHeight, viewportTop, viewportBottom)) {
                y += rowHeight + listRowGap;
                continue;
            }
            Rect2i row = new Rect2i(x, y, width, rowHeight);
            drawListRow(canvas, font, row, entry, category);
            Tooltip tooltip = materialTooltip(entry, category);
            interactiveAreas.add(new InteractiveArea(row, () -> tooltip, null));
            y += rowHeight + listRowGap;
        }
        return y;
    }

    private void drawListRow(SceneCanvas canvas, Font font, Rect2i row, MaterialEntry entry, MaterialCategory category) {
        canvas.texture(Background.Outlined.inset, row.getX(), row.getY(), row.getWidth(), row.getHeight());
        int itemX = row.getX() + 6;
        int itemY = row.getY() + Math.max(0, (row.getHeight() - 16) / 2);
        RecipeContent content = entry.requirement().material();
        if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            ExecutionOverlayUi.drawSlot(canvas, content, itemX - 1, itemY - 1);
        } else {
            String token = ExecutionOverlayContentHelper.fallbackToken(content);
            canvas.texture(Background.Slot.dark, itemX - 1, itemY - 1, ExecutionOverlayUi.slotSize, ExecutionOverlayUi.slotSize);
            canvas.drawString(token, itemX + Math.max(0, (16 - font.width(token)) / 2), itemY + 4, ExecutionOverlayUi.textSecondary, false);
        }

        ImmutableList<ListMetric> metrics = listMetrics(entry, category);
        int textX = row.getX() + 28;
        int rightPadding = 6;
        int metricWidth = measureListMetrics(font, metrics);
        boolean stackedMetrics = useStackedListMetrics(row.getWidth(), metricWidth);
        int metricX = stackedMetrics
            ? textX
            : row.getX() + Math.max(0, row.getWidth() - metricWidth - rightPadding);
        int nameWidth = stackedMetrics
            ? Math.max(18, row.getWidth() - textX - rightPadding)
            : Math.max(18, metricX - textX - 6);
        int nameY = row.getY() + 4;
        List<String> nameLines = ExecutionOverlayUi.wrapPlainLines(font, entry.label(), nameWidth, listNameMaxLines);
        for (String line : nameLines) {
            canvas.drawString(line, textX, nameY, ExecutionOverlayUi.titleColor, false);
            nameY += font.lineHeight;
        }

        if (stackedMetrics) {
            int metricY = row.getY() + row.getHeight() - gridMetricBlockHeight(font, metrics) - 3;
            drawGridBottomMetrics(canvas, font, metricX, metricY, Math.max(0, row.getWidth() - textX - rightPadding), metrics);
            return;
        }

        int metricY = row.getY() + row.getHeight() - font.lineHeight - 3;
        drawListMetrics(canvas, font, metricX, metricY, metrics);
    }

    private void drawViewToggleButton(SceneCanvas canvas, Rect2i area, int mouseX, int mouseY) {
        boolean hovered = hover(area, mouseX, mouseY);
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), false, hovered, true);
        var icon = viewMode == ViewMode.grid
            ? (hovered ? CirrusTextures.Toggle.Switch.Calc.gridHover : CirrusTextures.Toggle.Switch.Calc.gridOn)
            : (hovered ? CirrusTextures.Toggle.Switch.Calc.listHover : CirrusTextures.Toggle.Switch.Calc.listOn);
        int iconX = area.getX() + CirrusControlChrome.centeredIconX(area.getWidth(), icon.width());
        int iconY = area.getY() + CirrusControlChrome.centeredIconY(area.getHeight(), icon.height(), false);
        canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
        canvas.fill(area.getX() + 3, area.getY() + area.getHeight() - 2, area.getWidth() - 6, 1, withAlpha(ExecutionOverlayUi.accent, hovered ? 0xAA : 0x66));
        interactiveAreas.add(new InteractiveArea(area, this::viewTooltip, this::toggleViewMode));
    }

    private void drawSortButton(SceneCanvas canvas, Rect2i area, int mouseX, int mouseY) {
        boolean hovered = hover(area, mouseX, mouseY);
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), false, hovered, true);
        var icon = hovered ? sortMode.hoverIcon() : sortMode.icon();
        drawCenteredIcon(canvas, area, icon);
        interactiveAreas.add(new InteractiveArea(area, this::sortTooltip, this::cycleSortMode));
    }

    private void drawSortDirectionButton(SceneCanvas canvas, Rect2i area, int mouseX, int mouseY) {
        boolean hovered = hover(area, mouseX, mouseY);
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), false, hovered, true);
        var icon = sortDirection == SortDirection.ascending
            ? CirrusTextures.Icon.sortAscending
            : CirrusTextures.Icon.sortDescending;
        drawCenteredIcon(canvas, area, icon);
        interactiveAreas.add(new InteractiveArea(area, this::sortDirectionTooltip, this::toggleSortDirection));
    }

    private void drawCenteredIcon(SceneCanvas canvas, Rect2i area, SizedTexture icon) {
        int iconX = area.getX() + CirrusControlChrome.centeredIconX(area.getWidth(), icon.width());
        int iconY = area.getY() + CirrusControlChrome.centeredIconY(area.getHeight(), icon.height(), false);
        canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
    }

    private MaterialSnapshot buildMaterialSnapshot() {
        ExecutionDriverSnapshot snapshot = state.snapshot();
        if (snapshot == null) {
            return MaterialSnapshot.empty();
        }
        Map<String, CatalystInfo> catalysts = collectCatalysts(snapshot);
        List<MaterialEntry> entries = new ArrayList<>();
        for (OverlayMaterialRequirement requirement : snapshot.materials()) {
            MaterialEntry entry = MaterialEntry.fromRequirement(
                requirement,
                catalysts.get(key(requirement.material()))
            );
            entries.add(entry);
        }
        for (CatalystInfo catalyst : catalysts.values()) {
            if (entries.stream().noneMatch(entry -> entry.key().equals(catalyst.key()))) {
                entries.add(MaterialEntry.catalystOnly(catalyst));
            }
        }
        return new MaterialSnapshot(entries);
    }

    private Map<String, CatalystInfo> collectCatalysts(ExecutionDriverSnapshot snapshot) {
        LinkedHashMap<String, CatalystInfo> catalysts = new LinkedHashMap<>();
        ExecutionSession session = snapshot.session();
        if (session != null) {
            for (InteractionStep step : session.plan().steps()) {
                if (!(step instanceof InteractionStep.ReserveCatalystStep reserve)) {
                    continue;
                }
                RecipeContent contentKey = reserve.input();
                if (contentKey == null || contentKey.isEmpty()) {
                    continue;
                }
                String key = key(contentKey);
                catalysts.merge(
                    key,
                    new CatalystInfo(key, contentLabel(contentKey), contentKey, reserve.amount()),
                    CatalystInfo::merge
                );
            }
        }
        if (!catalysts.isEmpty()) {
            return catalysts;
        }
        RecipeActiveContext activeContext = state.activeRecipeContext();
        if (activeContext == null || activeContext.projected() == null) {
            return catalysts;
        }
        for (var input : activeContext.projected().inputs()) {
            if (input.isEmpty() || input.consumed()) {
                continue;
            }
            if (input.isEmpty()) {
                continue;
            }
            String key = key(input);
            catalysts.merge(
                key,
                new CatalystInfo(key, contentLabel(input), input, Math.max(1L, input.amount())),
                CatalystInfo::merge
            );
        }
        return catalysts;
    }

    private Tooltip materialTooltip(MaterialEntry entry, MaterialCategory category) {
        RecipeContent material = entry.requirement().material();
        Tooltip tooltip = Tooltip.create();
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(material);
        if (stack != null) {
            tooltip.setStack(stack);
        }
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(
            requirementLabel(entry.requirement())
        ));

        var builder = RichTooltipComponent.builder();
        builder.section(category.label(), category.accentColor());
        builder.contentIconText(material, category.accentColor(), entry.label(), ExecutionOverlayUi.tooltipTitleColor,
            metricTooltipValue(entry, category),
            category.accentColor());
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(entry.requirement().required()),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(entry.requirement().available()),
            ExecutionOverlayUi.tooltipSuccess
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.miss),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(entry.requirement().missing()),
            entry.requirement().missing() > 0 ? ExecutionOverlayUi.tooltipWarning : ExecutionOverlayUi.tooltipTextSecondary
        );
        if (entry.catalystAmount() > 0L) {
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Summary.catalysts),
                ExecutionOverlayUi.tooltipTextSecondary,
                ExecutionOverlayUi.compactNumber(entry.catalystAmount()),
                ExecutionOverlayUi.tooltipAccent
            );
        }
        long total = Math.max(1L, entry.requirement().required());
        float ratio = Math.min(1f, entry.requirement().available() / (float) total);
        builder.bar(ratio, entry.requirement().missing() > 0 ? ExecutionOverlayUi.warning : ExecutionOverlayUi.success, 112);
        tooltip.add(builder.build());
        return tooltip;
    }

    private Tooltip viewTooltip() {
        Tooltip tooltip = Tooltip.create();
        ViewMode next = viewMode == ViewMode.grid ? ViewMode.list : ViewMode.grid;
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(viewMode.label()));
        tooltip.add(ExecutionOverlayUi.tooltipBodyText(viewMode == ViewMode.grid
            ? ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Ingredients.viewGridDesc)
            : ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Ingredients.viewListDesc)));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(next.label()));
        return tooltip;
    }

    private Tooltip sortTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Ingredients.sort));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(sortMode.label()));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Ingredients.sortDesc));
        return tooltip;
    }

    private Tooltip sortDirectionTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(
            sortDirection == SortDirection.ascending
                ? CirrusLangs.Calculator.Database.sortAscending
                : CirrusLangs.Calculator.Database.sortDescending
        ));
        return tooltip;
    }

    private boolean hover(Rect2i area, int mouseX, int mouseY) {
        return ExecutionOverlayUi.contains(area, mouseX, mouseY);
    }

    private boolean intersectsViewport(int y, int itemHeight, int viewportTop, int viewportBottom) {
        return y + itemHeight >= viewportTop && y <= viewportBottom;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void drawListMetrics(SceneCanvas canvas, Font font, int x, int y, ImmutableList<ListMetric> metrics) {
        int cursor = x;
        for (ListMetric metric : metrics) {
            canvas.fill(cursor, y + 2, metricMarkerWidth, Math.max(1, font.lineHeight - 4), metric.markerColor());
            cursor += metricMarkerWidth + listMetricValueGap;
            canvas.drawString(metric.value(), cursor, y + 1, metric.valueColor(), false);
            cursor += font.width(metric.value()) + listMetricGap;
        }
    }

    private int measureListMetrics(Font font, ImmutableList<ListMetric> metrics) {
        int width = 0;
        for (int index = 0; index < metrics.size(); index++) {
            ListMetric metric = metrics.get(index);
            width += metricMarkerWidth + listMetricValueGap + font.width(metric.value());
            if (index + 1 < metrics.size()) {
                width += listMetricGap;
            }
        }
        return width;
    }

    private ImmutableList<ListMetric> listMetrics(MaterialEntry entry, MaterialCategory category) {
        return switch (category) {
            case missing -> Lists.immutable.of(
                new ListMetric(ExecutionOverlayUi.success, ExecutionOverlayUi.compactNumber(entry.requirement().available()), ExecutionOverlayUi.success),
                new ListMetric(
                    entry.requirement().missing() > 0 ? ExecutionOverlayUi.warning : ExecutionOverlayUi.textSecondary,
                    ExecutionOverlayUi.compactNumber(entry.requirement().missing()),
                    entry.requirement().missing() > 0 ? ExecutionOverlayUi.warning : ExecutionOverlayUi.textSecondary
                )
            );
            case available -> Lists.immutable.of(
                new ListMetric(ExecutionOverlayUi.success, ExecutionOverlayUi.compactNumber(entry.requirement().available()), ExecutionOverlayUi.success),
                new ListMetric(ExecutionOverlayUi.textSecondary, ExecutionOverlayUi.compactNumber(entry.requirement().required()), ExecutionOverlayUi.textPrimary)
            );
            case catalyst -> Lists.immutable.of(
                new ListMetric(ExecutionOverlayUi.accent, ExecutionOverlayUi.compactNumber(entry.catalystAmount()), ExecutionOverlayUi.accent)
            );
        };
    }

    private ImmutableList<ListMetric> gridMetrics(MaterialEntry entry, MaterialCategory category) {
        return listMetrics(entry, category);
    }

    private int listRowHeight(Font font, int rowWidth, MaterialEntry entry, MaterialCategory category) {
        ImmutableList<ListMetric> metrics = listMetrics(entry, category);
        int metricWidth = measureListMetrics(font, metrics);
        boolean stackedMetrics = useStackedListMetrics(rowWidth, metricWidth);
        int nameWidth = stackedMetrics
            ? Math.max(18, rowWidth - 28 - 6)
            : Math.max(18, rowWidth - 28 - metricWidth - 12);
        int lineCount = Math.max(1, ExecutionOverlayUi.wrapPlainLines(font, entry.label(), nameWidth, listNameMaxLines).size());
        if (stackedMetrics) {
            int compactHeight = 8 + lineCount * font.lineHeight + 2 + gridMetricBlockHeight(font, metrics) + 3;
            return Math.max(listRowExpandedHeight, compactHeight);
        }
        return lineCount > 1 ? listRowExpandedHeight : listRowHeight;
    }

    private String metricTooltipValue(MaterialEntry entry, MaterialCategory category) {
        return switch (category) {
            case missing -> ExecutionOverlayUi.compactNumber(entry.requirement().missing()) + " | "
                + ExecutionOverlayUi.compactNumber(entry.requirement().available()) + "/"
                + ExecutionOverlayUi.compactNumber(entry.requirement().required());
            case available -> ExecutionOverlayUi.compactNumber(entry.requirement().available()) + "/"
                + ExecutionOverlayUi.compactNumber(entry.requirement().required());
            case catalyst -> ExecutionOverlayUi.compactNumber(entry.catalystAmount());
        };
    }

    private void toggleViewMode() {
        viewMode = viewMode == ViewMode.grid ? ViewMode.list : ViewMode.grid;
        ExecutionOverlayUi.playClickSound();
    }

    private void cycleSortMode() {
        sortMode = switch (sortMode) {
            case missingFirst -> SortMode.availableFirst;
            case availableFirst -> SortMode.name;
            case name -> SortMode.missingFirst;
        };
        ExecutionOverlayUi.playClickSound();
    }

    private void toggleSortDirection() {
        sortDirection = sortDirection == SortDirection.ascending
            ? SortDirection.descending
            : SortDirection.ascending;
        ExecutionOverlayUi.playClickSound();
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

    private static String requirementLabel(OverlayMaterialRequirement requirement) {
        return contentLabel(requirement.material());
    }

    private static String contentLabel(RecipeContent material) {
        String label = ExecutionOverlayContentHelper.label(material);
        if (label == null || label.isBlank()) {
            return "-";
        }
        return label;
    }

    private enum ViewMode {
        grid(CirrusLangs.Execution.Overlay.Ingredients.viewGrid),
        list(CirrusLangs.Execution.Overlay.Ingredients.viewList);

        private final LangEntry label;

        ViewMode(LangEntry label) {
            this.label = label;
        }

        private String label() {
            return ExecutionOverlayUi.tr(label);
        }
    }

    static int resolvePagePadding(int width, int height) {
        return width < 112 || height < 148 ? 4 : pad;
    }

    static int resolveToolbarHeight(int width, int height) {
        return width < 112 || height < 148 ? toolbarHeightCompact : toolbarHeight;
    }

    static int resolveModeButtonWidth() {
        return modeButtonWidth;
    }

    static int resolveSortButtonWidth() {
        return sortButtonWidth;
    }

    static int resolveSortDirectionButtonWidth() {
        return sortDirectionButtonWidth;
    }

    static int resolveSectionGap(int width, int height) {
        return width < 112 || height < 148 ? sectionGapCompact : sectionGap;
    }

    static int resolveSectionHeaderHeight(int width, int height) {
        return width < 112 || height < 148 ? sectionHeaderHeightCompact : sectionHeaderHeight;
    }

    static int resolveGridGap(int width, int height) {
        return width < 112 || height < 148 ? gridGapCompact : gridGap;
    }

    static int resolveGridColumns(int availableWidth) {
        if (availableWidth <= 0) {
            return 1;
        }
        if (availableWidth < gridCellWidthMin * 2 + gridGapCompact) {
            return 1;
        }
        return 2;
    }

    static int resolveGridCellWidth(int availableWidth) {
        if (availableWidth <= 0) {
            return 1;
        }
        int columns = resolveGridColumns(availableWidth);
        if (columns <= 1) {
            return Math.max(1, Math.min(gridCellWidth, availableWidth));
        }
        int gap = availableWidth < 112 ? gridGapCompact : gridGap;
        int usableWidth = Math.max(1, availableWidth - gap * (columns - 1));
        return Math.max(gridCellWidthMin, Math.min(gridCellWidth, usableWidth / columns));
    }

    static int resolveGridCellHeight(int width, int height) {
        return width < 112 || height < 148 ? gridCellHeightCompact : gridCellHeight;
    }

    static int resolveGridCellPad(int width, int height) {
        return width < 112 || height < 148 ? gridCellPadCompact : gridCellPad;
    }

    static boolean useStackedListMetrics(int rowWidth, int metricWidth) {
        return rowWidth < 104 || metricWidth > Math.max(0, rowWidth - 50);
    }

    static int resolveScrollbarWidth(int width, int height) {
        return width < 112 || height < 148 ? 5 : scrollbarWidth;
    }

    private int measureContentHeight(Font font, int contentWidth, MaterialSnapshot snapshot) {
        if (snapshot.totalCount() <= 0 || contentWidth <= 0) {
            return 0;
        }
        int contentHeight = 0;
        contentHeight = measureSectionHeight(font, contentWidth, snapshot, MaterialCategory.missing, contentHeight);
        contentHeight = measureSectionHeight(font, contentWidth, snapshot, MaterialCategory.available, contentHeight);
        contentHeight = measureSectionHeight(font, contentWidth, snapshot, MaterialCategory.catalyst, contentHeight);
        return contentHeight;
    }

    private int measureSectionHeight(Font font, int width, MaterialSnapshot snapshot, MaterialCategory category, int y) {
        List<MaterialEntry> entries = snapshot.entries(category, sortMode, sortDirection);
        if (entries.isEmpty()) {
            return y;
        }
        y += resolveSectionGap(width, height());
        y += resolveSectionHeaderHeight(width, height()) + 4;
        if (viewMode == ViewMode.grid) {
            int columns = resolveGridColumns(width);
            int rows = (int) Math.ceil(entries.size() / (double) columns);
            int cellHeight = resolveGridCellHeight(width, height());
            int cellGap = resolveGridGap(width, height());
            return y + rows * cellHeight + Math.max(0, rows - 1) * cellGap;
        }
        for (MaterialEntry entry : entries) {
            y += listRowHeight(font, width, entry, category) + listRowGap;
        }
        return Math.max(0, y - listRowGap);
    }

    private Rect2i contentViewportArea() {
        int pagePad = resolvePagePadding(width(), height());
        int y = pagePad + resolveToolbarHeight(width(), height()) + 4;
        int viewportWidth = Math.max(0, width() - pagePad * 2);
        int viewportHeight = Math.max(0, height() - pagePad - y);
        return new Rect2i(pagePad, y, viewportWidth, viewportHeight);
    }

    private int scrollableContentWidth(int viewportWidth) {
        return Math.max(0, viewportWidth - resolveScrollbarWidth(width(), height()) - scrollbarGap);
    }

    private void syncScrollBounds(int contentHeight, int viewportHeight) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        scrollY = clamp(scrollY, 0f, max);
        targetScrollY = clamp(targetScrollY, 0f, max);
    }

    private float maxScroll() {
        Rect2i viewport = contentViewportArea();
        int contentHeight = measureContentHeight(context().font(), scrollableContentWidth(viewport.getWidth()), buildMaterialSnapshot());
        return Math.max(0f, contentHeight - viewport.getHeight());
    }

    private Rect2i scrollbarTrackArea() {
        Rect2i viewport = contentViewportArea();
        int width = resolveScrollbarWidth(width(), height());
        int verticalInset = Math.min(2, Math.max(0, viewport.getHeight() / 4));
        return new Rect2i(
            viewport.getX() + Math.max(0, viewport.getWidth() - width),
            viewport.getY() + verticalInset,
            width,
            Math.max(0, viewport.getHeight() - verticalInset * 2)
        );
    }

    private @Nullable Rect2i scrollbarThumbArea() {
        Rect2i viewport = contentViewportArea();
        int contentHeight = measureContentHeight(context().font(), scrollableContentWidth(viewport.getWidth()), buildMaterialSnapshot());
        if (contentHeight <= viewport.getHeight() || contentHeight <= 0) {
            return null;
        }
        Rect2i track = scrollbarTrackArea();
        int thumbHeight = Math.max(14, Math.min(track.getHeight(), (int) ((double) viewport.getHeight() / contentHeight * track.getHeight())));
        int travel = Math.max(0, track.getHeight() - thumbHeight);
        int thumbY = track.getY();
        if (travel > 0 && maxScroll() > 0f) {
            thumbY += Math.round((scrollY / maxScroll()) * travel);
        }
        return new Rect2i(track.getX(), thumbY, track.getWidth(), thumbHeight);
    }

    private float jumpScrollTo(int mouseY) {
        Rect2i track = scrollbarTrackArea();
        Rect2i thumb = scrollbarThumbArea();
        if (thumb == null) {
            return 0f;
        }
        int travel = Math.max(1, track.getHeight() - thumb.getHeight());
        float top = clamp(mouseY - thumb.getHeight() / 2f - track.getY(), 0f, travel);
        return top / travel * maxScroll();
    }

    private float dragScrollTo(float mouseY) {
        Rect2i track = scrollbarTrackArea();
        Rect2i thumb = scrollbarThumbArea();
        if (thumb == null) {
            return 0f;
        }
        int travel = Math.max(1, track.getHeight() - thumb.getHeight());
        float top = clamp(mouseY - scrollbarDragOffsetY - track.getY(), 0f, travel);
        return top / travel * maxScroll();
    }

    private void stopScrollbarDrag() {
        draggingScrollbar = false;
        scrollbarDragOffsetY = 0f;
    }

    private void drawScrollbar(SceneCanvas canvas, Rect2i viewport, int contentHeight) {
        if (contentHeight <= viewport.getHeight()) {
            return;
        }
        Rect2i track = scrollbarTrackArea();
        canvas.texture(Background.scroll, track.getX(), track.getY(), track.getWidth(), track.getHeight());
        Rect2i thumb = scrollbarThumbArea();
        if (thumb != null) {
            canvas.texture(CirrusTextures.Icon.scrollBarVertical, thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight());
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum SortMode {
        missingFirst(
            CirrusLangs.Execution.Overlay.Ingredients.sortModeMissing,
            CirrusTextures.Icon.Calc.Sort.missingFirst,
            CirrusTextures.Icon.Calc.Sort.missingFirstPressed
        ),
        availableFirst(
            CirrusLangs.Execution.Overlay.Ingredients.sortModeAvailable,
            CirrusTextures.Icon.Calc.Sort.availableFirst,
            CirrusTextures.Icon.Calc.Sort.availableFirstPressed
        ),
        name(
            CirrusLangs.Execution.Overlay.Ingredients.sortModeName,
            CirrusTextures.Icon.Calc.Sort.id,
            CirrusTextures.Icon.Calc.Sort.idPressed
        );

        private final LangEntry label;
        private final SizedTexture icon;
        private final SizedTexture hoverIcon;

        SortMode(LangEntry label, SizedTexture icon, SizedTexture hoverIcon) {
            this.label = label;
            this.icon = icon;
            this.hoverIcon = hoverIcon;
        }

        private String label() {
            return ExecutionOverlayUi.tr(label);
        }

        private SizedTexture icon() {
            return icon;
        }

        private SizedTexture hoverIcon() {
            return hoverIcon;
        }
    }

    private enum SortDirection {
        ascending,
        descending
    }

    private enum MaterialCategory {
        missing(
            CirrusLangs.Execution.Overlay.Ingredients.groupMissing,
            0xFFE45F5F
        ),
        available(
            CirrusLangs.Execution.Overlay.Ingredients.groupAvailable,
            ExecutionOverlayUi.success
        ),
        catalyst(
            CirrusLangs.Execution.Overlay.Ingredients.groupCatalyst,
            0xFF4C7DFF
        );

        private final LangEntry label;
        private final int accentColor;

        MaterialCategory(LangEntry label, int accentColor) {
            this.label = label;
            this.accentColor = accentColor;
        }

        private String label() {
            return ExecutionOverlayUi.tr(label);
        }

        private int accentColor() {
            return accentColor;
        }
    }

    private record InteractiveArea(Rect2i bounds, Supplier<@Nullable Tooltip> tooltip, @Nullable Runnable action) {
    }

    private record ListMetric(int markerColor, String value, int valueColor) {
    }

    private static String key(RecipeContent material) {
        String label = contentLabel(material);
        if (material instanceof dev.vfyjxf.cirrus.api.recipe.content.InputContent input && input.preserveOriginalShortageSemantics()
            && label != null && !label.isBlank() && !"-".equals(label)) {
            return label.toLowerCase(Locale.ROOT);
        }
        return ExecutionOverlayContentHelper.recipeContentKey(material);
    }

    private record CatalystInfo(
        String key,
        String label,
        RecipeContent material,
        long amount
    ) {
        private CatalystInfo merge(CatalystInfo other) {
            return new CatalystInfo(
                key,
                label == null || label.isBlank() ? other.label : label,
                material == null || material.isEmpty() ? other.material : material,
                amount + other.amount
            );
        }
    }

    private record MaterialEntry(
        String key,
        String label,
        OverlayMaterialRequirement requirement,
        long catalystAmount
    ) {
        private static MaterialEntry fromRequirement(OverlayMaterialRequirement requirement, @Nullable CatalystInfo catalyst) {
            String label = contentLabel(requirement.material());
            return new MaterialEntry(
                keyFor(requirement.material()),
                label,
                requirement,
                catalyst == null ? 0L : catalyst.amount()
            );
        }

        private static MaterialEntry catalystOnly(CatalystInfo catalyst) {
            return new MaterialEntry(
                catalyst.key(),
                catalyst.label(),
                new OverlayMaterialRequirement(
                    catalyst.material(),
                    catalyst.amount(),
                    ExecutionOverlayContentHelper.countInventory(catalyst.material()),
                    0L
                ),
                catalyst.amount()
            );
        }

        private static String keyFor(RecipeContent material) {
            String label = contentLabel(material);
            if (material instanceof dev.vfyjxf.cirrus.api.recipe.content.InputContent input && input.preserveOriginalShortageSemantics()
                && label != null && !label.isBlank() && !"-".equals(label)) {
                return label.toLowerCase(Locale.ROOT);
            }
            return ExecutionOverlayContentHelper.recipeContentKey(material);
        }
    }

    private record MaterialSnapshot(List<MaterialEntry> entries) {
        private static MaterialSnapshot empty() {
            return new MaterialSnapshot(List.of());
        }

        private int totalCount() {
            return entries.size();
        }

        private List<MaterialEntry> entries(MaterialCategory category, SortMode sortMode, SortDirection sortDirection) {
            ArrayList<MaterialEntry> filtered = new ArrayList<>();
            for (MaterialEntry entry : entries) {
                switch (category) {
                    case missing -> {
                        if (entry.requirement().missing() > 0) {
                            filtered.add(entry);
                        }
                    }
                    case available -> {
                        if (entry.requirement().available() > 0) {
                            filtered.add(entry);
                        }
                    }
                    case catalyst -> {
                        if (entry.catalystAmount() > 0) {
                            filtered.add(entry);
                        }
                    }
                }
            }
            Comparator<MaterialEntry> comparator = sortComparator(sortMode);
            if (sortDirection == SortDirection.descending) {
                comparator = comparator.reversed();
            }
            filtered.sort(comparator);
            return filtered;
        }

        private Comparator<MaterialEntry> sortComparator(SortMode sortMode) {
            return switch (sortMode) {
                case missingFirst -> Comparator.comparingLong((MaterialEntry entry) -> entry.requirement().missing()).reversed()
                    .thenComparing(MaterialEntry::label, String.CASE_INSENSITIVE_ORDER);
                case availableFirst -> Comparator.comparingLong((MaterialEntry entry) -> entry.requirement().available()).reversed()
                    .thenComparing(MaterialEntry::label, String.CASE_INSENSITIVE_ORDER);
                case name -> Comparator.comparing(MaterialEntry::label, String.CASE_INSENSITIVE_ORDER);
            };
        }
    }
}
