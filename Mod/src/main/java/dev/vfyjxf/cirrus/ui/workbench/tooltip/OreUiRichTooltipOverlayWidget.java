package dev.vfyjxf.cirrus.ui.workbench.tooltip;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagLocatorRulerWidget;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetPath;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Top-most shell overlay that queries hovered {@link OreUiTooltipProvider} widgets, draws styled
 * OreUI tooltips, and manages JEI-style {@code Shift}-pinned interactive tooltips for any tooltip
 * carrying an interactive {@link OreUiRichTooltip.Block.ItemGrid}.
 */
public class OreUiRichTooltipOverlayWidget extends Widget {

    /** Matches JEI's {@code INTERACTIVE_INGREDIENT_TOOLTIP_SCREEN_DIM} scrim alpha. */
    private static final int pinnedScreenDim = 0x40000000;

    private static final class PinnedSession {
        final OreUiRichTooltip tooltip;
        final OreUiRichTooltip.Block.ItemGrid grid;
        final OreUiRichTooltip.GridInteraction interaction;
        final OreUiRichTooltipRenderer.Placement lockedBox;
        int scrollRow;
        OreUiRichTooltipRenderer.@Nullable GridGeometry lastGridGeometry;
        boolean draggingScrollbar;
        boolean suppressNextClick;
        int pressedSlotIndex = -1;

        PinnedSession(
            OreUiRichTooltip tooltip,
            OreUiRichTooltip.Block.ItemGrid grid,
            OreUiRichTooltip.GridInteraction interaction,
            OreUiRichTooltipRenderer.Placement lockedBox
        ) {
            this.tooltip = tooltip;
            this.grid = grid;
            this.interaction = interaction;
            this.lockedBox = lockedBox;
        }
    }

    private final WorkbenchShellContext shellContext;
    private BooleanSupplier shiftHeldSupplier = OreUiRichTooltipOverlayWidget::defaultShiftHeld;
    private @Nullable OreUiRichTooltip pinnedTooltip;
    private int pinnedAnchorX;
    private int pinnedAnchorY;
    private @Nullable OreUiRichTooltip lastRendered;
    private OreUiRichTooltipRenderer.@Nullable Placement lastPlacement;
    private @Nullable String lastTooltipTitle;
    private long tooltipShownAtMs;
    private float lastRenderedSlideY;
    private int lastPointerX;
    private int lastPointerY;
    private boolean hasPointerPosition;
    private @Nullable PinnedSession pinnedSession;
    private boolean suppressPinUntilShiftReleased;
    private final OreUiMotion.SmoothValue smoothAnchorX = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue smoothAnchorY = new OreUiMotion.SmoothValue();

