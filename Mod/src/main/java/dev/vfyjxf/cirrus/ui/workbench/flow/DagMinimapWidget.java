package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Thumbnail of the whole plan with a draggable viewport frame that drives the canvas camera.
 */
public class DagMinimapWidget extends Widget implements OreUiTooltipProvider {

    public static final int padding = 4;
    public static final int wideWidth = 140;
    public static final int wideHeight = 80;
    public static final int compactWidth = 104;
    public static final int compactHeight = 60;
    public static final int closeButtonSize = 11;

    public static final float topClearance = 10f;
    public static final float rightClearance = 10f;
    public static final float bottomClearance = SpatialDagCanvasWidget.capsuleHeight + 14f;

    private final DagViewport viewport;
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private WorkbenchThemeMode theme = WorkbenchThemeMode.defaultMode();
    private PlanDagLayoutResult layout = PlanDagLayoutResult.empty;
    private Runnable onCameraChanged = () -> {};
    private Runnable onClose = () -> {};
    private boolean dragging;
    private boolean compactMode;
    private int appliedWidth = wideWidth;
    private int appliedHeight = wideHeight;

    private DagMinimapWidget(DagViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        useStyle(sizeOf(wideWidth, wideHeight), positionAbsolute(), insetTop(topClearance), insetRight(rightClearance));
        setFocusable(false);
        onMouseClicked((input, context) -> {
            if (input.isLeftClick()) {
                FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
                if (hitsCloseButton(local.x, local.y)) {
                    CirrusUiSound.playClickSound();
                    onClose.run();
                    context.consume();
                    return EventDispatch.consumed;
                }
                dragging = true;
                follow(input);
                context.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!dragging) {
                return EventDispatch.pass;
            }
            follow(input);
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            if (!dragging) {
                return EventDispatch.pass;
            }
            dragging = false;
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public static DagMinimapWidget of(DagViewport viewport) {
        return new DagMinimapWidget(viewport);
    }

    public DagMinimapWidget palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public DagMinimapWidget theme(WorkbenchThemeMode theme) {
        this.theme = theme == null ? WorkbenchThemeMode.defaultMode() : theme;
        return this;
    }

    public DagMinimapWidget setLayoutContent(PlanDagLayoutResult layout) {
        this.layout = layout == null ? PlanDagLayoutResult.empty : layout;
        return this;
    }

    public DagMinimapWidget onCameraChanged(Runnable listener) {
        this.onCameraChanged = listener == null ? () -> {} : listener;
        return this;
    }

    public DagMinimapWidget onClose(Runnable listener) {
        this.onClose = listener == null ? () -> {} : listener;
        return this;
    }

    public DagMinimapWidget compact(boolean compact) {
        if (this.compactMode == compact) {
            return this;
        }
        this.compactMode = compact;
        applySize(compact ? compactWidth : wideWidth, compact ? compactHeight : wideHeight);
        return this;
    }

    public void adaptToCanvas(int canvasW, int canvasH) {
        if (canvasW <= 0 || canvasH <= 0) {
            return;
        }
        boolean compact = canvasW < 520 || canvasH < 260;
        this.compactMode = compact;
        int baseW = compact ? compactWidth : wideWidth;
        int baseH = compact ? compactHeight : wideHeight;
        int maxW = Math.max(64, Math.round(canvasW * 0.26f));
        int maxH = Math.max(40, Math.round(canvasH * 0.30f));
        int targetW = Math.min(baseW, maxW);
        int targetH = Math.min(baseH, maxH);
        applySize(targetW, targetH);
    }

    private void applySize(int w, int h) {
        if (w == appliedWidth && h == appliedHeight) {
            return;
        }
        appliedWidth = w;
        appliedHeight = h;
        useStyle(sizeOf(w, h));
    }

    private int closeButtonX(int w) {
        return Math.max(2, w - closeButtonSize - 2);
    }

    private int closeButtonY() {
        return 2;
    }

    private boolean hitsCloseButton(double lx, double ly) {
        int w = width() > 0 ? width() : appliedWidth;
        if (w <= 0) {
            return false;
        }
        int bx = closeButtonX(w);
        int by = closeButtonY();
        return lx >= bx && lx < bx + closeButtonSize && ly >= by && ly < by + closeButtonSize;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!visible()) {
            return null;
        }
        if (hitsCloseButton(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Workbench.Flow.minimap.get())
                    .badge("HIDE", OreUiRichTooltip.Tone.muted).build();
        }
        return null;
    }

    @Override
    public boolean dragging() {
        return dragging;
    }

    public PlanDagLayoutResult layoutContent() {
        return layout;
    }

    public DagViewport camera() {
        return viewport;
    }

    private void follow(InputContext input) {
        if (layout.isEmpty()) {
            return;
        }
        FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
        viewport.centerOnMinimapPoint(
            local.x,
            local.y,
            width(),
            height(),
            layout.contentMinX(),
            layout.contentMinY(),
            layout.contentMaxX(),
            layout.contentMaxY(),
            canvasWidth(),
            canvasHeight(),
            padding
        );
        onCameraChanged.run();
    }

    private int canvasWidth() {
        Widget parent = parent();
        return parent == null ? wideWidth : parent.width();
    }

    private int canvasHeight() {
        Widget parent = parent();
        return parent == null ? wideHeight : parent.height();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette themePalette = palette.get();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.shadow(0, 0, width, height, 3f, 2f, 5f, themePalette.dropShadow());
        canvas.color(0xFFFFFFFF).texture(CirrusTextures.Workbench.minimapFrame(theme), 0, 0, width, height);
        canvas.resetColor();
        canvas.strokeRect(0, 0, width, height, dragging ? themePalette.focusRing() : themePalette.hairlineBorder(), 1);
        if (layout.isEmpty()) {
            return;
        }
        double scale = DagViewport.minimapContentScale(
            width,
            height,
            layout.contentMinX(),
            layout.contentMinY(),
            layout.contentMaxX(),
            layout.contentMaxY(),
            padding
        );
        canvas.withClip(1, 1, Math.max(0, width - 2), Math.max(0, height - 2), () -> {
            for (DagNodeBox node : layout.nodes()) {
                int x = (int) Math.round((node.x() - layout.contentMinX()) * scale) + padding;
                int y = (int) Math.round((node.y() - layout.contentMinY()) * scale) + padding;
                int w = Math.max(1, (int) Math.round(node.width() * scale));
                int h = Math.max(1, (int) Math.round(node.height() * scale));
                canvas.fill(x, y, w, h, themePalette.stepTint(node.step()).accent());
            }
            float minX = viewport.visibleWorldX0();
            float minY = viewport.visibleWorldY0();
            float maxX = viewport.visibleWorldX1(canvasWidth());
            float maxY = viewport.visibleWorldY1(canvasHeight());
            int rawX0 = (int) Math.round((minX - layout.contentMinX()) * scale) + padding;
            int rawY0 = (int) Math.round((minY - layout.contentMinY()) * scale) + padding;
            int rawX1 = rawX0 + Math.max(4, (int) Math.round((maxX - minX) * scale));
            int rawY1 = rawY0 + Math.max(4, (int) Math.round((maxY - minY) * scale));
            int x0 = Math.max(1, Math.min(width - 5, rawX0));
            int y0 = Math.max(1, Math.min(height - 5, rawY0));
            int x1 = Math.max(x0 + 4, Math.min(width - 1, rawX1));
            int y1 = Math.max(y0 + 4, Math.min(height - 1, rawY1));
            canvas.strokeRect(x0, y0, x1 - x0, y1 - y0, themePalette.textPrimary(), 1);
        });
        renderCloseButton(canvas, width, themePalette, mouseX, mouseY);
    }

    private void renderCloseButton(SceneCanvas canvas, int width, CirrusPalette p, int mouseX, int mouseY) {
        int bx = closeButtonX(width);
        int by = closeButtonY();
        boolean hover = hitsCloseButton(mouseX, mouseY);
        int bg = hover ? p.cardSurfaceHover() : p.recessedSurface();
        int border = hover ? p.focusRing() : p.hairlineBorder();
        canvas.fill(bx, by, closeButtonSize, closeButtonSize, bg);
        canvas.strokeRect(bx, by, closeButtonSize, closeButtonSize, border, 1);
        int ink = hover ? p.textPrimary() : p.textMuted();
        int cx = bx + closeButtonSize / 2;
        int cy = by + closeButtonSize / 2;
        for (int i = -2; i <= 2; i++) {
            canvas.fill(cx + i, cy + i, 1, 1, ink);
            canvas.fill(cx + i, cy - i, 1, 1, ink);
        }
    }
}
