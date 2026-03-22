package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import com.mojang.blaze3d.platform.Lighting;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGroup;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;

/**
 * Tree diagram widget — renders a node-based tree matching the web app's TreeDiagram.
 * Each node is a compact card (NODE_W × NODE_H) connected by Manhattan-routed lines.
 * Uses only canvas primitives (fill, roundedRect, shadow) — no MC textures.
 */
public class TreeCanvasWidget extends CompositeWidget<Widget> {

    // ═══════════════════ Node Dimensions (world-space pixels) ═══════════════════
    private static final int NODE_W      = 220;
    private static final int NODE_H      = 72;
    private static final int GAP_X       = 24;
    private static final int ROW_H       = 120;
    private static final int HDR_H       = 28;

    // ═══════════════════ Colors ═══════════════════
    private static final int BG             = TREE_CANVAS_BG;   // 0xFF0A0E17
    private static final int NODE_BG        = 0xFF111827;
    private static final int NODE_BD        = 0xFF374151;
    private static final int NODE_BD_HOVER  = 0xFF4B5563;
    private static final int NODE_HDR_BG    = 0xFF0A0F1A;
    private static final int NODE_BODY_BG   = 0xFF111827;
    private static final int CATALYST_BD    = 0xFF8B5CF6;
    private static final int CATALYST_BG_T  = 0x194C1D95;
    private static final int TRACKED_RING   = ACCENT;           // blue
    private static final int TRACKED_GLOW   = 0x403B82F6;
    private static final int LINK_DEFAULT   = 0xFF1E3A6A;
    private static final int LINK_CATALYST  = 0xFF5B3CB6;
    private static final int LINK_TRACKED   = 0xFF3B82F6;
    private static final int LAYER_LINE     = 0x401E3A8A;
    private static final int DOT_CLR        = 0xFF6B7280;
    private static final int DOT_CLR_ROOT   = ACCENT;
    private static final int LEAF_BG        = 0xFF111827;
    private static final int LEAF_BD        = 0xFF374151;
    private static final int COLLAPSE_BG    = 0xFF1F2937;
    private static final int COLLAPSE_BD    = 0xFF4B5563;
    private static final int MM_BG          = 0xF0111827;
    private static final int MM_BD          = 0xFF374151;
    private static final int MM_VP          = 0x884488DD;
    private static final int PANEL_BG_CLR   = 0xF0111827;

    // ═══════════════════ Layout ═══════════════════
    private static final int GRID_SPACING   = 40;
    private static final int MM_W           = 160;
    private static final int MM_H           = 100;
    private static final int MM_PAD         = 8;
    private static final int PANEL_W        = 200;
    private static final int PANEL_PAD      = 5;
    private static final int BAR_H          = 3;
    private static final int BOX_H          = 30;
    private static final int ENTRY_H        = BOX_H + BAR_H;
    private static final int ENTRY_GAP      = 2;
    private static final int SEC_HDR_H      = 16;
    private static final int TOTALS_H       = 52;
    private static final int PANEL_ICON_SZ  = 16;
    private static final int ICON_SLOT_SZ   = 22;
    private static final int SCROLLBAR_W    = 8;
    private static final int MAX_TREE_WIDTH = 3500;  // Width cap for auto-collapse
    private static final float MIN_ZOOM     = 0.15f;
    private static final float MAX_ZOOM     = 3.0f;
    private static final float ITEM_LOD_ZOOM   = 0.20f;
    private static final float TEXT_LOD_ZOOM   = 0.15f;
    private static final float DETAIL_LOD_ZOOM = 0.10f;
    private static final float ZOOM_SNAP         = 0.001f;
    private static final float ZOOM_SMOOTH_SPEED = 0.6f;
    private static final float MAT_SCROLL_SPEED  = 25f;
    private static final float MAT_SCROLL_SMOOTH = 0.35f;
    private static final long CYCLE_INTERVAL_TICKS = 40;

    // ═══════════════════ Inner Types ═══════════════════

    /** A laid-out tree node with position. */
    private static class LayoutNode {
        final ProcessNode processNode;
        final String pathId;
        final int depth;
        final List<LayoutNode> children = new ArrayList<>();
        int treeWidth;   // width of this subtree in pixels
        int x, y;        // top-left corner in world space
        boolean collapsed;
        int originalChildCount;

        LayoutNode(ProcessNode pn, String pathId, int depth) {
            this.processNode = pn;
            this.pathId = pathId;
            this.depth = depth;
        }
        boolean isLeaf() { return processNode == null || children.isEmpty() && originalChildCount == 0; }
    }

    private record LayoutLink(LayoutNode source, LayoutNode target, String id) {}

    private enum MatCategory {
        EXTERNAL_INPUT("外部输入", 0xFF55FFAA),
        BYPRODUCT("副产物 / 多余", 0xFFFFCC44),
        CATALYST("催化剂 / 工具", 0xFFBB77FF),
        INTERMEDIATE("中间产物", 0xFF88AACC);
        final String label;
        final int color;
        MatCategory(String l, int c) { this.label = l; this.color = c; }
    }

    private record MaterialEntry(
            String name, MaterialSlot refSlot,
            long needed, long produced, long missing, long inventory,
            MatCategory category, List<Integer> sources, List<Integer> consumers,
            double chance) {}

    private record DeferredItem(ItemStack stack, int x, int y, float scale) {}
    private record DeferredFluid(FluidStack fluid, int x, int y, float scale, float pt) {}

    /** Breadcrumb entry for path context / trace display. */
    public record BreadcrumbEntry(String name, String pathId) {}

    // ═══════════════════ State ═══════════════════

    private ProcessGraph graph;
    private @Nullable ProcessNode targetNode;
    private long craftCount = 1;

    // Tree layout
    private @Nullable LayoutNode rootLayout;
    private List<LayoutNode> flatNodes = List.of();
    private List<LayoutLink> flatLinks = List.of();
    private Map<Integer, List<LayoutNode>> layerNodes = Map.of();
    private int maxDepth = 0;
    private int layoutWidth = 2000, layoutHeight = 1000;
    // Minimap bounding box (world coords, computed from flatNodes)
    private int mmWorldMinX, mmWorldMinY, mmWorldW = 1, mmWorldH = 1;
    private Set<String> collapsedPaths = new HashSet<>();
    private Set<String> userExpandedPaths = new HashSet<>();
    private @Nullable String trackedNodeId = null;
    private int highlightedLayer = -1;  // -1 = none
    private @Nullable LayoutNode fullRootLayout;
    private String currentRootPathId = "root";
    private @Nullable Runnable onSubtreeChanged;

    // Viewport
    private double panX = 0, panY = 0;
    private float zoom = 1.0f, targetZoom = 1.0f;
    private boolean dragging = false;
    private double dragStartX, dragStartY, dragPanStartX, dragPanStartY;
    private double zoomFocusWX, zoomFocusWY, zoomFocusSX, zoomFocusSY;

    // Hover
    private @Nullable String hoveredNodeId = null;
    private boolean widgetHovered = false;
    private boolean minimapDragging = false;
    private int lastHoverMouseX = Integer.MIN_VALUE, lastHoverMouseY = Integer.MIN_VALUE;

    // Material panel
    private boolean showMaterialPanel = false;
    private float matScrollY = 0, matScrollTargetY = 0;
    private boolean scrollbarDragging = false;
    private float scrollbarDragStartY = 0, scrollbarDragStartScroll = 0;
    private Map<MatCategory, List<MaterialEntry>> materialByCategory = new LinkedHashMap<>();
    private int materialPanelContentH = 0;
    private long totalTimeTicks = 0;
    private long totalEU = 0;
    private @Nullable String hoveredMatName = null;
    // Search / filter
    private String matFilterText = "";
    private boolean matSearchActive = false;
    private int matFilteredContentH = 0;
    // Sticky header tracking (set per frame in renderMaterialContent)
    private @Nullable MatCategory stickyHeaderCat = null;
    private int stickyHeaderCount = 0;
    private int stickyHeaderOffsetY = 0; // how much to nudge up when next section pushes

    // Rendering
    private long machineCycleTicks = 0;
    private final Map<Long, String> truncationCache = new HashMap<>();
    private long truncationFrameId = 0;
    private final List<DeferredItem> deferredItems = new ArrayList<>();
    private final List<DeferredFluid> deferredFluids = new ArrayList<>();

