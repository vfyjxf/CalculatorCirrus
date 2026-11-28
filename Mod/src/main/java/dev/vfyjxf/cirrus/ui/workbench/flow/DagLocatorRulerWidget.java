package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.event.context.InterruptibleContext;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.event.InputEvents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Compact IDE-style stage locator ruler with fixed relative stage indices, graphical role/time-split signatures, and cursor-neighborhood tracking.
 */
public class DagLocatorRulerWidget extends Widget implements OreUiTooltipProvider {

    public static final int horizontalHeight = 46;
    public static final int verticalWidth = 108;
    public static final int collapsedHorizontalWidth = 34;
    public static final int collapsedHorizontalHeight = 11;
    public static final int collapsedVerticalWidth = 11;
    public static final int collapsedVerticalHeight = 32;
    public static final int collapseButtonSize = 10;
    public static final int trackThickness = 6;
    public static final int railPad = 4;
    public static final int cellGap = 4;
    public static final int iconSize = 14;
    public static final int slotSize = 16;
    public static final int verticalHeaderHeight = 14;
    public static final int maxIconsPerCell = 3;

    public record NodeIconHit(DagNodeBox node, int x, int y, int size) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + size && py >= y && py < y + size;
        }
    }

    public record StageCell(
        int displayIndex,
        int layer,
        List<DagNodeBox> nodes,
        int x,
        int y,
        int width,
        int height,
        float alignedAnchor,
        boolean inViewport,
        boolean hasSelected,
        boolean hasCritical,
        boolean hasBeacon,
        List<NodeIconHit> iconHits
    ) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final DagViewport viewport;
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private PlanDagLayoutResult layout = PlanDagLayoutResult.empty;
    private DagLayoutMode layoutMode = DagLayoutMode.horizontalLr;
    private Map<String, StepNodeCardRenderer.StepVisuals> visuals = Map.of();
    private @Nullable PlanVisualResolver resolver;
    private Set<String> selectedStepIds = Set.of();
    private Set<String> criticalStepIds = Set.of();
    private Map<String, String> inlineBeacons = Map.of();
    private Consumer<List<DagNodeBox>> onFocusStage = ignored -> {};
    private Consumer<DagNodeBox> onFocusNode = ignored -> {};
    private Runnable onCameraChanged = () -> {};
    private Runnable onToggleCollapsed = () -> {};

    private int canvasWidth;
    private int canvasHeight;
    private int appliedX = -1;
    private int appliedY = -1;
    private int appliedW = -1;
    private int appliedH = -1;
    private boolean collapsed;
    private boolean scrubbing;
    private @Nullable Integer hoveredStageIndex;
    private @Nullable String hoveredNodeStepId;
    private boolean hoveringTrack;
    private List<StageCell> cachedCells = List.of();
    private final OreUiMotion.SmoothValue thumbPos = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue thumbLen = new OreUiMotion.SmoothValue();
    private boolean lastTrackVertical;
    private float scrollOffsetPx;
    private float maxScrollPx;
    private int appliedTopClearance = 8;

    private DagLocatorRulerWidget(DagViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        addStyleClass("workbench-dag-locator");
        useStyle(positionAbsolute(), sizeOf(0, 0));
        setInteractive(true);
        setFocusable(false);

        onMouseClicked((input, context) -> {
            if (!input.isLeftClick() || layout.isEmpty()) {
                return EventDispatch.pass;
            }
            FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
            if (collapsed) {
                toggleCollapsed();
                CirrusUiSound.playClickSound();
                context.consume();
                return EventDispatch.consumed;
            }
            if (hitsCollapseButton(local.x, local.y)) {
                toggleCollapsed();
                CirrusUiSound.playClickSound();
                context.consume();
                return EventDispatch.consumed;
            }
            if (hitsTrack(local.x, local.y)) {
                scrubbing = true;
                scrubTo(local.x, local.y);
                context.consume();
                return EventDispatch.consumed;
            }
            if (clickScrollButton(local.x, local.y)) {
                context.consume();
                return EventDispatch.consumed;
            }
            if (!inCellsViewport(local.x, local.y)) {
                return EventDispatch.pass;
            }
            List<StageCell> cells = computeCells();
            for (StageCell cell : cells) {
                for (NodeIconHit icon : cell.iconHits()) {
                    if (icon.contains(local.x, local.y)) {
                        onFocusNode.accept(icon.node());
                        context.consume();
                        return EventDispatch.consumed;
                    }
                }
                if (cell.contains(local.x, local.y)) {
                    onFocusStage.accept(cell.nodes());
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });

        onMouseScrolled((mx, my, scrollX, scrollY, context) -> {
            if (maxScrollPx <= 0.5f) {
                return EventDispatch.pass;
            }
            double delta = Math.abs(scrollX) > Math.abs(scrollY) ? -scrollX : -scrollY;
            if (Math.abs(delta) < 0.001) {
                return EventDispatch.pass;
            }
            setScrollOffset(scrollOffsetPx + (float) (delta * 34.0));
            FloatPos local = sceneToLocal(mx, my);
            updateHover(local.x, local.y);
            context.consume();
            return EventDispatch.consumed;
        });

        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!scrubbing) {
                return EventDispatch.pass;
            }
            FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
            scrubTo(local.x, local.y);
            updateHover(local.x, local.y);
            context.consume();
            return EventDispatch.consumed;
        });

        onMouseReleased((input, context) -> {
            if (!scrubbing) {
                return EventDispatch.pass;
            }
            scrubbing = false;
            context.consume();
            return EventDispatch.consumed;
        });

        onEvent(InputEvents.onMouseMoved, (double mx, double my, InterruptibleContext ignored) -> {
            FloatPos local = sceneToLocal(mx, my);
            updateHover(local.x, local.y);
        });

        onMouseLeave((mx, my, ctx) -> {
            hoveredStageIndex = null;
            hoveredNodeStepId = null;
            hoveringTrack = false;
        });
    }

    public static DagLocatorRulerWidget of(DagViewport viewport) {
        return new DagLocatorRulerWidget(viewport);
    }

    public DagLocatorRulerWidget palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public DagLocatorRulerWidget onFocusStage(Consumer<List<DagNodeBox>> listener) {
        this.onFocusStage = listener == null ? ignored -> {} : listener;
        return this;
    }

    public DagLocatorRulerWidget onFocusNode(Consumer<DagNodeBox> listener) {
        this.onFocusNode = listener == null ? ignored -> {} : listener;
        return this;
    }

    public DagLocatorRulerWidget onCameraChanged(Runnable listener) {
        this.onCameraChanged = listener == null ? () -> {} : listener;
        return this;
    }

    public DagLocatorRulerWidget onToggleCollapsed(Runnable listener) {
        this.onToggleCollapsed = listener == null ? () -> {} : listener;
        return this;
    }

    public boolean collapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }
        this.collapsed = collapsed;
        if (collapsed) {
            scrubbing = false;
            hoveredStageIndex = null;
            hoveredNodeStepId = null;
            hoveringTrack = false;
        }
    }

    private void toggleCollapsed() {
        setCollapsed(!collapsed);
        onToggleCollapsed.run();
    }

    public void syncState(
        PlanDagLayoutResult layout,
        DagLayoutMode layoutMode,
        Map<String, StepNodeCardRenderer.StepVisuals> visuals,
        @Nullable PlanVisualResolver resolver,
        Set<String> selectedStepIds,
        Set<String> criticalStepIds,
        Map<String, String> inlineBeacons,
        int canvasWidth,
        int canvasHeight,
        int minimapReserveWidth
    ) {
        syncState(
            layout,
            layoutMode,
            visuals,
            resolver,
            selectedStepIds,
            criticalStepIds,
            inlineBeacons,
            canvasWidth,
            canvasHeight,
            minimapReserveWidth,
            32
        );
    }

    public void syncState(
        PlanDagLayoutResult layout,
        DagLayoutMode layoutMode,
        Map<String, StepNodeCardRenderer.StepVisuals> visuals,
        @Nullable PlanVisualResolver resolver,
        Set<String> selectedStepIds,
        Set<String> criticalStepIds,
        Map<String, String> inlineBeacons,
        int canvasWidth,
        int canvasHeight,
        int minimapReserveWidth,
        int topClearance
    ) {
        DagLayoutMode nextMode = layoutMode == null ? DagLayoutMode.horizontalLr : layoutMode;
        if (this.layoutMode.isVertical() != nextMode.isVertical()) {
            this.scrollOffsetPx = 0f;
        }
        this.layout = layout == null ? PlanDagLayoutResult.empty : layout;
        this.layoutMode = nextMode;
        this.visuals = visuals == null ? Map.of() : visuals;
        this.resolver = resolver;
        this.selectedStepIds = selectedStepIds == null ? Set.of() : selectedStepIds;
        this.criticalStepIds = criticalStepIds == null ? Set.of() : criticalStepIds;
        this.inlineBeacons = inlineBeacons == null ? Map.of() : inlineBeacons;
        this.canvasWidth = Math.max(0, canvasWidth);
        this.canvasHeight = Math.max(0, canvasHeight);
        this.appliedTopClearance = Math.max(8, topClearance);

        boolean visible = !this.layout.isEmpty() && canvasWidth >= 160 && canvasHeight >= 110;
        setVisible(visible);
        if (!visible) {
            return;
        }
        int x;
        int y;
        int w;
        int h;
        if (collapsed) {
            if (this.layoutMode.isVertical()) {
                x = 0;
                w = collapsedVerticalWidth;
                h = Math.min(collapsedVerticalHeight, Math.max(20, canvasHeight - this.appliedTopClearance - 8));
                y = Math.max(this.appliedTopClearance, (canvasHeight - h) / 2);
            } else {
                w = Math.min(collapsedHorizontalWidth, Math.max(22, canvasWidth - 16));
                h = collapsedHorizontalHeight;
                x = Math.max(4, (canvasWidth - w) / 2);
                y = Math.max(0, canvasHeight - h);
            }
        } else if (this.layoutMode.isVertical()) {
            x = 6;
            y = this.appliedTopClearance;
            w = Math.min(verticalWidth, Math.max(72, Math.round(canvasWidth * 0.28f)));
            h = Math.max(64, canvasHeight - y - 8);
        } else {
            x = 8;
            h = Math.min(horizontalHeight, Math.max(32, Math.round(canvasHeight * 0.28f)));
            y = Math.max(6, canvasHeight - h - 6);
            w = Math.max(88, canvasWidth - 16);
        }
        if (x != appliedX || y != appliedY || w != appliedW || h != appliedH) {
            appliedX = x;
            appliedY = y;
            appliedW = w;
            appliedH = h;
            useStyle(positionAbsolute(), insetLeft(x), insetTop(y), sizeOf(w, h));
        }
    }

    public float scrollOffset() {
        return scrollOffsetPx;
    }

    public float maxScroll() {
        return maxScrollPx;
    }

    public void setScrollOffset(float offset) {
        if (!Float.isFinite(offset)) {
            this.scrollOffsetPx = 0f;
            return;
        }
        this.scrollOffsetPx = Math.max(0f, Math.min(maxScrollPx, offset));
    }

    public boolean isVertical() {
        return layoutMode.isVertical();
    }

    public int appliedX() {
        return appliedX;
    }

    public int appliedY() {
        return appliedY;
    }

    public int appliedWidth() {
        return appliedW;
    }

    public int appliedHeight() {
        return appliedH;
    }

    public @Nullable Integer hoveredStageIndex() {
        return hoveredStageIndex;
    }

    public @Nullable String hoveredNodeStepId() {
        return hoveredNodeStepId;
    }

    public boolean hoveringTrack() {
        return hoveringTrack;
    }

    public List<StageCell> cachedStageCells() {
        return cachedCells.isEmpty() ? computeCells() : cachedCells;
    }

    public Set<String> hoveredHighlightStepIds() {
        if (hoveredNodeStepId != null) {
            return Set.of(hoveredNodeStepId);
        }
        if (hoveredStageIndex == null) {
            return Set.of();
        }
        for (StageCell cell : cachedStageCells()) {
            if (cell.displayIndex() == hoveredStageIndex) {
                LinkedHashSet<String> ids = new LinkedHashSet<>(cell.nodes().size());
                for (DagNodeBox node : cell.nodes()) {
                    ids.add(node.stepId());
                }
                return Set.copyOf(ids);
            }
        }
        return Set.of();
    }

    public List<StageCell> stageCells() {
        return computeCells();
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!visible() || layout.isEmpty()) {
            return null;
        }
        CirrusPalette activePalette = palette.get();
        List<StageCell> cells = computeCells();
        for (StageCell cell : cells) {
            for (NodeIconHit icon : cell.iconHits()) {
                if (icon.contains(localX, localY)) {
                    DagNodeBox node = icon.node();
                    StepNodeCardRenderer.StepVisuals data = visuals
                            .getOrDefault(node.stepId(), StepNodeCardRenderer.StepVisuals.empty());
                    int up = countUpstream(node.stepId());
                    int down = countDownstream(node.stepId());
                    return DagRichTooltips.stepNode(
                        node,
                        data,
                        resolver,
                        activePalette,
                        criticalStepIds.contains(node.stepId()),
                        inlineBeacons.containsKey(node.stepId()),
                        up,
                        down
                    );
                }
            }
            if (cell.contains(localX, localY)) {
                return DagRichTooltips.stageSummary(
                    cell.displayIndex(),
                    cell.nodes(),
                    visuals,
                    resolver,
                    activePalette,
                    criticalStepIds,
                    inlineBeacons,
                    totalPlanTime()
                );
            }
        }
        if (collapsed) {
            return OreUiRichTooltip.builder(CirrusLangs.Workbench.Flow.locatorRuler.get())
                    .badge("COLLAPSED", OreUiRichTooltip.Tone.muted)
                    .text(CirrusLangs.Workbench.Flow.locatorExpandHint.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (hitsCollapseButton(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Workbench.Flow.locatorRuler.get())
                    .badge("HIDE", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Workbench.Flow.locatorCollapseHint.get(), OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        if (hitsTrack(localX, localY)) {
            return OreUiRichTooltip.builder("Viewport Cursor Track").badge("NAVIGATOR", OreUiRichTooltip.Tone.accent)
                    .text("Shows the active camera window across the whole graph.", OreUiRichTooltip.Tone.secondary)
                    .footer("Click or drag along track to pan smoothly").build();
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0 || layout.isEmpty()) {
            return;
        }
        CirrusPalette p = palette.get();
        if (collapsed) {
            renderCollapsedPill(canvas, w, h, p, mouseX, mouseY);
            return;
        }
        List<StageCell> cells = computeCells();
        cachedCells = cells;
        if (mouseX >= 0 && mouseX < w && mouseY >= 0 && mouseY < h) {
            updateHover(mouseX, mouseY);
        }

        canvas.shadow(0, 0, w, h, 2f, 3f, 6f, p.dropShadow());
        OreUiPaint.popover(canvas, 0, 0, w, h, p);

        if (isVertical()) {
            renderVerticalHeader(canvas, w, cells.size(), p);
            renderVerticalTrack(canvas, w, h, p);
        } else {
            renderHorizontalTrack(canvas, w, h, p);
        }

        double planTime = totalPlanTime();
        int anchorStage = anchorStageIndex(cells);
        int clipX = cellsClipX(w);
        int clipY = cellsClipY();
        int clipW = cellsClipW(w);
        int clipH = cellsClipH(h);
        canvas.withClip(clipX, clipY, clipW, clipH, () -> {
            for (StageCell cell : cells) {
                if (cell.x() + cell.width() < clipX
                        || cell.x() > clipX + clipW
                        || cell.y() + cell.height() < clipY
                        || cell.y() > clipY + clipH) {
                    continue;
                }
                renderStageCell(canvas, cell, anchorStage, planTime, p, mouseX, mouseY);
            }
        });
        renderScrollIndicators(canvas, w, h, p, mouseX, mouseY);
        renderCollapseButton(canvas, w, h, p, mouseX, mouseY);
    }

    private void renderCollapsedPill(SceneCanvas canvas, int w, int h, CirrusPalette p, int mouseX, int mouseY) {
        boolean hover = mouseX >= 0 && mouseX < w && mouseY >= 0 && mouseY < h;
        int fill = hover ? p.cardSurfaceHover() : p.dockSurface();
        int border = hover ? p.focusRing() : p.hairlineBorder();
        int ink = hover ? p.focusRing() : p.textMuted();
        canvas.fill(0, 0, w, h, fill);
        if (isVertical()) {
            canvas.fill(0, 0, w, 1, border);
            canvas.fill(w - 1, 0, 1, h, border);
            canvas.fill(0, h - 1, w, 1, border);
            int cx = w / 2 - 1;
            int cy = h / 2;
            canvas.fill(cx, cy - 2, 1, 5, ink);
            canvas.fill(cx + 1, cy - 1, 1, 3, ink);
            canvas.fill(cx + 2, cy, 1, 1, ink);
        } else {
            canvas.fill(0, 0, w, 1, border);
            canvas.fill(0, 0, 1, h, border);
            canvas.fill(w - 1, 0, 1, h, border);
            int cx = w / 2;
            int cy = h / 2 + 1;
            canvas.fill(cx - 2, cy, 5, 1, ink);
            canvas.fill(cx - 1, cy - 1, 3, 1, ink);
            canvas.fill(cx, cy - 2, 1, 1, ink);
        }
    }

    private void renderCollapseButton(SceneCanvas canvas, int w, int h, CirrusPalette p, int mouseX, int mouseY) {
        int bx = collapseButtonX(w);
        int by = collapseButtonY();
        boolean hover = hitsCollapseButton(mouseX, mouseY);
        int bg = hover ? p.cardSurfaceHover() : p.recessedSurface();
        int border = hover ? p.focusRing() : p.hairlineBorder();
        canvas.fill(bx, by, collapseButtonSize, collapseButtonSize, bg);
        canvas.strokeRect(bx, by, collapseButtonSize, collapseButtonSize, border, 1);
        int ink = hover ? p.focusRing() : p.textMuted();
        int cx = bx + collapseButtonSize / 2;
        int cy = by + collapseButtonSize / 2;
        if (isVertical()) {
            canvas.fill(cx + 1, cy - 2, 1, 5, ink);
            canvas.fill(cx, cy - 1, 1, 3, ink);
            canvas.fill(cx - 1, cy, 1, 1, ink);
        } else {
            canvas.fill(cx - 2, cy - 1, 5, 1, ink);
            canvas.fill(cx - 1, cy, 3, 1, ink);
            canvas.fill(cx, cy + 1, 1, 1, ink);
        }
    }

    private int collapseButtonX(int w) {
        return isVertical()
                ? Math.max(railPad, w - railPad - trackThickness - collapseButtonSize - 4)
                : Math.max(railPad, w - railPad - collapseButtonSize);
    }

    private int collapseButtonY() {
        return railPad;
    }

    private boolean hitsCollapseButton(double lx, double ly) {
        int w = width() > 0 ? width() : appliedW;
        if (w <= 0 || collapsed) {
            return false;
        }
        int bx = collapseButtonX(w);
        int by = collapseButtonY();
        return lx >= bx && lx < bx + collapseButtonSize && ly >= by && ly < by + collapseButtonSize;
    }

    private int cellsClipX(int w) {
        return railPad;
    }

    private int cellsClipY() {
        return isVertical() ? railPad + verticalHeaderHeight + 3 : railPad;
    }

    private int cellsClipW(int w) {
        return isVertical()
                ? Math.max(1, w - railPad * 2 - trackThickness - 4)
                : Math.max(1, w - railPad * 2 - collapseButtonSize - 3);
    }

    private int cellsClipH(int h) {
        return isVertical()
                ? Math.max(1, h - cellsClipY() - railPad)
                : Math.max(1, h - railPad * 2 - trackThickness - 3);
    }

    private boolean inCellsViewport(double lx, double ly) {
        int w = width() > 0 ? width() : appliedW;
        int h = height() > 0 ? height() : appliedH;
        int cx = cellsClipX(w);
        int cy = cellsClipY();
        int cw = cellsClipW(w);
        int ch = cellsClipH(h);
        return lx >= cx && lx < cx + cw && ly >= cy && ly < cy + ch;
    }

    private boolean clickScrollButton(double lx, double ly) {
        if (maxScrollPx <= 0.5f) {
            return false;
        }
        int w = width() > 0 ? width() : appliedW;
        int h = height() > 0 ? height() : appliedH;
        int cx = cellsClipX(w);
        int cy = cellsClipY();
        int cw = cellsClipW(w);
        int ch = cellsClipH(h);
        if (isVertical()) {
            if (scrollOffsetPx > 1f && lx >= cx && lx < cx + cw && ly >= cy && ly < cy + 12) {
                setScrollOffset(scrollOffsetPx - 48f);
                return true;
            }
            if (scrollOffsetPx < maxScrollPx - 1f && lx >= cx && lx < cx + cw && ly >= cy + ch - 12 && ly < cy + ch) {
                setScrollOffset(scrollOffsetPx + 48f);
                return true;
            }
        } else {
            if (scrollOffsetPx > 1f && lx >= cx && lx < cx + 14 && ly >= cy && ly < cy + ch) {
                setScrollOffset(scrollOffsetPx - 76f);
                return true;
            }
            if (scrollOffsetPx < maxScrollPx - 1f && lx >= cx + cw - 14 && lx < cx + cw && ly >= cy && ly < cy + ch) {
                setScrollOffset(scrollOffsetPx + 76f);
                return true;
            }
        }
        return false;
    }

    private void renderScrollIndicators(SceneCanvas canvas, int w, int h, CirrusPalette p, int mouseX, int mouseY) {
        if (maxScrollPx <= 0.5f) {
            return;
        }
        int cx = cellsClipX(w);
        int cy = cellsClipY();
        int cw = cellsClipW(w);
        int ch = cellsClipH(h);
        boolean canPrev = scrollOffsetPx > 1f;
        boolean canNext = scrollOffsetPx < maxScrollPx - 1f;
        int bg = OreUiPaint.withAlpha(p.popoverSurface(), 0.90f);
        if (isVertical()) {
            if (canPrev) {
                boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + 11;
                canvas.fill(cx, cy, cw, 11, hover ? p.cardSurfaceHover() : bg);
                canvas.strokeRect(cx, cy, cw, 11, p.hairlineBorder(), 1);
                ChromeText.draw(
                    canvas,
                    Component.literal("\u25b2"),
                    cx + Math.max(2, (cw - 6) / 2),
                    cy + 2,
                    hover ? p.focusRing() : p.textSecondary()
                );
            }
            if (canNext) {
                int by = cy + ch - 11;
                boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= by && mouseY < by + 11;
                canvas.fill(cx, by, cw, 11, hover ? p.cardSurfaceHover() : bg);
                canvas.strokeRect(cx, by, cw, 11, p.hairlineBorder(), 1);
                ChromeText.draw(
                    canvas,
                    Component.literal("\u25bc"),
                    cx + Math.max(2, (cw - 6) / 2),
                    by + 2,
                    hover ? p.focusRing() : p.textSecondary()
                );
            }
        } else {
            if (canPrev) {
                boolean hover = mouseX >= cx && mouseX < cx + 14 && mouseY >= cy && mouseY < cy + ch;
                canvas.fill(cx, cy, 14, ch, hover ? p.cardSurfaceHover() : bg);
                canvas.strokeRect(cx, cy, 14, ch, p.hairlineBorder(), 1);
                ChromeText.draw(
                    canvas,
                    Component.literal("\u25c0"),
                    cx + 4,
                    ChromeText.centeredY(cy, ch, canvas),
                    hover ? p.focusRing() : p.textSecondary()
                );
            }
            if (canNext) {
                int bx = cx + cw - 14;
                boolean hover = mouseX >= bx && mouseX < bx + 14 && mouseY >= cy && mouseY < cy + ch;
                canvas.fill(bx, cy, 14, ch, hover ? p.cardSurfaceHover() : bg);
                canvas.strokeRect(bx, cy, 14, ch, p.hairlineBorder(), 1);
                ChromeText.draw(
                    canvas,
                    Component.literal("\u25b6"),
                    bx + 4,
                    ChromeText.centeredY(cy, ch, canvas),
                    hover ? p.focusRing() : p.textSecondary()
                );
            }
        }
    }

    private int anchorStageIndex(List<StageCell> cells) {
        for (StageCell cell : cells) {
            if (cell.hasSelected()) {
                return cell.displayIndex();
            }
        }
        return hoveredStageIndex != null ? hoveredStageIndex : 0;
    }

    public static String relativeOffsetLabel(int index, int anchorIndex) {
        int delta = index - anchorIndex;
        if (delta == 0) {
            return "@";
        }
        return delta > 0 ? "+" + delta : Integer.toString(delta);
    }

    private void renderVerticalHeader(SceneCanvas canvas, int w, int stageCount, CirrusPalette p) {
        int hy = railPad;
        int hw = Math.max(24, w - railPad * 2 - trackThickness - 4);
        String summary = "L" + stageCount + " \u00b7 " + WorkbenchFormatters.formatSeconds(totalPlanTime());
        ChromeText.drawFitted(
            canvas,
            Component.literal(summary),
            railPad + 2,
            ChromeText.centeredY(hy, verticalHeaderHeight, canvas),
            hw - 4,
            p.textMuted()
        );
        canvas.fill(railPad, hy + verticalHeaderHeight - 1, hw, 1, OreUiPaint.cardDivider(p));
    }

    private void renderHorizontalTrack(SceneCanvas canvas, int w, int h, CirrusPalette p) {
        int tx = railPad;
        int ty = h - railPad - trackThickness;
        int tw = Math.max(12, w - railPad * 2);
        OreUiPaint.searchField(canvas, tx, ty, tw, trackThickness, p, false);

        int tickColor = OreUiPaint.withAlpha(p.hairlineBorder(), 0.72f);
        for (StageCell cell : cachedCells) {
            int ax = Math.max(tx + 2, Math.min(tx + tw - 3, Math.round(cell.alignedAnchor())));
            canvas.fill(ax, ty + 1, 1, Math.max(2, trackThickness - 2), tickColor);
        }

        if (lastTrackVertical) {
            thumbPos.reset();
            thumbLen.reset();
            lastTrackVertical = false;
        }
        float contentSpan = Math.max(1f, layout.contentMaxX() - layout.contentMinX());
        float vis0 = clamp01((viewport.visibleWorldX0() - layout.contentMinX()) / contentSpan);
        float vis1 = clamp01((viewport.visibleWorldX1(Math.max(1, canvasWidth)) - layout.contentMinX()) / contentSpan);
        int targetX = tx + 1 + Math.round(vis0 * (tw - 2));
        int targetW = Math.max(18, Math.round((vis1 - vis0) * (tw - 2)));
        if (targetX + targetW > tx + tw - 1) {
            targetX = Math.max(tx + 1, tx + tw - 1 - targetW);
        }
        int clampedTargetW = Math.max(6, Math.min(tw - 2, targetW));
        boolean snapThumb = scrubbing || OreUiMotion.reducedMotion();
        long nowNanos = System.nanoTime();
        int thumbX = Math.round(thumbPos.update(targetX, 20f, nowNanos, snapThumb));
        int clampedW = Math
                .max(6, Math.min(tw - 2, Math.round(thumbLen.update(clampedTargetW, 20f, nowNanos, snapThumb))));
        thumbX = Math.max(tx + 1, Math.min(tx + tw - 1 - clampedW, thumbX));
        int thumbY = ty + 1;
        int thumbH = Math.max(4, trackThickness - 2);
        canvas.fill(
            thumbX,
            thumbY,
            clampedW,
            thumbH,
            scrubbing || hoveringTrack ? p.focusRing() : OreUiPaint.withAlpha(p.focusRing(), 0.72f)
        );
        if (thumbH >= 4) {
            canvas.fill(thumbX, thumbY, clampedW, 1, OreUiPaint.lighten(p.focusRing(), 0.30f));
        }
    }

    private void renderVerticalTrack(SceneCanvas canvas, int w, int h, CirrusPalette p) {
        int tx = w - railPad - trackThickness;
        int ty = railPad;
        int th = Math.max(12, h - railPad * 2);
        OreUiPaint.searchField(canvas, tx, ty, trackThickness, th, p, false);

        int tickColor = OreUiPaint.withAlpha(p.hairlineBorder(), 0.72f);
        for (StageCell cell : cachedCells) {
            int ay = Math.max(ty + 2, Math.min(ty + th - 3, Math.round(cell.alignedAnchor())));
            canvas.fill(tx + 1, ay, Math.max(2, trackThickness - 2), 1, tickColor);
        }

        if (!lastTrackVertical) {
            thumbPos.reset();
            thumbLen.reset();
            lastTrackVertical = true;
        }
        float contentSpan = Math.max(1f, layout.contentMaxY() - layout.contentMinY());
        float vis0 = clamp01((viewport.visibleWorldY0() - layout.contentMinY()) / contentSpan);
        float vis1 = clamp01((viewport.visibleWorldY1(Math.max(1, canvasHeight)) - layout.contentMinY()) / contentSpan);
        int targetY = ty + 1 + Math.round(vis0 * (th - 2));
        int targetH = Math.max(18, Math.round((vis1 - vis0) * (th - 2)));
        if (targetY + targetH > ty + th - 1) {
            targetY = Math.max(ty + 1, ty + th - 1 - targetH);
        }
        int clampedTargetH = Math.max(6, Math.min(th - 2, targetH));
        boolean snapThumb = scrubbing || OreUiMotion.reducedMotion();
        long nowNanos = System.nanoTime();
        int thumbY = Math.round(thumbPos.update(targetY, 20f, nowNanos, snapThumb));
        int clampedH = Math
                .max(6, Math.min(th - 2, Math.round(thumbLen.update(clampedTargetH, 20f, nowNanos, snapThumb))));
        thumbY = Math.max(ty + 1, Math.min(ty + th - 1 - clampedH, thumbY));
        int thumbX = tx + 1;
        int thumbW = Math.max(4, trackThickness - 2);
        canvas.fill(
            thumbX,
            thumbY,
            thumbW,
            clampedH,
            scrubbing || hoveringTrack ? p.focusRing() : OreUiPaint.withAlpha(p.focusRing(), 0.72f)
        );
    }

    private void renderStageCell(
        SceneCanvas canvas,
        StageCell cell,
        int anchorStage,
        double planTime,
        CirrusPalette p,
        int mouseX,
        int mouseY
    ) {
        boolean cellHovered = Objects.equals(hoveredStageIndex, cell.displayIndex()) || cell.contains(mouseX, mouseY);
        int x = cell.x();
        int y = cell.y();
        int cw = cell.width();
        int ch = cell.height();

        if (cell.hasSelected() || cellHovered) {
            OreUiPaint.raisedCard(canvas, x, y, cw, ch, p, cellHovered, cell.hasSelected());
        } else if (cell.inViewport()) {
            OreUiPaint.raisedCard(canvas, x, y, cw, ch, p, false, false);
        } else {
            canvas.fill(x, y, cw, ch, p.recessedSurface());
            canvas.strokeRect(x, y, cw, ch, OreUiPaint.cardDivider(p), 1);
        }

        int stageAccent = dominantAccent(cell, p);
        double workSec = stageWorkOf(cell);
        double waitSec = stageWaitOf(cell);
        double stageTime = workSec + waitSec;
        double totalShare = planTime > 0.01 ? Math.max(0.0, Math.min(1.0, stageTime / planTime)) : 0.0;
        double workShare = planTime > 0.01 ? Math.max(0.0, Math.min(1.0, workSec / planTime)) : 0.0;
        double waitShare = planTime > 0.01 ? Math.max(0.0, Math.min(1.0, waitSec / planTime)) : 0.0;
        String relLabel = relativeOffsetLabel(cell.displayIndex(), anchorStage);
        String timeText = WorkbenchFormatters.formatSeconds(stageTime);
        PlanStep leadStep = leadStepOf(cell);

        if (isVertical()) {
            renderVerticalStageBody(
                canvas,
                cell,
                stageAccent,
                leadStep,
                relLabel,
                timeText,
                workShare,
                waitShare,
                totalShare,
                p,
                mouseX,
                mouseY
            );
        } else {
            renderHorizontalStageBody(
                canvas,
                cell,
                stageAccent,
                leadStep,
                relLabel,
                timeText,
                workShare,
                waitShare,
                totalShare,
                p,
                mouseX,
                mouseY
            );
        }
    }

    private void renderHorizontalStageBody(
        SceneCanvas canvas,
        StageCell cell,
        int stageAccent,
        @Nullable PlanStep leadStep,
        String relLabel,
        String timeText,
        double workShare,
        double waitShare,
        double totalShare,
        CirrusPalette p,
        int mouseX,
        int mouseY
    ) {
        int x = cell.x();
        int y = cell.y();
        int cw = cell.width();
        int ch = cell.height();
        int innerTop = y + 2;
        int innerH = Math.max(18, ch - 5);

        // Fixed-width stage badge + IDE relative offset (01 / -1 / @ / +1) — never truncates.
        int badgeW = 16;
        int badgeH = 12;
        int badgeX = x + 4;
        int badgeY = innerTop + 1;
        drawStageBadge(canvas, cell, badgeX, badgeY, badgeW, badgeH, stageAccent, p);
        Component relComp = Component.literal(relLabel);
        int relW = ChromeText.width(canvas, relComp);
        ChromeText.draw(
            canvas,
            relComp,
            badgeX + Math.max(0, (badgeW - relW) / 2),
            innerTop + 14,
            "@".equals(relLabel) ? p.focusRing() : p.textMuted()
        );

        // Contiguous 16x16 item slots with native in-slot quantities.
        int slotsEndX = badgeX + badgeW + 4;
        for (NodeIconHit icon : cell.iconHits()) {
            drawNodeSlot(canvas, icon, p, mouseX, mouseY);
            slotsEndX = Math.max(slotsEndX, icon.x() + icon.size());
        }
        int overflow = cell.nodes().size() - cell.iconHits().size();
        if (overflow > 0) {
            int moreX = slotsEndX - 1;
            int moreY = cell.iconHits().isEmpty()
                    ? innerTop + Math.max(0, (innerH - slotSize) / 2)
                    : cell.iconHits().get(0).y();
            drawOverflowSlot(canvas, overflow, moreX, moreY, slotSize, p);
            slotsEndX = moreX + slotSize;
        }

        // Graphical stage feature cluster on right: step-kind emblem + fixed duration + split Work/Wait bar.
        int featX = slotsEndX + 5;
        int featW = x + cw - featX - 5;
        if (featW >= 24) {
            int gx = featX;
            if (leadStep != null) {
                drawStepKindGlyph(canvas, leadStep, gx, innerTop + 2, stageAccent);
                gx += 11;
            }
            if (cell.hasBeacon() || cell.hasCritical()) {
                int dotColor = cell.hasBeacon() ? p.advisoryDiscouraged() : p.edgeCriticalPath();
                canvas.fill(x + cw - 8, innerTop + 3, 4, 4, dotColor);
            }
            String fixedStats = cell.nodes().size() > 1 && featW >= 54
                    ? timeText + " \u00b7 \u00d7" + cell.nodes().size()
                    : timeText;
            int statMaxW = Math.max(8, x + cw - (cell.hasBeacon() || cell.hasCritical() ? 13 : 5) - gx);
            ChromeText.drawFitted(
                canvas,
                Component.literal(fixedStats),
                gx,
                innerTop + 2,
                statMaxW,
                cell.hasCritical() ? p.edgeCriticalPath() : p.textPrimary()
            );
            drawSplitTimeBar(canvas, featX, innerTop + 14, featW, 5, workShare, waitShare, totalShare, p);
        } else if (cell.hasBeacon() || cell.hasCritical()) {
            int dotColor = cell.hasBeacon() ? p.advisoryDiscouraged() : p.edgeCriticalPath();
            canvas.fill(x + cw - 7, y + 4, 4, 4, dotColor);
        }
    }

    private void renderVerticalStageBody(
        SceneCanvas canvas,
        StageCell cell,
        int stageAccent,
        @Nullable PlanStep leadStep,
        String relLabel,
        String timeText,
        double workShare,
        double waitShare,
        double totalShare,
        CirrusPalette p,
        int mouseX,
        int mouseY
    ) {
        int x = cell.x();
        int y = cell.y();
        int cw = cell.width();
        int ch = cell.height();
        int row1Y = y + 3;

        int badgeW = 16;
        int badgeH = 12;
        drawStageBadge(canvas, cell, x + 4, row1Y, badgeW, badgeH, stageAccent, p);

        int rightX = x + cw - 5;
        if (cell.hasBeacon() || cell.hasCritical()) {
            int dotColor = cell.hasBeacon() ? p.advisoryDiscouraged() : p.edgeCriticalPath();
            canvas.fill(rightX - 4, row1Y + 4, 4, 4, dotColor);
            rightX -= 7;
        }
        Component timeComp = Component.literal(timeText);
        int timeW = ChromeText.width(canvas, timeComp);
        int cursorX = x + 4 + badgeW + 3;
        Component relComp = Component.literal(relLabel);
        ChromeText.draw(
            canvas,
            relComp,
            cursorX,
            ChromeText.centeredY(row1Y, badgeH, canvas),
            "@".equals(relLabel) ? p.focusRing() : p.textMuted()
        );
        cursorX += ChromeText.width(canvas, relComp) + 3;
        if (leadStep != null && cursorX + 10 <= rightX - timeW - 2) {
            drawStepKindGlyph(canvas, leadStep, cursorX, row1Y + 2, stageAccent);
        }
        ChromeText.drawRight(
            canvas,
            timeComp,
            rightX,
            ChromeText.centeredY(row1Y, badgeH, canvas),
            cell.hasCritical() ? p.edgeCriticalPath() : p.textPrimary()
        );

        int slotsEndX = x + 4;
        for (NodeIconHit icon : cell.iconHits()) {
            drawNodeSlot(canvas, icon, p, mouseX, mouseY);
            slotsEndX = Math.max(slotsEndX, icon.x() + icon.size());
        }
        int overflow = cell.nodes().size() - cell.iconHits().size();
        if (overflow > 0 && !cell.iconHits().isEmpty()) {
            drawOverflowSlot(canvas, overflow, slotsEndX - 1, cell.iconHits().get(0).y(), slotSize, p);
            slotsEndX += slotSize - 1;
        }
        if (x + cw - slotsEndX >= 20 && !cell.iconHits().isEmpty()) {
            String fixedCount = cell.nodes().size() > 1
                    ? "\u00d7" + cell.nodes().size()
                    : Math.round(totalShare * 100.0) + "%";
            ChromeText.drawRight(
                canvas,
                Component.literal(fixedCount),
                x + cw - 5,
                ChromeText.centeredY(cell.iconHits().get(0).y(), slotSize, canvas),
                cell.hasCritical() ? p.edgeCriticalPath() : p.textMuted()
            );
        }

        if (ch >= 42) {
            int meterY = y + ch - 8;
            drawSplitTimeBar(canvas, x + 5, meterY, Math.max(12, cw - 10), 4, workShare, waitShare, totalShare, p);
        }
    }

    public static void drawSplitTimeBar(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        double workShare,
        double waitShare,
        double totalShare,
        CirrusPalette p
    ) {
        if (width <= 2 || height <= 2) {
            return;
        }
        canvas.fill(x, y, width, height, OreUiPaint.meterBorder(p));
        int innerW = width - 2;
        int innerH = height - 2;
        canvas.fill(x + 1, y + 1, innerW, innerH, OreUiPaint.meterTrack(p));
        int totalFill = Math
                .min(innerW, Math.max(0, (int) Math.round(innerW * Math.max(0.0, Math.min(1.0, totalShare)))));
        if (totalFill <= 0) {
            return;
        }
        double sum = Math.max(0.0001, workShare + waitShare);
        int workW = Math.min(totalFill, (int) Math.round(totalFill * (workShare / sum)));
        int waitW = Math.max(0, totalFill - workW);
        if (workW > 0) {
            canvas.fill(x + 1, y + 1, workW, innerH, p.costH());
        }
        if (waitW > 0) {
            canvas.fill(x + 1 + workW, y + 1, waitW, innerH, p.costP());
        }
    }

    public static void drawStepKindGlyph(SceneCanvas canvas, PlanStep step, int x, int y, int color) {
        switch (step) {
            case AcquireStep ignored -> {
                canvas.fill(x + 3, y, 2, 1, color);
                canvas.fill(x + 2, y + 1, 4, 2, color);
                canvas.fill(x + 1, y + 3, 6, 2, color);
                canvas.fill(x + 2, y + 5, 4, 2, color);
                canvas.fill(x + 3, y + 7, 2, 1, color);
            }
            case ProvisionMachineStep ignored -> {
                canvas.strokeRect(x, y + 1, 8, 6, color, 1);
                canvas.fill(x + 2, y + 3, 4, 2, color);
                canvas.fill(x + 2, y, 4, 1, color);
            }
            case SetupLineStep ignored -> {
                canvas.fill(x, y + 2, 6, 1, color);
                canvas.fill(x + 4, y + 1, 2, 3, color);
                canvas.fill(x + 2, y + 5, 6, 1, color);
                canvas.fill(x + 6, y + 4, 2, 3, color);
            }
            case LinePullStep ignored -> {
                canvas.fill(x, y + 3, 6, 2, color);
                canvas.fill(x + 5, y + 1, 2, 6, color);
            }
            case ConvertStep ignored -> {
                canvas.fill(x, y + 2, 6, 1, color);
                canvas.fill(x + 4, y + 1, 2, 3, color);
                canvas.fill(x + 2, y + 5, 6, 1, color);
                canvas.fill(x + 2, y + 4, 2, 3, color);
            }
            case CycleLoopStep ignored -> {
                canvas.strokeRect(x + 1, y + 1, 6, 6, color, 1);
                canvas.fill(x + 6, y + 2, 2, 2, color);
            }
            case CraftStep ignored -> {
                canvas.fill(x + 1, y + 1, 2, 2, color);
                canvas.fill(x + 5, y + 1, 2, 2, color);
                canvas.fill(x + 1, y + 5, 2, 2, color);
                canvas.fill(x + 5, y + 5, 2, 2, color);
            }
            default -> canvas.fill(x + 2, y + 2, 4, 4, color);
        }
    }

    private void drawStageBadge(
        SceneCanvas canvas,
        StageCell cell,
        int bx,
        int by,
        int bw,
        int bh,
        int stageAccent,
        CirrusPalette p
    ) {
        String numText = String.format("%02d", cell.displayIndex() + 1);
        int badgeFill = cell.hasSelected()
                ? p.focusRing()
                : OreUiPaint.withAlpha(stageAccent, OreUiPaint.isDark(p) ? 0.24f : 0.14f);
        int badgeInk = cell.hasSelected() ? p.textOnAccent() : stageAccent;
        canvas.fill(bx, by, bw, bh, badgeFill);
        canvas.strokeRect(bx, by, bw, bh, OreUiPaint.withAlpha(stageAccent, 0.68f), 1);
        Component numComp = Component.literal(numText);
        int numX = bx + (bw - ChromeText.width(canvas, numComp)) / 2;
        ChromeText.draw(canvas, numComp, numX, ChromeText.centeredY(by, bh, canvas), badgeInk);
    }

    private void drawNodeSlot(SceneCanvas canvas, NodeIconHit icon, CirrusPalette p, int mouseX, int mouseY) {
        boolean iconHover = icon.contains(mouseX, mouseY);
        boolean iconSelected = selectedStepIds.contains(icon.node().stepId());
        StepNodeCardRenderer.StepVisuals data = visuals
                .getOrDefault(icon.node().stepId(), StepNodeCardRenderer.StepVisuals.empty());
        ComponentStackKey resource = DagRichTooltips.primaryIconOf(icon.node().step(), data);
        long amount = data.outputAmount() > 0L ? data.outputAmount() : data.count();
        OreUiSlotAmount.drawSlot(canvas, resource, amount, icon.x(), icon.y(), icon.size(), p);
        if (iconSelected) {
            canvas.strokeRect(icon.x(), icon.y(), icon.size(), icon.size(), p.focusRing(), 1);
        } else if (iconHover) {
            canvas.strokeRect(icon.x(), icon.y(), icon.size(), icon.size(), p.stepTint(icon.node().step()).accent(), 1);
        }
    }

    private static void drawOverflowSlot(SceneCanvas canvas, int overflow, int x, int y, int size, CirrusPalette p) {
        OreUiPaint.slot(canvas, x, y, size, p);
        Component more = Component.literal("+" + overflow);
        int mw = ChromeText.width(canvas, more);
        ChromeText.draw(
            canvas,
            more,
            x + Math.max(1, (size - mw) / 2),
            ChromeText.centeredY(y, size, canvas),
            p.focusRing()
        );
    }

    public @Nullable PlanStep leadStepOf(StageCell cell) {
        if (cell.nodes().isEmpty()) {
            return null;
        }
        DagNodeBox lead = cell.nodes().get(0);
        double bestTime = -1.0;
        for (DagNodeBox node : cell.nodes()) {
            double t = node.step().stepCost().humanActiveSeconds() + node.step().stepCost().passiveWaitSeconds();
            if (criticalStepIds.contains(node.stepId()) || t > bestTime) {
                lead = node;
                bestTime = t;
                if (criticalStepIds.contains(node.stepId())) {
                    break;
                }
            }
        }
        return lead.step();
    }

    public static double stageWorkOf(StageCell cell) {
        double maxWork = 0.0;
        for (DagNodeBox node : cell.nodes()) {
            maxWork = Math.max(maxWork, node.step().stepCost().humanActiveSeconds());
        }
        return maxWork;
    }

    public static double stageWaitOf(StageCell cell) {
        double maxWait = 0.0;
        for (DagNodeBox node : cell.nodes()) {
            maxWait = Math.max(maxWait, node.step().stepCost().passiveWaitSeconds());
        }
        return maxWait;
    }

    private List<StageCell> computeCells() {
        int w = width() > 0 ? width() : appliedW;
        int h = height() > 0 ? height() : appliedH;
        if (w <= 0 || h <= 0 || layout.isEmpty()) {
            return List.of();
        }
        TreeMap<Integer, List<DagNodeBox>> byLayer = new TreeMap<>();
        for (DagNodeBox node : layout.nodes()) {
            byLayer.computeIfAbsent(node.layer(), ignored -> new ArrayList<>()).add(node);
        }
        List<Map.Entry<Integer, List<DagNodeBox>>> ordered = new ArrayList<>(byLayer.entrySet());
        if (layoutMode == DagLayoutMode.horizontalRl || layoutMode == DagLayoutMode.verticalBt) {
            Collections.reverse(ordered);
        }
        boolean vertical = isVertical();
        Comparator<DagNodeBox> withinStage = Comparator
                .comparingDouble((DagNodeBox n) -> vertical ? n.centerX() : n.centerY())
                .thenComparing(DagNodeBox::stepId);
        for (var entry : ordered) {
            entry.getValue().sort(withinStage);
        }

        if (ordered.isEmpty()) {
            return List.of();
        }
        return vertical ? layoutVerticalCells(ordered, w, h) : layoutHorizontalCells(ordered, w, h);
    }

    private List<StageCell> layoutHorizontalCells(List<Map.Entry<Integer, List<DagNodeBox>>> ordered, int w, int h) {
        int stageCount = ordered.size();
        int innerLeft = railPad;
        int innerWidth = Math.max(28, w - railPad * 2);
        int cellY = railPad;
        int cellH = Math.max(26, h - railPad * 2 - trackThickness - 3);
        int totalGap = cellGap * Math.max(0, stageCount - 1);
        int minCellW = 68;
        boolean scrollable = stageCount * minCellW + totalGap > innerWidth;
        int availW = scrollable ? stageCount * minCellW : Math.max(stageCount * 42, innerWidth - totalGap);

        double[] weights = new double[stageCount];
        double totalWeight = 0.0;
        float[] anchors = new float[stageCount];
        for (int i = 0; i < stageCount; i++) {
            List<DagNodeBox> nodes = ordered.get(i).getValue();
            weights[i] = 1.0 + 0.24 * Math.min(3, Math.max(0, nodes.size() - 1));
            totalWeight += weights[i];
            float worldCenterX = averageCenterX(nodes);
            anchors[i] = (float) viewport.worldToScreenX(worldCenterX) - appliedX;
        }

        int[] widths = new int[stageCount];
        int used = 0;
        for (int i = 0; i < stageCount; i++) {
            int cw = scrollable
                    ? minCellW
                    : i == stageCount - 1
                            ? Math.max(42, availW - used)
                            : Math.max(42, (int) Math.round(availW * (weights[i] / totalWeight)));
            widths[i] = cw;
            used += cw;
        }
        int contentTotalW = used + totalGap;
        maxScrollPx = Math.max(0f, contentTotalW - innerWidth);
        scrollOffsetPx = Math.max(0f, Math.min(maxScrollPx, scrollOffsetPx));

        List<StageCell> cells = new ArrayList<>(stageCount);
        float visX0 = viewport.visibleWorldX0();
        float visX1 = viewport.visibleWorldX1(Math.max(1, canvasWidth));
        int cx = innerLeft - Math.round(scrollOffsetPx);
        int slotStep = slotSize - 1;

        for (int i = 0; i < stageCount; i++) {
            int layer = ordered.get(i).getKey();
            List<DagNodeBox> nodes = List.copyOf(ordered.get(i).getValue());
            int cw = widths[i];
            boolean inView = false;
            boolean hasSelected = false;
            boolean hasCritical = false;
            boolean hasBeacon = false;
            for (DagNodeBox node : nodes) {
                if (node.maxX() >= visX0 && node.x() <= visX1) {
                    inView = true;
                }
                if (selectedStepIds.contains(node.stepId())) {
                    hasSelected = true;
                }
                if (criticalStepIds.contains(node.stepId())) {
                    hasCritical = true;
                }
                if (inlineBeacons.containsKey(node.stepId())) {
                    hasBeacon = true;
                }
            }
            List<NodeIconHit> hits = new ArrayList<>();
            int innerTop = cellY + 2;
            int innerH = Math.max(18, cellH - 5);
            int slotX = cx + 24;
            int slotY = innerTop + Math.max(0, (innerH - slotSize) / 2);
            int maxIcons = Math.min(maxIconsPerCell, nodes.size());
            int overflowReserve = nodes.size() > maxIcons ? slotSize + 2 : 4;
            int maxSlotRight = cx + cw - overflowReserve;
            for (int k = 0; k < maxIcons; k++) {
                if (slotX + slotSize > maxSlotRight) {
                    break;
                }
                hits.add(new NodeIconHit(nodes.get(k), slotX, slotY, slotSize));
                slotX += slotStep;
            }
            cells.add(
                new StageCell(
                    i,
                    layer,
                    nodes,
                    cx,
                    cellY,
                    cw,
                    cellH,
                    anchors[i],
                    inView,
                    hasSelected,
                    hasCritical,
                    hasBeacon,
                    List.copyOf(hits)
                )
            );
            cx += cw + cellGap;
        }
        return cells;
    }

    private List<StageCell> layoutVerticalCells(List<Map.Entry<Integer, List<DagNodeBox>>> ordered, int w, int h) {
        int stageCount = ordered.size();
        int innerTop = railPad + verticalHeaderHeight + 3;
        int innerHeight = Math.max(46, h - innerTop - railPad);
        int cellX = railPad;
        int cellW = Math.max(66, w - railPad * 2 - trackThickness - 4);
        int totalGap = cellGap * Math.max(0, stageCount - 1);
        int cellH = Math.max(42, Math.min(50, (innerHeight - totalGap) / stageCount));
        int contentTotalH = stageCount * cellH + totalGap;
        maxScrollPx = Math.max(0f, contentTotalH - innerHeight);
        scrollOffsetPx = Math.max(0f, Math.min(maxScrollPx, scrollOffsetPx));

        List<StageCell> cells = new ArrayList<>(stageCount);
        float visY0 = viewport.visibleWorldY0();
        float visY1 = viewport.visibleWorldY1(Math.max(1, canvasHeight));
        int cy = innerTop - Math.round(scrollOffsetPx);
        int slotStep = slotSize - 1;

        for (int i = 0; i < stageCount; i++) {
            int layer = ordered.get(i).getKey();
            List<DagNodeBox> nodes = List.copyOf(ordered.get(i).getValue());
            float worldCenterY = averageCenterY(nodes);
            float anchorY = (float) viewport.worldToScreenY(worldCenterY) - appliedY;
            boolean inView = false;
            boolean hasSelected = false;
            boolean hasCritical = false;
            boolean hasBeacon = false;
            for (DagNodeBox node : nodes) {
                if (node.maxY() >= visY0 && node.y() <= visY1) {
                    inView = true;
                }
                if (selectedStepIds.contains(node.stepId())) {
                    hasSelected = true;
                }
                if (criticalStepIds.contains(node.stepId())) {
                    hasCritical = true;
                }
                if (inlineBeacons.containsKey(node.stepId())) {
                    hasBeacon = true;
                }
            }
            List<NodeIconHit> hits = new ArrayList<>();
            int slotX = cellX + 4;
            int slotY = cy + 18;
            if (slotY + slotSize <= cy + chBottomSafe(cellH)) {
                int maxIcons = nodes.size() > maxIconsPerCell ? 2 : Math.min(maxIconsPerCell, nodes.size());
                int iconLimitRight = nodes.size() > maxIcons ? cellX + cellW - slotSize - 4 : cellX + cellW - 22;
                for (int k = 0; k < maxIcons; k++) {
                    if (slotX + slotSize > iconLimitRight) {
                        break;
                    }
                    hits.add(new NodeIconHit(nodes.get(k), slotX, slotY, slotSize));
                    slotX += slotStep;
                }
            }
            cells.add(
                new StageCell(
                    i,
                    layer,
                    nodes,
                    cellX,
                    cy,
                    cellW,
                    cellH,
                    anchorY,
                    inView,
                    hasSelected,
                    hasCritical,
                    hasBeacon,
                    List.copyOf(hits)
                )
            );
            cy += cellH + cellGap;
        }
        return cells;
    }

    private static int chBottomSafe(int cellH) {
        return cellH >= 42 ? cellH - 9 : cellH - 3;
    }

    private boolean hitsTrack(double localX, double localY) {
        if (isVertical()) {
            int tx = width() - railPad - trackThickness - 2;
            return localX >= tx && localX <= width() && localY >= 0 && localY <= height();
        }
        int ty = height() - railPad - trackThickness - 2;
        return localX >= 0 && localX <= width() && localY >= ty && localY <= height();
    }

    private void scrubTo(double localX, double localY) {
        if (layout.isEmpty()) {
            return;
        }
        if (isVertical()) {
            int th = Math.max(1, height() - railPad * 2);
            double v = clamp01((localY - railPad) / th);
            double worldY = layout.contentMinY() + v * Math.max(1.0, layout.contentMaxY() - layout.contentMinY());
            double currentCenterX = viewport.screenToWorldX(Math.max(1, canvasWidth) * 0.5);
            viewport.centerOnWorld(currentCenterX, worldY, Math.max(1, canvasWidth), Math.max(1, canvasHeight));
        } else {
            int tw = Math.max(1, width() - railPad * 2);
            double u = clamp01((localX - railPad) / tw);
            double worldX = layout.contentMinX() + u * Math.max(1.0, layout.contentMaxX() - layout.contentMinX());
            double currentCenterY = viewport.screenToWorldY(Math.max(1, canvasHeight) * 0.5);
            viewport.centerOnWorld(worldX, currentCenterY, Math.max(1, canvasWidth), Math.max(1, canvasHeight));
        }
        onCameraChanged.run();
    }

    private void updateHover(double localX, double localY) {
        Integer nextStage = null;
        String nextNode = null;
        boolean onTrack = hitsTrack(localX, localY);
        List<StageCell> cells = computeCells();
        for (StageCell cell : cells) {
            for (NodeIconHit icon : cell.iconHits()) {
                if (icon.contains(localX, localY)) {
                    nextStage = cell.displayIndex();
                    nextNode = icon.node().stepId();
                    break;
                }
            }
            if (nextNode != null) {
                break;
            }
            if (cell.contains(localX, localY)) {
                nextStage = cell.displayIndex();
                break;
            }
        }
        if (nextStage == null && onTrack && !cells.isEmpty()) {
            double bestDist = Double.MAX_VALUE;
            for (StageCell cell : cells) {
                double center = isVertical() ? cell.y() + cell.height() * 0.5 : cell.x() + cell.width() * 0.5;
                double d = Math.abs((isVertical() ? localY : localX) - center);
                if (d < bestDist) {
                    bestDist = d;
                    nextStage = cell.displayIndex();
                }
            }
        }
        hoveredStageIndex = nextStage;
        hoveredNodeStepId = nextNode;
        hoveringTrack = onTrack;
    }

    public int dominantAccent(StageCell cell, CirrusPalette p) {
        if (cell.hasSelected()) {
            return p.focusRing();
        }
        if (cell.hasCritical()) {
            return p.edgeCriticalPath();
        }
        return cell.nodes().isEmpty() ? p.focusRing() : p.stepTint(cell.nodes().get(0).step()).accent();
    }

    private int countUpstream(String stepId) {
        int count = 0;
        for (var edge : layout.edges()) {
            if (edge.toStepId().equals(stepId)) {
                count++;
            }
        }
        return count;
    }

    private int countDownstream(String stepId) {
        int count = 0;
        for (var edge : layout.edges()) {
            if (edge.fromStepId().equals(stepId)) {
                count++;
            }
        }
        return count;
    }

    public double totalPlanTime() {
        double total = 0.0;
        for (DagNodeBox node : layout.nodes()) {
            total += node.step().stepCost().humanActiveSeconds() + node.step().stepCost().passiveWaitSeconds();
        }
        return total;
    }

    private static float averageCenterX(List<DagNodeBox> nodes) {
        float sum = 0f;
        for (DagNodeBox node : nodes) {
            sum += node.centerX();
        }
        return nodes.isEmpty() ? 0f : sum / nodes.size();
    }

    private static float averageCenterY(List<DagNodeBox> nodes) {
        float sum = 0f;
        for (DagNodeBox node : nodes) {
            sum += node.centerY();
        }
        return nodes.isEmpty() ? 0f : sum / nodes.size();
    }

    private static float clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
