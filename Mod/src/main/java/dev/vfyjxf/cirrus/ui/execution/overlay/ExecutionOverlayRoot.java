package dev.vfyjxf.cirrus.ui.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionCockpitTheme;
import dev.vfyjxf.cirrus.ui.execution.layout.ExecutionOverlayLayout;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CoordinateSpace;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneLayer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class ExecutionOverlayRoot extends CompositeWidget<Widget> {
    //region geometry

    private static final long openAnimDurationMs = 190L;
    //endregion

    //region state

    private final TraceLayerWidget traceLayer;
    private final CockpitTooltipLayer tooltipLayer;
    private final MainPanel panel;
    private final Runnable openStateListener = this::applyOpenState;
    private Rect2i panelBounds;
    private boolean targetOpen;
    private float animFrom;
    private float animProgress;
    private long animStartMs;
    private boolean animating;
    //endregion

    //region setup

    public ExecutionOverlayRoot(
        ExecutionDriverHandle driverHandle,
        Rect2i panelBounds,
        int sceneWidth,
        int sceneHeight
    ) {
        this.panelBounds = panelBounds;
        this.targetOpen = ExecutionOverlayOpenState.open();
        this.animProgress = targetOpen ? 1f : 0f;
        this.animFrom = animProgress;
        this.animating = false;
        setTickable(true);
        applyRootBounds();

        traceLayer = addWidget(new TraceLayerWidget());
        traceLayer.setSceneLayer(SceneLayer.overlay);
        traceLayer.setCoordinateSpace(CoordinateSpace.scene);
        traceLayer.useStyle(
            UIStyle.of(positionAbsolute(), inset(0), sizeOf(Math.max(0, sceneWidth), Math.max(0, sceneHeight)))
        );
        traceLayer.setInteractive(false);
        traceLayer.setActive(false);

        panel = addWidget(new MainPanel(driverHandle));

        tooltipLayer = addWidget(new CockpitTooltipLayer());
        tooltipLayer.setSceneLayer(SceneLayer.overlay);
        tooltipLayer.setCoordinateSpace(CoordinateSpace.scene);
        tooltipLayer.useStyle(
            UIStyle.of(positionAbsolute(), inset(0), sizeOf(Math.max(0, sceneWidth), Math.max(0, sceneHeight)))
        );
        tooltipLayer.setInteractive(false);
        tooltipLayer.setActive(false);

        ExecutionOverlayOpenState.addListener(openStateListener);
        onUnmount(() -> {
            panel.autoSaveCheckpointOnClose();
            ExecutionOverlayOpenState.removeListener(openStateListener);
        });
        applyOpenStateImmediate();
    }
    //endregion

    //region access

    public MainPanel panel() {
        return panel;
    }

    public Rect2i panelBounds() {
        return panelBounds;
    }

    public float openAnimationProgress() {
        stepOpenAnimation();
        return animProgress;
    }

    public Rect2i currentBounds() {
        if (!targetOpen && !animating && animProgress <= 0.001f) {
            return new Rect2i(panelBounds.getX(), panelBounds.getY(), 0, 0);
        }
        int closedX = -panelBounds.getWidth() - 8;
        int animatedX = Math.round(OreUiMotion.lerp(closedX, panelBounds.getX(), animProgress));
        return new Rect2i(animatedX, panelBounds.getY(), panelBounds.getWidth(), panelBounds.getHeight());
    }

    public List<Rect2i> exclusionBounds() {
        if (!ExecutionOverlayOpenState.open() || panelBounds.getWidth() <= 0 || panelBounds.getHeight() <= 0) {
            return List.of();
        }
        var screen = lifecycle().mounted() ? context().currentScreen() : null;
        return List.of(ExecutionOverlayLayout.jeiExclusionBounds(screen, panelBounds));
    }
    //endregion

    //region layout

    private void applyRootBounds() {
        Rect2i bounds = currentBounds();
        useStyle(
            UIStyle.of(
                positionAbsolute(),
                flexColumn(),
                insetLeft(bounds.getX()),
                insetTop(bounds.getY()),
                sizeOf(bounds.getWidth(), bounds.getHeight())
            )
        );
    }

    private void syncContainerBounds() {
        if (!lifecycle().mounted()) {
            return;
        }
        var screen = context().currentScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        int screenW = containerScreen.width;
        int screenH = containerScreen.height;
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        Rect2i next = ExecutionOverlayLayout.panelBounds(containerScreen, screenW, screenH);
        if (next.getX() != panelBounds.getX()
                || next.getY() != panelBounds.getY()
                || next.getWidth() != panelBounds.getWidth()
                || next.getHeight() != panelBounds.getHeight()) {
            panelBounds = next;
            applyRootBounds();
            traceLayer.useStyle(UIStyle.of(positionAbsolute(), inset(0), sizeOf(screenW, screenH)));
            tooltipLayer.useStyle(UIStyle.of(positionAbsolute(), inset(0), sizeOf(screenW, screenH)));
        }
    }

    private void applyOpenStateImmediate() {
        boolean open = ExecutionOverlayOpenState.open();
        targetOpen = open;
        animProgress = open ? 1f : 0f;
        animFrom = animProgress;
        animating = false;
        setInteractive(open);
        applyRootBounds();
        panel.setVisible(open);
        panel.setActive(open);
        panel.setTickable(open);
        traceLayer.setVisible(open);
        traceLayer.setActive(false);
        tooltipLayer.setVisible(open);
        tooltipLayer.setActive(false);
    }

    private void applyOpenState() {
        boolean nextOpen = ExecutionOverlayOpenState.open();
        if (nextOpen != targetOpen) {
            if (!nextOpen) {
                panel.archiveAndClearOnClose();
            }
            stepOpenAnimation();
            animFrom = animProgress;
            targetOpen = nextOpen;
            animStartMs = OreUiMotion.nowMs();
            if (OreUiMotion.reducedMotion()) {
                animProgress = targetOpen ? 1f : 0f;
                animating = false;
            } else {
                animating = true;
            }
        }
        boolean showPanel = targetOpen || animating;
        setInteractive(targetOpen);
        applyRootBounds();
        panel.setVisible(showPanel);
        panel.setActive(targetOpen);
        panel.setTickable(targetOpen);
        traceLayer.setVisible(targetOpen);
        traceLayer.setActive(false);
        tooltipLayer.setVisible(targetOpen);
        tooltipLayer.setActive(false);
    }

    private void stepOpenAnimation() {
        if (!animating) {
            return;
        }
        long elapsed = Math.max(0L, OreUiMotion.nowMs() - animStartMs);
        float t = OreUiMotion.cubicOut(elapsed, openAnimDurationMs, OreUiMotion.reducedMotion());
        animProgress = OreUiMotion.lerp(animFrom, targetOpen ? 1f : 0f, t);
        if (t >= 1f) {
            animProgress = targetOpen ? 1f : 0f;
            animating = false;
            if (!targetOpen) {
                panel.setVisible(false);
            }
        }
        applyRootBounds();
    }
    //endregion

    //region lifecycle

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        stepOpenAnimation();
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
        stepOpenAnimation();
        if (!ExecutionOverlayOpenState.open()) {
            return;
        }
        syncContainerBounds();
        super.tick();
    }
    //endregion

    //region trace layer

    private final class TraceLayerWidget extends Widget {

        private final SlotFlowTraceRenderer flowTraceRenderer = new SlotFlowTraceRenderer();

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            if (!ExecutionOverlayOpenState.open() || !panel.traceEnabled()) {
                return;
            }
            ExecutionOverlayTraceSnapshot trace = panel.trace();
            if (trace == null || !trace.available()) {
                return;
            }
            var screen = context().currentScreen();
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
                return;
            }
            flowTraceRenderer.render(canvas, containerScreen, panel, trace, ExecutionCockpitTheme.palette(panel));
        }
    }
    //endregion

    private final class CockpitTooltipLayer extends Widget {

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (!ExecutionOverlayOpenState.open() || !lifecycle().mounted()) {
                return;
            }
            int viewW = width();
            int viewH = height();
            if (viewW <= 0 || viewH <= 0) {
                return;
            }
            Scene currentScene = scene();
            Widget hit = currentScene.hitTest(mouseX, mouseY);
            OreUiRichTooltip tooltip = null;
            for (Widget cursor = hit; cursor != null; cursor = cursor.parent()) {
                if (cursor == this) {
                    continue;
                }
                if (cursor instanceof OreUiTooltipProvider provider) {
                    FloatPos local = cursor.sceneToLocal(mouseX, mouseY);
                    OreUiRichTooltip resolved = provider.richTooltipAt(local.x, local.y);
                    if (resolved != null && !resolved.isEmpty()) {
                        tooltip = resolved;
                        break;
                    }
                }
            }
            if (tooltip == null) {
                return;
            }
            CirrusPalette palette = ExecutionCockpitTheme.palette(panel);
            OreUiRichTooltipRenderer.render(canvas, tooltip, mouseX, mouseY, viewW, viewH, palette);
        }
    }
}