    // ═══════════════════ Constructor ═══════════════════
    public TreeCanvasWidget() {
        setFocusable(true);
        onMouseClicked((input, ctx) -> {
            FloatPos local = input.mouseRelative(TreeCanvasWidget.this);
            double mx = local.x(), my = local.y();
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            // Search icon click in material panel header
            if (input.isLeftClick() && showMaterialPanel && isInMaterialPanel(mx, my)) {
                int px = width() - PANEL_W;
                int iconX = px + PANEL_W - 28;
                if (mx >= iconX && mx <= iconX + 14 && my >= 3 && my <= 17) {
                    matSearchActive = !matSearchActive;
                    if (!matSearchActive) { matFilterText = ""; updateFilteredContentH(); }
                    matScrollY = 0; matScrollTargetY = 0;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
            }
            // Scrollbar thumb
            if (input.isLeftClick() && showMaterialPanel && isOnScrollbarThumb(mx, my)) {
                scrollbarDragging = true;
                scrollbarDragStartY = (float) my;
                scrollbarDragStartScroll = matScrollTargetY;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            // Minimap click
            if (input.isLeftClick() && isInMinimap(mx, my)) {
                minimapClickToPan(mx, my);
                minimapDragging = true;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            // Left click on collapse button
            if (input.isLeftClick()) {
                LayoutNode clicked = findNodeWithButtonAt(mx, my);
                if (clicked != null && clicked.originalChildCount > 0) {
                    double wx = panX + mx / zoom, wy = panY + my / zoom;
                    // Focus subgraph button (top-right of header)
                    double fbX = clicked.x + NODE_W - 18;
                    double fbY = clicked.y + 5;
                    if (wx >= fbX && wx <= fbX + 16 && wy >= fbY && wy <= fbY + 16) {
                        focusSubgraph(clicked.pathId);
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    // Collapse/expand button (bottom center)
                    double btnX = clicked.x + NODE_W / 2.0 - 10;
                    double btnY = clicked.y + NODE_H - 2;
                    if (wx >= btnX && wx <= btnX + 20 && wy >= btnY && wy <= btnY + 20) {
                        toggleCollapse(clicked.pathId);
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                }
                // Click on a node to track it
                LayoutNode nodeHit = findNodeAt(mx, my);
                if (nodeHit != null) {
                    trackedNodeId = nodeHit.pathId;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
            }
            // Middle-click drag
            if (input.key().getValue() == 2) {
                dragging = true;
                zoom = targetZoom;
                dragStartX = mx; dragStartY = my;
                dragPanStartX = panX; dragPanStartY = panY;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseReleased((input, ctx) -> {
            if (scrollbarDragging) { scrollbarDragging = false; ctx.cancel(); return EventDispatch.consumed; }
            if (minimapDragging) { minimapDragging = false; ctx.cancel(); return EventDispatch.consumed; }
            if (dragging) { dragging = false; ctx.cancel(); }
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            FloatPos local = input.mouseRelative(TreeCanvasWidget.this);
            double mx = local.x(), my = local.y();
            if (scrollbarDragging) {
                int visibleH = panelContentVisibleH();
                int maxScroll = Math.max(0, matFilteredContentH - visibleH);
                int thumbH = Math.max(16, (int) ((float) visibleH / matFilteredContentH * visibleH));
                int trackH = visibleH - thumbH;
                if (trackH > 0 && maxScroll > 0) {
                    float delta = (float) my - scrollbarDragStartY;
                    matScrollTargetY = Math.max(0, Math.min(maxScroll, scrollbarDragStartScroll + delta / trackH * maxScroll));
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (minimapDragging) { minimapClickToPan(mx, my); ctx.cancel(); return EventDispatch.consumed; }
            if (dragging) {
                panX = dragPanStartX - (mx - dragStartX) / zoom;
                panY = dragPanStartY - (my - dragStartY) / zoom;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, ctx) -> {
            FloatPos local = sceneToLocal(mouseX, mouseY);
            double mx = local.x(), my = local.y();
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            if (showMaterialPanel && isInMaterialPanel(mx, my)) {
                int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
                if (scrollY > 0) matScrollTargetY = Math.max(0, matScrollTargetY - MAT_SCROLL_SPEED);
                else if (scrollY < 0) matScrollTargetY = Math.min(maxScroll, matScrollTargetY + MAT_SCROLL_SPEED);
                ctx.cancel();
                return EventDispatch.consumed;
            }
            zoomFocusWX = panX + mx / (double) zoom;
            zoomFocusWY = panY + my / (double) zoom;
            zoomFocusSX = mx; zoomFocusSY = my;
            if (scrollY > 0) targetZoom = Math.min(MAX_ZOOM, targetZoom * 1.15f);
            else if (scrollY < 0) targetZoom = Math.max(MIN_ZOOM, targetZoom / 1.15f);
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, ctx) -> {
            if (!matSearchActive) return EventDispatch.pass;
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                matSearchActive = false; matFilterText = ""; updateFilteredContentH();
                matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !matFilterText.isEmpty()) {
                matFilterText = matFilterText.substring(0, matFilterText.length() - 1);
                updateFilteredContentH(); matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!matSearchActive) return EventDispatch.pass;
            char c = (char) codePoint;
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == '.') {
                matFilterText += c;
                updateFilteredContentH(); matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseEnter((mouseX, mouseY, ctx) -> widgetHovered = true);
        onMouseLeave((mouseX, mouseY, ctx) -> {
            widgetHovered = false;
            hoveredNodeId = null;
            scene().setHoverTooltip(Tooltip.create());
        });
    }

    // ═══════════════════ Public API ═══════════════════

    public TreeCanvasWidget setCraftCount(long count) {
        this.craftCount = Math.max(1, count);
        return this;
    }

    public void toggleMaterialPanel() {
        showMaterialPanel = !showMaterialPanel;
        matScrollY = 0; matScrollTargetY = 0;
        matSearchActive = false; matFilterText = "";
        updateFilteredContentH();
    }

    public boolean isMaterialPanelVisible() { return showMaterialPanel; }

    /** Direct viewport manipulation for dump tests. */
    public void setViewport(double px, double py, float z) {
        this.panX = px; this.panY = py;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        this.targetZoom = this.zoom;
    }

    public double getPanX() { return panX; }
    public double getPanY() { return panY; }
    public float getZoom() { return zoom; }

    /** Scroll material panel programmatically. */
    public void setMaterialScroll(float scrollY) {
        int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
        float clamped = Math.max(0, Math.min(scrollY, maxScroll));
        this.matScrollY = clamped;
        this.matScrollTargetY = clamped;
    }

    /** Set material panel search filter for dump tests. */
    public void setMatFilter(String text) {
        this.matSearchActive = !text.isEmpty();
        this.matFilterText = text;
        updateFilteredContentH();
        matScrollY = 0; matScrollTargetY = 0;
    }

    /** Track a specific node by path ID. */
    public void setTrackedNode(@Nullable String pathId) {
        this.trackedNodeId = pathId;
    }

    public List<LayoutNode> getFlatNodes() { return flatNodes; }

    /** Center viewport on the root node at the given zoom level. */
    public void centerOnRoot(float z) {
        if (rootLayout == null) return;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        this.targetZoom = this.zoom;
        this.panX = rootLayout.x + NODE_W / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = rootLayout.y + NODE_H / 2.0 - height() / (2.0 * zoom);
    }

    /** Center viewport on a node in the given layer at the given zoom level.
     *  Picks the node closest to the layer's horizontal centroid. */
    public void centerOnLayer(int layer, float z) {
        var nodes = layerNodes.getOrDefault(layer, List.of());
        if (nodes.isEmpty()) return;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        this.targetZoom = this.zoom;
        double avgX = nodes.stream().mapToInt(n -> n.x + NODE_W / 2).average().orElse(0);
        // Pick the node closest to the centroid (guarantees a node is on screen)
        LayoutNode closest = nodes.stream()
                .min(java.util.Comparator.comparingDouble(n -> Math.abs(n.x + NODE_W / 2.0 - avgX)))
                .orElse(nodes.get(0));
        this.panX = closest.x + NODE_W / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = closest.y + NODE_H / 2.0 - height() / (2.0 * zoom);
    }

    /** Get the maximum tree depth (layer count - 1). */
    public int getMaxDepth() { return maxDepth; }

    /** Set the highlighted (active) layer. -1 = none. */
    public void setHighlightedLayer(int layer) {
        this.highlightedLayer = layer;
    }

    public int getHighlightedLayer() { return highlightedLayer; }

    public void setOnSubtreeChanged(@Nullable Runnable callback) {
        this.onSubtreeChanged = callback;
    }

    public @Nullable String getTrackedNodeId() { return trackedNodeId; }
    public String getCurrentRootPathId() { return currentRootPathId; }

    public int getLayerNodeCount(int layer) {
        return layerNodes.getOrDefault(layer, List.of()).size();
    }

    /** Get pathIds of all nodes in a given layer. */
    public List<String> getLayerNodePathIds(int layer) {
        return layerNodes.getOrDefault(layer, List.of()).stream()
                .map(n -> n.pathId).toList();
    }

    /** Focus on a subtree rooted at the given pathId. */
    public void focusSubgraph(String pathId) {
        if (fullRootLayout == null) return;
        if (pathId.equals(currentRootPathId)) return; // already focused here
        LayoutNode sub = findInTree(fullRootLayout, pathId);
        if (sub == null) return;
        currentRootPathId = pathId;
        rootLayout = sub;
        reflattenFromRoot();
        computeMaterialSummary();
        // Center on the subtree root at current zoom (don't reset zoom)
        centerOnNode(sub.pathId);
        if (onSubtreeChanged != null) onSubtreeChanged.run();
    }

    /** Reset to the full tree view. */
    public void resetToFullTree() {
        if (fullRootLayout == null || "root".equals(currentRootPathId)) return;
        String previousPathId = currentRootPathId;
        currentRootPathId = "root";
        rootLayout = fullRootLayout;
        reflattenFromRoot();
        computeMaterialSummary();
        // Center on the node we came back from, keeping current zoom
        LayoutNode prev = findInTree(fullRootLayout, previousPathId);
        if (prev != null) {
            centerOnNode(prev.pathId);
        }
        if (onSubtreeChanged != null) onSubtreeChanged.run();
    }

    /** Center viewport on a specific node by pathId. */
    public void centerOnNode(String pathId) {
        LayoutNode node = fullRootLayout != null ? findInTree(fullRootLayout, pathId) : null;
        if (node == null) return;
        this.panX = node.x + NODE_W / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = node.y + NODE_H / 2.0 - height() / (2.0 * zoom);
    }

    /** Expand ancestors, set tracked node, and center on it. */
    public void scrollToNode(String pathId) {
        String[] parts = pathId.split("-");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i == 0) current.append(parts[i]);
            else current.append("-").append(parts[i]);
            String ancestor = current.toString();
            collapsedPaths.remove(ancestor);
            userExpandedPaths.add(ancestor);
        }
        float keepZoom = this.zoom;
        trackedNodeId = pathId;
        rebuildTree(false); // don't auto-collapse, preserve user state
        this.zoom = keepZoom;
        this.targetZoom = keepZoom;
        centerOnNode(pathId);
    }

    /** Get breadcrumbs from root to currentRootPathId. */
    public List<BreadcrumbEntry> getPathContextBreadcrumbs() {
        return getBreadcrumbs(currentRootPathId);
    }

    /** Get breadcrumbs from root to trackedNodeId. */
    public List<BreadcrumbEntry> getTraceBreadcrumbs() {
        return trackedNodeId != null ? getBreadcrumbs(trackedNodeId) : List.of();
    }

    private List<BreadcrumbEntry> getBreadcrumbs(String pathId) {
        if (fullRootLayout == null) return List.of();
        List<BreadcrumbEntry> result = new ArrayList<>();
        String[] parts = pathId.split("-");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i == 0) current.append(parts[i]);
            else current.append("-").append(parts[i]);
            LayoutNode node = findInTree(fullRootLayout, current.toString());
            if (node != null && node.processNode != null) {
                result.add(new BreadcrumbEntry(node.processNode.name(), node.pathId));
            }
        }
        return result;
    }

    private @Nullable LayoutNode findInTree(@Nullable LayoutNode node, String pathId) {
        if (node == null) return null;
        if (node.pathId.equals(pathId)) return node;
        for (LayoutNode child : node.children) {
            LayoutNode found = findInTree(child, pathId);
            if (found != null) return found;
        }
        return null;
    }

    private void reflattenFromRoot() {
        if (rootLayout == null) return;
        List<LayoutNode> fNodes = new ArrayList<>();
        List<LayoutLink> fLinks = new ArrayList<>();
        Map<Integer, List<LayoutNode>> lNodes = new HashMap<>();
        flatten(rootLayout, fNodes, fLinks, lNodes);
        this.flatNodes = fNodes;
        this.flatLinks = fLinks;
        this.layerNodes = lNodes;
        this.maxDepth = fNodes.stream().mapToInt(n -> n.depth).max().orElse(0);
        // Update minimap bounds and layout dimensions
        if (!fNodes.isEmpty()) {
            mmWorldMinX = fNodes.stream().mapToInt(n -> n.x).min().orElse(0);
            mmWorldMinY = fNodes.stream().mapToInt(n -> n.y).min().orElse(0);
            int maxX = fNodes.stream().mapToInt(n -> n.x + NODE_W).max().orElse(0);
            int maxY = fNodes.stream().mapToInt(n -> n.y + NODE_H).max().orElse(0);
            mmWorldW = Math.max(1, maxX - mmWorldMinX);
            mmWorldH = Math.max(1, maxY - mmWorldMinY);
        }
        this.layoutWidth = Math.max(500, mmWorldW + 200);
        this.layoutHeight = Math.max(500, (maxDepth + 1) * ROW_H + 200);
    }

    private int viewportW() {
        return showMaterialPanel ? width() - PANEL_W - 2 : width();
    }

    // ═══════════════════ Data Setup ═══════════════════

    public TreeCanvasWidget setGraph(ProcessGraph graph) {
        this.graph = graph;
        List<ProcessNode> sinks = graph.sinks();
        this.targetNode = sinks.isEmpty() ? null : sinks.get(0);
        rebuildTree();
        resetView();
        computeMaterialSummary();
        return this;
    }

    private int collapseLevel = 0; // current auto-collapse aggressiveness

    private void rebuildTree() {
        rebuildTree(true);
    }

    /**
     * @param autoCollapse if true, reset collapseLevel and iterate width constraint.
     *                     if false, keep current collapseLevel (used for user-initiated toggle).
     */
    private void rebuildTree(boolean autoCollapse) {
        if (graph == null || targetNode == null) {
            rootLayout = null; fullRootLayout = null;
            flatNodes = List.of(); flatLinks = List.of();
            layerNodes = Map.of(); maxDepth = 0;
            return;
        }
        if (autoCollapse) {
            // Build tree, iteratively increasing collapse aggressiveness until width fits
            collapseLevel = 0;
            for (int attempt = 0; attempt < 6; attempt++) {
                fullRootLayout = buildLayoutTree(targetNode, 0, "root");
                computeWidths(fullRootLayout);
                if (fullRootLayout.treeWidth <= MAX_TREE_WIDTH) break;
                collapseLevel++;
            }
        } else {
            // User-initiated toggle: keep current collapseLevel, just rebuild once
            fullRootLayout = buildLayoutTree(targetNode, 0, "root");
            computeWidths(fullRootLayout);
        }

        int offsetX = Math.max(0, (Math.min(2000, MAX_TREE_WIDTH) - fullRootLayout.treeWidth) / 2);
        assignPositions(fullRootLayout, offsetX);

        // Use subtree root if set
        LayoutNode effectiveRoot = fullRootLayout;
        if (!"root".equals(currentRootPathId)) {
            LayoutNode sub = findInTree(fullRootLayout, currentRootPathId);
            if (sub != null) effectiveRoot = sub;
            else currentRootPathId = "root";
        }
        rootLayout = effectiveRoot;

        // Flatten from effective root
        List<LayoutNode> fNodes = new ArrayList<>();
        List<LayoutLink> fLinks = new ArrayList<>();
        Map<Integer, List<LayoutNode>> lNodes = new HashMap<>();
        flatten(effectiveRoot, fNodes, fLinks, lNodes);

        this.flatNodes = fNodes;
        this.flatLinks = fLinks;
        this.layerNodes = lNodes;
        this.maxDepth = fNodes.stream().mapToInt(n -> n.depth).max().orElse(0);
        this.layoutWidth = Math.max(500, fullRootLayout.treeWidth + offsetX * 2);
        this.layoutHeight = Math.max(500, (maxDepth + 1) * ROW_H + 200);

        // Compute minimap bounding box from actual visible nodes
        if (!fNodes.isEmpty()) {
            mmWorldMinX = fNodes.stream().mapToInt(n -> n.x).min().orElse(0);
            mmWorldMinY = fNodes.stream().mapToInt(n -> n.y).min().orElse(0);
            int maxX = fNodes.stream().mapToInt(n -> n.x + NODE_W).max().orElse(0);
            int maxY = fNodes.stream().mapToInt(n -> n.y + NODE_H).max().orElse(0);
            mmWorldW = Math.max(1, maxX - mmWorldMinX);
            mmWorldH = Math.max(1, maxY - mmWorldMinY);
        }

        // Auto-track the deepest main trunk node
        if (trackedNodeId == null) {
            LayoutNode trunk = effectiveRoot;
            while (!trunk.children.isEmpty()) {
                trunk = trunk.children.stream()
                        .max(Comparator.comparingInt(this::subtreeDepth))
                        .orElse(trunk.children.get(0));
            }
            trackedNodeId = trunk.pathId;
        }
    }

    private LayoutNode buildLayoutTree(ProcessNode pn, int depth, String pathId) {
        LayoutNode node = new LayoutNode(pn, pathId, depth);

        // Get child nodes (inputs to this step)
        List<ProcessEdge> incoming = new ArrayList<>(graph.incomingEdges(pn.id()));
        node.originalChildCount = incoming.size();

        // Auto-collapse heuristic — aggressiveness increases with collapseLevel
        boolean shouldAutoCollapse = false;
        int inputCount = incoming.size();
        int cl = collapseLevel;
        if (depth == 0) shouldAutoCollapse = inputCount > Math.max(3, 20 - cl * 4);
        else if (depth == 1) shouldAutoCollapse = inputCount > Math.max(2, 12 - cl * 3);
        else if (depth <= 3) shouldAutoCollapse = inputCount > Math.max(2, 6 - cl * 2);
        else if (depth <= 6) shouldAutoCollapse = inputCount >= Math.max(2, 4 - cl);
        else if (depth <= 10) shouldAutoCollapse = inputCount >= Math.max(1, 3 - cl);
        else shouldAutoCollapse = true;

        boolean isCollapsed = collapsedPaths.contains(pathId)
                || (shouldAutoCollapse && !userExpandedPaths.contains(pathId));
        node.collapsed = isCollapsed;

        if (!isCollapsed) {
            int idx = 0;
            for (ProcessEdge edge : incoming) {
                ProcessNode child = graph.node(edge.fromNodeId());
                if (child != null) {
                    node.children.add(buildLayoutTree(child, depth + 1, pathId + "-" + idx));
                }
                idx++;
            }
        }
        return node;
    }

    private void computeWidths(LayoutNode n) {
        if (n.children.isEmpty() || n.collapsed) {
            n.treeWidth = NODE_W;
        } else {
            int w = 0;
            for (int i = 0; i < n.children.size(); i++) {
                computeWidths(n.children.get(i));
                w += n.children.get(i).treeWidth;
                if (i > 0) w += GAP_X;
            }
            n.treeWidth = Math.max(NODE_W, w);
        }
    }

    private void assignPositions(LayoutNode n, int startX) {
        n.x = startX + n.treeWidth / 2 - NODE_W / 2;
        n.y = n.depth * ROW_H + (ROW_H / 2 - NODE_H / 2);

        if (!n.collapsed && !n.children.isEmpty()) {
            int childrenSum = n.children.stream().mapToInt(c -> c.treeWidth).sum()
                    + Math.max(0, n.children.size() - 1) * GAP_X;
            int offset = startX + (n.treeWidth - childrenSum) / 2;
            for (LayoutNode c : n.children) {
                assignPositions(c, offset);
                offset += c.treeWidth + GAP_X;
            }
        }
    }

    private void flatten(LayoutNode n, List<LayoutNode> nodes, List<LayoutLink> links,
                          Map<Integer, List<LayoutNode>> byLayer) {
        nodes.add(n);
        byLayer.computeIfAbsent(n.depth, k -> new ArrayList<>()).add(n);
        if (!n.collapsed) {
            for (LayoutNode c : n.children) {
                links.add(new LayoutLink(n, c, n.pathId + "->" + c.pathId));
                flatten(c, nodes, links, byLayer);
            }
        }
    }

    private int subtreeDepth(LayoutNode n) {
        if (n.children.isEmpty()) return 0;
        return 1 + n.children.stream().mapToInt(this::subtreeDepth).max().orElse(0);
    }

    private void toggleCollapse(String pathId) {
        // Find actual collapsed state of the node (may be auto-collapsed)
        boolean currentlyCollapsed = false;
        for (LayoutNode ln : flatNodes) {
            if (ln.pathId.equals(pathId)) { currentlyCollapsed = ln.collapsed; break; }
        }
        if (currentlyCollapsed) {
            // Expand: remove from manual collapse, add to user-expanded (overrides auto)
            collapsedPaths.remove(pathId);
            userExpandedPaths.add(pathId);
        } else {
            // Collapse: add to manual collapse, remove from user-expanded
            collapsedPaths.add(pathId);
            userExpandedPaths.remove(pathId);
        }
        rebuildTree(false);  // user-initiated: keep collapseLevel, don't re-run width constraint
    }

    /** Toggle collapse state of a node by pathId. For dump tests. */
    public void toggleNodeCollapse(String pathId) {
        toggleCollapse(pathId);
    }

    /**
     * Returns the absolute screen position of the collapse button center for the given pathId.
     * Returns null if the node is not found or has no children.
     * The returned coordinates are relative to this widget's parent (screen-space).
     */
    public @Nullable Pos getCollapseButtonScreenPos(String pathId) {
        for (LayoutNode ln : flatNodes) {
            if (ln.pathId.equals(pathId) && ln.originalChildCount > 0) {
                // Button center in world coords: x + NODE_W/2, y + NODE_H - 2 + 10
                double wx = ln.x + NODE_W / 2.0;
                double wy = ln.y + NODE_H - 2 + 10;
                // Transform to screen coords relative to this widget
                double sx = (wx - panX) * zoom;
                double sy = (wy - panY) * zoom;
                // Add absolute position offset
                Pos abs = absolutePos();
                return new Pos((int) (abs.x() + sx), (int) (abs.y() + sy));
            }
        }
        return null;
    }

    /** Get list of collapsed node pathIds in current layout. */
    public List<String> getCollapsedNodePaths() {
        List<String> result = new ArrayList<>();
        for (LayoutNode ln : flatNodes) {
            if (ln.collapsed) result.add(ln.pathId);
        }
        return result;
    }

    /** Reset all user collapse/expand overrides back to auto-collapse defaults. */
    public void resetCollapseState() {
        collapsedPaths.clear();
        userExpandedPaths.clear();
        rebuildTree();
        resetView();
    }

    public void resetView() {
        fitContent();
    }

    /** Auto-fit all content into the viewport, centering both axes. */
    private void fitContent() {
        if (flatNodes.isEmpty() || width() <= 0 || height() <= 0) return;
        int vw = viewportW();
        int vh = height();
        // Compute actual content bounds from flat nodes
        int contentW = Math.max(1, mmWorldW);
        int contentH = Math.max(1, mmWorldH);
        // Compute zoom to fit content with padding
        float zx = (float) vw / (contentW + 60);
        float zy = (float) vh / (contentH + 60);
        zoom = Math.max(MIN_ZOOM, Math.min(1.0f, Math.min(zx, zy)));
        targetZoom = zoom;
        // Center content in viewport
        double contentCenterX = mmWorldMinX + contentW / 2.0;
        double contentCenterY = mmWorldMinY + contentH / 2.0;
        panX = contentCenterX - vw / (2.0 * zoom);
        panY = contentCenterY - vh / (2.0 * zoom);
    }

    public int getLayoutWidth() { return layoutWidth; }
    public int getLayoutHeight() { return layoutHeight; }

    // ═══════════════════ Material Summary ═══════════════════

    private void computeMaterialSummary() {
        if (graph == null || flatNodes.isEmpty()) {
            materialByCategory = new LinkedHashMap<>();
            materialPanelContentH = 0; totalTimeTicks = 0; totalEU = 0;
            return;
        }
        Map<String, long[]> amounts = new LinkedHashMap<>();
        Map<String, MaterialSlot> slotRef = new LinkedHashMap<>();
        Set<String> catalystNames = new LinkedHashSet<>();
        Map<String, List<Integer>> producerSteps = new HashMap<>(), consumerSteps = new HashMap<>();
        Map<String, Double> chanceAccum = new HashMap<>();
        Map<String, Integer> chanceCount = new HashMap<>();
        long totalTime = 0, totalEnergy = 0;
        int stepNum = 0;
        for (LayoutNode ln : flatNodes) {
            stepNum++;
            ProcessNode node = ln.processNode;
            if (node == null) continue;
            totalTime += node.durationTicks();
            totalEnergy += (long) node.euPerTick() * node.durationTicks();
            for (MaterialSlot input : node.inputs()) {
                amounts.computeIfAbsent(input.materialName(), k -> new long[2])[0] += input.amount();
                slotRef.putIfAbsent(input.materialName(), input);
                if (input.isCatalyst()) catalystNames.add(input.materialName());
                consumerSteps.computeIfAbsent(input.materialName(), k -> new ArrayList<>()).add(stepNum);
            }
            for (MaterialSlot output : node.outputs()) {
                amounts.computeIfAbsent(output.materialName(), k -> new long[2])[1] += output.amount();
                slotRef.putIfAbsent(output.materialName(), output);
                producerSteps.computeIfAbsent(output.materialName(), k -> new ArrayList<>()).add(stepNum);
                chanceAccum.merge(output.materialName(), output.chance(), Double::sum);
                chanceCount.merge(output.materialName(), 1, Integer::sum);
            }
        }
        this.totalTimeTicks = totalTime;
        this.totalEU = totalEnergy;
        Map<MatCategory, List<MaterialEntry>> byCategory = new LinkedHashMap<>();
        for (MatCategory cat : MatCategory.values()) byCategory.put(cat, new ArrayList<>());
        for (var e : amounts.entrySet()) {
            String name = e.getKey();
            long consumed = e.getValue()[0], produced = e.getValue()[1];
            long missing = Math.max(0, consumed - produced);
            long inventory = Math.max(0, produced - consumed);
            double avgChance = chanceCount.containsKey(name)
                    ? chanceAccum.get(name) / chanceCount.get(name) : 1.0;
            MatCategory cat;
            if (catalystNames.contains(name)) cat = MatCategory.CATALYST;
            else if (consumed > 0 && produced == 0) cat = MatCategory.EXTERNAL_INPUT;
            else if (consumed == 0 && produced > 0) cat = MatCategory.BYPRODUCT;
            else cat = MatCategory.INTERMEDIATE;
            byCategory.get(cat).add(new MaterialEntry(name, slotRef.get(name),
                    consumed, produced, missing, inventory, cat,
                    producerSteps.getOrDefault(name, List.of()),
                    consumerSteps.getOrDefault(name, List.of()), avgChance));
        }
        byCategory.values().removeIf(List::isEmpty);
        this.materialByCategory = byCategory;
        int h = 0;
        for (var entry : byCategory.entrySet()) {
            h += SEC_HDR_H + 2;
            h += entry.getValue().size() * (ENTRY_H + ENTRY_GAP);
            h += 6;
        }
        h += TOTALS_H + 10;
        this.materialPanelContentH = h;
        updateFilteredContentH();
    }

    private void updateFilteredContentH() {
        if (matFilterText.isEmpty()) {
            matFilteredContentH = materialPanelContentH;
            return;
        }
        String lower = matFilterText.toLowerCase();
        int h = 0;
        for (var entry : materialByCategory.entrySet()) {
            long matchCount = entry.getValue().stream()
                    .filter(e -> e.name().toLowerCase().contains(lower))
                    .count();
            if (matchCount > 0) {
                h += SEC_HDR_H + 2 + (int) (matchCount * (ENTRY_H + ENTRY_GAP)) + 6;
            }
        }
        h += TOTALS_H + 10;
        matFilteredContentH = h;
    }

    // ═══════════════════ Animation ═══════════════════

    private void animateSmooth() {
        if (Math.abs(targetZoom - zoom) >= ZOOM_SNAP) {
            zoom += (targetZoom - zoom) * ZOOM_SMOOTH_SPEED;
            if (Math.abs(targetZoom - zoom) < ZOOM_SNAP) zoom = targetZoom;
            panX = zoomFocusWX - zoomFocusSX / zoom;
            panY = zoomFocusWY - zoomFocusSY / zoom;
        }
        if (Math.abs(matScrollTargetY - matScrollY) > 0.5f) {
            matScrollY += (matScrollTargetY - matScrollY) * MAT_SCROLL_SMOOTH;
            if (Math.abs(matScrollTargetY - matScrollY) < 0.5f) matScrollY = matScrollTargetY;
        }
    }

    // ═══════════════════ Rendering ═══════════════════

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (graph == null || flatNodes.isEmpty()) {
            canvas.fill(0, 0, width(), height(), BG);
            canvas.drawCenteredString("无配方数据", width() / 2, height() / 2 - 4, TEXT_WEAK);
            return;
        }
        Font font = Minecraft.getInstance().font;
        animateSmooth();
        truncationFrameId++;
        truncationCache.clear();

        if (mouseX != lastHoverMouseX || mouseY != lastHoverMouseY) {
            lastHoverMouseX = mouseX; lastHoverMouseY = mouseY;
            updateHover(mouseX, mouseY);
        }
        updateTooltip(mouseX, mouseY);
        machineCycleTicks++;

        int vw = viewportW();
        canvas.fill(0, 0, vw, height(), BG);

        Pos abs = absolutePos();
        canvas.withClip(abs.x(), abs.y(), vw, height(), () -> {
            canvas.pushTransform();
            canvas.scale(zoom);
            canvas.translate((float) -panX, (float) -panY);

            drawGrid(canvas);
            drawLayerBands(canvas, font);
            drawConnections(canvas);
            for (LayoutNode ln : flatNodes) {
                double nRight = ln.x + NODE_W, nBot = ln.y + NODE_H;
                double visMinX = panX, visMinY = panY;
                double visMaxX = panX + vw / zoom, visMaxY = panY + height() / zoom;
                if (nRight < visMinX || ln.x > visMaxX || nBot < visMinY || ln.y > visMaxY) continue;
                drawNodeCard(canvas, font, ln, partialTicks);
            }
            flushDeferredIcons(canvas);
            canvas.popTransform();
        });

        drawMinimap(canvas);
        if (showMaterialPanel) drawMaterialPanel(canvas, font, mouseX, mouseY, partialTicks);
        drawStatusBar(canvas, font);
    }

    // ═══════════════════ Grid & Layer Bands ═══════════════════

    private void drawGrid(SceneCanvas canvas) {
        double visW = viewportW() / zoom, visH = height() / zoom;
        float pixelSpacing = GRID_SPACING * zoom;
        boolean drawMinor = pixelSpacing >= 6;
        int sx = ((int) (panX / GRID_SPACING) - 1) * GRID_SPACING;
        int sy = ((int) (panY / GRID_SPACING) - 1) * GRID_SPACING;
        int ex = (int) (panX + visW) + GRID_SPACING;
        int ey = (int) (panY + visH) + GRID_SPACING;
        // Dot grid matching web app style (dots at intersections, not full lines)
        int dotMinor = 0xFF1A1F2E;
        int dotMajor = 0xFF232A3A;
        int dotSz = Math.max(1, (int)(1.5f / zoom)); // 1-2px dots in world space
        for (int gx = sx; gx <= ex; gx += GRID_SPACING) {
            int idxX = gx / GRID_SPACING;
            for (int gy = sy; gy <= ey; gy += GRID_SPACING) {
                int idxY = gy / GRID_SPACING;
                boolean major = (idxX % 5 == 0) && (idxY % 5 == 0);
                boolean minor = drawMinor;
                if (major) {
                    canvas.fill(gx, gy, dotSz + 1, dotSz + 1, dotMajor);
                } else if (minor) {
                    canvas.fill(gx, gy, dotSz, dotSz, dotMinor);
                }
            }
        }
    }

    private void drawLayerBands(SceneCanvas canvas, Font font) {
        double visMinY = panY, visMaxY = panY + height() / zoom;
        int bandX = (int) (panX - 200);
        int bandW = (int) (viewportW() / zoom + 400);

        for (int i = 0; i <= maxDepth; i++) {
            int bandTop = i * ROW_H;
            int bandBot = bandTop + ROW_H;
            if (bandBot < visMinY || bandTop > visMaxY) continue;

            boolean isHighlighted = (i == highlightedLayer);

            // Alternating band background (even layers slightly lighter)
            if (isHighlighted) {
                canvas.fill(bandX, bandTop, bandW, ROW_H, 0xFF0F1B2E);
            } else if (i % 2 == 0) {
                canvas.fill(bandX, bandTop, bandW, ROW_H, 0xFF101520);
            }

            // Horizontal divider line at top of layer
            canvas.fill(bandX, bandTop, bandW, 1, isHighlighted ? 0xFF1E3A8A : LAYER_LINE);
            // Bottom divider for highlighted layer
            if (isHighlighted) {
                canvas.fill(bandX, bandBot - 1, bandW, 1, 0xFF1E3A8A);
            }

            // Repeating watermark text tiled at fixed screen intervals (like browser background)
            int textColor = isHighlighted ? 0x40B0C4FF : 0x18FFFFFF;
            String layerLabel = "Layer " + i;
            int labelW = font.width(layerLabel);
            // Tile spacing in screen pixels, convert to world coords
            float tileSpacingScreen = Math.max(labelW + 60, 160);
            float tileSpacing = tileSpacingScreen / zoom;
            // Compute start x aligned to tile grid so tiles stay fixed when panning
            float startX = (float) (Math.floor(panX / tileSpacing) * tileSpacing);
            float endX = (float) (panX + viewportW() / zoom);
            float labelY = bandTop + ROW_H / 2f - 4;
            for (float tx = startX; tx < endX; tx += tileSpacing) {
                canvas.drawString(layerLabel, (int) tx, (int) labelY, textColor, false);
            }
        }
    }

    // ═══════════════════ Connections (Manhattan routing) ═══════════════════

    private boolean isNodeInTrackedPath(String nodeId) {
        if (trackedNodeId == null) return false;
        if (nodeId.equals(trackedNodeId)) return true;
        return trackedNodeId.startsWith(nodeId + "-");
    }

    private void drawConnections(SceneCanvas canvas) {
        // Sort links so tracked ones draw on top
        List<LayoutLink> sorted = new ArrayList<>(flatLinks);
        sorted.sort(Comparator.comparingInt(l -> isNodeInTrackedPath(l.target.pathId) ? 1 : 0));

        for (LayoutLink link : sorted) {
            int startX = link.source.x + NODE_W / 2;
            int startY = link.source.y + NODE_H;
            int endX = link.target.x + NODE_W / 2;
            int endY = link.target.y;
            int midY = (startY + endY) / 2;

            boolean tracked = isNodeInTrackedPath(link.target.pathId);
            boolean isCatalyst = link.target.processNode != null
                    && link.target.processNode.inputs().stream().anyMatch(MaterialSlot::isCatalyst);

            int color = tracked ? LINK_TRACKED : (isCatalyst ? LINK_CATALYST : LINK_DEFAULT);
            int thickness = tracked ? 3 : 2;

            // Manhattan routing: down, horizontal, down
            // Vertical segment from source bottom to midY
            canvas.fill(startX - thickness / 2, startY, thickness, midY - startY, color);
            // Horizontal segment at midY
            int hx1 = Math.min(startX, endX) - thickness / 2;
            int hx2 = Math.max(startX, endX) + thickness / 2;
            canvas.fill(hx1, midY - thickness / 2, hx2 - hx1, thickness, color);
            // Vertical segment from midY to target top
            canvas.fill(endX - thickness / 2, midY, thickness, endY - midY, color);

            // Tracked glow effect
            if (tracked) {
                int glowColor = withAlpha(LINK_TRACKED, 0x30);
                int glowThick = thickness + 6;
                canvas.fill(startX - glowThick / 2, startY, glowThick, midY - startY, glowColor);
                canvas.fill(hx1 - 3, midY - glowThick / 2, hx2 - hx1 + 6, glowThick, glowColor);
                canvas.fill(endX - glowThick / 2, midY, glowThick, endY - midY, glowColor);
            }
        }
    }

    // ═══════════════════ Minimap ═══════════════════

    private void drawMinimap(SceneCanvas canvas) {
        if (flatNodes.isEmpty() || mmWorldW <= 0 || mmWorldH <= 0) return;
        int mmX = viewportW() - MM_W - MM_PAD;
        int mmY = MM_PAD;

        canvas.roundedRect(mmX, mmY, MM_W, MM_H, 0, MM_BG, 1f, MM_BD);

        int contentX = mmX + 4;
        int contentY = mmY + 4;
        int contentH = MM_H - 8;
        int contentW = MM_W - 8;

        // Scale based on actual visible-node bounding box
        double sx = (double) contentW / mmWorldW;
        double sy = (double) contentH / mmWorldH;

        // Draw connections first (behind nodes), right-angle routing
        int linkColor = 0xFF2A3A50;
        int linkTracked = 0xFF2266AA;
        for (LayoutLink link : flatLinks) {
            int lsx = contentX + (int) ((link.source.x + NODE_W / 2.0 - mmWorldMinX) * sx);
            int lsy = contentY + (int) ((link.source.y + NODE_H - mmWorldMinY) * sy);
            int lex = contentX + (int) ((link.target.x + NODE_W / 2.0 - mmWorldMinX) * sx);
            int ley = contentY + (int) ((link.target.y - mmWorldMinY) * sy);
            boolean tracked = isNodeInTrackedPath(link.target.pathId);
            int c = tracked ? linkTracked : linkColor;
            int midY = (lsy + ley) / 2;
            canvas.fill(lsx, lsy, 1, midY - lsy, c);
            canvas.fill(Math.min(lsx, lex), midY, Math.abs(lex - lsx) + 1, 1, c);
            canvas.fill(lex, midY, 1, ley - midY, c);
        }

        // Draw nodes
        for (LayoutNode ln : flatNodes) {
            int rx = contentX + (int) ((ln.x - mmWorldMinX) * sx);
            int ry = contentY + (int) ((ln.y - mmWorldMinY) * sy);
            int rw = Math.max(1, (int) (NODE_W * sx));
            int rh = Math.max(1, (int) (NODE_H * sy));
            boolean isTracked = isNodeInTrackedPath(ln.pathId);
            int color = isTracked ? LINK_TRACKED : (ln.processNode != null ? withAlpha(ln.processNode.tierColor(), 0xFF) : 0xFF4B5563);
            canvas.fill(rx, ry, rw, rh, color);
        }

        // Viewport indicator (pan is in world-space, offset by bounding box origin)
        int vpW = Math.min(contentW, Math.max(4, (int) ((viewportW() / zoom) * sx)));
        int vpH = Math.min(contentH, Math.max(4, (int) ((height() / zoom) * sy)));
        int ivpX = contentX + (int) ((panX - mmWorldMinX) * sx);
        int ivpY = contentY + (int) ((panY - mmWorldMinY) * sy);
        ivpX = Math.max(contentX, Math.min(ivpX, contentX + contentW - vpW));
        ivpY = Math.max(contentY, Math.min(ivpY, contentY + contentH - vpH));
        canvas.fill(ivpX, ivpY, vpW, 1, MM_VP);
        canvas.fill(ivpX, ivpY + vpH - 1, vpW, 1, MM_VP);
        canvas.fill(ivpX, ivpY, 1, vpH, MM_VP);
        canvas.fill(ivpX + vpW - 1, ivpY, 1, vpH, MM_VP);
    }

    private boolean isInMinimap(double mx, double my) {
        int mmX = viewportW() - MM_W - MM_PAD;
        return mx >= mmX && mx <= mmX + MM_W && my >= MM_PAD && my <= MM_PAD + MM_H;
    }

    private void minimapClickToPan(double mx, double my) {
        if (mmWorldW <= 0 || mmWorldH <= 0) return;
        int mmX = viewportW() - MM_W - MM_PAD;
        int contentX = mmX + 4;
        int contentY = MM_PAD + 4;
        int contentH = MM_H - 8;
        int contentW = MM_W - 8;
        double sx = (double) contentW / mmWorldW;
        double sy = (double) contentH / mmWorldH;
        double clampedMx = Math.max(contentX, Math.min(mx, contentX + contentW));
        double clampedMy = Math.max(contentY, Math.min(my, contentY + contentH));
        double worldX = (clampedMx - contentX) / sx + mmWorldMinX;
        double worldY = (clampedMy - contentY) / sy + mmWorldMinY;
        double vpWorldW = viewportW() / zoom, vpWorldH = height() / zoom;
        panX = Math.max(0, Math.min(worldX - vpWorldW / 2.0, layoutWidth - vpWorldW));
        panY = Math.max(0, Math.min(worldY - vpWorldH / 2.0, layoutHeight - vpWorldH));
    }

    // ═══════════════════ Node Card ═══════════════════

    private void drawNodeCard(SceneCanvas canvas, Font font, LayoutNode ln, float pt) {
        int x = ln.x, y = ln.y;
        ProcessNode pn = ln.processNode;
        if (pn == null) return;

        boolean hovered = ln.pathId.equals(hoveredNodeId);
        boolean tracked = ln.pathId.equals(trackedNodeId);
        boolean isRoot = ln.depth == 0;
        boolean isCatalystNode = pn.inputs().stream().anyMatch(MaterialSlot::isCatalyst);
        boolean isLeaf = ln.originalChildCount == 0;

        // Very low zoom: simplified rectangle
        if (zoom < DETAIL_LOD_ZOOM) {
            int bgC = hovered ? 0xFF1F2937 : NODE_BG;
            canvas.fill(x, y, NODE_W, NODE_H, bgC);
            canvas.fill(x, y, NODE_W, 2, pn.tierColor());
            return;
        }

        // Shadow + card bg
        canvas.shadow(x, y, NODE_W, NODE_H, 6, 2, 5, 0x40000000);

        int bgC = isCatalystNode ? CATALYST_BG_T : NODE_BG;
        int bdC = isCatalystNode ? CATALYST_BD : (hovered ? NODE_BD_HOVER : NODE_BD);
        canvas.roundedRect(x, y, NODE_W, NODE_H, isLeaf ? 6 : 8, bgC, 1f, bdC);

        // Tracked ring + glow
        if (tracked) {
            canvas.roundedRect(x - 2, y - 2, NODE_W + 4, NODE_H + 4, 10,
                    0x00000000, 2f, TRACKED_RING);
            canvas.shadow(x - 2, y - 2, NODE_W + 4, NODE_H + 4, 10, 4, 12, TRACKED_GLOW);
        }

        if (isLeaf) {
            drawLeafCard(canvas, font, ln, x, y, pt);
        } else {
            drawStepCard(canvas, font, ln, x, y, pt);
        }

        // Collapse/expand button at bottom center
        if (ln.originalChildCount > 0) {
            int btnX = x + NODE_W / 2 - 10;
            int btnY = y + NODE_H - 2;
            canvas.roundedRect(btnX, btnY, 20, 20, 10, COLLAPSE_BG, 1f, COLLAPSE_BD);
            String sym = ln.collapsed ? "+" : "-";
            canvas.drawCenteredString(sym, btnX + 10, btnY + 6, TEXT_SECONDARY);

            if (ln.collapsed) {
                String badge = "+ " + ln.originalChildCount + " Inputs";
                int bw = font.width(badge) + 8;
                int bx = x + NODE_W / 2 - bw / 2;
                int by = btnY + 22;
                canvas.roundedRect(bx, by, bw, 14, 4,
                        withAlpha(ACCENT, 0x40), 1f, withAlpha(ACCENT, 0x50));
                canvas.drawCenteredString(badge, bx + bw / 2, by + 3, ACCENT);
            }
        }
    }

    private void drawLeafCard(SceneCanvas canvas, Font font, LayoutNode ln, int x, int y, float pt) {
        ProcessNode pn = ln.processNode;
        // Simple leaf: icon + name + amount
        int pad = 8;
        int iconSz = 24;
        int iconX = x + pad;
        int iconY = y + (NODE_H - iconSz) / 2;

        if (!pn.outputs().isEmpty()) {
            MaterialSlot slot = pn.outputs().get(0);
            drawSlotIcon(canvas, slot, iconX, iconY, iconSz / 16.0f, pt);
        }

        int textX = iconX + iconSz + 8;
        int textW = NODE_W - textX + x - pad;
        String name = pn.name();
        canvas.drawString(cachedTruncate(name, textW, font), textX, y + 24, TEXT_PRIMARY, true);

        String amtStr = "Amount: " + fmtNum(pn.outputs().isEmpty() ? 0 : pn.outputs().get(0).amount());
        canvas.drawString(cachedTruncate(amtStr, textW, font), textX, y + 40, TEXT_WEAK, false);
    }

    private void drawStepCard(SceneCanvas canvas, Font font, LayoutNode ln, int x, int y, float pt) {
        ProcessNode pn = ln.processNode;
        boolean isRoot = ln.depth == 0;

        // ── Colored top stripe (tier color bar) ──
        canvas.fill(x + 4, y + 2, NODE_W - 8, 3, pn.tierColor());

        // ── Header bar ──
        canvas.roundedRect(x + 2, y + 5, NODE_W - 4, HDR_H - 3, 4f, 2f, 2f, 4f, NODE_HDR_BG, 0, 0);

        // Depth indicator dot
        int dotX = x + 8;
        int dotY = y + 5 + (HDR_H - 3) / 2;
        int dotC = isRoot ? DOT_CLR_ROOT : DOT_CLR;
        canvas.fill(dotX, dotY - 2, 4, 4, dotC);

        // Machine name
        MachineInfo displayMachine = getDisplayMachine(pn);
        String machineName = displayMachine != null ? displayMachine.name() : pn.machine();
        int nameX = dotX + 10;
        int nameMaxW = NODE_W - nameX + x - 66;
        canvas.drawString(cachedTruncate(machineName, nameMaxW, font), nameX, y + 8, TEXT_PRIMARY, true);

        // Focus subgraph button (top-right, for non-leaf with children)
        if (ln.originalChildCount > 0) {
            int fbX = x + NODE_W - 18;
            int fbY = y + 6;
            canvas.roundedRect(fbX, fbY, 14, 14, 3, 0xFF1F2937, 1f, NODE_BD);
            canvas.drawCenteredString("\u2295", fbX + 7, fbY + 3, ACCENT);
        }

        // Tier badge at right (before focus button)
        String tierStr = pn.tierName();
        int tierW = font.width(tierStr) + 8;
        int tierX = x + NODE_W - tierW - (ln.originalChildCount > 0 ? 22 : 8);
        int tierY = y + 6;
        canvas.roundedRect(tierX, tierY, tierW, 14, 3, 0xFF1F2937, 1f, NODE_BD);
        canvas.drawCenteredString(tierStr, tierX + tierW / 2, tierY + 3, pn.tierColor());

        // ── Body ──
        int bodyY = y + HDR_H + 2;
        int bodyH = NODE_H - HDR_H - 2;

        // Item icon (scaled small)
        int itemIconSz = 16;
        int itemX = x + 8;
        int itemY = bodyY + (bodyH - itemIconSz) / 2;
        if (!pn.outputs().isEmpty()) {
            MaterialSlot outSlot = pn.outputs().get(0);
            drawSlotIcon(canvas, outSlot, itemX - 2, itemY, itemIconSz / 16.0f * 0.75f, pt);
        }

        // Output amount + name
        int outTextX = itemX + itemIconSz + 4;
        int outTextW = NODE_W - (outTextX - x) - 40;
        if (!pn.outputs().isEmpty()) {
            MaterialSlot out = pn.outputs().get(0);
            String outStr = "+" + fmtNum(out.amount()) + " " + out.materialName();
            canvas.drawString(cachedTruncate(outStr, outTextW, font), outTextX, bodyY + 8, GREEN, true);
        }

        // Duration at right
        String durStr = pn.durationTicks() + "t";
        int durW = font.width(durStr);
        canvas.drawString(durStr, x + NODE_W - durW - 8, bodyY + bodyH / 2 - 4, TEXT_WEAK, false);
    }

    // ═══════════════════ Slot Icons ═══════════════════

    private void drawSlotIcon(SceneCanvas canvas, MaterialSlot slot, int ix, int iy, float scale, float pt) {
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix, iy, scale));
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            deferredFluids.add(new DeferredFluid(slot.fluidStack(), ix, iy, scale, pt));
        } else {
            int sz = (int)(16 * scale);
            canvas.roundedRect(ix, iy, sz, sz, 3, 0xFF0D1117, 1, 0xFF2D3748);
        }
    }

    private void flushDeferredIcons(SceneCanvas canvas) {
        if (deferredItems.isEmpty() && deferredFluids.isEmpty()) return;
        canvas.renderLayered(g -> {
            Lighting.setupFor3DItems();
            for (DeferredItem item : deferredItems) {
                g.pose().pushPose();
                g.pose().translate(item.x, item.y, 0);
                g.pose().scale(item.scale, item.scale, 1);
                g.pose().last().normal().identity();
                g.renderItem(item.stack, 0, 0);
                g.pose().popPose();
            }
            for (DeferredFluid fluid : deferredFluids) {
                g.pose().pushPose();
                g.pose().translate(fluid.x, fluid.y, 0);
                g.pose().scale(fluid.scale, fluid.scale, 1);
                g.pose().last().normal().identity();
                FluidStackRenderer.instance.renderBase(g, fluid.fluid, 16, 16, fluid.pt);
                g.pose().popPose();
            }
        });
        deferredItems.clear();
        deferredFluids.clear();
    }

    private void drawStatusBar(SceneCanvas canvas, Font font) {
        String s = String.format("节点: %d | 缩放: %.0f%% | 中键拖动: 平移, 滚轮: 缩放",
                flatNodes.size(), zoom * 100);
        int barH = 14;
        int vw = viewportW();
        canvas.fill(0, height() - barH, vw, barH, 0xCC111827);
        canvas.drawString(s, 4, height() - barH + 3, TEXT_PRIMARY, false);
    }

    // ═══════════════════ Material Panel ═══════════════════

    private static final int SEARCH_BAR_H = 16;

    private int panelContentVisibleH() {
        int base = height() - 22 - 2;
        return matSearchActive ? base - SEARCH_BAR_H : base;
    }

    private boolean isInMaterialPanel(double mx, double my) {
        if (!showMaterialPanel) return false;
        int px = width() - PANEL_W;
        return mx >= px && mx <= width() && my >= 0 && my <= height();
    }

    private boolean isOnScrollbarThumb(double mx, double my) {
        if (!showMaterialPanel) return false;
        int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
        if (maxScroll <= 0) return false;
        int px = width() - PANEL_W;
        int contentY = 24 + 4;
        int visibleH = panelContentVisibleH();
        int sbX = px + PANEL_W - SCROLLBAR_W - 3;
        int sbH = visibleH - 2;
        int thumbH = Math.max(16, (int) ((float) visibleH / materialPanelContentH * sbH));
        float progress = matScrollTargetY / maxScroll;
        int thumbY = contentY + 1 + (int) (progress * (sbH - thumbH));
        return mx >= sbX && mx <= sbX + SCROLLBAR_W && my >= thumbY && my <= thumbY + thumbH;
    }

    private void drawMaterialPanel(SceneCanvas canvas, Font font,
                                    int mouseX, int mouseY, float pt) {
        int px = width() - PANEL_W;
        int ph = height();
        canvas.shadow(px - 2, 0, PANEL_W + 2, ph, 4, 2, 8, 0x60000000);
        canvas.roundedRect(px, 0, PANEL_W, ph, 0, PANEL_BG_CLR, 1, BORDER_MAIN);

        int headerH = 20;
        canvas.fill(px, 0, PANEL_W, headerH, 0xFF0D1520);
        canvas.fill(px, headerH - 1, PANEL_W, 1, BORDER_MAIN);
        // Accent bar on left side of header
        canvas.fill(px, 0, 3, headerH, ACCENT);
        canvas.drawString("材料统计", px + 8, 6, ACCENT_TEXT, true);
        // Search icon
        int searchIconX = px + PANEL_W - 28;
        boolean searchHover = mouseX >= searchIconX && mouseX <= searchIconX + 14
                && mouseY >= 3 && mouseY <= 17;
        canvas.texture(matSearchActive || searchHover ? Icon.searchSelected : Icon.search,
                searchIconX, 5, 10, 10);
        // Item count badge
        int totalItems = materialByCategory.values().stream().mapToInt(List::size).sum();
        String badge = String.valueOf(totalItems);
        int badgeW = font.width(badge) + 6;
        int badgeX = searchIconX - badgeW - 4;
        canvas.roundedRect(badgeX, 4, badgeW, 12, 6, 0xFF1E293B, 0, 0);
        canvas.drawCenteredString(badge, badgeX + badgeW / 2, 6, TEXT_SECONDARY);

        // Search bar (below header, when active)
        int searchBarBottom = headerH;
        if (matSearchActive) {
            searchBarBottom = headerH + SEARCH_BAR_H;
            canvas.fill(px, headerH, PANEL_W, SEARCH_BAR_H, 0xFF0F1824);
            canvas.fill(px, searchBarBottom - 1, PANEL_W, 1, BORDER_MAIN);
            // Search text field
            int sfX = px + 6, sfY = headerH + 2, sfW = PANEL_W - 12, sfH = SEARCH_BAR_H - 4;
            canvas.roundedRect(sfX, sfY, sfW, sfH, 4, 0xFF1A2332, 1, BORDER_SEC);
            if (matFilterText.isEmpty()) {
                canvas.drawString("搜索物料...", sfX + 4, sfY + 2, TEXT_WEAK, false);
            } else {
                String displayText = matFilterText;
                int maxTextW = sfW - 8;
                while (font.width(displayText) > maxTextW && displayText.length() > 1) {
                    displayText = displayText.substring(1);
                }
                canvas.drawString(displayText, sfX + 4, sfY + 2, TEXT_PRIMARY, false);
                // Cursor blink
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    int cursorX = sfX + 4 + font.width(displayText);
                    canvas.fill(cursorX, sfY + 2, 1, sfH - 4, TEXT_PRIMARY);
                }
            }
        }

        int contentY = searchBarBottom + 2;
        int visibleH = panelContentVisibleH();
        Pos absP = absolutePos();
        stickyHeaderCat = null;

        // Render scrollable content with translate (no withClip due to rendering bug)
        canvas.pushTransform();
        canvas.translate(px + PANEL_PAD, contentY - matScrollY);
        renderMaterialContent(canvas, font, mouseX - px - PANEL_PAD, (int) (mouseY - contentY + matScrollY), pt,
                matScrollY, matScrollY + visibleH);
        flushDeferredIcons(canvas);
        canvas.popTransform();

        // ── Overlay clipping: paint over overflow areas ──
        // Cover overflow above content area (panel header zone)
        canvas.fill(px, 0, PANEL_W, headerH, 0xFF0D1520);
        canvas.fill(px, headerH - 1, PANEL_W, 1, BORDER_MAIN);
        canvas.fill(px, 0, 3, headerH, ACCENT);
        canvas.drawString("材料统计", px + 8, 6, ACCENT_TEXT, true);
        canvas.texture(matSearchActive || searchHover ? Icon.searchSelected : Icon.search,
                searchIconX, 5, 10, 10);
        canvas.roundedRect(badgeX, 4, badgeW, 12, 6, 0xFF1E293B, 0, 0);
        canvas.drawCenteredString(badge, badgeX + badgeW / 2, 6, TEXT_SECONDARY);
        // Cover search bar area
        if (matSearchActive) {
            canvas.fill(px, headerH, PANEL_W, SEARCH_BAR_H, 0xFF0F1824);
            canvas.fill(px, searchBarBottom - 1, PANEL_W, 1, BORDER_MAIN);
            int sfX = px + 6, sfY = headerH + 2, sfW = PANEL_W - 12, sfH = SEARCH_BAR_H - 4;
            canvas.roundedRect(sfX, sfY, sfW, sfH, 4, 0xFF1A2332, 1, BORDER_SEC);
            if (matFilterText.isEmpty()) {
                canvas.drawString("搜索物料...", sfX + 4, sfY + 2, TEXT_WEAK, false);
            } else {
                String displayText = matFilterText;
                int maxTextW = sfW - 8;
                while (font.width(displayText) > maxTextW && displayText.length() > 1)
                    displayText = displayText.substring(1);
                canvas.drawString(displayText, sfX + 4, sfY + 2, TEXT_PRIMARY, false);
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    int cursorX = sfX + 4 + font.width(displayText);
                    canvas.fill(cursorX, sfY + 2, 1, sfH - 4, TEXT_PRIMARY);
                }
            }
        }
        // Cover contentY gap
        canvas.fill(px, searchBarBottom, PANEL_W, 2, PANEL_BG_CLR);
        // Cover overflow below content area
        canvas.fill(px, contentY + visibleH, PANEL_W, ph - contentY - visibleH, PANEL_BG_CLR);
        // Re-draw panel border on edges
        canvas.fill(px, ph - 1, PANEL_W, 1, BORDER_MAIN);

        // Draw sticky category header ON TOP of the clipped content, in widget-local coords
        boolean hasScrollbar = matFilteredContentH > visibleH;
        int innerW = PANEL_W - 2 * PANEL_PAD - (hasScrollbar ? SCROLLBAR_W + 4 : 0);
        if (stickyHeaderCat != null) {
            int stickyDrawY = contentY + stickyHeaderOffsetY;
            canvas.fill(px + PANEL_PAD, stickyDrawY - 1, innerW, SEC_HDR_H + 3, PANEL_BG_CLR);
            drawCategoryHeader(canvas, font, stickyHeaderCat, stickyHeaderCount,
                    px + PANEL_PAD, stickyDrawY, innerW);
            // Bottom shadow for depth
            for (int i = 0; i < 3; i++) {
                canvas.fill(px + PANEL_PAD, stickyDrawY + SEC_HDR_H + i, innerW, 1,
                        0xFF0A0F18);
            }
        }

        int maxScroll = Math.max(0, matFilteredContentH - visibleH);
        if (maxScroll > 0) {
            int sbX = px + PANEL_W - SCROLLBAR_W - 2;
            int sbY = contentY + 1;
            int sbH = visibleH - 2;
            canvas.roundedRect(sbX, sbY, SCROLLBAR_W, sbH, 4, 0xFF0D1117, 0, 0);
            int thumbH = Math.max(16, (int) ((float) visibleH / matFilteredContentH * sbH));
            float progress = maxScroll > 0 ? matScrollY / maxScroll : 0;
            int thumbY = sbY + (int) (progress * (sbH - thumbH));
            canvas.roundedRect(sbX + 1, thumbY, SCROLLBAR_W - 2, thumbH, 3, BG_ELEVATED, 1, BORDER_SEC);
        }
    }

    private void renderMaterialContent(SceneCanvas canvas, Font font,
                                        int mouseX, int mouseY, float pt,
                                        float visMinY, float visMaxY) {
        int drawY = 0;
        boolean hasScrollbar = matFilteredContentH > (int) (visMaxY - visMinY);
        int innerW = PANEL_W - 2 * PANEL_PAD - (hasScrollbar ? SCROLLBAR_W + 4 : 0);
        hoveredMatName = null;
        String filterLower = matFilterText.isEmpty() ? null : matFilterText.toLowerCase();

        for (var catEntry : materialByCategory.entrySet()) {
            MatCategory cat = catEntry.getKey();
            List<MaterialEntry> allEntries = catEntry.getValue();
            List<MaterialEntry> entries = filterLower == null ? allEntries
                    : allEntries.stream().filter(e -> e.name().toLowerCase().contains(filterLower)).toList();
            if (entries.isEmpty()) continue;
            int headerY = drawY;

            // Draw category header (only if visible)
            if (drawY + SEC_HDR_H >= visMinY && drawY <= visMaxY) {
                drawCategoryHeader(canvas, font, cat, entries.size(), 0, drawY, innerW);
            }
            drawY += SEC_HDR_H + 2;
            for (MaterialEntry entry : entries) {
                if (drawY + ENTRY_H >= visMinY && drawY <= visMaxY)
                    drawMaterialEntry(canvas, font, entry, 0, drawY, innerW, pt, mouseX, mouseY);
                drawY += ENTRY_H + ENTRY_GAP;
            }
            int sectionEnd = drawY;
            drawY += 6;

            // Track sticky header: category whose header scrolled past the top
            if (headerY < visMinY && sectionEnd > visMinY) {
                stickyHeaderCat = cat;
                stickyHeaderCount = entries.size();
                // Calculate offset for "push up" effect when next section approaches
                int distToEnd = (int) (sectionEnd - visMinY);
                stickyHeaderOffsetY = distToEnd < SEC_HDR_H + 6 ? distToEnd - SEC_HDR_H - 6 : 0;
            }
        }

        drawY += 2;
        if (drawY + TOTALS_H + 6 >= visMinY && drawY <= visMaxY) {
            canvas.fill(4, drawY, innerW - 8, 1, 0xFF2D3748);
            drawY += 6;
            canvas.roundedRect(0, drawY, innerW, TOTALS_H, 4, BG_SURFACE, 1, BORDER_MAIN);
            int txL = 6, txR = innerW - 8;
            int r0 = drawY + 6, r1 = drawY + 20, r2 = drawY + 36;
            canvas.texture(Icon.Sign.Energy.time, txL, r0, 9, 9);
            canvas.drawString("总时间", txL + 12, r0, TEXT_WEAK, false);
            String timeStr = formatDuration(totalTimeTicks);
            canvas.drawString(timeStr, txR - font.width(timeStr), r0, TEXT_PRIMARY, false);
            canvas.texture(Icon.Sign.Energy.eu, txL, r1, 9, 9);
            canvas.drawString("总能量", txL + 12, r1, TEXT_WEAK, false);
            String euStr = fmtNum(totalEU) + " EU";
            canvas.drawString(euStr, txR - font.width(euStr), r1, ORANGE, true);
            canvas.fill(txL, r2 + 3, 6, 3, GREEN);
            canvas.drawString("步骤数", txL + 10, r2, TEXT_WEAK, false);
            String stepStr = String.valueOf(flatNodes.size());
            canvas.drawString(stepStr, txR - font.width(stepStr), r2, GREEN, false);
        }
    }

    private void drawCategoryHeader(SceneCanvas canvas, Font font,
                                     MatCategory cat, int count, int x, int y, int w) {
        canvas.fill(x, y, w, SEC_HDR_H, 0xFF141E2E);
        canvas.fill(x, y + SEC_HDR_H - 1, w, 1, 0xFF1E293B);
        canvas.fill(x, y, 3, SEC_HDR_H, cat.color);
        canvas.drawString(cat.label, x + 7, y + 4, cat.color, true);
        String countStr = String.valueOf(count);
        int cw = font.width(countStr);
        canvas.drawString(countStr, x + w - cw - 4, y + 4, TEXT_WEAK, false);
    }

    private void drawMaterialEntry(SceneCanvas canvas, Font font,
                                    MaterialEntry entry, int x, int y, int w,
                                    float pt, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ENTRY_H;
        if (hovered) hoveredMatName = entry.name();

        int bgC = hovered ? BG_HOVER : BG_SURFACE;
        canvas.roundedRect(x, y, w, BOX_H, 3, bgC, 1, hovered ? BORDER_SEC : BORDER_MAIN);

        // Row 1: icon + material name
        int pad = 3;
        int slotX = x + pad, slotY = y + pad;
        // Icon slot background
        canvas.roundedRect(slotX, slotY, ICON_SLOT_SZ, ICON_SLOT_SZ, 3, 0xFF0D1117, 1, 0xFF2D3748);
        int iconX = slotX + (ICON_SLOT_SZ - PANEL_ICON_SZ) / 2;
        int iconY = slotY + (ICON_SLOT_SZ - PANEL_ICON_SZ) / 2;
        drawPanelSlotIcon(canvas, entry.refSlot(), iconX, iconY, pt);

        // Material name (truncated to fit)
        int nameX = slotX + ICON_SLOT_SZ + 3;
        int nameMaxW = w - (nameX - x) - pad;
        String displayName = cachedTruncate(entry.name(), nameMaxW, font);
        int nameColor = hovered ? 0xFFE2E8F0 : TEXT_PRIMARY;
        canvas.drawString(displayName, nameX, y + 4, nameColor, false);

        // Row 2: +amount (green) / -amount (red) inline with mini progress bar
        int row2Y = y + 15;
        int numX = nameX;

        String availStr, needStr;
        switch (entry.category()) {
            case EXTERNAL_INPUT -> { availStr = ""; needStr = fmtNum(entry.needed()); }
            case INTERMEDIATE -> {
                availStr = fmtNum(entry.produced());
                needStr = entry.missing() > 0 ? fmtNum(entry.missing()) : "0";
            }
            case BYPRODUCT -> { availStr = fmtNum(entry.produced()); needStr = ""; }
            case CATALYST -> { availStr = fmtNum(entry.needed()); needStr = ""; }
            default -> { availStr = ""; needStr = ""; }
        }

        // Draw amounts: "+N" green, "-N" red
        int textEndX = numX;
        if (!availStr.isEmpty()) {
            String plusStr = "+" + availStr;
            canvas.drawString(plusStr, numX, row2Y, GREEN, false);
            textEndX = numX + font.width(plusStr) + 2;
        }
        if (!needStr.isEmpty()) {
            String minusStr = "-" + needStr;
            canvas.drawString(minusStr, textEndX, row2Y, RED, false);
            textEndX += font.width(minusStr) + 2;
        }

        // Chance indicator for byproducts
        if (entry.category() == MatCategory.BYPRODUCT && entry.chance() < 1.0) {
            String chStr = (int) (entry.chance() * 100) + "%%";
            int chW = font.width(chStr);
            canvas.drawString(chStr, w - chW - pad, row2Y, ORANGE, false);
        }

        // Progress bar at bottom of entry
        int barY = y + BOX_H;
        int barX = x, barW = w;
        canvas.fill(barX, barY, barW, BAR_H, 0xFF0D1117);
        switch (entry.category()) {
            case EXTERNAL_INPUT -> canvas.fill(barX, barY, barW, BAR_H, RED);
            case INTERMEDIATE -> {
                long total = Math.max(entry.needed(), entry.produced());
                if (total > 0) {
                    int greenW = (int) (barW * Math.min(1.0, (double) entry.produced() / total));
                    if (greenW > 0) canvas.fill(barX, barY, greenW, BAR_H, GREEN);
                    if (entry.missing() > 0) {
                        int redW = (int) (barW * ((double) entry.missing() / total));
                        canvas.fill(barX + greenW, barY, Math.max(1, redW), BAR_H, RED);
                    }
                }
            }
            case BYPRODUCT -> canvas.fill(barX, barY, barW, BAR_H,
                    withAlpha(ORANGE, entry.chance() < 1.0 ? 0x88 : 0xBB));
            case CATALYST -> canvas.fill(barX, barY, barW, BAR_H, withAlpha(PURPLE, 0x88));
        }
    }

    private void drawPanelSlotIcon(SceneCanvas canvas, MaterialSlot slot, int ix, int iy, float pt) {
        float iconScale = PANEL_ICON_SZ / 16.0f;
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix, iy, iconScale));
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            deferredFluids.add(new DeferredFluid(slot.fluidStack(), ix, iy, iconScale, pt));
        } else {
            int c = slot.type().color;
            canvas.roundedRect(ix, iy, PANEL_ICON_SZ, PANEL_ICON_SZ, 3, 0xFF0D1117, 1, 0xFF2D3748);
            canvas.drawCenteredString(slot.type().name().substring(0, 1),
                    ix + PANEL_ICON_SZ / 2, iy + (PANEL_ICON_SZ - 9) / 2, c);
        }
    }