    public OreUiRichTooltipOverlayWidget(WorkbenchShellContext shellContext) {
        this.shellContext = Objects.requireNonNull(shellContext, "shellContext");
        addStyleClass("workbench-tooltip-overlay");
        useStyle(sizeFull(), positionAbsolute(), insetZero());
        setInteractive(false);
        setFocusable(false);
        setTickable(true);

        onTick(this::tickPinnedState);
        onUnmount(this::unpinInteractiveTooltip);
        onMouseClicked((input, ctx) -> {
            PinnedSession session = pinnedSession;
            if (session == null) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            OreUiRichTooltipRenderer.GridGeometry geom = session.lastGridGeometry;
            session.pressedSlotIndex = -1;
            if (input.isLeftClick() && geom != null) {
                if (geom.isOverScrollbar(local.x(), local.y())) {
                    session.draggingScrollbar = true;
                    session.suppressNextClick = true;
                    session.scrollRow = geom.scrollRowForPointerY(local.y());
                } else {
                    session.pressedSlotIndex = geom.itemIndexAt(local.x(), local.y(), session.scrollRow);
                }
            }
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            PinnedSession session = pinnedSession;
            if (session == null) {
                return EventDispatch.pass;
            }
            if (session.draggingScrollbar && session.lastGridGeometry != null) {
                FloatPos local = input.mouseRelative(this);
                session.scrollRow = session.lastGridGeometry.scrollRowForPointerY(local.y());
            }
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseReleased((input, ctx) -> {
            PinnedSession session = pinnedSession;
            if (session == null) {
                return EventDispatch.pass;
            }
            session.draggingScrollbar = false;
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, ctx) -> {
            PinnedSession session = pinnedSession;
            if (session == null) {
                return EventDispatch.pass;
            }
            if (session.suppressNextClick) {
                session.suppressNextClick = false;
                session.pressedSlotIndex = -1;
                ctx.consume();
                return EventDispatch.consumed;
            }
            int pressedIdx = session.pressedSlotIndex;
            session.pressedSlotIndex = -1;
            if (input.isLeftClick()) {
                FloatPos local = input.mouseRelative(this);
                OreUiRichTooltipRenderer.GridGeometry geom = session.lastGridGeometry;
                int idx = geom != null ? geom.itemIndexAt(local.x(), local.y(), session.scrollRow) : -1;
                if (idx >= 0 && idx < session.grid.items().size() && (pressedIdx < 0 || pressedIdx == idx)) {
                    ContentStack<?, ?> picked = session.grid.items().get(idx);
                    OreUiRichTooltip.GridInteraction interaction = session.interaction;
                    CirrusUiSound.playClickSound();
                    dismissInteractiveTooltip();
                    interaction.onItemClick(idx, picked);
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                boolean overScrollbar = geom != null && geom.isOverScrollbar(local.x(), local.y());
                if (!overScrollbar && !session.lockedBox.contains(local.x(), local.y())) {
                    dismissInteractiveTooltip();
                }
            }
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseScrolled((mx, my, scrollX, scrollY, ctx) -> {
            PinnedSession session = pinnedSession;
            if (session == null) {
                return EventDispatch.pass;
            }
            double delta = Math.abs(scrollX) > Math.abs(scrollY) ? scrollX : scrollY;
            scrollPinnedGrid(session, delta);
            ctx.consume();
            return EventDispatch.consumed;
        });
    }

    public void pinTooltip(@Nullable OreUiRichTooltip tooltip, int anchorX, int anchorY) {
        this.pinnedTooltip = tooltip;
        this.pinnedAnchorX = anchorX;
        this.pinnedAnchorY = anchorY;
    }

    public void clearPinnedTooltip() {
        this.pinnedTooltip = null;
    }

    public @Nullable OreUiRichTooltip lastRendered() {
        return lastRendered;
    }

    public OreUiRichTooltipRenderer.@Nullable Placement lastPlacement() {
        return lastPlacement;
    }

    public boolean isInteractiveTooltipPinned() {
        return pinnedSession != null;
    }

    public OreUiRichTooltipRenderer.@Nullable Placement pinnedInteractivePlacement() {
        PinnedSession session = pinnedSession;
        return session == null ? null : session.lockedBox;
    }

    public int pinnedInteractiveScrollRow() {
        PinnedSession session = pinnedSession;
        return session == null ? 0 : session.scrollRow;
    }

    public void stopScrollbarDrag() {
        PinnedSession session = pinnedSession;
        if (session != null) {
            session.draggingScrollbar = false;
        }
    }

    /**
     * Dismisses any active {@code Shift}-pinned interactive tooltip and suppresses re-pinning until
     * {@code Shift} is released.
     */
    public boolean dismissInteractiveTooltip() {
        if (pinnedSession == null) {
            return false;
        }
        unpinInteractiveTooltip();
        suppressPinUntilShiftReleased = shiftHeldSupplier.getAsBoolean();
        return true;
    }

    /** Test seam for controlling {@code Shift} state without GLFW window focus. */
    public void setShiftHeldSupplier(BooleanSupplier supplier) {
        this.shiftHeldSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    /** Test seam for scrolling the active pinned grid directly. */
    public boolean scrollPinnedInteractiveGrid(double scrollDelta) {
        PinnedSession session = pinnedSession;
        if (session == null) {
            return false;
        }
        return scrollPinnedGrid(session, scrollDelta);
    }

    /** Test seam for syncing pinned state at a specific overlay-local cursor coordinate. */
    public void syncInteractivePinAt(int mouseX, int mouseY, @Nullable Font font) {
        this.lastPointerX = mouseX;
        this.lastPointerY = mouseY;
        this.hasPointerPosition = true;
        syncPinnedState(mouseX, mouseY, font, true);
    }

    private static boolean scrollPinnedGrid(PinnedSession session, double delta) {
        OreUiRichTooltipRenderer.GridGeometry geom = session.lastGridGeometry;
        if (delta == 0.0 || geom == null || geom.maxRowOffset() <= 0) {
            return false;
        }
        int step = delta > 0.0 ? -1 : 1;
        int next = Math.max(0, Math.min(geom.maxRowOffset(), session.scrollRow + step));
        if (next == session.scrollRow) {
            return false;
        }
        session.scrollRow = next;
        return true;
    }

    private void tickPinnedState() {
        if (!lifecycle().mounted()) {
            unpinInteractiveTooltip();
            return;
        }
        if (hasPointerPosition) {
            syncPinnedState(lastPointerX, lastPointerY, null, false);
        } else if (!shiftHeldSupplier.getAsBoolean()) {
            suppressPinUntilShiftReleased = false;
            unpinInteractiveTooltip();
        }
    }

    private void syncPinnedState(int mouseX, int mouseY, @Nullable Font font, boolean allowNullFontMeasure) {
        boolean shiftHeld = shiftHeldSupplier.getAsBoolean();
        if (!shiftHeld) {
            suppressPinUntilShiftReleased = false;
            unpinInteractiveTooltip();
            return;
        }
        if (suppressPinUntilShiftReleased) {
            return;
        }
        if (pinnedSession != null) {
            if (shellContext.commandPaletteOpen() || !lifecycle().mounted()) {
                unpinInteractiveTooltip();
            }
            return;
        }
        if (!lifecycle().mounted() || shellContext.commandPaletteOpen() || isTextInputFocused(scene())) {
            return;
        }
        int viewW = width();
        int viewH = height();
        if (viewW <= 0 || viewH <= 0) {
            return;
        }

        FloatPos scenePos = localToScene(mouseX, mouseY);
        OreUiRichTooltip candidate = resolveAt(scenePos.x, scenePos.y);
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        OreUiRichTooltip.Block.ItemGrid grid = candidate.interactiveGrid();
        OreUiRichTooltip.GridInteraction interaction = grid == null ? null : grid.interaction();
        if (grid == null || interaction == null) {
            return;
        }

        OreUiRichTooltip tooltipToPin = candidate;
        OreUiRichTooltipRenderer.Placement lockedBox;
        OreUiRichTooltip prevRendered = lastRendered;
        OreUiRichTooltipRenderer.Placement prevPlace = lastPlacement;
        if (prevRendered != null
                && prevPlace != null
                && prevPlace.width() > 0
                && prevPlace.height() > 0
                && prevRendered.hasInteractiveGrid()
                && Objects.equals(prevRendered.title().getString(), candidate.title().getString())) {
            int margin = OreUiRichTooltipRenderer.viewportMargin;
            int lockedX = Math.max(margin, Math.min(Math.max(margin, viewW - prevPlace.width() - margin), prevPlace.x()));
            int rawY = prevPlace.y() + Math.round(lastRenderedSlideY);
            int lockedY = Math.max(margin, Math.min(Math.max(margin, viewH - prevPlace.height() - margin), rawY));
            lockedBox = new OreUiRichTooltipRenderer.Placement(lockedX, lockedY, prevPlace.width(), prevPlace.height());
            tooltipToPin = prevRendered;
            OreUiRichTooltip.Block.ItemGrid prevGrid = prevRendered.interactiveGrid();
            if (prevGrid != null && prevGrid.interaction() != null) {
                grid = prevGrid;
                interaction = prevGrid.interaction();
            }
        } else {
            if (font == null && !allowNullFontMeasure) {
                return;
            }
            int dynamicMaxW = Math.min(
                OreUiRichTooltipRenderer.maxWidth,
                Math.max(OreUiRichTooltipRenderer.minWidth, viewW - OreUiRichTooltipRenderer.viewportMargin * 2)
            );
            OreUiRichTooltipRenderer.Measured measured = OreUiRichTooltipRenderer.measure(tooltipToPin, font, dynamicMaxW);
            lockedBox = OreUiRichTooltipRenderer.position(
                mouseX,
                mouseY,
                measured.width(),
                measured.height(),
                viewW,
                viewH
            );
        }

        pinnedSession = new PinnedSession(tooltipToPin, grid, interaction, lockedBox);
        setInteractive(true);
    }

    private void unpinInteractiveTooltip() {
        if (pinnedSession == null) {
            return;
        }
        pinnedSession = null;
        setInteractive(false);
        lastRendered = null;
        lastPlacement = null;
        lastTooltipTitle = null;
        tooltipShownAtMs = 0L;
        lastRenderedSlideY = 0f;
        smoothAnchorX.reset();
        smoothAnchorY.reset();
    }

    public @Nullable OreUiRichTooltip resolveAt(double sceneX, double sceneY) {
        if (!lifecycle().mounted()) {
            return null;
        }
        Scene currentScene = scene();
        Widget hit = currentScene.hitTest(sceneX, sceneY);
        for (Widget cursor = hit; cursor != null; cursor = cursor.parent()) {
            if (cursor == this) {
                continue;
            }
            if (cursor instanceof OreUiTooltipProvider provider) {
                FloatPos local = cursor.sceneToLocal(sceneX, sceneY);
                OreUiRichTooltip tooltip = provider.richTooltipAt(local.x, local.y);
                if (tooltip != null && !tooltip.isEmpty()) {
                    return tooltip;
                }
            }
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int viewW = width();
        int viewH = height();
        if (viewW <= 0 || viewH <= 0) {
            return;
        }
        lastPointerX = mouseX;
        lastPointerY = mouseY;
        hasPointerPosition = true;
        syncPinnedState(mouseX, mouseY, ChromeText.font(canvas), true);

        CirrusPalette palette = shellContext.palette();
        PinnedSession session = pinnedSession;
        if (session != null) {
            renderPinnedSession(canvas, session, mouseX, mouseY, viewW, viewH, palette);
            return;
        }

        OreUiRichTooltip tooltip;
        int anchorX;
        int anchorY;
        int effectiveViewH = viewH;
        if (pinnedTooltip != null && !pinnedTooltip.isEmpty()) {
            tooltip = pinnedTooltip;
            anchorX = pinnedAnchorX;
            anchorY = pinnedAnchorY;
        } else {
            FloatPos scenePos = localToScene(mouseX, mouseY);
            tooltip = resolveAt(scenePos.x, scenePos.y);
            anchorX = mouseX;
            anchorY = mouseY;
            DagLocatorRulerWidget locator = hoveredLocatorAt(scenePos.x, scenePos.y);
            if (locator != null) {
                Rect locBounds = locator.absoluteBounds();
                FloatPos locOrigin = sceneToLocal(locBounds.x(), locBounds.y());
                if (locator.isVertical()) {
                    anchorX = Math.min(viewW - 16, (int) Math.round(locOrigin.x) + locBounds.width() + 174);
                } else {
                    effectiveViewH = Math.max(140, (int) Math.round(locOrigin.y) - 62);
                    anchorY = effectiveViewH - 2;
                }
            }
        }
        lastRendered = tooltip;
        if (tooltip == null || tooltip.isEmpty()) {
            lastPlacement = null;
            lastTooltipTitle = null;
            tooltipShownAtMs = 0L;
            lastRenderedSlideY = 0f;
            smoothAnchorX.reset();
            smoothAnchorY.reset();
            return;
        }
        boolean pinned = pinnedTooltip != null && !pinnedTooltip.isEmpty();
        boolean reduced = pinned || OreUiMotion.reducedMotion();
        String titleKey = tooltip.title().getString();
        long nowMs = OreUiMotion.nowMs();
        if (!Objects.equals(lastTooltipTitle, titleKey)) {
            lastTooltipTitle = titleKey;
            tooltipShownAtMs = nowMs;
            if (!smoothAnchorX.initialized()) {
                smoothAnchorX.snap(anchorX, System.nanoTime());
                smoothAnchorY.snap(anchorY, System.nanoTime());
            }
        }
        long nowNanos = System.nanoTime();
        int renderAnchorX = Math.round(smoothAnchorX.update(anchorX, 24f, nowNanos, reduced));
        int renderAnchorY = Math.round(smoothAnchorY.update(anchorY, 24f, nowNanos, reduced));
        float appear = OreUiMotion.cubicOut(nowMs - tooltipShownAtMs, OreUiMotion.tooltipAppearMs, reduced);
        float slideY = (1f - appear) * 4f;
        lastRenderedSlideY = slideY;
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            lastPlacement = OreUiRichTooltipRenderer
                    .render(canvas, tooltip, renderAnchorX, renderAnchorY, viewW, effectiveViewH, palette);
        } finally {
            canvas.popTransform();
        }
    }

    private void renderPinnedSession(
        SceneCanvas canvas,
        PinnedSession session,
        int mouseX,
        int mouseY,
        int viewW,
        int viewH,
        CirrusPalette palette
    ) {
        canvas.fill(0, 0, viewW, viewH, pinnedScreenDim);

        OreUiRichTooltipRenderer.GridGeometry prevGeom = session.lastGridGeometry;
        int hoveredIdx = prevGeom != null && !session.draggingScrollbar
                ? prevGeom.itemIndexAt(mouseX, mouseY, session.scrollRow)
                : -1;
        boolean sbHover = prevGeom != null && prevGeom.isOverScrollbar(mouseX, mouseY);
        boolean scrollable = prevGeom != null && prevGeom.maxRowOffset() > 0;
        Component pinnedFooter = session.interaction.pinnedFooterHint(scrollable);

        OreUiRichTooltipRenderer.RenderResult result = OreUiRichTooltipRenderer.renderPinned(
            canvas,
            session.tooltip,
            session.lockedBox,
            new OreUiRichTooltipRenderer.GridRenderState(
                session.scrollRow,
                hoveredIdx,
                sbHover,
                session.draggingScrollbar
            ),
            pinnedFooter,
            palette
        );
        session.lastGridGeometry = result.interactiveGrid();
        lastRendered = session.tooltip;
        lastPlacement = result.placement();
        lastRenderedSlideY = 0f;

        if (prevGeom == null && result.interactiveGrid() != null && !session.draggingScrollbar) {
            hoveredIdx = result.interactiveGrid().itemIndexAt(mouseX, mouseY, session.scrollRow);
        }
        if (hoveredIdx >= 0 && hoveredIdx < session.grid.items().size() && !session.draggingScrollbar) {
            OreUiRichTooltip subTip = session.interaction.tooltipForItem(hoveredIdx, session.grid.items().get(hoveredIdx));
            if (subTip != null && !subTip.isEmpty()) {
                OreUiRichTooltipRenderer.render(canvas, subTip, mouseX, mouseY, viewW, viewH, palette);
            }
        }
    }

    private static boolean defaultShiftHeld() {
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTextInputFocused(@Nullable Scene scene) {
        if (scene == null) {
            return false;
        }
        Widget focused = scene.focusingWidget();
        if (focused == null) {
            return false;
        }
        WidgetPath path = focused.path();
        for (int i = 0; i < path.size(); i++) {
            Widget w = path.get(i);
            if (w instanceof TextFieldWidget
                    || w instanceof CirrusTextInputWidget
                    || w instanceof SearchBoxWidget
                    || w instanceof NumberInputWidget) {
                return true;
            }
        }
        return false;
    }

    private @Nullable DagLocatorRulerWidget hoveredLocatorAt(double sceneX, double sceneY) {
        if (!lifecycle().mounted()) {
            return null;
        }
        Widget hit = scene().hitTest(sceneX, sceneY);
        for (Widget cursor = hit; cursor != null; cursor = cursor.parent()) {
            if (cursor instanceof DagLocatorRulerWidget locator) {
                return locator;
            }
        }
        return null;
    }
}
