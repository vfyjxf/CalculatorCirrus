package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import com.mojang.blaze3d.platform.Lighting;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Sign;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;

/**
 * Tree diagram widget — renders a node-based tree matching the web app's TreeDiagram.
 * Each node is a compact card (nodeW × nodeH) connected by Manhattan-routed lines.
 */
public class TreeCanvasWidget extends CompositeWidget<Widget> {

    //region Metrics
    // Node dimensions (world-space pixels)
    private static final int nodeW = 220;
    private static final int nodeH = 82;
    private static final int gapX = 24;
    private static final int rowH = 128;
    private static final int focusBtnW = 18;
    private static final int focusBtnH = 18;
    private static final int stepSectionGap = 8;

    // Color accents still used by line/text overlays
    private static final int trackedRing = 0xFFEEF3F8;
    private static final int externalSelectedRing = 0xFF7FAFD6;
    private static final int linkDefault = 0xFF6B7077;
    private static final int linkCatalyst = 0xFF8E939A;
    private static final int linkTracked = 0xFFFFFFFF;
    private static final int layerLine = 0x70A9A9A9;
    private static final int dotClr = 0xFF666666;
    private static final int dotClrRoot = 0xFFFFFFFF;
    private static final int mmVp = 0xCFFFFFFF;
    private static final int matBaseFill = 0xFFCFCFCF;
    private static final int matShadow = 0xFF555555;
    private static final int matHighlight = 0xFFFFFFFF;
    private static final int matFrame = 0xFFE6E6E6;

    // Layout
    private static final int mmW = 160;
    private static final int mmH = 100;
    private static final int mmPad = 8;
    private static final int mmContentPad = 6;
    private static final double mmScaleShrink = 0.92;
    private static final int panelW = 200;
    private static final int panelPad = 5;
    private static final int barH = 3;
    private static final int boxH = 40;
    private static final int entryH = boxH + barH;
    private static final int entryGap = 3;
    private static final int secHdrH = 16;
    private static final int totalsH = 52;
    private static final int panelIconSz = 16;
    private static final int iconSlotSz = 22;
    private static final int indCellSz = 10;
    private static final int scrollbarW = 8;
    private static final int maxTreeWidth = 3500;  // Width cap for auto-collapse
    private static final float minZoom = 0.15f;
    private static final float maxZoom = 3.0f;
    private static final float detailLodZoom = 0.10f;
    private static final float zoomSnap = 0.001f;
    private static final float zoomSmoothSpeed = 0.6f;
    private static final float matScrollSpeed = 25f;
    private static final float matScrollSmooth = 0.35f;
    private static final long cycleIntervalTicks = 40;
    private static final int cardTitleText = 0xFF555D65;
    private static final int cardMetaText = 0xFF656E77;
    private static final int cardMinorText = 0xFF7A838C;
    //endregion

    //region Inner Types

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
        INTERMEDIATE("中间产物", 0xFF9EA7AF);
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
    public record MaterialPanelPalette(int baseFill, int shadow, int highlight, int frame) {}
    public record StepCardChrome(int machineTitleColor, boolean machineTitleShadow,
                                 boolean usesSlotBackedMachineIcon, boolean usesSlotBackedOutputIcon,
                                 boolean usesDrawnSeparator, boolean focusButtonUsesForkGlyph, int sectionGap) {}
    public record MinimapViewportRect(int x, int y, int w, int h) {}
    //endregion

    //region State

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
    private @Nullable String externalHoveredNodeId = null;
    private @Nullable String externalSelectedNodeId = null;
    private boolean minimapDragging = false;

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
    //endregion

