package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollDirection;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Tree diagram view — wraps RecipeStepsWidget with overlay controls
 * matching the omnicalc-pro TreeDiagram overlays.
 * <p>
 * Layout:
 * <pre>
 * ┌──────────────────────────────────────────────────┐
 * │ ┌───────┐                          ┌──────────┐ │
 * │ │ Layer │                          │ Minimap  │ │
 * │ │ Nav   │    RecipeStepsWidget     │          │ │
 * │ │       │    (zoom/pan canvas)     └──────────┘ │
 * │ │       │                                       │
 * │ └───────┘                                       │
 * ├─────────────────────────────────────────────────┤
 * │ Layer Info │ Path Context │ Trace               │
 * └─────────────────────────────────────────────────┘
 * </pre>
 */
public class TreeViewWidget extends CompositeWidget<Widget> {

    //region Metrics
    private static final int layerNavWidth = 36;  // Layer navigator width
    private static final int bottomPanelHeight = 24;
    private static final int breadcrumbScrollViewWidthPadding = 8;
    private static final int breadcrumbScrollbarBottomY = 1;
    private static final int breadcrumbScrollbarTrackHeight = 5;
    private static final int breadcrumbScrollbarThumbHeight = 7;
    private static final float crumbScale = 1.0f; // keep pixel text crisp
    private static final int crumbIconSize = 12;
    private static final int layerHeaderHeight = 22;
    private static final int layerHeaderIconSize = 18;
    //endregion

    //region State
    private @Nullable ProcessGraph graph;
    private final TreeCanvasWidget stepsWidget;
    private final WidgetGroup<Widget> layerNavContainer;
    private final WidgetGroup<Widget> layerButtonsContainer;
    private final ScrollState layerScrollState;
    private final WidgetGroup<Widget> bottomPanel;
    private int activeLayer = 0;
    private int maxLayers = 0;

    // Separate scroll states for path and trace rows
    private float pathScrollX = 0, pathScrollTargetX = 0;
    private float traceScrollX = 0, traceScrollTargetX = 0;
    private int pathContentW = 0, traceContentW = 0; // total breadcrumb content width
    private final List<BreadcrumbHit> pathHitAreas = new ArrayList<>();
    private final List<BreadcrumbHit> traceHitAreas = new ArrayList<>();
    private @Nullable String hoveredCrumbId = null;
    private @Nullable String selectedCrumbId = null;
    private @Nullable ScrollRegion activeScrollDrag;
    private float dragThumbOffsetX = 0;
    private boolean layerScrollbarInsetApplied = false;
    //endregion

    //region Types
    private enum ScrollRegion { PATH, TRACE }
    private record ScrollbarThumb(int x, int y, int w, int h, int barX, int barW, int maxScroll) {}

    private record BreadcrumbHit(int x, int y, int w, int h, String pathId) {}
    //endregion

