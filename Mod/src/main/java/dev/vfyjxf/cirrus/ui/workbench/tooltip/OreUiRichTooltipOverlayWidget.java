package dev.vfyjxf.cirrus.ui.workbench.tooltip;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagLocatorRulerWidget;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Top-most shell overlay that queries hovered {@link OreUiTooltipProvider} widgets and draws styled OreUI tooltips.
 */
public class OreUiRichTooltipOverlayWidget extends Widget {

    private final WorkbenchShellContext shellContext;
    private @Nullable OreUiRichTooltip pinnedTooltip;
    private int pinnedAnchorX;
    private int pinnedAnchorY;
    private @Nullable OreUiRichTooltip lastRendered;
    private OreUiRichTooltipRenderer.@Nullable Placement lastPlacement;
    private @Nullable String lastTooltipTitle;
    private long tooltipShownAtMs;
    private final OreUiMotion.SmoothValue smoothAnchorX = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue smoothAnchorY = new OreUiMotion.SmoothValue();

    public OreUiRichTooltipOverlayWidget(WorkbenchShellContext shellContext) {
        this.shellContext = Objects.requireNonNull(shellContext, "shellContext");
        addStyleClass("workbench-tooltip-overlay");
        useStyle(sizeFull(), positionAbsolute(), insetZero());
        setInteractive(false);
        setFocusable(false);
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
        CirrusPalette palette = shellContext.palette();
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            lastPlacement = OreUiRichTooltipRenderer
                    .render(canvas, tooltip, renderAnchorX, renderAnchorY, viewW, effectiveViewH, palette);
        } finally {
            canvas.popTransform();
        }
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