    //region Constructor
    public TreeCanvasWidget() {
        setFocusable(true);
        onMouseClicked((input, ctx) -> {
            FloatPos local = input.mouseRelative(TreeCanvasWidget.this);
            double mx = local.x(), my = local.y();
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            // Search icon click in material panel header
            if (input.isLeftClick() && showMaterialPanel && isInMaterialPanel(mx, my)) {
                int px = width() - panelW;
                int iconX = px + panelW - 28;
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
                    int[] focusBounds = focusButtonBounds(clicked.x, clicked.y);
                    if (containsWorldPoint(focusBounds, wx, wy)) {
                        focusSubgraph(clicked.pathId);
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    // Collapse/expand button (bottom center)
                    double btnX = clicked.x + nodeW / 2.0 - 10;
                    double btnY = clicked.y + nodeH - 2;
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
                snapPanToPixelGrid();
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
                if (scrollY > 0) matScrollTargetY = Math.max(0, matScrollTargetY - matScrollSpeed);
                else if (scrollY < 0) matScrollTargetY = Math.min(maxScroll, matScrollTargetY + matScrollSpeed);
                ctx.cancel();
                return EventDispatch.consumed;
            }
            zoomFocusWX = panX + mx / (double) zoom;
            zoomFocusWY = panY + my / (double) zoom;
            zoomFocusSX = mx; zoomFocusSY = my;
            if (scrollY > 0) targetZoom = Math.min(maxZoom, targetZoom * 1.15f);
            else if (scrollY < 0) targetZoom = Math.max(minZoom, targetZoom / 1.15f);
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
        onMouseLeave((mouseX, mouseY, ctx) -> {
            hoveredNodeId = null;
            scene().setHoverTooltip(Tooltip.create());
        });
    }
    //endregion

    //region Public API

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

    static MaterialPanelPalette debugMaterialPanelPalette() {
        return new MaterialPanelPalette(matBaseFill, matShadow, matHighlight, matFrame);
    }

    static StepCardChrome debugStepCardChrome() {
        return new StepCardChrome(textWhite, true, true, true, true, true, stepSectionGap);
    }

    static boolean debugMinimapShowsTitle() {
        return false;
    }

    static boolean debugTrackedLinksUseGlow() {
        return false;
    }

    static MinimapViewportRect debugClampMinimapViewportRect(int contentX, int contentY, int contentW, int contentH,
                                                             int rawX, int rawY, int rawW, int rawH) {
        return clampMinimapViewportRect(contentX, contentY, contentW, contentH, rawX, rawY, rawW, rawH);
    }

    int debugMaterialEntryCount() {
        return materialByCategory.values().stream().mapToInt(List::size).sum();
    }

    /** Direct viewport manipulation for dump tests. */
    public void setViewport(double px, double py, float z) {
        this.panX = px; this.panY = py;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
    }

    public double getPanX() { return panX; }
    public double getPanY() { return panY; }
    public float getZoom() { return zoom; }

    /** Scroll material panel programmatically. */
    public void setMaterialScroll(float scrollY) {
        int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
        if (Float.isNaN(scrollY) || Float.isInfinite(scrollY)) {
            scrollY = 0;
        }
        float clamped = Math.max(0, Math.min(scrollY, maxScroll));
        this.matScrollY = clamped;
        this.matScrollTargetY = clamped;
    }

    /** Set material panel search filter for dump tests. */
    public void setMatFilter(String text) {
        this.matSearchActive = !text.isEmpty();
        this.matFilterText = text;
        updateFilteredContentH();
        sanitizeMaterialScroll();
    }

    /** Track a specific node by path ID. */
    public void setTrackedNode(@Nullable String pathId) {
        this.trackedNodeId = pathId;
    }

    public List<LayoutNode> getFlatNodes() { return flatNodes; }

    /** Center viewport on the root node at the given zoom level. */
    public void centerOnRoot(float z) {
        if (rootLayout == null) return;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
        this.panX = rootLayout.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = rootLayout.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /** Center viewport on a node in the given layer at the given zoom level.
     *  Picks the node closest to the layer's horizontal centroid. */
    public void centerOnLayer(int layer, float z) {
        var nodes = layerNodes.getOrDefault(layer, List.of());
        if (nodes.isEmpty()) return;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
        double avgX = nodes.stream().mapToInt(n -> n.x + nodeW / 2).average().orElse(0);
        // Pick the node closest to the centroid (guarantees a node is on screen)
        LayoutNode closest = nodes.stream()
                .min(java.util.Comparator.comparingDouble(n -> Math.abs(n.x + nodeW / 2.0 - avgX)))
                .orElse(nodes.get(0));
        this.panX = closest.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = closest.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /** Get the maximum tree depth (layer count - 1). */
    public int getMaxDepth() { return maxDepth; }

    /** Layer index at current viewport center (for layer-nav tracking). */
    public int getViewportCenterLayer() {
        if (maxDepth <= 0) return 0;
        double centerWorldY = panY + (height() / zoom) * 0.5;
        int layer = (int) Math.floor(centerWorldY / rowH);
        if (layer < 0) return 0;
        return Math.min(layer, maxDepth);
    }

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

    /** Highlight a node from external UI (e.g., path breadcrumbs). */
    public void setExternalHoveredNode(@Nullable String pathId) {
        externalHoveredNodeId = pathId;
    }

    /** Persistently select a node from external UI without mutating tracked/root path states. */
    public void setExternalSelectedNode(@Nullable String pathId) {
        externalSelectedNodeId = pathId;
    }

    /** Build recipe tooltip for a specific node path; null when path not found. */
    public @Nullable Tooltip buildNodeTooltipForPath(String pathId) {
        return buildNodeTooltipForPath(pathId, null);
    }

    /** Build recipe tooltip for a specific node path with breadcrumb source metadata. */
    public @Nullable Tooltip buildNodeTooltipForPath(String pathId, @Nullable String sourceLabel) {
        LayoutNode ln = findLayoutNode(pathId);
        LayoutNode fullNode = fullRootLayout != null ? findInTree(fullRootLayout, pathId) : null;
        LayoutNode node = ln != null ? ln : fullNode;
        if (node == null || node.processNode == null) return null;

        Tooltip tip = Tooltip.create();
        var crumbs = getBreadcrumbs(pathId);
        var pathInfo = FlowChartTooltipComponent.builder();
        pathInfo.section(sourceLabel != null ? sourceLabel : "路径信息", 0xFF7D8791);
        pathInfo.kv("Path", 0xFF888888, pathId, 0xFF6E7882);
        pathInfo.kv("层级", 0xFF888888, "L" + node.depth, 0xFF6E7882);
        pathInfo.kv("深度", 0xFF888888, String.valueOf(Math.max(0, crumbs.size() - 1)), 0xFF6E7882);
        if (!crumbs.isEmpty()) {
            pathInfo.kv("节点", 0xFF888888, crumbs.get(crumbs.size() - 1).name(), 0xFF6E7882);
        }
        int branchCount = Math.max(0, node.originalChildCount);
        pathInfo.kv("分叉", 0xFF888888, String.valueOf(branchCount), 0xFF6E7882);
        if (branchCount > 1) {
            pathInfo.kv("支点", 0xFF888888, "是", 0xFF88C070);
        }
        pathInfo.divider();
        tip.add(pathInfo.build());
        appendNodeTooltip(tip, node.processNode);
        return tip;
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
        this.panX = node.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = node.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /**
     * Navigate the path-context breadcrumb to its corresponding subtree root and
     * keep the clicked node centered after the root switch.
     */
    public void navigatePathContextTo(String pathId) {
        if ("root".equals(pathId)) {
            resetToFullTree();
            centerOnNode("root");
            return;
        }
        focusSubgraph(pathId);
        centerOnNode(pathId);
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
        notifyTreeStructureChanged();
        this.zoom = keepZoom;
        this.targetZoom = keepZoom;
        centerOnNode(pathId);
    }

    /** Select and jump to a node from path breadcrumbs in a full-tree context. */
    public void selectAndJumpToPath(String pathId) {
        if (!"root".equals(currentRootPathId)) {
            resetToFullTree();
        }
        scrollToNode(pathId);
    }

    /** Expand ancestors and focus node, but do not change tracked node or root/subgraph context. */
    public void focusNodeWithoutCropping(String pathId) {
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
        rebuildTree(false);
        notifyTreeStructureChanged();
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
    //endregion

    //region Internals
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
            int maxX = fNodes.stream().mapToInt(n -> n.x + nodeW).max().orElse(0);
            int maxY = fNodes.stream().mapToInt(n -> n.y + nodeH).max().orElse(0);
            mmWorldW = Math.max(1, maxX - mmWorldMinX);
            mmWorldH = Math.max(1, maxY - mmWorldMinY);
        }
        this.layoutWidth = Math.max(500, mmWorldW + 200);
        this.layoutHeight = Math.max(500, (maxDepth + 1) * rowH + 200);
    }

    private int viewportW() {
        return showMaterialPanel ? width() - panelW - 2 : width();
    }

    // ═══════════════════ Data Setup ═══════════════════

    public TreeCanvasWidget setGraph(ProcessGraph graph) {
        this.graph = graph;
        this.showMaterialPanel = false;
        this.matSearchActive = false;
        this.matFilterText = "";
        this.matScrollY = 0;
        this.matScrollTargetY = 0;
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
                if (fullRootLayout.treeWidth <= maxTreeWidth) break;
                collapseLevel++;
            }
        } else {
            // User-initiated toggle: keep current collapseLevel, just rebuild once
            fullRootLayout = buildLayoutTree(targetNode, 0, "root");
            computeWidths(fullRootLayout);
        }

        int offsetX = Math.max(0, (Math.min(2000, maxTreeWidth) - fullRootLayout.treeWidth) / 2);
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
        this.layoutHeight = Math.max(500, (maxDepth + 1) * rowH + 200);

        // Compute minimap bounding box from actual visible nodes
        if (!fNodes.isEmpty()) {
            mmWorldMinX = fNodes.stream().mapToInt(n -> n.x).min().orElse(0);
            mmWorldMinY = fNodes.stream().mapToInt(n -> n.y).min().orElse(0);
            int maxX = fNodes.stream().mapToInt(n -> n.x + nodeW).max().orElse(0);
            int maxY = fNodes.stream().mapToInt(n -> n.y + nodeH).max().orElse(0);
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
            n.treeWidth = nodeW;
        } else {
            int w = 0;
            for (int i = 0; i < n.children.size(); i++) {
                computeWidths(n.children.get(i));
                w += n.children.get(i).treeWidth;
                if (i > 0) w += gapX;
            }
            n.treeWidth = Math.max(nodeW, w);
        }
    }

    private void assignPositions(LayoutNode n, int startX) {
        n.x = startX + n.treeWidth / 2 - nodeW / 2;
        n.y = n.depth * rowH + (rowH / 2 - nodeH / 2);

        if (!n.collapsed && !n.children.isEmpty()) {
            int childrenSum = n.children.stream().mapToInt(c -> c.treeWidth).sum()
                    + Math.max(0, n.children.size() - 1) * gapX;
            int offset = startX + (n.treeWidth - childrenSum) / 2;
            for (LayoutNode c : n.children) {
                assignPositions(c, offset);
                offset += c.treeWidth + gapX;
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
        notifyTreeStructureChanged();
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
                // Button center in world coords: x + nodeW/2, y + nodeH - 2 + 10
                double wx = ln.x + nodeW / 2.0;
                double wy = ln.y + nodeH - 2 + 10;
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
        notifyTreeStructureChanged();
        resetView();
    }

    private void notifyTreeStructureChanged() {
        if (onSubtreeChanged != null) onSubtreeChanged.run();
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
        zoom = Math.max(minZoom, Math.min(1.0f, Math.min(zx, zy)));
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
            h += secHdrH + 2;
            h += entry.getValue().size() * (entryH + entryGap);
            h += 6;
        }
        h += totalsH + 10;
        this.materialPanelContentH = h;
        updateFilteredContentH();
    }

    private void updateFilteredContentH() {
        if (matFilterText.isEmpty()) {
            matFilteredContentH = materialPanelContentH;
            sanitizeMaterialScroll();
            return;
        }
        String lower = matFilterText.toLowerCase();
        int h = 0;
        for (var entry : materialByCategory.entrySet()) {
            long matchCount = entry.getValue().stream()
                    .filter(e -> e.name().toLowerCase().contains(lower))
                    .count();
            if (matchCount > 0) {
                h += secHdrH + 2 + (int) (matchCount * (entryH + entryGap)) + 6;
            }
        }
        h += totalsH + 10;
        matFilteredContentH = h;
        sanitizeMaterialScroll();
    }

    private void sanitizeMaterialScroll() {
        int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
        if (Float.isNaN(matScrollY) || Float.isInfinite(matScrollY)) {
            matScrollY = 0;
        }
        if (Float.isNaN(matScrollTargetY) || Float.isInfinite(matScrollTargetY)) {
            matScrollTargetY = 0;
        }
        matScrollY = Math.max(0, Math.min(matScrollY, maxScroll));
        matScrollTargetY = Math.max(0, Math.min(matScrollTargetY, maxScroll));
    }

    // ═══════════════════ Animation ═══════════════════

    private void animateSmooth() {
        sanitizeMaterialScroll();
        if (Math.abs(targetZoom - zoom) >= zoomSnap) {
            zoom += (targetZoom - zoom) * zoomSmoothSpeed;
            if (Math.abs(targetZoom - zoom) < zoomSnap) zoom = targetZoom;
            panX = zoomFocusWX - zoomFocusSX / zoom;
            panY = zoomFocusWY - zoomFocusSY / zoom;
        }
        snapPanToPixelGrid();
        if (Math.abs(matScrollTargetY - matScrollY) > 0.5f) {
            matScrollY += (matScrollTargetY - matScrollY) * matScrollSmooth;
            if (Math.abs(matScrollTargetY - matScrollY) < 0.5f) matScrollY = matScrollTargetY;
        }
    }

    // ═══════════════════ Rendering ═══════════════════

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (graph == null || flatNodes.isEmpty()) {
            canvas.fill(0, 0, width(), height(), bgPanel);
            canvas.drawCenteredString("无配方数据", width() / 2, height() / 2 - 4, textWeak);
            return;
        }
        Font font = Minecraft.getInstance().font;
        animateSmooth();
        truncationFrameId++;
        truncationCache.clear();

        updateHover(mouseX, mouseY);
        updateTooltip(mouseX, mouseY);
        machineCycleTicks++;

        int vw = viewportW();
        canvas.fill(0, 0, vw, height(), bgPanel);

        Pos abs = absolutePos();
        canvas.withClip(abs.x(), abs.y(), vw, height(), () -> {
            drawLayerOverlay(canvas, font);
            canvas.pushTransform();
            canvas.scale(zoom);
            canvas.translate((float) -panX, (float) -panY);

            drawGrid(canvas);
            drawLayerBands(canvas, font);
            drawConnections(canvas);
            for (LayoutNode ln : flatNodes) {
                double nRight = ln.x + nodeW, nBot = ln.y + nodeH;
                double visMinX = panX, visMinY = panY;
                double visMaxX = panX + vw / zoom, visMaxY = panY + height() / zoom;
                if (nRight < visMinX || ln.x > visMaxX || nBot < visMinY || ln.y > visMaxY) continue;
                drawNodeCard(canvas, font, ln, partialTicks);
            }
            flushDeferredIcons(canvas);
            canvas.popTransform();
        });

        drawMinimap(canvas, mouseX, mouseY);
        if (showMaterialPanel) drawMaterialPanel(canvas, font, mouseX, mouseY, partialTicks);
    }

    // ═══════════════════ Grid & Layer Bands ═══════════════════

    private void drawGrid(SceneCanvas canvas) {
        // Intentionally left blank: user requested removing background dot grid.
    }

    private void drawLayerBands(SceneCanvas canvas, Font font) {
        double visMinY = panY, visMaxY = panY + height() / zoom;
        int bandX = (int) (panX - 200);
        int bandW = (int) (viewportW() / zoom + 400);

        for (int i = 0; i <= maxDepth; i++) {
            int bandTop = i * rowH;
            int bandBot = bandTop + rowH;
            if (bandBot < visMinY || bandTop > visMaxY) continue;

            boolean isHighlighted = (i == highlightedLayer);

            // Alternating band background (even layers slightly lighter)
            if (isHighlighted) {
                canvas.fill(bandX, bandTop, bandW, rowH, withAlpha(green, 0x30));
            } else if (i % 2 == 0) {
                canvas.fill(bandX, bandTop, bandW, rowH, 0x30FFFFFF);
            }

            // Horizontal divider line at top of layer
            canvas.fill(bandX, bandTop, bandW, 1, isHighlighted ? withAlpha(green, 0xD8) : layerLine);
            if (isHighlighted) {
                canvas.fill(bandX, bandBot - 1, bandW, 1, withAlpha(green, 0xD8));
            }

        }
    }

    private void drawLayerOverlay(SceneCanvas canvas, Font font) {
        int labelX = 23;
        // Watermark scale is reduced by ~0.15 ratio to keep it lighter/smaller at all zoom levels.
        float bgScale = Math.max(0.78f, Math.min(1.03f, 0.87f + (zoom - 0.7f) * 0.06f));
        double visMinWX = panX;
        double visMaxWX = panX + viewportW() / zoom;
        int worldRepeatAnchorX = 320;
        int worldRepeatSpacing = 500;

        for (int i = 0; i <= maxDepth; i++) {
            int bandTopWorld = i * rowH;
            int bandBottomWorld = bandTopWorld + rowH;
            int bandTop = (int) Math.round((bandTopWorld - panY) * zoom);
            int bandBottom = (int) Math.round((bandBottomWorld - panY) * zoom);
            if (bandBottom < 0 || bandTop > height()) continue;

            boolean isHighlighted = (i == highlightedLayer);
            String layerLabel = "LAYER " + i;
            int scaledLabelW = Math.max(1, (int) Math.round(font.width(layerLabel) * bgScale));
            int bgLabelY = bandTop + Math.max(8, (bandBottom - bandTop) / 2 - 8);
            int bgLabelColor = isHighlighted ? withAlpha(0xFF7F9589, 0x90) : withAlpha(0xFF7A7A7A, 0x58);

            int firstRepeatIndex = (int) Math.floor((visMinWX - worldRepeatAnchorX) / worldRepeatSpacing) - 1;
            int lastRepeatIndex = (int) Math.ceil((visMaxWX - worldRepeatAnchorX) / worldRepeatSpacing) + 1;
            for (int repeatIndex = firstRepeatIndex; repeatIndex <= lastRepeatIndex; repeatIndex++) {
                double worldX = worldRepeatAnchorX + (double) repeatIndex * worldRepeatSpacing;
                int x = (int) Math.round((worldX - panX) * zoom);
                // Only cull labels that are fully outside. Partially visible labels should
                // still render and let clip do natural edge trimming.
                if (x + scaledLabelW < labelX + 2 || x > viewportW() - 8) continue;
                canvas.pushTransform();
                canvas.translate(x, bgLabelY);
                canvas.scale(bgScale);
                canvas.drawString(layerLabel, 0, 0, bgLabelColor, false);
                canvas.popTransform();
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
            int startX = link.source.x + nodeW / 2;
            int startY = link.source.y + nodeH;
            int endX = link.target.x + nodeW / 2;
            int endY = link.target.y;
            int midY = (startY + endY) / 2;

            boolean tracked = isNodeInTrackedPath(link.target.pathId);
            boolean isCatalyst = link.target.processNode != null
                    && link.target.processNode.inputs().stream().anyMatch(MaterialSlot::isCatalyst);

            int color = tracked ? linkTracked : (isCatalyst ? linkCatalyst : linkDefault);
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

        }
    }

    private int nodeAccent(LayoutNode ln) {
        if (ln.pathId.equals(trackedNodeId)) return linkTracked;
        if (ln.depth == 0) return dotClrRoot;
        ProcessNode pn = ln.processNode;
        if (pn == null) return dotClr;
        if (pn.inputs().stream().anyMatch(MaterialSlot::isCatalyst)) return 0xFF95AED4;
        if (!pn.outputs().isEmpty()) {
            return switch (pn.outputs().get(0).type()) {
                case ITEM -> 0xFFA8BAD8;
                case FLUID -> 0xFF8EC3DC;
                case ENERGY -> 0xFFB4A3E8;
                case GAS -> 0xFF8CC9BF;
                case CATALYST -> 0xFFC1B3E8;
            };
        }
        return dotClr;
    }

    private int minimapNodeColor(LayoutNode ln) {
        if (ln.pathId.equals(trackedNodeId)) return linkTracked;
        if (ln.depth == 0) return withAlpha(dotClrRoot, 0xE0);
        return withAlpha(0xFFA1ACBA, 0xD2);
    }

    // ═══════════════════ Minimap ═══════════════════

    private int minimapX() {
        return viewportW() - mmW - mmPad;
    }

    private int minimapY() {
        return mmPad;
    }

    private record MinimapProjection(int contentX, int contentY, int contentW, int contentH,
                                     double scale, double drawX, double drawY, double drawW, double drawH) {}

    private static MinimapViewportRect clampMinimapViewportRect(int contentX, int contentY, int contentW, int contentH,
                                                                int rawX, int rawY, int rawW, int rawH) {
        if (contentW <= 0 || contentH <= 0) {
            return new MinimapViewportRect(contentX, contentY, 1, 1);
        }
        int vpW = Math.max(1, Math.min(rawW, contentW));
        int vpH = Math.max(1, Math.min(rawH, contentH));
        int minX = contentX;
        int minY = contentY;
        int maxX = contentX + contentW - vpW;
        int maxY = contentY + contentH - vpH;
        int x = Math.max(minX, Math.min(rawX, maxX));
        int y = Math.max(minY, Math.min(rawY, maxY));
        int right = Math.min(contentX + contentW, x + vpW);
        int bottom = Math.min(contentY + contentH, y + vpH);
        return new MinimapViewportRect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private @Nullable MinimapProjection minimapProjection() {
        if (mmWorldW <= 0 || mmWorldH <= 0) return null;
        int mmX = minimapX();
        int mmY = minimapY();
        int contentX = mmX + mmContentPad;
        int contentY = mmY + mmContentPad;
        int contentH = mmH - mmContentPad * 2;
        int contentW = mmW - mmContentPad * 2;
        if (contentW <= 0 || contentH <= 0) return null;
        double fitScale = Math.min((double) contentW / mmWorldW, (double) contentH / mmWorldH);
        double scale = fitScale * mmScaleShrink;
        if (scale <= 0) return null;
        double drawW = mmWorldW * scale;
        double drawH = mmWorldH * scale;
        double drawX = contentX + (contentW - drawW) * 0.5;
        double drawY = contentY + (contentH - drawH) * 0.5;
        return new MinimapProjection(contentX, contentY, contentW, contentH, scale, drawX, drawY, drawW, drawH);
    }

    private void drawMinimap(SceneCanvas canvas, int mouseX, int mouseY) {
        if (flatNodes.isEmpty() || mmWorldW <= 0 || mmWorldH <= 0) return;
        MinimapProjection proj = minimapProjection();
        if (proj == null) return;

        int contentX = proj.contentX;
        int contentY = proj.contentY;
        int contentH = proj.contentH;
        int contentW = proj.contentW;
        // Keep minimap with a single background layer (no extra chrome frame).
        canvas.texture(Background.inset, contentX, contentY, contentW, contentH);

        double sx = proj.scale;
        double sy = proj.scale;
        double ox = proj.drawX;
        double oy = proj.drawY;

        // Draw connections first (behind nodes), right-angle routing
        int linkColor = 0xFF6F747B;
        int linkTracked = 0xFF9BA8B6;
        for (LayoutLink link : flatLinks) {
            int lsx = (int) (ox + (link.source.x + nodeW / 2.0 - mmWorldMinX) * sx);
            int lsy = (int) (oy + (link.source.y + nodeH - mmWorldMinY) * sy);
            int lex = (int) (ox + (link.target.x + nodeW / 2.0 - mmWorldMinX) * sx);
            int ley = (int) (oy + (link.target.y - mmWorldMinY) * sy);
            boolean tracked = isNodeInTrackedPath(link.target.pathId);
            int c = tracked ? linkTracked : linkColor;
            int midY = (lsy + ley) / 2;
            canvas.fill(lsx, lsy, 1, midY - lsy, c);
            canvas.fill(Math.min(lsx, lex), midY, Math.abs(lex - lsx) + 1, 1, c);
            canvas.fill(lex, midY, 1, ley - midY, c);
        }

        // Draw nodes
        for (LayoutNode ln : flatNodes) {
            int rx = (int) (ox + (ln.x - mmWorldMinX) * sx);
            int ry = (int) (oy + (ln.y - mmWorldMinY) * sy);
            int rw = Math.max(1, (int) Math.round(nodeW * sx));
            int rh = Math.max(1, (int) Math.round(nodeH * sy));
            int color = minimapNodeColor(ln);
            canvas.fill(rx, ry, rw, rh, color);
        }

        // Viewport indicator (pan is in world-space, offset by bounding box origin)
        int rawVpW = Math.max(4, (int) Math.round((viewportW() / zoom) * sx));
        int rawVpH = Math.max(4, (int) Math.round((height() / zoom) * sy));
        int rawVpX = (int) Math.round(ox + (panX - mmWorldMinX) * sx);
        int rawVpY = (int) Math.round(oy + (panY - mmWorldMinY) * sy);
        MinimapViewportRect vp = clampMinimapViewportRect(contentX, contentY, contentW, contentH,
                rawVpX, rawVpY, rawVpW, rawVpH);
        canvas.fill(vp.x() + 1, vp.y() + 1, Math.max(1, vp.w() - 2), Math.max(1, vp.h() - 2), 0x1AFFFFFF);
        canvas.fill(vp.x(), vp.y(), vp.w(), 1, mmVp);
        canvas.fill(vp.x(), vp.y() + vp.h() - 1, vp.w(), 1, mmVp);
        canvas.fill(vp.x(), vp.y(), 1, vp.h(), mmVp);
        canvas.fill(vp.x() + vp.w() - 1, vp.y(), 1, vp.h(), mmVp);
    }

    private boolean isInMinimap(double mx, double my) {
        int mmX = minimapX();
        int mmY = minimapY();
        return mx >= mmX && mx <= mmX + mmW && my >= mmY && my <= mmY + mmH;
    }

    private void minimapClickToPan(double mx, double my) {
        MinimapProjection proj = minimapProjection();
        if (proj == null) return;
        double clampedMx = Math.max(proj.drawX, Math.min(mx, proj.drawX + proj.drawW));
        double clampedMy = Math.max(proj.drawY, Math.min(my, proj.drawY + proj.drawH));
        double worldX = (clampedMx - proj.drawX) / proj.scale + mmWorldMinX;
        double worldY = (clampedMy - proj.drawY) / proj.scale + mmWorldMinY;
        double vpWorldW = viewportW() / zoom, vpWorldH = height() / zoom;
        panX = Math.max(0, Math.min(worldX - vpWorldW / 2.0, layoutWidth - vpWorldW));
        panY = Math.max(0, Math.min(worldY - vpWorldH / 2.0, layoutHeight - vpWorldH));
        snapPanToPixelGrid();
    }

    private void snapPanToPixelGrid() {
        if (zoom <= 0f || Double.isNaN(panX) || Double.isNaN(panY)) return;
        double snappedX = Math.round(panX * zoom) / zoom;
        double snappedY = Math.round(panY * zoom) / zoom;
        panX = snappedX;
        panY = snappedY;
    }

    // ═══════════════════ Node Card ═══════════════════

    private void drawNodeCard(SceneCanvas canvas, Font font, LayoutNode ln, float pt) {
        int x = ln.x, y = ln.y;
        ProcessNode pn = ln.processNode;
        if (pn == null) return;

        boolean hovered = isNodeHovered(ln.pathId);
        boolean tracked = ln.pathId.equals(trackedNodeId);
        boolean externallySelected = Objects.equals(ln.pathId, externalSelectedNodeId);
        boolean isCatalystNode = pn.inputs().stream().anyMatch(MaterialSlot::isCatalyst);
        boolean isLeaf = ln.originalChildCount == 0;
        int accent = nodeAccent(ln);

        // Very low zoom: simplified rectangle
        if (zoom < detailLodZoom) {
            canvas.texture(hovered ? Background.Border.light : Background.frame, x, y, nodeW, nodeH);
            canvas.texture(Background.flat, x + 2, y + 2, nodeW - 4, nodeH - 4);
            canvas.texture(Background.Sign.bar, x + 2, y + 2, nodeW - 4, 2);
            canvas.fill(x + 2, y + 3, nodeW - 4, 1, accent);
            return;
        }

        canvas.texture(hovered ? Background.Border.light : Background.frame, x, y, nodeW, nodeH);
        canvas.texture(Background.flat, x + 2, y + 2, nodeW - 4, nodeH - 4);
        if (isCatalystNode) {
            canvas.texture(Sign.Group.catalyst, x + 5, y + 5, 3, nodeH - 10);
        }

        // Tracked ring + glow
        if (tracked) {
            drawOutline(canvas, x - 1, y - 1, nodeW + 2, nodeH + 2, trackedRing);
        }
        if (externallySelected) {
            drawOutline(canvas, x - 2, y - 2, nodeW + 4, nodeH + 4, externalSelectedRing);
        }

        if (isLeaf) {
            drawLeafCard(canvas, font, ln, x, y, pt);
        } else {
            drawStepCard(canvas, font, ln, x, y, pt);
        }

        // Collapse/expand button at bottom center
        if (ln.originalChildCount > 0) {
            int btnX = x + nodeW / 2 - 10;
            int btnY = y + nodeH - 2;
            canvas.texture(Background.flat, btnX, btnY, 20, 18);
            canvas.drawCenteredString(ln.collapsed ? "+" : "-", btnX + 10, btnY + 5, cardMetaText);

            if (ln.collapsed) {
                String badge = "+ " + ln.originalChildCount + " Inputs";
                int bw = font.width(badge) + 8;
                int bx = x + nodeW / 2 - bw / 2;
                int by = btnY + 22;
                canvas.drawString(badge, bx + 2, by + 3, cardMetaText, false);
            }
        }
    }

    private void drawLeafCard(SceneCanvas canvas, Font font, LayoutNode ln, int x, int y, float pt) {
        ProcessNode pn = ln.processNode;
        int pad = 10;
        int iconSz = 18;
        int iconX = x + pad;
        int iconY = y + 31;
        canvas.texture(Background.Slot.dark, iconX - 1, iconY - 1, iconSz + 2, iconSz + 2);

        if (!pn.outputs().isEmpty()) {
            MaterialSlot slot = pn.outputs().get(0);
            drawSlotIcon(canvas, slot, iconX, iconY, iconSz / 16.0f, pt);
        }

        int textX = iconX + iconSz + 10;
        int textW = nodeW - textX + x - pad;
        canvas.drawString(cachedTruncate(pn.machine(), textW, font), textX, y + 12, cardMetaText, false);
        canvas.drawString(cachedTruncate(pn.name(), textW, font), textX, y + 25, cardTitleText, false);

        String amtStr = "+" + fmtNum(pn.outputs().isEmpty() ? 0 : pn.outputs().get(0).amount());
        canvas.drawString(cachedTruncate(amtStr, textW, font), textX, y + 41, green, false);
        canvas.drawString(cachedTruncate(pn.durationTicks() + "t", textW, font), textX, y + 56, cardMinorText, false);
    }

    private void drawStepCard(SceneCanvas canvas, Font font, LayoutNode ln, int x, int y, float pt) {
        ProcessNode pn = ln.processNode;
        MachineInfo displayMachine = getDisplayMachine(pn);
        int accent = nodeAccent(ln);
        int headerX = x + 5;
        int headerY = y + 8;
        int headerW = nodeW - 10;
        int machineIconX = x + 11;
        int machineIconY = y + 11;
        int[] focusBounds = focusButtonBounds(x, y);
        String machineName = buildMachineLabel(pn, displayMachine);
        int nameX = machineIconX + 20;
        int nameMaxW = focusBounds[0] - nameX - 5;

        canvas.texture(Background.Sign.bar, x + 4, y + 4, nodeW - 8, 3);
        canvas.fill(x + 4, y + 4, nodeW - 8, 1, accent);
        canvas.texture(Background.dark, headerX, headerY, headerW, 22);
        drawMachineIcon(canvas, displayMachine, machineIconX, machineIconY);
        canvas.drawString(cachedTruncate(machineName, nameMaxW, font), nameX, y + 12, textWhite, true);
        canvas.drawString(cachedTruncate(pn.name(), nameMaxW, font), nameX, y + 22, 0xFFD9D9D9, true);

        if (ln.originalChildCount > 0) {
            drawFocusButton(canvas, focusBounds, isNodeHovered(ln.pathId));
        }

        int separatorY = headerY + 22 + stepSectionGap / 2;
        canvas.fill(x + 10, separatorY, nodeW - 20, 1, 0xFF8F959C);
        canvas.fill(x + 10, separatorY + 1, nodeW - 20, 1, 0x40FFFFFF);

        int bodyY = separatorY + stepSectionGap;
        int itemIconSz = 18;
        int itemX = x + 11;
        int itemY = bodyY + 1;
        canvas.texture(Background.Slot.dark, itemX - 1, itemY - 1, itemIconSz + 2, itemIconSz + 2);
        if (!pn.outputs().isEmpty()) {
            MaterialSlot outSlot = pn.outputs().get(0);
            drawSlotIcon(canvas, outSlot, itemX, itemY, itemIconSz / 16.0f, pt);
        }

        int outTextX = itemX + itemIconSz + 10;
        int outTextW = nodeW - (outTextX - x) - 10;
        if (!pn.outputs().isEmpty()) {
            MaterialSlot out = pn.outputs().get(0);
            canvas.drawString(cachedTruncate(out.materialName(), outTextW, font), outTextX, bodyY + 2, cardTitleText, false);
            String amountText = "+" + fmtNum(out.amount());
            if (out.chance() < 1.0) {
                amountText += "  " + (int) Math.round(out.chance() * 100) + "%";
            }
            canvas.drawString(cachedTruncate(amountText, outTextW, font), outTextX, bodyY + 13, green, false);
        }

        List<String> metaLines = buildStepMetaLines(pn);
        for (int i = 0; i < Math.min(2, metaLines.size()); i++) {
            canvas.drawString(cachedTruncate(metaLines.get(i), outTextW, font),
                    outTextX, bodyY + 25 + i * 10, cardMinorText, false);
        }
    }

    static int[] focusButtonBounds(int nodeX, int nodeY) {
        return new int[] { nodeX + nodeW - focusBtnW - 6, nodeY + 6, focusBtnW, focusBtnH };
    }

    static List<String> buildStepMetaLines(ProcessNode node) {
        List<String> lines = new ArrayList<>(2);
        lines.add(node.tierName() + " · " + node.durationFormatted());

        List<String> detail = new ArrayList<>(3);
        if (node.euPerTick() > 0) {
            detail.add(node.euFormatted());
        }
        if (node.machineOptions().size() > 1) {
            detail.add("+" + (node.machineOptions().size() - 1) + " alt");
        }
        if (node.outputs().size() > 1) {
            detail.add(node.outputs().size() + " outputs");
        }
        if (!detail.isEmpty()) {
            lines.add(String.join(" · ", detail));
        }
        return lines;
    }

    private static boolean containsWorldPoint(int[] bounds, double wx, double wy) {
        return wx >= bounds[0] && wx <= bounds[0] + bounds[2]
                && wy >= bounds[1] && wy <= bounds[1] + bounds[3];
    }

    private static String buildMachineLabel(ProcessNode node, @Nullable MachineInfo displayMachine) {
        String machineName = displayMachine != null ? displayMachine.name() : node.machine();
        if (node.machineOptions().size() > 1) {
            machineName += " +" + (node.machineOptions().size() - 1);
        }
        return machineName;
    }

    private void drawFocusButton(SceneCanvas canvas, int[] bounds, boolean hovered) {
        var bg = hovered ? Background.Outlined.inset : Background.flat;
        canvas.texture(bg, bounds[0], bounds[1], bounds[2], bounds[3]);
        canvas.texture(Icon.Calc.Path.branch, bounds[0] + 3, bounds[1] + 3, 12, 12);
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
            canvas.texture(Background.Slot.dark, ix, iy, sz, sz);
        }
    }

    private void drawMachineIcon(SceneCanvas canvas, @Nullable MachineInfo machine, int ix, int iy) {
        canvas.texture(Background.Slot.dark, ix - 1, iy - 1, 18, 18);
        ItemStack stack = machine == null ? ItemStack.EMPTY : machine.icon();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix + 1, iy + 1, 0.875f));
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

    // ═══════════════════ Material Panel ═══════════════════

    private static final int searchBarH = 16;

    private int panelContentVisibleH() {
        int contentTop = 26 + (matSearchActive ? searchBarH : 0);
        return Math.max(0, height() - contentTop - 4);
    }

    private boolean isInMaterialPanel(double mx, double my) {
        if (!showMaterialPanel) return false;
        int px = width() - panelW;
        return mx >= px && mx <= width() && my >= 0 && my <= height();
    }

    private boolean isOnScrollbarThumb(double mx, double my) {
        if (!showMaterialPanel) return false;
        int maxScroll = Math.max(0, matFilteredContentH - panelContentVisibleH());
        if (maxScroll <= 0) return false;
        int px = width() - panelW;
        int contentY = 26 + (matSearchActive ? searchBarH : 0);
        int visibleH = panelContentVisibleH();
        int sbX = px + panelW - scrollbarW - 3;
        int sbH = visibleH - 2;
        int thumbH = Math.max(16, (int) ((float) visibleH / materialPanelContentH * sbH));
        float progress = matScrollTargetY / maxScroll;
        int thumbY = contentY + 1 + (int) (progress * (sbH - thumbH));
        return mx >= sbX && mx <= sbX + scrollbarW && my >= thumbY && my <= thumbY + thumbH;
    }

    private void drawMaterialPanel(SceneCanvas canvas, Font font,
                                    int mouseX, int mouseY, float pt) {
        sanitizeMaterialScroll();
        int px = width() - panelW;
        int ph = height();
        canvas.shadow(px - 2, 0, panelW + 2, ph, 4, 2, 8, 0x60000000);
        canvas.texture(Background.frame, px, 0, panelW, ph);
        canvas.texture(Background.inset, px + 4, 4, panelW - 8, ph - 8);

        int headerH = 20;
        canvas.texture(Background.Outlined.inset, px + 4, 4, panelW - 8, headerH);
        canvas.texture(Sign.Group.item, px + 6, 6, 3, headerH - 4);
        canvas.drawString("材料清单", px + 14, 11, textPrimary, false);
        // Search icon
        int searchIconX = px + panelW - 28;
        boolean searchHover = mouseX >= searchIconX && mouseX <= searchIconX + 14
                && mouseY >= 3 && mouseY <= 17;
        canvas.texture(matSearchActive || searchHover ? Icon.searchSelected : Icon.search,
                searchIconX, 9, 10, 10);
        // Item count badge
        int totalItems = materialByCategory.values().stream().mapToInt(List::size).sum();
        String badge = String.valueOf(totalItems);
        int badgeW = font.width(badge) + 6;
        int badgeX = searchIconX - badgeW - 4;
        canvas.texture(Background.Outlined.inset, badgeX, 8, badgeW, 12);
        canvas.drawCenteredString(badge, badgeX + badgeW / 2, 11, textPrimary);

        // Search bar (below header, when active)
        int searchBarBottom = 4 + headerH;
        if (matSearchActive) {
            searchBarBottom = 4 + headerH + searchBarH;
            canvas.texture(Background.inset, px + 4, 4 + headerH + 2, panelW - 8, searchBarH - 2);
            // Search text field
            int sfX = px + 8, sfY = 4 + headerH + 4, sfW = panelW - 16, sfH = searchBarH - 6;
            canvas.texture(Background.Outlined.inset, sfX, sfY, sfW, sfH);
            if (matFilterText.isEmpty()) {
                canvas.drawString("搜索物料...", sfX + 4, sfY + 2, textWeak, false);
            } else {
                String displayText = matFilterText;
                int maxTextW = sfW - 8;
                while (font.width(displayText) > maxTextW && displayText.length() > 1) {
                    displayText = displayText.substring(1);
                }
                canvas.drawString(displayText, sfX + 4, sfY + 2, textPrimary, false);
                // Cursor blink
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    int cursorX = sfX + 4 + font.width(displayText);
                    canvas.fill(cursorX, sfY + 2, 1, sfH - 4, textPrimary);
                }
            }
        }

        int contentY = searchBarBottom + 2;
        int visibleH = panelContentVisibleH();
        stickyHeaderCat = null;

        Pos absP = absolutePos();
        canvas.pushClip(absP.x() + px + 4, absP.y() + contentY, panelW - 8, visibleH);
        canvas.pushTransform();
        canvas.translate(px + panelPad, contentY - matScrollY);
        renderMaterialContent(canvas, font, mouseX - px - panelPad, (int) (mouseY - contentY + matScrollY), pt,
                matScrollY, matScrollY + visibleH);
        flushDeferredIcons(canvas);
        canvas.popTransform();
        canvas.popClip();

        // Draw sticky category header ON TOP of the clipped content, in widget-local coords
        boolean hasScrollbar = matFilteredContentH > visibleH;
        int innerW = panelW - 2 * panelPad - (hasScrollbar ? scrollbarW + 4 : 0);
        if (stickyHeaderCat != null) {
            int stickyDrawY = contentY + stickyHeaderOffsetY;
            canvas.texture(Background.inset, px + panelPad, stickyDrawY - 1, innerW, secHdrH + 3);
            drawCategoryHeader(canvas, font, stickyHeaderCat, stickyHeaderCount,
                    px + panelPad, stickyDrawY, innerW);
        }

        int maxScroll = Math.max(0, matFilteredContentH - visibleH);
        if (maxScroll > 0) {
            int sbX = px + panelW - scrollbarW - 2;
            int sbY = contentY + 1;
            int sbH = visibleH - 2;
            canvas.texture(Background.inset, sbX, sbY, scrollbarW, sbH);
            int thumbH = Math.max(16, (int) ((float) visibleH / matFilteredContentH * sbH));
            float progress = maxScroll > 0 ? matScrollY / maxScroll : 0;
            int thumbY = sbY + (int) (progress * (sbH - thumbH));
            canvas.texture(Icon.scrollBar, sbX + 1, thumbY, scrollbarW - 2, thumbH);
        }
    }

    private void renderMaterialContent(SceneCanvas canvas, Font font,
                                        int mouseX, int mouseY, float pt,
                                        float visMinY, float visMaxY) {
        int drawY = 0;
        boolean hasScrollbar = matFilteredContentH > (int) (visMaxY - visMinY);
        int innerW = panelW - 2 * panelPad - (hasScrollbar ? scrollbarW + 4 : 0);
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
            if (drawY + secHdrH >= visMinY && drawY <= visMaxY) {
                drawCategoryHeader(canvas, font, cat, entries.size(), 0, drawY, innerW);
            }
            drawY += secHdrH + 2;
            for (MaterialEntry entry : entries) {
                if (drawY + entryH >= visMinY && drawY <= visMaxY)
                    drawMaterialEntry(canvas, font, entry, 0, drawY, innerW, pt, mouseX, mouseY);
                drawY += entryH + entryGap;
            }
            int sectionEnd = drawY;
            drawY += 6;

            // Track sticky header: category whose header scrolled past the top
            if (headerY < visMinY && sectionEnd > visMinY) {
                stickyHeaderCat = cat;
                stickyHeaderCount = entries.size();
                // Calculate offset for "push up" effect when next section approaches
                int distToEnd = (int) (sectionEnd - visMinY);
                stickyHeaderOffsetY = distToEnd < secHdrH + 6 ? distToEnd - secHdrH - 6 : 0;
            }
        }

        drawY += 2;
        if (drawY + totalsH + 6 >= visMinY && drawY <= visMaxY) {
            canvas.texture(Background.Sign.bar, 4, drawY, Math.max(16, innerW - 8), 2);
            drawY += 6;
            canvas.texture(Background.inset, 0, drawY, innerW, totalsH);
            int txL = 6, txR = innerW - 8;
            int r0 = drawY + 6, r1 = drawY + 20, r2 = drawY + 36;
            canvas.texture(Icon.Sign.Energy.time, txL, r0, 9, 9);
            canvas.drawString("总时间", txL + 12, r0, textWeak, false);
            String timeStr = formatDuration(totalTimeTicks);
            canvas.drawString(timeStr, txR - font.width(timeStr), r0, textPrimary, false);
            canvas.texture(Icon.Sign.Energy.eu, txL, r1, 9, 9);
            canvas.drawString("总能量", txL + 12, r1, textWeak, false);
            String euStr = fmtNum(totalEU) + " EU";
            canvas.drawString(euStr, txR - font.width(euStr), r1, orange, false);
            canvas.texture(Icon.Sign.Amount.available, txL + 1, r2 + 3, 5, 5);
            canvas.drawString("步骤数", txL + 10, r2, textWeak, false);
            String stepStr = String.valueOf(flatNodes.size());
            canvas.drawString(stepStr, txR - font.width(stepStr), r2, green, false);
        }
    }

    private static dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture categoryMarker(MatCategory cat) {
        return switch (cat) {
            case BYPRODUCT -> Sign.Group.fluid;
            case CATALYST -> Sign.Group.catalyst;
            case EXTERNAL_INPUT, INTERMEDIATE -> Sign.Group.item;
        };
    }

    private void drawCategoryHeader(SceneCanvas canvas, Font font,
                                     MatCategory cat, int count, int x, int y, int w) {
        canvas.texture(Background.Outlined.inset, x, y, w, secHdrH);
        canvas.texture(categoryMarker(cat), x + 2, y + 2, 3, secHdrH - 4);
        canvas.drawString(cat.label, x + 8, y + 4, textSecondary, false);
        String countStr = String.valueOf(count);
        int cw = font.width(countStr);
        canvas.drawString(countStr, x + w - cw - 4, y + 4, textSecondary, false);
    }

    private void drawMaterialEntry(SceneCanvas canvas, Font font,
                                    MaterialEntry entry, int x, int y, int w,
                                    float pt, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + entryH;
        if (hovered) hoveredMatName = entry.name();

        canvas.texture(hovered ? Background.Border.light : Background.Slot.dark, x, y, w, boxH);
        canvas.texture(categoryMarker(entry.category()), x + 1, y + 1, 3, boxH - 2);

        int pad = 3;
        int slotX = x + pad, slotY = y + pad;
        canvas.texture(Background.Slot.dark, slotX, slotY, iconSlotSz, iconSlotSz);
        int iconX = slotX + (iconSlotSz - panelIconSz) / 2;
        int iconY = slotY + (iconSlotSz - panelIconSz) / 2;
        drawPanelSlotIcon(canvas, entry.refSlot(), iconX, iconY, pt);

        int nameX = slotX + iconSlotSz + 4;
        int nameW = w - (nameX - x) - pad;
        String name = cachedTruncate(entry.name(), nameW, font);
        canvas.drawString(name, nameX, y + 3, textPrimary, false);

        int indX = slotX + iconSlotSz + 2;
        int row1Y = y + 18;
        int row2Y = y + 29;
        int textX = indX + indCellSz + 2;
        int textW = w - (textX - x) - pad;

        canvas.texture(Background.inset, indX, row1Y, indCellSz, indCellSz);
        canvas.texture(entry.category() == MatCategory.CATALYST ? Icon.Sign.Amount.catalyst : Icon.Sign.Amount.available,
                indX + 2, row1Y + 2, 6, 6);
        canvas.texture(Background.inset, textX, row1Y, textW, indCellSz);
        String row1Value = switch (entry.category()) {
            case EXTERNAL_INPUT -> "0";
            case INTERMEDIATE -> fmtNum(entry.produced());
            case BYPRODUCT -> fmtNum(entry.produced());
            case CATALYST -> fmtNum(entry.needed());
        };
        canvas.drawString(cachedTruncate(row1Value, textW - 6, font), textX + 3, row1Y + 1,
                entry.category() == MatCategory.CATALYST ? purple : green, false);

        canvas.texture(Background.inset, indX, row2Y, indCellSz, indCellSz);
        canvas.texture(Icon.Sign.Amount.missing, indX + 2, row2Y + 2, 6, 6);
        canvas.texture(Background.inset, textX, row2Y, textW, indCellSz);
        String row2Value = switch (entry.category()) {
            case EXTERNAL_INPUT -> fmtNum(entry.needed());
            case INTERMEDIATE -> entry.missing() > 0 ? fmtNum(entry.missing()) : "0";
            case BYPRODUCT -> entry.chance() < 1.0 ? (int) (entry.chance() * 100) + "%" : "0";
            case CATALYST -> "0";
        };
        canvas.drawString(cachedTruncate(row2Value, textW - 6, font), textX + 3, row2Y + 1,
                entry.category() == MatCategory.BYPRODUCT ? orange : red, false);

        int barY = y + boxH;
        int barX = x;
        int barW = w;
        canvas.texture(Background.inset, barX, barY, barW, barH + 1);
        int barInnerW = Math.max(0, barW - 2);
        if (barInnerW <= 0) return;
        switch (entry.category()) {
            case EXTERNAL_INPUT -> canvas.fill(barX + 1, barY, barInnerW, barH, red);
            case INTERMEDIATE -> {
                long total = Math.max(entry.needed(), entry.produced());
                if (total > 0) {
                    int greenW = (int) (barInnerW * Math.min(1.0, (double) entry.produced() / total));
                    if (greenW > 0) canvas.fill(barX + 1, barY, greenW, barH, green);
                    if (entry.missing() > 0) {
                        int redW = (int) (barInnerW * ((double) entry.missing() / total));
                        canvas.fill(barX + 1 + greenW, barY, Math.max(1, redW), barH, red);
                    }
                }
            }
            case BYPRODUCT -> canvas.fill(barX + 1, barY, barInnerW, barH,
                    withAlpha(orange, entry.chance() < 1.0 ? 0x88 : 0xBB));
            case CATALYST -> canvas.fill(barX + 1, barY, barInnerW, barH, withAlpha(purple, 0x88));
        }
    }

    private void drawPanelSlotIcon(SceneCanvas canvas, MaterialSlot slot, int ix, int iy, float pt) {
        float iconScale = panelIconSz / 16.0f;
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix, iy, iconScale));
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            deferredFluids.add(new DeferredFluid(slot.fluidStack(), ix, iy, iconScale, pt));
        } else {
            int c = slot.type().color;
            canvas.texture(Background.Slot.dark, ix, iy, panelIconSz, panelIconSz);
            canvas.drawCenteredString(slot.type().name().substring(0, 1),
                    ix + panelIconSz / 2, iy + (panelIconSz - 9) / 2, c);
        }
    }

    // ═══════════════════ Hover & Hit Testing ═══════════════════

    private void updateHover(int mouseX, int mouseY) {
        hoveredNodeId = null;
        double lx = mouseX, ly = mouseY;
        if (lx < 0 || ly < 0 || lx > width() || ly > height()) return;
        if (showMaterialPanel && isInMaterialPanel(lx, ly)) return;
        LayoutNode found = findNodeWithButtonAt(lx, ly);
        if (found != null) hoveredNodeId = found.pathId;
    }

    private boolean isNodeHovered(String pathId) {
        return Objects.equals(pathId, hoveredNodeId) || Objects.equals(pathId, externalHoveredNodeId);
    }

    private @Nullable LayoutNode findNodeAt(double screenX, double screenY) {
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        for (LayoutNode ln : flatNodes) {
            if (wx >= ln.x && wx <= ln.x + nodeW && wy >= ln.y && wy <= ln.y + nodeH) {
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
            // Button area: x centered, y from nodeH-2 to nodeH+18
            double btnX = ln.x + nodeW / 2.0 - 10;
            double btnY = ln.y + nodeH - 2;
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
        return opts.get((int) ((machineCycleTicks / cycleIntervalTicks) % opts.size()));
    }

    // ═══════════════════ Tooltip ═══════════════════

    private void updateTooltip(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
            scene().setHoverTooltip(Tooltip.create());
            return;
        }
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
                        b.kv("需求量", 0xFF888888, fmtNum(found.needed()), red);
                        b.bar(1.0f, red);
                    }
                    case INTERMEDIATE -> {
                        b.kv("消耗量", 0xFF888888, fmtNum(found.needed()), 0xFF88CC88);
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), 0xFFCC8888);
                        if (found.missing() > 0) b.kv("缺少", 0xFFCC4444, fmtNum(found.missing()), 0xFFFF6666);
                        float ratio = found.needed() > 0 ? (float) found.produced() / found.needed() : 1.0f;
                        b.bar(Math.min(1.0f, ratio), ratio >= 1.0f ? green : red);
                    }
                    case BYPRODUCT -> {
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), orange);
                        b.bar((float) found.chance(), orange);
                    }
                    case CATALYST -> {
                        b.kv("用量", 0xFF888888, fmtNum(found.needed()), purple);
                        b.bar(1.0f, purple);
                    }
                }
                tip.add(b.build());
            }
        } else {
            String previewPathId = hoveredNodeId != null ? hoveredNodeId : externalHoveredNodeId;
            if (previewPathId != null) {
                Tooltip nodeTip = buildNodeTooltipForPath(previewPathId, "配方预览");
                if (nodeTip != null) {
                    scene().setHoverTooltip(nodeTip);
                    return;
                }
            }
        }
        scene().setHoverTooltip(tip);
    }

    private void appendNodeTooltip(Tooltip tip, ProcessNode node) {
        var b = FlowChartTooltipComponent.builder();
        b.section(node.name(), node.tierColor());
        b.gap(2);
        MachineInfo machine = getDisplayMachine(node);
        if (machine != null) {
            String machineName = machine.name();
            if (node.machineOptions().size() > 1) {
                machineName += " +" + (node.machineOptions().size() - 1);
            }
            b.iconText(machine.icon(), 0xFF444466, machineName, node.tierColor(), "机器", 0xFF888888);
        } else {
            b.kv("机器", 0xFF888888, node.machine(), node.tierColor());
        }
        b.divider();
        if (!node.inputs().isEmpty()) { b.section("输入", 0xFF88CC88); b.slots(null, 0, node.inputs()); }
        if (!node.outputs().isEmpty()) { b.section("输出", 0xFFCC8888); b.slots(null, 0, node.outputs()); }
        b.divider();
        b.kv("时长", node.durationFormatted());
        b.kv("速率", 0xFF888888, node.euFormatted(), orange);
        long seu = (long) node.euPerTick() * node.durationTicks();
        b.kv("总 EU", 0xFF888888, fmtNum(seu) + " EU", 0xFFFFCC44);
        b.kv("等级", 0xFF888888, node.tierName(), node.tierColor());
        tip.add(b.build());
    }

    private @Nullable LayoutNode findLayoutNode(String pathId) {
        for (LayoutNode ln : flatNodes) {
            if (ln.pathId.equals(pathId)) return ln;
        }
        return null;
    }

    // ═══════════════════ Utilities ═══════════════════

    private static void drawPanelFrame(SceneCanvas canvas, int x, int y, int w, int h, int fill, int border) {
        canvas.fill(x, y, w, h, fill);
        drawOutline(canvas, x, y, w, h, border);
    }

    private static void drawOutline(SceneCanvas canvas, int x, int y, int w, int h, int color) {
        canvas.fill(x, y, w, 1, color);
        canvas.fill(x, y + h - 1, w, 1, color);
        canvas.fill(x, y, 1, h, color);
        canvas.fill(x + w - 1, y, 1, h, color);
    }

    private static void drawGridGlyph(SceneCanvas canvas, int x, int y, int size, int color) {
        int cell = Math.max(2, (size - 1) / 2);
        int gap = 1;
        canvas.fill(x, y, cell, cell, color);
        canvas.fill(x + cell + gap, y, cell, cell, color);
        canvas.fill(x, y + cell + gap, cell, cell, color);
        canvas.fill(x + cell + gap, y + cell + gap, cell, cell, color);
    }

    private static void drawHomeGlyph(SceneCanvas canvas, int x, int y, int size, int color) {
        int roofY = y;
        int bodyY = y + size / 2;
        int right = x + size;
        int mid = x + size / 2;
        canvas.line(x, bodyY, mid, roofY, 1.25f, color);
        canvas.line(mid, roofY, right, bodyY, 1.25f, color);
        canvas.fill(x + 1, bodyY, size - 2, size / 2 + 1, color);
        canvas.fill(mid - 1, y + size - 3, 2, 3, 0xFF555555);
    }

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
    //endregion
}
