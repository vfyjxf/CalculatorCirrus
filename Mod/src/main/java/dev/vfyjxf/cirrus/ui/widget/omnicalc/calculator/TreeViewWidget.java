package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    private static final int LAYER_NAV_W = 36;  // Layer navigator width
    private static final float CRUMB_SCALE = 0.75f; // breadcrumb text scale

    private @Nullable ProcessGraph graph;
    private final TreeCanvasWidget stepsWidget;
    private final WidgetGroup<Widget> layerNavContainer;
    private final WidgetGroup<Widget> bottomPanel;
    private int activeLayer = 0;
    private int maxLayers = 0;

    // Separate scroll states for path and trace rows
    private float pathScrollX = 0, pathScrollTargetX = 0;
    private float traceScrollX = 0, traceScrollTargetX = 0;
    private int pathContentW = 0, traceContentW = 0; // total breadcrumb content width
    private final List<BreadcrumbHit> pathHitAreas = new ArrayList<>();
    private final List<BreadcrumbHit> traceHitAreas = new ArrayList<>();

    private record BreadcrumbHit(int x, int y, int w, int h, String pathId) {}

    public TreeViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                background(OmniCalcColors.TEX_TREE_CANVAS)
        );

        // ── Main area: Layer Nav + Canvas ──
        var mainArea = new WidgetGroup<>();
        mainArea.useStyle(flexRow(), flexGrow(1), minHeight(0));

        // Layer navigator (left side)
        layerNavContainer = new WidgetGroup<>();
        layerNavContainer.useStyle(
                flexColumn(),
                widthOf(LAYER_NAV_W),
                background(new ColorTexture(TREE_CANVAS_BG))
        );
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
        bottomPanel.useStyle(heightOf(18));

        bottomPanel.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width(), h = self.height();
            Font font = Minecraft.getInstance().font;

            // Smooth scroll animation
            pathScrollX += (pathScrollTargetX - pathScrollX) * 0.35f;
            traceScrollX += (traceScrollTargetX - traceScrollX) * 0.35f;
            if (Math.abs(pathScrollX - pathScrollTargetX) < 0.5f) pathScrollX = pathScrollTargetX;
            if (Math.abs(traceScrollX - traceScrollTargetX) < 0.5f) traceScrollX = traceScrollTargetX;

            // Background
            canvas.fill(0, 0, w, h, 0xF0080C14);
            canvas.fill(0, 0, w, 1, 0xFF1E293B);

            int halfW = w / 2;
            // Divider between path and trace
            canvas.fill(halfW, 2, 1, h - 4, 0xFF1E293B);

            // ── Left half: PATH ──
            FloatPos absOrigin = self.localToScene(0, 0);
            int absX = (int) absOrigin.x(), absY = (int) absOrigin.y();
            drawBreadcrumbRow(canvas, font, mx, my, stepsWidget.getPathContextBreadcrumbs(),
                    pathHitAreas, pathScrollX, 0, halfW, h, absX, absY,
                    0xFF3B82F6, ACCENT, false);

            // Path scrollbar
            if (pathContentW > halfW - 8) {
                drawMiniScrollbar(canvas, 2, h - 2, halfW - 4, pathScrollX, pathContentW, halfW - 8);
            }

            // ── Right half: TRACE ──
            drawBreadcrumbRow(canvas, font, mx - halfW - 1, my, stepsWidget.getTraceBreadcrumbs(),
                    traceHitAreas, traceScrollX, halfW + 1, halfW - 1, h, absX + halfW + 1, absY,
                    0xFF6B7280, GREEN, true);

            // Trace scrollbar
            if (traceContentW > halfW - 10) {
                drawMiniScrollbar(canvas, halfW + 3, h - 2, halfW - 5, traceScrollX, traceContentW, halfW - 10);
            }
        });

        // Click handler — path clicks center on node, trace clicks scroll to node
        bottomPanel.onMouseClick((input, clickCount, ctx) -> {
            FloatPos local = input.mouseRelative(bottomPanel);
            double mx = local.x(), my = local.y();
            for (var hit : pathHitAreas) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    // Only center viewport on the clicked node — don't switch subtree
                    stepsWidget.centerOnNode(hit.pathId);
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    return EventDispatch.consumed;
                }
            }
            for (var hit : traceHitAreas) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    stepsWidget.scrollToNode(hit.pathId);
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });

        // Independent scroll for left/right halves
        bottomPanel.onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
            FloatPos local = bottomPanel.sceneToLocal(mouseX, mouseY);
            int halfW = bottomPanel.width() / 2;
            if (local.x() < halfW) {
                int maxScroll = Math.max(0, pathContentW - halfW + 8);
                pathScrollTargetX = Math.max(0, Math.min(maxScroll, pathScrollTargetX - (float) sy * 15));
            } else {
                int maxScroll = Math.max(0, traceContentW - halfW + 10);
                traceScrollTargetX = Math.max(0, Math.min(maxScroll, traceScrollTargetX - (float) sy * 15));
            }
            ctx.cancel();
            return EventDispatch.consumed;
        });

        addWidget(bottomPanel);
    }

    private void drawBreadcrumbRow(SceneCanvas canvas, Font font,
                                   double mx, double my,
                                   List<TreeCanvasWidget.BreadcrumbEntry> crumbs,
                                   List<BreadcrumbHit> hitAreas,
                                   float scrollX, int areaX, int areaW, int areaH,
                                   int absX, int absY,
                                   int iconColor, int accentColor,
                                   boolean isTrace) {
        hitAreas.clear();
        float s = CRUMB_SCALE;
        int rowY = (int) ((areaH - 9 * s) / 2);

        // Clip to area
        canvas.pushClip(absX, absY, areaW, areaH);
        canvas.pushTransform();
        canvas.translate(areaX - scrollX, 0);
        canvas.scale(s);

        float invS = 1f / s;
        int px = 4;
        int textY = (int)(rowY * invS);

        // Draw leading icon
        if (isTrace) {
            // Small dot
            canvas.circle(px + 3, textY + 4, 2.5f, iconColor);
            px += 9;
        } else {
            // Right-pointing triangle (play icon)
            drawTriangleRight(canvas, px, textY + 1, 6, 7, iconColor);
            px += 10;
        }

        if (crumbs.isEmpty()) {
            canvas.drawString("--", px, textY, 0xFF374151, false);
        } else {
            for (int i = 0; i < crumbs.size(); i++) {
                var crumb = crumbs.get(i);
                String displayName = truncName(crumb.name(), 18);
                int nameW = font.width(displayName);
                boolean isLast = i == crumbs.size() - 1;

                if (i > 0) {
                    // Chevron separator
                    drawChevronRight(canvas, px + 1, textY + 1, 4, 7, 0xFF374151);
                    px += 8;
                }

                // Hit area in panel-local coords (account for scroll and area offset)
                int hitX = (int)(px * s + areaX - scrollX);
                int hitY = rowY - 1;
                int hitW = (int)(nameW * s) + 2;
                int hitH = (int)(9 * s) + 2;
                hitAreas.add(new BreadcrumbHit(hitX, hitY, hitW, hitH, crumb.pathId()));

                // Hover detect in panel-local
                boolean isHover = mx >= hitX && mx <= hitX + hitW && my >= hitY && my <= hitY + hitH;
                if (isHover) {
                    canvas.fill(px - 1, textY - 1, nameW + 2, 11, 0xFF1A2744);
                }

                int color = isLast ? accentColor : (isHover ? 0xFFBFDBFE : 0xFF94A3B8);
                canvas.drawString(displayName, px, textY, color, isLast);
                px += nameW + 1;
            }
        }

        // Store total content width for scrollbar
        int contentW = (int)(px * s) + 4;
        if (isTrace) traceContentW = contentW;
        else pathContentW = contentW;

        canvas.popTransform();
        canvas.popClip();
    }

    // ── SVG-like icon drawing helpers ──

    /** Right-pointing filled triangle (play button). */
    private static void drawTriangleRight(SceneCanvas canvas, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int rowW = Math.max(1, (int) Math.round((double) w * Math.min(row + 1, h - row) / ((h + 1.0) / 2.0)));
            canvas.fill(x, y + row, rowW, 1, color);
        }
    }

    /** Right-pointing chevron (> shape). */
    private static void drawChevronRight(SceneCanvas canvas, int x, int y, int w, int h, int color) {
        int midY = y + h / 2;
        canvas.line(x, y, x + w, midY, 1f, color);
        canvas.line(x, y + h, x + w, midY, 1f, color);
    }

    /** Small 2x2 grid icon (material/grid). */
    private static void drawGridIcon(SceneCanvas canvas, int x, int y, int size, int color) {
        int half = size / 2;
        int gap = 1;
        int cellW = half - gap;
        canvas.fill(x, y, cellW, cellW, color);
        canvas.fill(x + half + gap, y, cellW, cellW, color);
        canvas.fill(x, y + half + gap, cellW, cellW, color);
        canvas.fill(x + half + gap, y + half + gap, cellW, cellW, color);
    }

    /** Small house icon (home/root). */
    private static void drawHomeIcon(SceneCanvas canvas, int x, int y, int size, int color) {
        int mid = x + size / 2;
        int roofTop = y;
        int roofBot = y + size / 2;
        int houseBot = y + size;
        canvas.line(x, roofBot, mid, roofTop, 1.5f, color);
        canvas.line(mid, roofTop, x + size, roofBot, 1.5f, color);
        canvas.fill(x + 1, roofBot, size - 2, houseBot - roofBot, color);
        int doorW = Math.max(2, size / 3);
        int doorH = Math.max(2, (houseBot - roofBot) * 2 / 3);
        canvas.fill(mid - doorW / 2, houseBot - doorH, doorW, doorH, 0xFF0D1117);
    }

    private void drawMiniScrollbar(SceneCanvas canvas,
                                   int barX, int barY, int barW,
                                   float scrollPos, int contentW, int viewW) {
        // Track
        canvas.fill(barX, barY, barW, 1, 0xFF1E293B);
        // Thumb
        if (contentW <= 0) return;
        int thumbW = Math.max(8, barW * viewW / contentW);
        int maxScroll = Math.max(1, contentW - viewW);
        int thumbX = barX + (int)((barW - thumbW) * (scrollPos / maxScroll));
        canvas.fill(thumbX, barY, thumbW, 1, 0xFF475569);
    }

    private static String truncName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 1) + "..";
    }

    public void setGraph(ProcessGraph graph) {
        this.graph = graph;
        stepsWidget.setGraph(graph);
        stepsWidget.setCraftCount(1);
        rebuildLayerNav(graph);
    }

    private void rebuildLayerNav(ProcessGraph graph) {
        layerNavContainer.clear();

        // Spacer at top
        var spacer = new WidgetGroup<>();
        spacer.useStyle(heightOf(4));
        layerNavContainer.addWidget(spacer);

        maxLayers = stepsWidget.getMaxDepth();

        for (int i = 0; i <= maxLayers; i++) {
            if (stepsWidget.getLayerNodeCount(i) > 0) {
                layerNavContainer.addWidget(new LayerButton(i));
            }
        }

        // ── Action buttons ──
        var matBtn = new NavActionButton(NavIconType.GRID, ACCENT, () -> {
            stepsWidget.toggleMaterialPanel();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        });
        layerNavContainer.addWidget(matBtn);

        var rootBtn = new NavActionButton(NavIconType.HOME, GREEN, () -> {
            stepsWidget.resetToFullTree();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        });
        layerNavContainer.addWidget(rootBtn);
    }

    private enum NavIconType { GRID, HOME }

    private void setActiveLayer(int layer) {
        this.activeLayer = layer;
        updateLayerButtons(layer);
        // Sync highlight to canvas and auto-center
        stepsWidget.setHighlightedLayer(layer);
        stepsWidget.centerOnLayer(layer, Math.max(0.25f, stepsWidget.getZoom()));
    }

    /** Update layer button highlight state without changing the canvas. */
    public void updateLayerButtons(int layer) {
        this.activeLayer = layer;
        for (var child : layerNavContainer.children()) {
            if (child instanceof LayerButton lb) {
                lb.updateActive(layer);
            }
        }
    }

    // ═══════════════════ LayerButton ═══════════════════

    private class LayerButton extends CompositeWidget<Widget> {

        private final int layer;
        private boolean active = false;

        LayerButton(int layer) {
            this.layer = layer;
            useStyle(heightOf(14), paddingHorizontal(2));
            updateActive(activeLayer);

            onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                int w = width(), h = height();
                int nodeCount = stepsWidget.getLayerNodeCount(layer);
                // Bar width proportional to node count (min 4, max w-6)
                int barW = Math.max(4, Math.min(w - 18, 4 + nodeCount * 2));
                String label = String.valueOf(layer);
                Font font = Minecraft.getInstance().font;
                int labelW = font.width(label);

                if (active) {
                    canvas.fill(2, 1, w - 4, h - 2, 0xFF0F1B2E);
                    canvas.fill(2, 1, 2, h - 2, GREEN);
                    canvas.fill(6, h / 2, barW, 2, 0xFF22C55E);
                    canvas.drawString(label, w - labelW - 4, h / 2 - 3, 0xFF22C55E, false);
                } else if (hovered()) {
                    canvas.fill(2, 1, w - 4, h - 2, 0xFF151D2E);
                    canvas.fill(6, h / 2, barW, 2, 0xFF334155);
                    canvas.drawString(label, w - labelW - 4, h / 2 - 3, 0xFF94A3B8, false);
                } else {
                    canvas.fill(6, h / 2, barW, 1, 0xFF1E293B);
                    canvas.drawString(label, w - labelW - 4, h / 2 - 3, 0xFF475569, false);
                }
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

    // ═══════════════════ NavActionButton ═══════════════════

    private static class NavActionButton extends CompositeWidget<Widget> {

        NavActionButton(NavIconType iconType, int color, Runnable action) {
            useStyle(heightOf(14), paddingHorizontal(2));

            onRender((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                int w = self.width(), h = self.height();
                if (self.hovered()) {
                    canvas.fill(2, 1, w - 4, h - 2, 0xFF1A2744);
                }
                int iconSize = 8;
                int ix = (w - iconSize) / 2;
                int iy = (h - iconSize) / 2;
                if (iconType == NavIconType.GRID) {
                    drawGridIcon(canvas, ix, iy, iconSize, color);
                } else {
                    drawHomeIcon(canvas, ix, iy, iconSize, color);
                }
            });

            onMouseClick((input, clickCount, context) -> {
                action.run();
                return EventDispatch.consumed;
            });
        }
    }
}