    // ═══════════════════ Hover & Hit Testing ═══════════════════

    private void updateHover(int mouseX, int mouseY) {
        hoveredNodeId = null;
        double lx = mouseX, ly = mouseY;
        if (lx < 0 || ly < 0 || lx > width() || ly > height()) return;
        if (showMaterialPanel && isInMaterialPanel(lx, ly)) return;
        LayoutNode found = findNodeAt(lx, ly);
        if (found != null) hoveredNodeId = found.pathId;
    }

    private @Nullable LayoutNode findNodeAt(double screenX, double screenY) {
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        for (LayoutNode ln : flatNodes) {
            if (wx >= ln.x && wx <= ln.x + NODE_W && wy >= ln.y && wy <= ln.y + NODE_H) {
                return ln;
            }
        }
        return null;
    }

    /** Extended hit-test that includes the collapse button area below the node. */
    private @Nullable LayoutNode findNodeWithButtonAt(double screenX, double screenY) {
        LayoutNode hit = findNodeAt(screenX, screenY);
        if (hit != null) return hit;
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        for (LayoutNode ln : flatNodes) {
            if (ln.originalChildCount <= 0) continue;
            // Button area: x centered, y from NODE_H-2 to NODE_H+18
            double btnX = ln.x + NODE_W / 2.0 - 10;
            double btnY = ln.y + NODE_H - 2;
            if (wx >= btnX && wx <= btnX + 20 && wy >= btnY && wy <= btnY + 20) {
                return ln;
            }
        }
        return null;
    }