    //region Init
    public TreeViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                background(Background.inset)
        );

        // ── Main area: Layer Nav + Canvas ──
        var mainArea = new WidgetGroup<>();
        mainArea.useStyle(flexRow(), flexGrow(1), minHeight(0), alignItemsStretch());
        mainArea.onRender((canvas, mx, my, pt, self, ctx) -> {
            syncLayerScrollbarInset();
            syncLayerTrackingFromViewport();
        });

        // Layer navigator (left side)
        layerNavContainer = new WidgetGroup<>();
        layerNavContainer.useStyle(
                flexColumn(),
                widthOf(layerNavWidth),
                minWidth(layerNavWidth),
                maxWidth(layerNavWidth),
                minHeight(0),
                alignSelfStretch(),
                flexShrink(0),
                paddingHorizontal(0),
                paddingVertical(1)
        );
        layerNavContainer.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.texture(Background.inset, 0, 0, self.width(), self.height());
            canvas.fill(self.width() - 1, 0, 1, self.height(), borderMain);
        });
        layerNavContainer.addWidget(new LayerHeaderWidget());

        layerButtonsContainer = new WidgetGroup<>();
        layerButtonsContainer.useStyle(
                flexColumn(),
                flexGrow(1),
                minHeight(0)
        );
        layerScrollState = ScrollState.create(ScrollDirection.vertical)
                .scrollSpeed(12)
                .smooth(true)
                .smoothSpeed(0.35f)
                .trackTexture(Background.inset)
                .thumbTexture(Icon.scrollBar)
                .scrollbarWidth(6);
        layerButtonsContainer.useEffect(UIEffects.scrollable(layerScrollState));
        layerNavContainer.addWidget(layerButtonsContainer);
        mainArea.addWidget(layerNavContainer);

        // Canvas (TreeCanvasWidget — dark theme, no MC textures)
        stepsWidget = new TreeCanvasWidget();
        stepsWidget.useStyle(flexGrow(1), minWidth(0));
        stepsWidget.setOnSubtreeChanged(() -> {
            activeLayer = 0;
            rebuildLayerNav(graph);
        });
        mainArea.addWidget(stepsWidget);

        addWidget(mainArea);

        // ── Bottom info panel ──
        bottomPanel = new WidgetGroup<>();
        bottomPanel.useStyle(heightOf(bottomPanelHeight), minHeight(bottomPanelHeight), maxHeight(bottomPanelHeight), flexShrink(0));

        bottomPanel.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            Font font = Minecraft.getInstance().font;
            var pathCrumbs = stepsWidget.getPathContextBreadcrumbs();
            var traceCrumbs = stepsWidget.getTraceBreadcrumbs();
            int halfW = w / 2;
            pathContentW = computeBreadcrumbContentWidth(font, pathCrumbs);
            traceContentW = computeBreadcrumbContentWidth(font, traceCrumbs);
            sanitizeBreadcrumbScroll(halfW);

            // Smooth scroll animation
            pathScrollX += (pathScrollTargetX - pathScrollX) * 0.35f;
            traceScrollX += (traceScrollTargetX - traceScrollX) * 0.35f;
            if (Math.abs(pathScrollX - pathScrollTargetX) < 0.5f) pathScrollX = pathScrollTargetX;
            if (Math.abs(traceScrollX - traceScrollTargetX) < 0.5f) traceScrollX = traceScrollTargetX;
            sanitizeBreadcrumbScroll(halfW);

            canvas.texture(Background.flat, 0, 0, w, h);
            int leftJoinW = Math.max(0, Math.min(w, layerNavWidth + 1));
            // Merge the bottom edge of the layer panel with path bar: avoid double bright seam.
            canvas.fill(0, 0, leftJoinW, 1, bgPanel);
            if (leftJoinW > 0 && leftJoinW <= w) {
                // Soften the corner transition by one extra pixel row.
                canvas.fill(leftJoinW - 1, 1, 1, 1, bgPanel);
            }

            canvas.fill(halfW, 3, 1, h - 6, borderMain);
            int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
            int scrollBarY = h - breadcrumbScrollbarBottomY;

            // ── Left half: PATH ──
            FloatPos absOrigin = self.localToScene(0, 0);
            int absX = (int) absOrigin.x(), absY = (int) absOrigin.y();
            drawBreadcrumbRow(canvas, font, mx, my, pathCrumbs,
                    pathHitAreas, pathScrollX, 0, halfW, h, absX, absY,
                    textPrimary, textPrimary, false, selectedCrumbId);

            // Path scrollbar
            if (pathContentW > scrollViewW) {
                drawMiniScrollbar(canvas, 2, scrollBarY, halfW - 4, pathScrollX, pathContentW, scrollViewW);
            }

            // ── Right half: TRACE ──
            drawBreadcrumbRow(canvas, font, mx, my, traceCrumbs,
                    traceHitAreas, traceScrollX, halfW + 1, halfW - 1, h, absX + halfW + 1, absY,
                    textPrimary, textPrimary, true, selectedCrumbId);

            // Trace scrollbar
            if (traceContentW > scrollViewW) {
                drawMiniScrollbar(canvas, halfW + 2, scrollBarY, halfW - 4, traceScrollX, traceContentW, scrollViewW);
            }

            updateBreadcrumbHoverFeedback(mx, my);
        });

        // Click handler — path clicks center on node, trace clicks scroll to node
        bottomPanel.onMouseClick((input, clickCount, ctx) -> {
            FloatPos local = input.mouseRelative(bottomPanel);
            double mx = local.x(), my = local.y();
            int halfW = bottomPanel.width() / 2;
            int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
            int scrollBarY = bottomPanel.height() - breadcrumbScrollbarBottomY;
            ScrollbarThumb pathThumb = buildScrollbarThumb(2, scrollBarY, halfW - 4,
                    pathScrollX, pathContentW, scrollViewW);
            if (pathThumb != null && isPointInside(pathThumb, mx, my)) {
                activeScrollDrag = ScrollRegion.PATH;
                dragThumbOffsetX = (float) (mx - pathThumb.x);
                return EventDispatch.consumed;
            }

            ScrollbarThumb traceThumb = buildScrollbarThumb(halfW + 2, scrollBarY,
                    halfW - 4, traceScrollX, traceContentW, scrollViewW);
            if (traceThumb != null && isPointInside(traceThumb, mx, my)) {
                activeScrollDrag = ScrollRegion.TRACE;
                dragThumbOffsetX = (float) (mx - traceThumb.x);
                return EventDispatch.consumed;
            }

            for (var hit : pathHitAreas) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    return selectAndJumpToBreadcrumb(hit.pathId());
                }
            }
            for (var hit : traceHitAreas) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    return selectAndJumpToBreadcrumb(hit.pathId());
                }
            }
            return EventDispatch.pass;
        });

        bottomPanel.onMouseDragged((input, deltaX, deltaY, ctx) -> {
            if (activeScrollDrag == null) return EventDispatch.pass;
            FloatPos local = input.mouseRelative(bottomPanel);
            int halfW = bottomPanel.width() / 2;
            int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
            int scrollBarY = bottomPanel.height() - breadcrumbScrollbarBottomY;
            if (activeScrollDrag == ScrollRegion.PATH) {
                pathScrollTargetX = dragThumb(pathScrollTargetX, local.x(), 2,
                        halfW - 4, scrollBarY, pathContentW, scrollViewW);
            } else {
                traceScrollTargetX = dragThumb(traceScrollTargetX, local.x(), halfW + 2,
                        halfW - 4, scrollBarY, traceContentW, scrollViewW);
            }
            ctx.cancel();
            return EventDispatch.consumed;
        });

        bottomPanel.onMouseReleased((input, ctx) -> {
            if (activeScrollDrag == null) return EventDispatch.pass;
            activeScrollDrag = null;
            dragThumbOffsetX = 0;
            return EventDispatch.consumed;
        });

        bottomPanel.onMouseLeave((mouseX, mouseY, ctx) -> {
            hoveredCrumbId = null;
            stepsWidget.setExternalHoveredNode(null);
            scene().setHoverTooltip(Tooltip.create());
        });

        // Independent scroll for left/right halves
        bottomPanel.onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
            FloatPos local = bottomPanel.sceneToLocal(mouseX, mouseY);
            int halfW = bottomPanel.width() / 2;
            int scrollViewW = halfW - breadcrumbScrollViewWidthPadding;
            if (local.x() < halfW) {
                int maxScroll = Math.max(0, pathContentW - scrollViewW);
                pathScrollTargetX = Math.max(0, Math.min(maxScroll, pathScrollTargetX - (float) sy * 15));
            } else {
                int maxScroll = Math.max(0, traceContentW - scrollViewW);
                traceScrollTargetX = Math.max(0, Math.min(maxScroll, traceScrollTargetX - (float) sy * 15));
            }
            ctx.cancel();
            return EventDispatch.consumed;
        });

        addWidget(bottomPanel);
    }
    //endregion

    //region Rendering
    private void drawBreadcrumbRow(SceneCanvas canvas, Font font,
                                   double mx, double my,
                                   List<TreeCanvasWidget.BreadcrumbEntry> crumbs,
                                   List<BreadcrumbHit> hitAreas,
                                   float scrollX, int areaX, int areaW, int areaH,
                                   int absX, int absY,
                                   int iconColor, int accentColor,
                                   boolean isTrace,
                                   @Nullable String selectedPathId) {
        hitAreas.clear();
        float s = crumbScale;
        int rowY = (int) ((areaH - 9 * s) / 2) - 1;

        // Clip to area
        canvas.pushClip(absX, absY, areaW, areaH);
        canvas.pushTransform();
        canvas.translate(areaX - scrollX, 0);
        canvas.scale(s);

        float invS = 1f / s;
        int px = 3;
        int textY = (int)(rowY * invS);

        // Draw leading icon
        canvas.texture(isTrace ? Icon.Calc.Path.branch : Icon.Calc.Path.tree, px, textY - 2, crumbIconSize, crumbIconSize);
        px += 16;

        if (crumbs.isEmpty()) {
            canvas.drawString("--", px, textY, textSecondary, false);
        } else {
            for (int i = 0; i < crumbs.size(); i++) {
                var crumb = crumbs.get(i);
                String displayName = crumb.name();
                int nameW = font.width(displayName);
                boolean isLast = i == crumbs.size() - 1;

                if (i > 0) {
                    canvas.texture(Icon.Small.arrowRight, px, textY + 1, 5, 6);
                    px += 9;
                }

                // Hit area in panel-local coords (account for scroll and area offset)
                int hitX = (int)(px * s + areaX - scrollX);
                int hitY = rowY - 1;
                int hitW = (int)(nameW * s) + 2;
                int hitH = (int)(9 * s) + 2;
                hitAreas.add(new BreadcrumbHit(hitX, hitY, hitW, hitH, crumb.pathId()));

                // Hover detect in panel-local
                boolean isHover = mx >= hitX && mx <= hitX + hitW && my >= hitY && my <= hitY + hitH;
                boolean isSelected = Objects.equals(selectedPathId, crumb.pathId());
                if (isSelected) {
                    canvas.fill(px - 2, textY - 2, nameW + 4, 12, 0x557FAFD6);
                }
                if (isHover) {
                    canvas.fill(px - 1, textY - 1, nameW + 2, 11, 0x88FFFFFF);
                }

                int color = isSelected ? 0xFF7FAFD6 : (isLast ? accentColor : (isHover ? textPrimary : textSecondary));
                canvas.drawString(displayName, px, textY, color, false);
                px += nameW + 1;
            }
        }

        // Store total content width for scrollbar
        int contentW = (int)(px * s) + 3;
        if (isTrace) traceContentW = contentW;
        else pathContentW = contentW;

        canvas.popTransform();
        canvas.popClip();
    }

    private void drawMiniScrollbar(SceneCanvas canvas,
                                   int barX, int barY, int barW,
                                   float scrollPos, int contentW, int viewW) {
        int trackY = barY - (breadcrumbScrollbarTrackHeight - 1);
        canvas.texture(Background.Slot.dark, barX, trackY, barW, breadcrumbScrollbarTrackHeight);
        if (contentW <= 0) return;
        int thumbW = Math.max(8, barW * viewW / contentW);
        int maxScroll = Math.max(1, contentW - viewW);
        int thumbX = barX + (int)((barW - thumbW) * (scrollPos / maxScroll));
        int thumbY = barY - (breadcrumbScrollbarThumbHeight - 1);
        canvas.texture(Icon.scrollBar, thumbX, thumbY, thumbW, breadcrumbScrollbarThumbHeight);
    }
    //endregion

    //region Interactions
    private int computeBreadcrumbContentWidth(Font font, List<TreeCanvasWidget.BreadcrumbEntry> crumbs) {
        int px = 3;
        px += 16; // leading icon
        if (crumbs.isEmpty()) {
            px += font.width("--");
        } else {
            for (int i = 0; i < crumbs.size(); i++) {
                if (i > 0) px += 9; // arrow
                px += font.width(crumbs.get(i).name()) + 1;
            }
        }
        return (int) (px * crumbScale) + 3;
    }

    private void sanitizeBreadcrumbScroll(int halfW) {
        int pathMaxScroll = Math.max(0, pathContentW - halfW + 8);
        int traceMaxScroll = Math.max(0, traceContentW - halfW + 10);
        pathScrollTargetX = Math.max(0, Math.min(pathMaxScroll, pathScrollTargetX));
        traceScrollTargetX = Math.max(0, Math.min(traceMaxScroll, traceScrollTargetX));
        pathScrollX = Math.max(0, Math.min(pathMaxScroll, pathScrollX));
        traceScrollX = Math.max(0, Math.min(traceMaxScroll, traceScrollX));
    }

    private EventDispatch selectAndJumpToBreadcrumb(String pathId) {
        selectedCrumbId = pathId;
        stepsWidget.setExternalSelectedNode(pathId);
        stepsWidget.focusNodeWithoutCropping(pathId);
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return EventDispatch.consumed;
    }

    private void updateBreadcrumbHoverFeedback(double mx, double my) {
        if (my < 0 || my > bottomPanel.height()) {
            return;
        }
        BreadcrumbHit hovered = null;
        String hoverSource = null;
        for (var hit : pathHitAreas) {
            if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                hovered = hit;
                hoverSource = "Tree Path";
                break;
            }
        }
        if (hovered == null) {
            for (var hit : traceHitAreas) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    hovered = hit;
                    hoverSource = "Trace Path";
                    break;
                }
            }
        }
        hoveredCrumbId = hovered != null ? hovered.pathId() : null;
        stepsWidget.setExternalHoveredNode(hoveredCrumbId);
        if (hoveredCrumbId == null) {
            return;
        }
        Tooltip tip = stepsWidget.buildNodeTooltipForPath(hoveredCrumbId, hoverSource);
        scene().setHoverTooltip(tip != null ? tip : singleLineTooltip("Path: " + hoveredCrumbId));
    }

    public void setGraph(ProcessGraph graph) {
        this.graph = graph;
        this.selectedCrumbId = null;
        stepsWidget.setGraph(graph);
        stepsWidget.setExternalSelectedNode(null);
        stepsWidget.setCraftCount(1);
        rebuildLayerNav(graph);
    }

    boolean debugIsMaterialPanelVisible() {
        return stepsWidget.isMaterialPanelVisible();
    }

    int debugMaterialEntryCount() {
        return stepsWidget.debugMaterialEntryCount();
    }

    boolean debugIsRootFocused() {
        return "root".equals(stepsWidget.getCurrentRootPathId());
    }

    void toggleMaterialPanel() {
        stepsWidget.toggleMaterialPanel();
    }

    void resetToFullTree() {
        stepsWidget.resetToFullTree();
    }

    private void rebuildLayerNav(ProcessGraph graph) {
        layerButtonsContainer.clear();

        maxLayers = stepsWidget.getMaxDepth();

        for (int i = 0; i <= maxLayers; i++) {
            if (stepsWidget.getLayerNodeCount(i) > 0) {
                layerButtonsContainer.addWidget(new LayerButton(i));
            }
        }
        syncLayerTrackingFromViewport();
    }

    private void syncLayerScrollbarInset() {
        int viewportH = layerButtonsContainer.height();
        if (viewportH <= 0) return;

        int buttonCount = 0;
        for (var child : layerButtonsContainer.children()) {
            if (child instanceof LayerButton) buttonCount++;
        }
        // LayerButton is fixed 20px height with 1px bottom margin.
        int contentH = buttonCount * 21;
        boolean needsInset = contentH > viewportH;

        if (needsInset == layerScrollbarInsetApplied) return;
        layerScrollbarInsetApplied = needsInset;
        layerButtonsContainer.useStyle(paddingRight(needsInset ? layerScrollState.scrollbarWidth() : 0));
    }

    private void setActiveLayer(int layer) {
        int targetLayer = nearestExistingLayer(layer);
        this.activeLayer = targetLayer;
        updateLayerButtons(targetLayer);
        // Sync highlight to canvas and auto-center
        stepsWidget.setHighlightedLayer(targetLayer);
        stepsWidget.centerOnLayer(targetLayer, Math.max(0.25f, stepsWidget.getZoom()));
    }

    private int nearestExistingLayer(int layer) {
        int nearest = layer;
        int bestDist = Integer.MAX_VALUE;
        for (var child : layerButtonsContainer.children()) {
            if (child instanceof LayerButton lb) {
                int d = Math.abs(lb.layer - layer);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = lb.layer;
                }
            }
        }
        return nearest;
    }

    private void syncLayerTrackingFromViewport() {
        if (graph == null) return;
        int viewportLayer = nearestExistingLayer(stepsWidget.getViewportCenterLayer());
        if (viewportLayer == activeLayer) return;
        activeLayer = viewportLayer;
        updateLayerButtons(viewportLayer);
        stepsWidget.setHighlightedLayer(viewportLayer);
    }

    private @Nullable ScrollbarThumb buildScrollbarThumb(int barX, int barY, int barW,
                                                         float scrollPos, int contentW, int viewW) {
        if (contentW <= viewW || contentW <= 0) return null;
        int thumbW = Math.max(8, barW * viewW / contentW);
        int maxScroll = Math.max(1, contentW - viewW);
        int thumbX = barX + (int) ((barW - thumbW) * (scrollPos / maxScroll));
        int thumbY = barY - (breadcrumbScrollbarThumbHeight - 1);
        return new ScrollbarThumb(thumbX, thumbY, thumbW, breadcrumbScrollbarThumbHeight, barX, barW, maxScroll);
    }

    private static boolean isPointInside(ScrollbarThumb thumb, double x, double y) {
        return x >= thumb.x && x <= thumb.x + thumb.w && y >= thumb.y && y <= thumb.y + thumb.h;
    }

    private float dragThumb(float currentValue, double mouseX, int barX, int barW, int barY, int contentW, int viewW) {
        ScrollbarThumb thumb = buildScrollbarThumb(barX, barY, barW, currentValue, contentW, viewW);
        if (thumb == null) return 0;
        float track = Math.max(1, barW - thumb.w);
        float thumbLeft = (float) Math.max(barX, Math.min(mouseX - dragThumbOffsetX, barX + track));
        float progress = track <= 0 ? 0 : (thumbLeft - barX) / track;
        return Math.max(0, Math.min(thumb.maxScroll, progress * thumb.maxScroll));
    }
    //endregion

    //region Public API
    static int computeThumbWidth(int barW, int contentW, int viewW) {
        return Math.max(8, barW * viewW / Math.max(contentW, 1));
    }

    static String layerLabel(int layer) {
        return "L" + layer;
    }

    static int debugLayerNavWidth() {
        return layerNavWidth;
    }

    static int debugBottomStripHeight() {
        return bottomPanelHeight;
    }

    static boolean debugLayerHeaderUsesIconOnly() {
        return true;
    }

    private static Tooltip singleLineTooltip(String text) {
        var tooltip = Tooltip.create();
        tooltip.add(Component.literal(text));
        return tooltip;
    }

    /** Update layer button highlight state without changing the canvas. */
    public void updateLayerButtons(int layer) {
        this.activeLayer = layer;
        for (var child : layerButtonsContainer.children()) {
            if (child instanceof LayerButton lb) {
                lb.updateActive(layer);
            }
        }
    }
    //endregion

    // ═══════════════════ LayerButton ═══════════════════

    private class LayerButton extends CompositeWidget<Widget> {

        private final int layer;
        private boolean active = false;

        LayerButton(int layer) {
            this.layer = layer;
            useStyle(alignSelfStretch(), heightOf(20), minHeight(20), maxHeight(20), flexShrink(0), paddingHorizontal(2), marginBottom(1));
            updateActive(activeLayer);
            int nodeCount = stepsWidget.getLayerNodeCount(layer);
            setTooltip(singleLineTooltip("层级 " + layer + " · " + nodeCount + " 节点"));

            onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                int w = width(), h = height();
                String label = layerLabel(layer);

                if (active) {
                    canvas.texture(Background.Outlined.inset, 0, 0, w, h);
                    canvas.fill(1, 1, w - 2, h - 2, 0x33FFFFFF);
                    canvas.fill(1, 1, 3, h - 2, green);
                } else if (hovered()) {
                    canvas.texture(Background.Outlined.flat, 0, 0, w, h);
                } else {
                    canvas.texture(Background.flat, 0, 0, w, h);
                }

                int labelW = Minecraft.getInstance().font.width(label);
                canvas.drawString(label, (w - labelW) / 2, h / 2 - 3,
                        active ? textPrimary : textSecondary, false);
            });

            onMouseClick((input, clickCount, context) -> {
                setActiveLayer(layer);
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return EventDispatch.consumed;
            });
        }

        void updateActive(int currentLayer) {
            this.active = (layer == currentLayer);
        }
    }

    private static final class LayerHeaderWidget extends Widget {

        private LayerHeaderWidget() {
            useStyle(heightOf(layerHeaderHeight), minHeight(layerHeaderHeight), maxHeight(layerHeaderHeight), flexShrink(0), marginBottom(1));
            setTooltip(singleLineTooltip("层级栏：点击层号以高亮并定位该层节点"));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int w = width();
            canvas.texture(Background.flat, 0, 0, w, height());
            canvas.texture(Icon.Calc.layerNav, (w - layerHeaderIconSize) / 2, (height() - layerHeaderIconSize) / 2, layerHeaderIconSize, layerHeaderIconSize);
        }
    }
}