    private @Nullable MachineInfo getDisplayMachine(ProcessNode node) {
        List<MachineInfo> opts = node.machineOptions();
        if (opts.isEmpty()) return null;
        if (opts.size() == 1) return opts.get(0);
        for (MachineInfo mi : opts) if (mi.name().equals(node.machine())) return mi;
        return opts.get((int) ((machineCycleTicks / CYCLE_INTERVAL_TICKS) % opts.size()));
    }

    // ═══════════════════ Tooltip ═══════════════════

    private void updateTooltip(int mouseX, int mouseY) {
        if (!widgetHovered) return;
        Tooltip tip = Tooltip.create();

        if (showMaterialPanel && isInMaterialPanel(mouseX, mouseY) && hoveredMatName != null) {
            MaterialEntry found = null;
            for (var entries : materialByCategory.values()) {
                for (MaterialEntry me : entries) {
                    if (me.name().equals(hoveredMatName)) { found = me; break; }
                }
                if (found != null) break;
            }
            if (found != null) {
                var b = FlowChartTooltipComponent.builder();
                b.section(found.name(), found.refSlot().type().color, found.category().color);
                b.kv("分类", 0xFF888888, found.category().label, found.category().color);
                b.divider();
                switch (found.category()) {
                    case EXTERNAL_INPUT -> {
                        b.kv("需求量", 0xFF888888, fmtNum(found.needed()), RED);
                        b.bar(1.0f, RED);
                    }
                    case INTERMEDIATE -> {
                        b.kv("消耗量", 0xFF888888, fmtNum(found.needed()), 0xFF88CC88);
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), 0xFFCC8888);
                        if (found.missing() > 0) b.kv("缺少", 0xFFCC4444, fmtNum(found.missing()), 0xFFFF6666);
                        float ratio = found.needed() > 0 ? (float) found.produced() / found.needed() : 1.0f;
                        b.bar(Math.min(1.0f, ratio), ratio >= 1.0f ? GREEN : RED);
                    }
                    case BYPRODUCT -> {
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), ORANGE);
                        b.bar((float) found.chance(), ORANGE);
                    }
                    case CATALYST -> {
                        b.kv("用量", 0xFF888888, fmtNum(found.needed()), PURPLE);
                        b.bar(1.0f, PURPLE);
                    }
                }
                tip.add(b.build());
            }
        } else if (hoveredNodeId != null) {
            LayoutNode ln = findLayoutNode(hoveredNodeId);
            if (ln != null && ln.processNode != null) {
                ProcessNode node = ln.processNode;
                var b = FlowChartTooltipComponent.builder();
                b.section(node.name(), node.tierColor());
                b.gap(2);
                b.kv("机器", 0xFF888888, node.machine(), node.tierColor());
                b.divider();
                if (!node.inputs().isEmpty()) { b.section("输入", 0xFF88CC88); b.slots(null, 0, node.inputs()); }
                if (!node.outputs().isEmpty()) { b.section("输出", 0xFFCC8888); b.slots(null, 0, node.outputs()); }
                b.divider();
                b.kv("时长", node.durationFormatted());
                b.kv("速率", 0xFF888888, node.euFormatted(), ORANGE);
                long seu = (long) node.euPerTick() * node.durationTicks();
                b.kv("总 EU", 0xFF888888, fmtNum(seu) + " EU", 0xFFFFCC44);
                b.kv("等级", 0xFF888888, node.tierName(), node.tierColor());
                tip.add(b.build());
            }
        }
        scene().setHoverTooltip(tip);
    }

    private @Nullable LayoutNode findLayoutNode(String pathId) {
        for (LayoutNode ln : flatNodes) {
            if (ln.pathId.equals(pathId)) return ln;
        }
        return null;
    }

    // ═══════════════════ Utilities ═══════════════════

    private static String fmtNum(long v) {
        if (v < 0) v = -v;
        if (v < 1_000) return String.valueOf(v);
        if (v < 1_000_000) return String.format("%.1fk", v / 1_000.0);
        if (v < 1_000_000_000) return String.format("%.1fM", v / 1_000_000.0);
        return String.format("%.1fG", v / 1_000_000_000.0);
    }

    private static String truncate(String text, int maxPxW, Font font) {
        if (maxPxW <= 10) return "";
        if (font.width(text) <= maxPxW) return text;
        String ell = "..";
        int ellW = font.width(ell);
        while (text.length() > 1 && font.width(text) + ellW > maxPxW)
            text = text.substring(0, text.length() - 1);
        return text + ell;
    }

    private String cachedTruncate(String text, int maxPxW, Font font) {
        long key = ((long) text.hashCode() << 32) | (maxPxW & 0xFFFFFFFFL);
        return truncationCache.computeIfAbsent(key, k -> truncate(text, maxPxW, font));
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static String formatDuration(long ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 60) return String.format("%.1fs (%dt)", seconds, ticks);
        double minutes = seconds / 60;
        if (minutes < 60) return String.format("%.1fm (%dt)", minutes, ticks);
        return String.format("%.1fh (%dt)", minutes / 60, ticks);
    }
}
