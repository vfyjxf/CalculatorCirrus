package dev.vfyjxf.cirrus.ui.widget.flowchart;

import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
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
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Zoomable / pannable recipe-step viewer.
 * <p>
 * Displays topologically-sorted recipe step cards with a three-column
 * layout (inputs | arrow bridge | outputs + requirements). Steps are
 * grouped by level with provenance tags showing connections.
 * <p>
 * Interaction: middle-click drag to pan, scroll to zoom.
 */
public class RecipeStepsWidget extends CompositeWidget<Widget> {

    private static final Logger LOGGER = LoggerFactory.getLogger("RecipeStepsWidget");

    // ═══════════════════ Palette (light calculator theme) ═══════════════════

    private int bg              = 0xFF8B8B8B;  // darker gray background
    private int gridClr          = 0xFF838383;  // minor grid (subtle)
    private int gridClrMajor     = 0xFF7D7D7D;  // major grid (every 5th)
    private int gridClrSuper     = 0xFF767676;  // super grid (every 25th)

    // ── Dark theme card colors ──
    private static final int DK_CARD_BG       = 0xFF00FFFF;  // DEBUG: bright cyan
    private static final int DK_CARD_BORDER   = 0xFF1E293B;
    private static final int DK_CARD_HOVER_BG = 0xFF162033;
    private static final int DK_HEADER_BG     = 0xFF1E293B;
    private static final int DK_SLOT_BG       = 0xFF0D1117;
    private static final int DK_SLOT_BORDER   = 0xFF2D3748;
    private static final int DK_INSET_BG      = 0xFF111827;
    private static final int DK_INSET_BORDER  = 0xFF2D3748;
    private static final int DK_DIVIDER       = 0xFF2D3748;
    private static final int CARD_BG         = 0xFFC6C6C6;
    private static final int CARD_BG_HOVER   = 0xFFD0D0D0;
    private static final int CARD_BORDER     = 0xFF8B8B8B;
    private static final int CARD_BORDER_HVR = 0xFF9999AA;
    private static final int HEADER_BG       = 0xFFB0B0B8;
    private static final int DIV             = 0xFFAAAAAA;
    private static final int TEXT_W          = 0xFF2244AA;  // primary text (dark blue)
    private static final int TEXT_G          = 0xFF556699;  // secondary text (medium blue)
    private static final int TEXT_D          = 0xFF7788AA;  // dim/label text (muted blue)
    private static final int CENTER_LINE     = 0xFFAAAAAA;
    private static final int BRIDGE_BG       = 0xFFB8B8C0;
    private static final int BRIDGE_BD       = 0xFF8888AA;
    private static final int CATALYST_BG     = 0x20AA44FF;
    private static final int CATALYST_TXT    = 0xFF8855CC;
    private static final int TARGET_BG       = 0xFFD8D0B0;
    private static final int TARGET_BD       = 0xFFAA8830;
    private static final int TARGET_TXT      = 0xFF886600;
    private static final int REQMT_ACCENT    = 0xFFCC7700;
    private static final int LEVEL_CLR       = 0xFF5566AA;
    private static final int PROV_CLR        = 0xFF6677BB;
    private static final int MINIMAP_BG       = 0xDD383844;
    private static final int MINIMAP_BORDER   = 0xFF505060;
    private static final int MINIMAP_VP       = 0x884488DD;
    private static final int MINIMAP_CARD     = 0xCCAABBDD;
    private static final int MINIMAP_TARGET   = 0xFFBB9933;
    // ── Material Panel ──
    private static final int PANEL_BG       = 0xF0C0C0C8;
    private static final int PANEL_BD       = 0xFF888899;
    private static final int PANEL_HDR      = 0xFF3366AA;
    private static final int PANEL_ENTRY_BG = 0xFFB8B8C0;
    private static final int PANEL_BAR_BG   = 0xFFAAAAAA;
    private static final int BAR_GREEN      = 0xFF33AA55;
    private static final int BAR_RED        = 0xFFBB3333;
    private static final int BAR_ORANGE     = 0xFFBB7722;
    private static final int BAR_PURPLE     = 0xFF8855BB;
    private static final int TOTALS_BG      = 0xFFB0B0B8;

    // ═══════════════════ Layout (world space) ═══════════════════

    private static final int CARD_W       = 540;
    private static final int MARGIN       = 20;
    private static final int ROW_H        = 36;
    private static final int HDR_H        = 34;
    private static final int SECTION_H    = 24;
    private static final int REQMT_SEC_H  = 34;
    private static final int CARD_PAD     = 8;
    private static final int CARD_RADIUS  = 8;
    private static final int CARD_GAP     = 16;
    private static final int LEVEL_HDR_H  = 24;
    private static final int TARGET_H     = 56;
    private static final int ICON_SZ      = 22;
    private static final int GRID_SPACING = 40;
    private static final int MM_W         = 90;
    private static final int MM_H         = 56;
    private static final int MM_PAD       = 4;

    private static final int PANEL_W     = 200;
    private static final int PANEL_PAD   = 5;
    private static final int BAR_H       = 4;
    private static final int BOX_H      = 42; // main box height (without bar)
    private static final int ENTRY_H     = BOX_H + BAR_H + 1; // box + bar + bottom border
    private static final int ENTRY_GAP   = 2;
    private static final int SEC_HDR_H   = 18;
    private static final int TOTALS_H    = 52;
    private static final int PANEL_ICON_SZ = 26;
    private static final int GROUP_STRIP_W = 3;
    private static final int ICON_SLOT_SZ = 36;
    private static final int IND_CELL_SZ = 16;

    private static final float LEFT_R   = 0.38f;
    private static final float CENTER_R = 0.14f;

    private static final float MIN_ZOOM = 0.15f;
    private static final float MAX_ZOOM = 3.0f;
    /** Below this zoom level, expensive renderItem calls are replaced with cheap coloured placeholders. */
    private static final float ITEM_LOD_ZOOM = 0.20f;
    /** Below this zoom level, secondary text (provider/dest tags, catalyst labels) is skipped. */
    private static final float TEXT_LOD_ZOOM  = 0.15f;
    /** Below this zoom level, card interiors are simplified to colored rectangles. */
    private static final float DETAIL_LOD_ZOOM = 0.10f;

    // Smooth zoom constants
    private static final float ZOOM_SNAP         = 0.001f;
    private static final float ZOOM_SMOOTH_SPEED = 0.6f;

    private boolean darkTheme = false;

    /**
     * Switch to dark-theme colors for use in OmniCalc's tree canvas.
     */
    public RecipeStepsWidget setDarkTheme() {
        this.darkTheme    = true;
        this.bg           = 0xFF0A0E17;
        this.gridClr      = 0xFF121620;
        this.gridClrMajor = 0xFF181D2A;
        this.gridClrSuper = 0xFF1F2530;
        return this;
    }

    // ── Dark theme drawing helpers ──

    /** Dark-theme replacement for Background.frame + Background.flat (card body). */
    private void drawDarkCardBody(SceneCanvas canvas, int x, int y, int w, int h, boolean hovered, boolean isTarget) {
        int bgC = isTarget ? 0xFF1A1507 : (hovered ? DK_CARD_HOVER_BG : DK_CARD_BG);
        int bd = isTarget ? 0xFF92700A : (hovered ? 0xFF334155 : DK_CARD_BORDER);
        canvas.fill(x, y, w, h, bgC);
        canvas.fill(x, y, w, 1, bd); canvas.fill(x, y + h - 1, w, 1, bd);
        canvas.fill(x, y, 1, h, bd); canvas.fill(x + w - 1, y, 1, h, bd);
    }

    /** Dark-theme replacement for Background.inset (header). */
    private void drawDarkHeader(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, DK_HEADER_BG);
    }

    /** Dark-theme replacement for Background.Slot.dark (badge/slot). */
    private void drawDarkSlot(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, DK_SLOT_BG);
        canvas.fill(x, y, w, 1, DK_SLOT_BORDER); canvas.fill(x, y + h - 1, w, 1, DK_SLOT_BORDER);
        canvas.fill(x, y, 1, h, DK_SLOT_BORDER); canvas.fill(x + w - 1, y, 1, h, DK_SLOT_BORDER);
    }

    /** Dark-theme replacement for Background.Outlined.inset (panels, bridge circles). */
    private void drawDarkInset(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, DK_INSET_BG);
        canvas.fill(x, y, w, 1, DK_INSET_BORDER); canvas.fill(x, y + h - 1, w, 1, DK_INSET_BORDER);
        canvas.fill(x, y, 1, h, DK_INSET_BORDER); canvas.fill(x + w - 1, y, 1, h, DK_INSET_BORDER);
    }

    /** Dark-theme replacement for Background.Sign.bar (divider line). */
    private void drawDarkBar(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, DK_DIVIDER);
    }

    // ═══════════════════ State ═══════════════════

    private ProcessGraph graph;
    private @Nullable ProcessNode targetNode;
    private long craftCount = 1;

    private List<StepEntry> allSteps = List.of();
    private List<LevelDiv> levelDividers = List.of();
    private double targetHeaderY = 10;
    private double totalContentW = CARD_W + 2.0 * MARGIN;
    private double totalContentH = 0;

    private Map<String, Map<String, List<Integer>>> inputSources  = Map.of();
    private Map<String, Map<String, List<Integer>>> outputTargets = Map.of();

    private double panX = 0, panY = 0;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private boolean dragging = false;
    private double dragStartX, dragStartY, dragPanStartX, dragPanStartY;

    // Smooth zoom focal point (world + screen coords captured on scroll)
    private double zoomFocusWX, zoomFocusWY;
    private double zoomFocusSX, zoomFocusSY;

    private @Nullable String hoveredStepId = null;
    private boolean hoveredTarget = false;
    private boolean widgetHovered = false;
    private boolean minimapDragging = false;

    // Material statistics panel
    private boolean showMaterialPanel = false;

    public void toggleMaterialPanel() {
        showMaterialPanel = !showMaterialPanel;
        matScrollY = 0;
        matScrollTargetY = 0;
    }

    public boolean isMaterialPanelVisible() {
        return showMaterialPanel;
    }

    /** Flowchart viewport width (excludes material panel + gap when visible). */
    private int viewportW() {
        return showMaterialPanel ? width() - PANEL_W - 2 : width();
    }
    private float matScrollY = 0;           // current rendered scroll offset
    private float matScrollTargetY = 0;     // target scroll offset (smooth)
    private boolean scrollbarDragging = false;  // scrollbar thumb drag state
    private float scrollbarDragStartY = 0;      // mouse Y when drag started
    private float scrollbarDragStartScroll = 0; // scroll offset when drag started
    private static final float MAT_SCROLL_SPEED = 25f;
    private static final float MAT_SCROLL_SMOOTH = 0.35f;
    private static final int SCROLLBAR_W = 10;  // scrollbar width
    private Map<MatCategory, List<MaterialEntry>> materialByCategory = new LinkedHashMap<>();
    private int materialPanelContentH = 0;
    private long totalTimeTicks = 0;
    private long totalEU = 0;
    private @Nullable String hoveredMatName = null;

    // Machine cycling timer (for cards with multiple machine options and no selection)
    private long machineCycleTicks = 0;
    private static final long CYCLE_INTERVAL_TICKS = 40; // ~2 seconds per machine

    // ── Hover optimization ──
    private int lastHoverMouseX = Integer.MIN_VALUE, lastHoverMouseY = Integer.MIN_VALUE;

    // ── Pre-computed provenance tags (built in setGraph, not per-frame) ──
    // nodeId → slotMaterialName → provenance tag string (e.g. " <1,2")
    private Map<String, Map<String, String>> inputProvTags = Map.of();
    // nodeId → slotMaterialName → destination tag string (e.g. " >3,4")
    private Map<String, Map<String, String>> outputDestTags = Map.of();

    // ── Per-frame truncation cache (avoids repeated font.width + substring) ──
    private final Map<Long, String> truncationCache = new HashMap<>();
    private long truncationFrameId = 0;

    // ── Cached format strings (constant per-node, recomputed in setGraph) ──
    private Map<String, String> cachedDurationFmt = Map.of();
    private Map<String, String> cachedDurationTicks = Map.of();
    private Map<String, String> cachedEuFmt = Map.of();
    private Map<String, String> cachedTotalEuFmt = Map.of();

    // ── Deferred icon rendering for batching ──
    // Item/fluid icons are collected during card drawing and flushed
    // in a single renderLayered call, reducing per-item overhead
    // (flushBatch + scissor + depth clear) from O(N) to O(1).
    private record DeferredItem(ItemStack stack, int x, int y, float scale) {}
    private record DeferredFluid(FluidStack fluid, int x, int y, float scale, float pt) {}
    private final List<DeferredItem> deferredItems = new ArrayList<>();
    private final List<DeferredFluid> deferredFluids = new ArrayList<>();

    // ═══════════════════ Records ═══════════════════

    private record StepEntry(ProcessNode node, int stepNumber, int level,
                             int cardHeight, double worldX, double worldY) {}

    private record LevelDiv(int level, double worldX, int colWidth, double worldY, int colHeight,
                             @Nullable String groupName, int groupColor) {}

    private enum MatCategory {
        EXTERNAL_INPUT("外部输入", 0xFF55FFAA),
        BYPRODUCT("副产物 / 多余", 0xFFFFCC44),
        CATALYST("催化剂 / 工具", 0xFFBB77FF),
        INTERMEDIATE("中间产物", 0xFF88AACC);

        final String label;
        final int color;
        MatCategory(String label, int color) { this.label = label; this.color = color; }
    }

    /**
     * @param needed      total consumed across all steps
     * @param produced    total produced across all steps
     * @param missing     max(0, needed - produced) — how much the player must supply
     * @param inventory   how much is available from internal production
     * @param sources     step numbers that produce this material
     * @param consumers   step numbers that consume this material
     * @param chance      average output chance (1.0 = guaranteed)
     */
    private record MaterialEntry(
            String name, MaterialSlot refSlot,
            long needed, long produced, long missing, long inventory,
            MatCategory category, List<Integer> sources, List<Integer> consumers,
            double chance
    ) {}

    // ═══════════════════ Constructor ═══════════════════

    public RecipeStepsWidget() {
        // ── Mouse handlers ──
        // NOTE: All InputContext coordinates are in SCENE (screen) space.
        // Use input.mouseRelative(this) or sceneToLocal() to get local coords.
        onMouseClicked((input, ctx) -> {
            FloatPos local = input.mouseRelative(RecipeStepsWidget.this);
            double mx = local.x(), my = local.y();
            // Ignore clicks outside widget viewport
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;

            // Left-click on scrollbar thumb → start dragging
            if (input.isLeftClick() && showMaterialPanel && isOnScrollbarThumb(mx, my)) {
                scrollbarDragging = true;
                scrollbarDragStartY = (float) my;
                scrollbarDragStartScroll = matScrollTargetY;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            // Left-click on minimap → jump viewport
            if (input.isLeftClick() && isInMinimap(mx, my)) {
                minimapClickToPan(mx, my);
                minimapDragging = true;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (input.key().getValue() == 2) {
                dragging = true;
                zoom = targetZoom;
                dragStartX = mx;
                dragStartY = my;
                dragPanStartX = panX;
                dragPanStartY = panY;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseReleased((input, ctx) -> {
            if (scrollbarDragging) {
                scrollbarDragging = false;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (minimapDragging) {
                minimapDragging = false;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (dragging) {
                dragging = false;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            FloatPos local = input.mouseRelative(RecipeStepsWidget.this);
            double mx = local.x(), my = local.y();
            // Allow ongoing drags to continue even outside viewport
            if (scrollbarDragging) {
                int visibleH = panelContentVisibleH();
                int maxScroll = Math.max(0, materialPanelContentH - visibleH);
                int thumbH = Math.max(16, (int) ((float) visibleH / materialPanelContentH * visibleH));
                int trackH = visibleH - thumbH;
                if (trackH > 0 && maxScroll > 0) {
                    float deltaPixels = (float) my - scrollbarDragStartY;
                    float scrollDelta = deltaPixels / trackH * maxScroll;
                    matScrollTargetY = Math.max(0, Math.min(maxScroll, scrollbarDragStartScroll + scrollDelta));
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (minimapDragging) {
                minimapClickToPan(mx, my);
                ctx.cancel();
                return EventDispatch.consumed;
            }
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
            // Ignore scrolls outside widget viewport
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            // Handle material panel scrolling manually
            if (showMaterialPanel && isInMaterialPanel(mx, my)) {
                int maxScroll = Math.max(0, materialPanelContentH - panelContentVisibleH());
                if (scrollY > 0) {
                    matScrollTargetY = Math.max(0, matScrollTargetY - MAT_SCROLL_SPEED);
                } else if (scrollY < 0) {
                    matScrollTargetY = Math.min(maxScroll, matScrollTargetY + MAT_SCROLL_SPEED);
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }
            // Capture focal point using current rendered state
            zoomFocusWX = panX + mx / (double) zoom;
            zoomFocusWY = panY + my / (double) zoom;
            zoomFocusSX = mx;
            zoomFocusSY = my;
            if (scrollY > 0) targetZoom = Math.min(MAX_ZOOM, targetZoom * 1.15f);
            else if (scrollY < 0) targetZoom = Math.max(MIN_ZOOM, targetZoom / 1.15f);
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onMouseEnter((mouseX, mouseY, ctx) -> {
            widgetHovered = true;
        });
        onMouseLeave((mouseX, mouseY, ctx) -> {
            widgetHovered = false;
            hoveredStepId = null;
            hoveredTarget = false;
            scene().setHoverTooltip(Tooltip.create());
        });
    }

    // ═══════════════════ Data Setup ═══════════════════

    public RecipeStepsWidget setGraph(ProcessGraph graph) {
        this.graph = graph;
        List<ProcessNode> sinks = graph.sinks();
        this.targetNode = sinks.isEmpty() ? null : sinks.get(0);

        List<ProcessNode> sorted = topoSort(graph);

        // Only keep nodes that are ancestors of (or equal to) the target
        Set<String> relevant = new HashSet<>();
        if (targetNode != null) {
            Deque<String> frontier = new ArrayDeque<>();
            frontier.add(targetNode.id());
            while (!frontier.isEmpty()) {
                String nid = frontier.poll();
                if (!relevant.add(nid)) continue;
                for (ProcessEdge e : graph.incomingEdges(nid)) {
                    frontier.add(e.fromNodeId());
                }
            }
        } else {
            for (ProcessNode n : sorted) relevant.add(n.id());
        }
        sorted.removeIf(n -> !relevant.contains(n.id()));

        Map<String, Integer> stepNum = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) stepNum.put(sorted.get(i).id(), i + 1);

        Map<String, Integer> levels = new HashMap<>();
        for (ProcessNode n : sorted) {
            int maxPred = -1;
            for (ProcessEdge e : graph.incomingEdges(n.id())) {
                Integer pl = levels.get(e.fromNodeId());
                if (pl != null) maxPred = Math.max(maxPred, pl);
            }
            levels.put(n.id(), maxPred + 1);
        }

        Map<String, Map<String, List<Integer>>> inSrc = new HashMap<>();
        Map<String, Map<String, List<Integer>>> outTgt = new HashMap<>();
        for (ProcessEdge e : graph.edges()) {
            Integer srcS = stepNum.get(e.fromNodeId());
            Integer tgtS = stepNum.get(e.toNodeId());
            if (srcS != null) {
                inSrc.computeIfAbsent(e.toNodeId(), k -> new HashMap<>())
                     .computeIfAbsent(e.materialName(), k -> new ArrayList<>())
                     .add(srcS);
            }
            if (tgtS != null) {
                outTgt.computeIfAbsent(e.fromNodeId(), k -> new HashMap<>())
                      .computeIfAbsent(e.materialName(), k -> new ArrayList<>())
                      .add(tgtS);
            }
        }
        this.inputSources = inSrc;
        this.outputTargets = outTgt;

        // Group nodes by level
        Map<Integer, List<ProcessNode>> byLevel = new LinkedHashMap<>();
        for (ProcessNode node : sorted) {
            int level = levels.getOrDefault(node.id(), 0);
            byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(node);
        }
        int maxLevel = byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        String targetId = targetNode != null ? targetNode.id() : null;

        // Target header row at top
        double headerY = 10;
        targetHeaderY = headerY;
        double curY = headerY + TARGET_H + CARD_GAP;
        int divLabelH = LEVEL_HDR_H + 8;

        // Layout: final step at top, then rows in descending level order.
        // Each level is a horizontal row; cards placed side-by-side.
        List<StepEntry> entries = new ArrayList<>();
        List<LevelDiv> divs = new ArrayList<>();
        int maxRowW = CARD_W + 2 * MARGIN;

        // 1. Final target card at the very top (no stage divider)
        if (targetNode != null) {
            int sn = stepNum.getOrDefault(targetNode.id(), 0);
            int cardH = computeCardH(targetNode);
            entries.add(new StepEntry(targetNode, sn, maxLevel, cardH, MARGIN, curY));
            maxRowW = Math.max(maxRowW, CARD_W + 2 * MARGIN);
            curY += cardH + CARD_GAP;
        }

        // 2. Remaining levels in descending order (closest-to-final first)
        for (int level = maxLevel; level >= 0; level--) {
            List<ProcessNode> nodesInLevel = byLevel.getOrDefault(level, List.of());
            // Filter out the final target (already rendered above)
            List<ProcessNode> filtered = new ArrayList<>();
            for (ProcessNode n : nodesInLevel) {
                if (targetId != null && n.id().equals(targetId)) continue;
                filtered.add(n);
            }
            if (filtered.isEmpty()) continue;

            // Row width
            int rowW = filtered.size() * CARD_W + (filtered.size() - 1) * CARD_GAP;
            // Resolve group name for this level (use first node's group)
            String groupName = null;
            int groupColor = LEVEL_CLR;
            String gid = graph.groupOfNode(filtered.get(0).id());
            if (gid != null) {
                ProcessGroup grp = graph.group(gid);
                if (grp != null) {
                    groupName = grp.name();
                    groupColor = grp.color();
                }
            }
            divs.add(new LevelDiv(level, MARGIN, rowW, curY, divLabelH, groupName, groupColor));
            curY += divLabelH;

            // Uniform card height per row
            int maxCardH = 0;
            for (ProcessNode n : filtered) maxCardH = Math.max(maxCardH, computeCardH(n));

            // Place cards side-by-side
            double curX = MARGIN;
            for (ProcessNode node : filtered) {
                int sn = stepNum.getOrDefault(node.id(), 0);
                entries.add(new StepEntry(node, sn, level, maxCardH, curX, curY));
                curX += CARD_W + CARD_GAP;
            }
            maxRowW = Math.max(maxRowW, (int) curX - CARD_GAP + MARGIN);
            curY += maxCardH + CARD_GAP;
        }

        this.allSteps = entries;
        this.levelDividers = divs;
        this.totalContentW = maxRowW;
        this.totalContentH = curY + 20;
        resetView();
        computeMaterialSummary();
        precomputeProvenanceTags();
        precomputeFormatStrings();

        return this;
    }

    public RecipeStepsWidget setCraftCount(long count) {
        this.craftCount = Math.max(1, count);
        return this;
    }

    private int computeCardH(ProcessNode node) {
        int inputRows  = Math.max(node.inputs().size(), 1);
        int outputRows = Math.max(node.outputs().size(), 1);
        int leftH  = inputRows * ROW_H;
        int rightH = outputRows * ROW_H;
        if (node.euPerTick() > 0) rightH += 8 + REQMT_SEC_H;
        int bodyH = Math.max(leftH, Math.max(rightH, 80));
        return HDR_H + 2 + SECTION_H + bodyH + 10;
    }

    /**
     * Pre-computes provenance/destination tag strings for all node slots.
     * Called once during setGraph() instead of every render frame.
     */
    private void precomputeProvenanceTags() {
        Map<String, Map<String, String>> inTags = new HashMap<>();
        Map<String, Map<String, String>> outTags = new HashMap<>();

        for (var nodeEntry : inputSources.entrySet()) {
            String nodeId = nodeEntry.getKey();
            Map<String, String> slotTags = new HashMap<>();
            for (var slotEntry : nodeEntry.getValue().entrySet()) {
                slotTags.put(slotEntry.getKey(), " <" + joinSteps(slotEntry.getValue()));
            }
            inTags.put(nodeId, slotTags);
        }

        for (var nodeEntry : outputTargets.entrySet()) {
            String nodeId = nodeEntry.getKey();
            Map<String, String> slotTags = new HashMap<>();
            for (var slotEntry : nodeEntry.getValue().entrySet()) {
                slotTags.put(slotEntry.getKey(), " >" + joinSteps(slotEntry.getValue()));
            }
            outTags.put(nodeId, slotTags);
        }

        this.inputProvTags = inTags;
        this.outputDestTags = outTags;
    }

    /**
     * Pre-computes formatted duration/EU strings for each node.
     * These are constant per-node and don't need per-frame recalculation.
     */
    private void precomputeFormatStrings() {
        Map<String, String> durFmt = new HashMap<>();
        Map<String, String> durTicks = new HashMap<>();
        Map<String, String> euFmt = new HashMap<>();
        Map<String, String> totalEuFmt = new HashMap<>();

        for (StepEntry step : allSteps) {
            ProcessNode n = step.node;
            durFmt.put(n.id(), n.durationFormatted());
            durTicks.put(n.id(), n.durationTicks() + "t");
            euFmt.put(n.id(), fmtNum(n.euPerTick()) + " /t");
            totalEuFmt.put(n.id(), fmtNum((long) n.euPerTick() * n.durationTicks()));
        }

        this.cachedDurationFmt = durFmt;
        this.cachedDurationTicks = durTicks;
        this.cachedEuFmt = euFmt;
        this.cachedTotalEuFmt = totalEuFmt;
    }

    private void resetView() {
        if (totalContentH <= 0 || width() <= 0 || height() <= 0) return;
        float zx = (float) (width() / (totalContentW + 40));
        float zy = (float) (height() / (totalContentH + 40));
        zoom = Math.max(MIN_ZOOM, Math.min(1.0f, Math.min(zx, zy)));
        targetZoom = zoom;
        panX = -(width() / zoom - totalContentW) / 2.0;
        panY = -20;
    }

    // ═══════════════════ Smooth Animation ═══════════════════

    /**
     * Advances smooth zoom interpolation.
     * Called once per render frame before drawing.
     */
    private void animateSmooth() {
        // ── Smooth zoom with focal-point preservation ──
        if (Math.abs(targetZoom - zoom) >= ZOOM_SNAP) {
            zoom += (targetZoom - zoom) * ZOOM_SMOOTH_SPEED;
            if (Math.abs(targetZoom - zoom) < ZOOM_SNAP) zoom = targetZoom;
            // Adjust pan so the focal point stays stable on screen
            panX = zoomFocusWX - zoomFocusSX / zoom;
            panY = zoomFocusWY - zoomFocusSY / zoom;
        }

        // ── Smooth material panel scroll ──
        if (Math.abs(matScrollTargetY - matScrollY) > 0.5f) {
            matScrollY += (matScrollTargetY - matScrollY) * MAT_SCROLL_SMOOTH;
            if (Math.abs(matScrollTargetY - matScrollY) < 0.5f) matScrollY = matScrollTargetY;
        }
    }

    // ═══════════════════ Rendering ═══════════════════

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (graph == null || allSteps.isEmpty()) {
            canvas.fill(0, 0, width(), height(), bg);
            canvas.drawCenteredString("无配方数据", width() / 2, height() / 2 - 4, TEXT_D);
            return;
        }
        Font font = Minecraft.getInstance().font;

        // ── Smooth animations ──
        animateSmooth();

        // ── Invalidate per-frame truncation cache ──
        truncationFrameId++;
        truncationCache.clear();

        // ── Hover: skip if mouse hasn't moved ──
        if (mouseX != lastHoverMouseX || mouseY != lastHoverMouseY) {
            lastHoverMouseX = mouseX;
            lastHoverMouseY = mouseY;
            updateHover(mouseX, mouseY);
        }
        updateTooltip(mouseX, mouseY);
        machineCycleTicks++;

        int vw = viewportW();
        // Flowchart area with its own border
        canvas.fill(0, 0, vw, height(), bg);
        if (!darkTheme) {
            canvas.texture(Background.Outlined.inset, 0, 0, vw, height());
        } else {
            // Dark theme: subtle border instead of MC texture
            canvas.fill(0, 0, vw, 1, 0xFF1F2937);
            canvas.fill(0, height() - 1, vw, 1, 0xFF1F2937);
            canvas.fill(0, 0, 1, height(), 0xFF1F2937);
            canvas.fill(vw - 1, 0, 1, height(), 0xFF1F2937);
        }
        // Use absolute screen coordinates for scissor clip (inset by 2px to avoid drawing over border)
        Pos abs = absolutePos();
        int borderInset = 2;
        canvas.withClip(abs.x() + borderInset, abs.y() + borderInset, vw - borderInset * 2, height() - borderInset * 2, () -> {
            // DEBUG: fill ENTIRE widget area with bright blue inside clip
            canvas.fill(0, 0, vw, height(), 0xFF0000FF);

            canvas.pushTransform();
            canvas.scale(zoom);
            canvas.translate((float) -panX, (float) -panY);

            // DEBUG: skip grid to test card rendering
            // drawGrid(canvas);

            // Compute visible world-space bounds for frustum culling
            double visMinX = panX;
            double visMinY = panY;
            double visMaxX = panX + vw / zoom;
            double visMaxY = panY + height() / zoom;

            drawTargetHeader(canvas, font, MARGIN, targetHeaderY,
                    CARD_W, partialTicks);

            for (LevelDiv ld : levelDividers) {
                // Cull dividers outside visible area
                double ldBot = ld.worldY + LEVEL_HDR_H;
                if (ldBot < visMinY || ld.worldY > visMaxY) continue;
                drawLevelDivider(canvas, font, (int) ld.worldX, ld.worldY, ld.colWidth,
                        ld.level, ld.groupName, ld.groupColor);
            }

            for (StepEntry se : allSteps) {
                // Frustum cull: skip cards entirely outside the visible area
                double cardRight = se.worldX + CARD_W;
                double cardBot = se.worldY + se.cardHeight;
                if (cardRight < visMinX || se.worldX > visMaxX
                        || cardBot < visMinY || se.worldY > visMaxY) continue;
                boolean hv = se.node.id().equals(hoveredStepId);
                drawStepCard(canvas, font, se, (int) se.worldX, se.worldY, CARD_W, hv, partialTicks);
            }

            // Flush all deferred item/fluid icons in one batch
            flushDeferredIcons(canvas);

            canvas.popTransform();
        });
        // DEBUG: test fill outside withClip
        canvas.fill(50, 50, 100, 50, 0xFF00FF00);
        drawMinimap(canvas);
        if (showMaterialPanel) drawMaterialPanel(canvas, font, mouseX, mouseY, partialTicks);
        drawStatusBar(canvas, font);
    }

    // ─────── Grid ───────

    private void drawGrid(SceneCanvas canvas) {
        double visW = viewportW() / zoom, visH = height() / zoom;
        // Adaptive grid: skip minor lines when too dense, show major/super when visible
        float pixelSpacing = GRID_SPACING * zoom;

        // Determine which grid levels to draw based on pixel density
        boolean drawMinor = pixelSpacing >= 6;   // minor lines visible when >= 6px apart
        int majorEvery = 5;                       // every 5th line is major
        int superEvery = 25;                      // every 25th line is super

        int sx = ((int) (panX / GRID_SPACING) - 1) * GRID_SPACING;
        int sy = ((int) (panY / GRID_SPACING) - 1) * GRID_SPACING;
        int ex = (int) (panX + visW) + GRID_SPACING;
        int ey = (int) (panY + visH) + GRID_SPACING;

        // Use canvas.fill for grid lines (batchable) instead of canvas.line (not batchable).
        // Thickness is 1 world-pixel for all levels; differentiated only by color.
        // This allows all grid fills to be batched into a single draw call.

        // Vertical lines — batch all colored fills together
        for (int gx = sx; gx <= ex; gx += GRID_SPACING) {
            int idx = gx / GRID_SPACING;
            int clr;
            if (idx % superEvery == 0) clr = gridClrSuper;
            else if (idx % majorEvery == 0) clr = gridClrMajor;
            else if (drawMinor) clr = gridClr;
            else continue;
            canvas.fill(gx, sy, 1, ey - sy, clr);
        }
        // Horizontal lines
        for (int gy = sy; gy <= ey; gy += GRID_SPACING) {
            int idx = gy / GRID_SPACING;
            int clr;
            if (idx % superEvery == 0) clr = gridClrSuper;
            else if (idx % majorEvery == 0) clr = gridClrMajor;
            else if (drawMinor) clr = gridClr;
            else continue;
            canvas.fill(sx, gy, ex - sx, 1, clr);
        }
    }

    // ─────── Target Header ───────

    private void drawTargetHeader(SceneCanvas canvas, Font font,
                                   int x, double wy, int w, float pt) {
        int y = (int) wy;
        // Target header with outlined panel
        canvas.shadow(x, y, w, TARGET_H, CARD_RADIUS, 4, 10, 0x40000000);
        if (darkTheme) drawDarkInset(canvas, x, y, w, TARGET_H);
        else canvas.texture(Background.Outlined.inset, x, y, w, TARGET_H);

        int iconX = x + 14, iconY = y + (TARGET_H - 32) / 2;
        boolean drawn = false;
        if (targetNode != null && !targetNode.outputs().isEmpty()) {
            MaterialSlot primary = targetNode.outputs().get(0);
            ItemStack stack = primary.itemStack();
            if (stack != null && !stack.isEmpty()) {
                deferredItems.add(new DeferredItem(stack, iconX, iconY, 2.0f));
                drawn = true;
            } else if (primary.fluidStack() != null && !primary.fluidStack().isEmpty()) {
                deferredFluids.add(new DeferredFluid(primary.fluidStack(), iconX, iconY, 2.0f, pt));
                drawn = true;
            }
        }
        if (!drawn) {
            if (darkTheme) drawDarkSlot(canvas, iconX, iconY, 32, 32);
            else canvas.texture(Background.Slot.dark, iconX, iconY, 32, 32);
            canvas.drawCenteredString("?", iconX + 16, iconY + 12, TARGET_TXT);
        }

        int textX = iconX + 42;
        String title = "合成: ";
        if (targetNode != null && !targetNode.outputs().isEmpty())
            title += targetNode.outputs().get(0).materialName();
        else if (targetNode != null) title += targetNode.name();
        canvas.drawString(title, textX, y + 14, TARGET_TXT, true);
        canvas.drawString("\u00d7 " + craftCount, textX, y + 28, TEXT_G, false);

        if (targetNode != null) {
            String info = targetNode.machine() + " \u00b7 " + targetNode.tierName();
            int infoW = font.width(info);
            canvas.drawString(info, x + w - infoW - 12, y + 22, TEXT_D, false);
        }
    }

    // ─────── Level Divider (horizontal label across each row) ───────

    private void drawLevelDivider(SceneCanvas canvas, Font font,
                                   int x, double wy, int w, int level,
                                   @Nullable String groupName, int groupColor) {
        int y = (int) wy;
        int lineW = Math.max(w, (int) totalContentW - 2 * MARGIN);
        int midY = y + LEVEL_HDR_H / 2;
        // Use fill (batchable) instead of line (not batchable)
        canvas.fill(x + 4, midY - 2, lineW - 8, 2, LEVEL_CLR);
        canvas.fill(x + 4, midY + 1, lineW - 8, 1, withAlpha(LEVEL_CLR, 0x60));

        // Group/stage name on the left
        if (groupName != null) {
            String tag = "\u25C6 " + groupName;
            int tagW = font.width(tag) + 12;
            if (darkTheme) drawDarkInset(canvas, x + 6, y + 2, tagW, LEVEL_HDR_H - 4);
            else canvas.texture(Background.Outlined.inset, x + 6, y + 2, tagW, LEVEL_HDR_H - 4);
            canvas.drawString(tag, x + 12, y + 8, groupColor, true);
        }

        // Level number badge in center
        String label = " \u00b7 " + (level + 1) + " ";
        int lw = font.width(label) + 16;
        int cx = x + lineW / 2;
        if (darkTheme) drawDarkInset(canvas, cx - lw / 2, y + 2, lw, LEVEL_HDR_H - 4);
        else canvas.texture(Background.Outlined.inset, cx - lw / 2, y + 2, lw, LEVEL_HDR_H - 4);
        canvas.drawCenteredString(label, cx, y + 8, TEXT_W);
    }

    // ═══════════════════ Step Card ═══════════════════

    private void drawStepCard(SceneCanvas canvas, Font font, StepEntry step,
                               int x, double wy, int w, boolean hovered, float pt) {
        int y = (int) wy;
        ProcessNode node = step.node;
        int h = step.cardHeight;
        boolean isTarget = targetNode != null && node.id().equals(targetNode.id());

        // ── DETAIL_LOD: at very low zoom, draw simplified colored rectangle ──
        if (zoom < DETAIL_LOD_ZOOM) {
            int bgColor, borderColor;
            if (darkTheme) {
                bgColor = isTarget ? 0xFF1A1507 : (hovered ? DK_CARD_HOVER_BG : DK_CARD_BG);
                borderColor = isTarget ? 0xFF92700A : (hovered ? 0xFF334155 : DK_CARD_BORDER);
            } else {
                bgColor = isTarget ? TARGET_BG : (hovered ? CARD_BG_HOVER : CARD_BG);
                borderColor = isTarget ? TARGET_BD : (hovered ? CARD_BORDER_HVR : CARD_BORDER);
            }
            canvas.fill(x, y, w, 1, borderColor);
            canvas.fill(x, y + h - 1, w, 1, borderColor);
            canvas.fill(x, y, 1, h, borderColor);
            canvas.fill(x + w - 1, y, 1, h, borderColor);
            // Tier color strip at top
            canvas.fill(x + 1, y + 1, w - 2, 3, node.tierColor());
            return;
        }

        // Card body with layered panel texture
        if (darkTheme) {
            LOGGER.info("drawDarkCardBody: darkTheme={} x={} y={} w={} h={} zoom={}", darkTheme, x, y, w, h, zoom);
            drawDarkCardBody(canvas, x, y, w, h, hovered, isTarget);
            return;  // DEBUG: skip rest of card to isolate card body fill
        } else if (isTarget) {
            canvas.texture(Background.frame, x, y, w, h);
            canvas.texture(Background.Outlined.inset, x + 4, y + 4, w - 8, h - 8);
        } else if (hovered) {
            canvas.texture(Background.frame, x, y, w, h);
            canvas.texture(Background.Border.light, x + 4, y + 4, w - 8, h - 8);
        } else {
            canvas.texture(Background.frame, x, y, w, h);
            canvas.texture(Background.flat, x + 4, y + 4, w - 8, h - 8);
        }

        // Header
        if (darkTheme) drawDarkHeader(canvas, x + 2, y + 2, w - 4, HDR_H);
        else canvas.texture(Background.inset, x + 2, y + 2, w - 4, HDR_H);
        int tc = node.tierColor();

        // Step number badge in header
        String stepBadge = isTarget ? "★" : "#" + step.stepNumber;
        int badgeW = font.width(stepBadge) + 8;
        int badgeX = x + 5, badgeY = y + (HDR_H - 14) / 2 + 1;
        if (darkTheme) drawDarkSlot(canvas, badgeX, badgeY, badgeW, 14);
        else canvas.texture(Background.Slot.dark, badgeX, badgeY, badgeW, 14);
        canvas.drawCenteredString(stepBadge, badgeX + badgeW / 2, badgeY + 3,
                isTarget ? TARGET_TXT : TEXT_D);

        // Machine icon (16×16) — use current context machine or cycle through options
        int iconX = x + badgeW + 10;
        int iconY = y + (HDR_H - 16) / 2 + 1;
        MachineInfo displayMachine = getDisplayMachine(node);
        boolean iconDrawn = false;
        if (displayMachine != null && displayMachine.icon() != null && !displayMachine.icon().isEmpty()) {
            deferredItems.add(new DeferredItem(displayMachine.icon(), iconX, iconY, 1.0f));
            iconDrawn = true;
        }
        if (!iconDrawn) {
            // Fallback: tier-coloured slot texture
            if (darkTheme) drawDarkSlot(canvas, x + 8, y + (HDR_H - 16) / 2 + 1, 16, 16);
            else canvas.texture(Background.Slot.dark, x + 8, y + (HDR_H - 16) / 2 + 1, 16, 16);
        }

        // Machine name text
        int textStartX = iconDrawn ? iconX + 20 : x + 24;
        String machineName = displayMachine != null ? displayMachine.name() : node.machine();
        // If cycling, show a subtle rotation indicator
        if (!node.machineOptions().isEmpty() && node.machineOptions().size() > 1) {
            machineName += " +" + (node.machineOptions().size() - 1);
        }
        canvas.drawString(machineName, textStartX, y + (HDR_H - 9) / 2, TEXT_W, true);

        // Tier badge
        String tierStr = node.tierName();
        int tierW = font.width(tierStr) + 10;
        int bx = x + w - tierW - 30, by = y + (HDR_H - 16) / 2;
        if (darkTheme) drawDarkSlot(canvas, bx, by, tierW, 16);
        else canvas.texture(Background.Slot.dark, bx, by, tierW, 16);
        canvas.drawCenteredString(tierStr, bx + tierW / 2, by + 4, tc);
        canvas.drawString("...", x + w - 18, y + (HDR_H - 9) / 2, TEXT_D, false);

        // Divider below header
        int curY = y + HDR_H;
        if (darkTheme) drawDarkBar(canvas, x + 2, curY, w - 4, 2);
        else canvas.texture(Background.Sign.bar, x + 2, curY, w - 4, 2);
        curY += 2;

        // Section labels
        int innerW = w - 2 * CARD_PAD;
        int leftW   = (int) (innerW * LEFT_R);
        int centerW = (int) (innerW * CENTER_R);
        int rightW  = innerW - leftW - centerW;
        int leftX   = x + CARD_PAD;
        int centerX = leftX + leftW;
        int rightX  = centerX + centerW;

        canvas.drawString("输入 (" + node.inputs().size() + ")",
                leftX + 2, curY + 7, TEXT_D, false);
        String outLabel = "输出 (" + node.outputs().size() + ")";
        int olw = font.width(outLabel);
        canvas.drawString(outLabel, x + w - CARD_PAD - olw - 2, curY + 7, TEXT_D, false);
        curY += SECTION_H;

        // Column dividers — use fill (batchable) instead of line
        int bodyTop = curY, bodyBot = y + h - 8;
        canvas.fill(centerX, bodyTop + 2, 1, bodyBot - bodyTop - 4, DIV);
        canvas.fill(rightX,  bodyTop + 2, 1, bodyBot - bodyTop - 4, DIV);

        int bodyH = bodyBot - bodyTop;
        drawInputColumn(canvas, font, node, leftX, bodyTop, leftW, pt);
        drawCenterBridge(canvas, font, node, centerX, bodyTop, centerW, bodyH);
        drawOutputColumn(canvas, font, node, rightX, bodyTop, rightW, pt);
    }

    // ─────── Input Column ───────

    private void drawInputColumn(SceneCanvas canvas, Font font,
                                  ProcessNode node, int x, int y, int colW, float pt) {
        int rowY = y + 2;
        for (MaterialSlot slot : node.inputs()) {
            int c = slot.type().color;
            if (slot.isCatalyst())
                canvas.fill(x + 1, rowY, colW - 3, ROW_H - 2, CATALYST_BG);

            drawSlotIcon(canvas, slot, x + 4, rowY + (ROW_H - ICON_SZ) / 2, pt);

            String name = slot.isCatalyst() ? "* " + slot.materialName() : slot.materialName();
            String amt = String.valueOf(slot.amount());
            int amtW = font.width(amt);
            canvas.drawString(amt, x + colW - amtW - 6, rowY + 8, TEXT_G, false);

            // Use pre-computed provenance tags
            String provTag = inputProvTags
                    .getOrDefault(node.id(), Map.of())
                    .getOrDefault(slot.materialName(), "");

            String dn = cachedTruncate(name, colW - ICON_SZ - amtW - 24, font);
            canvas.drawString(dn, x + ICON_SZ + 10, rowY + 8, withAlpha(c, 0xDD), false);

            if (zoom >= TEXT_LOD_ZOOM) {
                if (slot.isCatalyst()) {
                    canvas.drawString("催化剂 (不消耗)",
                            x + ICON_SZ + 10, rowY + 22, CATALYST_TXT, false);
                } else if (!provTag.isEmpty()) {
                    canvas.drawString(provTag, x + ICON_SZ + 10, rowY + 22, PROV_CLR, false);
                }
            }
            rowY += ROW_H;
        }
    }

    // ─────── Output Column ───────

    private void drawOutputColumn(SceneCanvas canvas, Font font,
                                   ProcessNode node, int x, int y, int colW, float pt) {
        int rowY = y + 2;
        for (MaterialSlot slot : node.outputs()) {
            int c = slot.type().color;
            String amt = String.valueOf(slot.amount());
            int amtW = font.width(amt);
            canvas.drawString(amt, x + 8, rowY + 8, brighten(c, 30), true);

            String name = slot.materialName();
            if (slot.chance() < 1.0)
                name += String.format(" %.0f%%", slot.chance() * 100);

            // Use pre-computed destination tags
            String destTag = outputDestTags
                    .getOrDefault(node.id(), Map.of())
                    .getOrDefault(slot.materialName(), "");

            String dn = cachedTruncate(name, colW - ICON_SZ - amtW - 30, font);
            canvas.drawString(dn, x + amtW + 18, rowY + 8, withAlpha(c, 0xDD), false);

            if (zoom >= TEXT_LOD_ZOOM && !destTag.isEmpty())
                canvas.drawString(destTag, x + amtW + 18, rowY + 22, PROV_CLR, false);

            drawSlotIcon(canvas, slot, x + colW - ICON_SZ - 6,
                    rowY + (ROW_H - ICON_SZ) / 2, pt);
            rowY += ROW_H;
        }

        if (node.euPerTick() > 0) {
            rowY += 4;
            drawRequirements(canvas, font, node, x, rowY, colW);
        }
    }

    // ─────── Centre Bridge ───────

    private void drawCenterBridge(SceneCanvas canvas, Font font,
                                   ProcessNode node, int x, int y, int w, int bodyH) {
        int cx = x + w / 2;
        // Use fill (batchable) instead of line
        canvas.fill(cx, y + 6, 1, bodyH - 12, CENTER_LINE);

        // Skip decorative elements at very low zoom
        if (zoom < TEXT_LOD_ZOOM) return;

        int contentH = 36 + 10 + 32 + 6 + 12 + 12;
        int startY = y + Math.max(0, (bodyH - contentH) / 2);

        // Arrow circle
        if (darkTheme) drawDarkInset(canvas, cx - 17, startY + 1, 34, 34);
        else canvas.texture(Background.Outlined.inset, cx - 17, startY + 1, 34, 34);
        canvas.drawCenteredString(">", cx, startY + 14, TEXT_G);

        // Timer circle
        int timerCY = startY + 54;
        if (darkTheme) drawDarkInset(canvas, cx - 15, timerCY - 15, 30, 30);
        else canvas.texture(Background.Outlined.inset, cx - 15, timerCY - 15, 30, 30);
        canvas.drawCenteredString("T", cx, timerCY - 4, TEXT_G);

        // Duration text — use pre-computed format strings
        int timeY = timerCY + 18;
        canvas.drawCenteredString(cachedDurationFmt.getOrDefault(node.id(), node.durationFormatted()), cx, timeY, TEXT_W);
        canvas.drawCenteredString(cachedDurationTicks.getOrDefault(node.id(), node.durationTicks() + "t"), cx, timeY + 13, TEXT_G);
    }

    // ─────── Requirements ───────

    private void drawRequirements(SceneCanvas canvas, Font font,
                                   ProcessNode node, int x, int y, int w) {
        // Simple thin line separator
        canvas.fill(x + 8, y + 1, w - 16, 1, DIV);
        y += 4;

        // Background panel that fits all content: header row (12px) + EU row (14px) + padding
        int panelH = REQMT_SEC_H - 4;
        if (darkTheme) drawDarkSlot(canvas, x + 4, y, w - 8, panelH);
        else canvas.texture(Background.Slot.dark, x + 4, y, w - 8, panelH);

        // Skip text details at very low zoom — panel texture is enough
        if (zoom < TEXT_LOD_ZOOM) return;

        // Three-column layout
        int col1X = x + 10;
        int col2X = x + (w * 2 / 3) - 16;
        int col3X = x + w - 12;

        // Headers
        canvas.drawString("需求", col1X, y + 3, TEXT_D, false);
        canvas.drawString("速率", col2X, y + 3, TEXT_D, false);
        String totalHdr = "总计";
        canvas.drawString(totalHdr, col3X - font.width(totalHdr), y + 3, TEXT_D, false);

        // EU row with icon — use pre-computed format strings
        int euY = y + 16;
        canvas.texture(Icon.Sign.Energy.eu, col1X, euY, 12, 12);
        canvas.drawString("EU", col1X + 15, euY + 1, REQMT_ACCENT, true);

        String rateStr = cachedEuFmt.getOrDefault(node.id(), fmtNum(node.euPerTick()) + " /t");
        canvas.drawString(rateStr, col2X, euY + 1, TEXT_W, false);

        String totalStr = cachedTotalEuFmt.getOrDefault(node.id(), fmtNum((long) node.euPerTick() * node.durationTicks()));
        canvas.drawString(totalStr, col3X - font.width(totalStr), euY + 1, REQMT_ACCENT, true);
    }

    // ─────── Slot Icon ───────

    /**
     * Defers item/fluid icon rendering into {@link #deferredItems}/{@link #deferredFluids}
     * so they can be flushed together in a single renderLayered call.
     * Fallback (unknown type) icons are rendered immediately since they use batchable textures.
     */
    private void drawSlotIcon(SceneCanvas canvas, MaterialSlot slot,
                              int ix, int iy, float pt) {
        float iconScale = ICON_SZ / 16.0f;
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix, iy, iconScale));
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            deferredFluids.add(new DeferredFluid(slot.fluidStack(), ix, iy, iconScale, pt));
        // Slot icon fallback — immediate (batchable texture + text)
        } else {
            int c = slot.type().color;
            if (darkTheme) drawDarkSlot(canvas, ix, iy, ICON_SZ, ICON_SZ);
            else canvas.texture(Background.Slot.dark, ix, iy, ICON_SZ, ICON_SZ);
            String initial = slot.type().name().substring(0, 1);
            canvas.drawCenteredString(initial, ix + ICON_SZ / 2, iy + (ICON_SZ - 9) / 2, c);
        }
    }

    /**
     * Renders all deferred item/fluid icons in a single layeredDraw pass.
     * This reduces per-icon overhead (flushBatch + scissor + depth clear)
     * from O(N) to O(1), batching all items into one GPU submission.
     */
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

    // ─────── Status Bar ───────

    private void drawStatusBar(SceneCanvas canvas, Font font) {
        String s = String.format("步骤: %d | 缩放: %.0f%% | 中键拖动: 平移, 滚轮: 缩放",
                allSteps.size(), zoom * 100);
        int barH = 14;
        int vw = viewportW();
        canvas.fill(0, height() - barH, vw, barH, darkTheme ? 0xCC111827 : 0xCC9999AA);
        canvas.drawString(s, 4, height() - barH + 3, TEXT_W, false);
    }

    // ─────── Minimap ───────

    private void drawMinimap(SceneCanvas canvas) {
        if (totalContentW <= 0 || totalContentH <= 0) return;

        int mmX = viewportW() - MM_W - MM_PAD;
        int mmY = MM_PAD;

        // Background
        canvas.fill(mmX, mmY, MM_W, MM_H, MINIMAP_BG);
        // Border using fill (batchable) instead of strokeRect (not batchable)
        canvas.fill(mmX, mmY, MM_W, 1, MINIMAP_BORDER);              // top
        canvas.fill(mmX, mmY + MM_H - 1, MM_W, 1, MINIMAP_BORDER);  // bottom
        canvas.fill(mmX, mmY, 1, MM_H, MINIMAP_BORDER);              // left
        canvas.fill(mmX + MM_W - 1, mmY, 1, MM_H, MINIMAP_BORDER);  // right

        // Scale: world → minimap
        double scaleX = (MM_W - 4.0) / totalContentW;
        double scaleY = (MM_H - 4.0) / totalContentH;
        double scale = Math.min(scaleX, scaleY);

        // Draw cards as small rectangles
        for (StepEntry se : allSteps) {
            int rx = mmX + 2 + (int) (se.worldX * scale);
            int ry = mmY + 2 + (int) (se.worldY * scale);
            int rw = Math.max(3, (int) (CARD_W * scale));
            int rh = Math.max(2, (int) (se.cardHeight * scale));
            boolean isTgt = targetNode != null && se.node.id().equals(targetNode.id());
            int color = isTgt ? MINIMAP_TARGET : withAlpha(se.node.tierColor(), 0xAA);
            if (isTgt) canvas.fill(rx - 1, ry - 1, rw + 2, rh + 2, 0x30FFCC44);
            canvas.fill(rx, ry, rw, rh, color);
        }

        // Level divider labels as thin horizontal fills
        for (LevelDiv ld : levelDividers) {
            int lx = mmX + 2 + (int) (ld.worldX * scale);
            int ly = mmY + 2 + (int) (ld.worldY * scale);
            int lw = Math.max(3, (int) (ld.colWidth * scale));
            canvas.fill(lx, ly, lw, 1, withAlpha(LEVEL_CLR, 0x66));
        }

        // Viewport rectangle using fill (batchable) instead of strokeRect
        int vpW = Math.max(4, (int) ((viewportW() / zoom) * scale));
        int vpH = Math.max(4, (int) ((height() / zoom) * scale));
        // Clamp viewport rect size to minimap inner area
        int innerW = MM_W - 4;
        int innerH = MM_H - 4;
        vpW = Math.min(vpW, innerW);
        vpH = Math.min(vpH, innerH);
        // Clamp viewport rect position within minimap bounds
        int ivpX = Math.max(mmX + 2, Math.min((int) (mmX + 2 + panX * scale), mmX + 2 + innerW - vpW));
        int ivpY = Math.max(mmY + 2, Math.min((int) (mmY + 2 + panY * scale), mmY + 2 + innerH - vpH));
        canvas.fill(ivpX, ivpY, vpW, 1, MINIMAP_VP);              // top
        canvas.fill(ivpX, ivpY + vpH - 1, vpW, 1, MINIMAP_VP);  // bottom
        canvas.fill(ivpX, ivpY, 1, vpH, MINIMAP_VP);              // left
        canvas.fill(ivpX + vpW - 1, ivpY, 1, vpH, MINIMAP_VP);  // right
    }

    private boolean isInMinimap(double mx, double my) {
        int mmX = viewportW() - MM_W - MM_PAD;
        return mx >= mmX && mx <= mmX + MM_W && my >= MM_PAD && my <= MM_PAD + MM_H;
    }

    private void minimapClickToPan(double mx, double my) {
        if (totalContentW <= 0 || totalContentH <= 0) return;
        int mmX = viewportW() - MM_W - MM_PAD;
        double scaleX = (MM_W - 4.0) / totalContentW;
        double scaleY = (MM_H - 4.0) / totalContentH;
        double scale = Math.min(scaleX, scaleY);

        // Clamp mouse position within minimap inner area
        double clampedMx = Math.max(mmX + 2, Math.min(mx, mmX + MM_W - 2));
        double clampedMy = Math.max(MM_PAD + 2, Math.min(my, MM_PAD + MM_H - 2));

        // Convert minimap coords to world center
        double worldX = (clampedMx - mmX - 2) / scale;
        double worldY = (clampedMy - MM_PAD - 2) / scale;
        // Center viewport on that world point
        double vpWorldW = viewportW() / zoom;
        double vpWorldH = height() / zoom;
        panX = worldX - vpWorldW / 2.0;
        panY = worldY - vpWorldH / 2.0;
        // Clamp pan so viewport stays within content bounds
        panX = Math.max(0, Math.min(panX, totalContentW - vpWorldW));
        panY = Math.max(0, Math.min(panY, totalContentH - vpWorldH));
    }

    // ─────── Material Statistics ───────

    private void computeMaterialSummary() {
        if (graph == null || allSteps.isEmpty()) {
            materialByCategory = new LinkedHashMap<>();
            materialPanelContentH = 0;
            totalTimeTicks = 0;
            totalEU = 0;
            return;
        }

        Map<String, long[]> amounts = new LinkedHashMap<>();   // [consumed, produced]
        Map<String, MaterialSlot> slotRef = new LinkedHashMap<>();
        Set<String> catalystNames = new LinkedHashSet<>();
        Map<String, List<Integer>> producerSteps = new HashMap<>();
        Map<String, List<Integer>> consumerSteps = new HashMap<>();
        Map<String, Double> chanceAccum = new HashMap<>();
        Map<String, Integer> chanceCount = new HashMap<>();

        long totalTime = 0;
        long totalEnergy = 0;

        for (StepEntry step : allSteps) {
            ProcessNode node = step.node;
            totalTime += node.durationTicks();
            totalEnergy += (long) node.euPerTick() * node.durationTicks();

            for (MaterialSlot input : node.inputs()) {
                amounts.computeIfAbsent(input.materialName(), k -> new long[2])[0] += input.amount();
                slotRef.putIfAbsent(input.materialName(), input);
                if (input.isCatalyst()) catalystNames.add(input.materialName());
                consumerSteps.computeIfAbsent(input.materialName(), k -> new ArrayList<>())
                             .add(step.stepNumber);
            }
            for (MaterialSlot output : node.outputs()) {
                amounts.computeIfAbsent(output.materialName(), k -> new long[2])[1] += output.amount();
                slotRef.putIfAbsent(output.materialName(), output);
                producerSteps.computeIfAbsent(output.materialName(), k -> new ArrayList<>())
                             .add(step.stepNumber);
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
            long consumed = e.getValue()[0];
            long produced = e.getValue()[1];
            MaterialSlot ref = slotRef.get(name);
            long missing = Math.max(0, consumed - produced);
            long inventory = Math.max(0, produced - consumed);
            double avgChance = chanceCount.containsKey(name)
                    ? chanceAccum.get(name) / chanceCount.get(name) : 1.0;

            MatCategory cat;
            if (catalystNames.contains(name)) {
                cat = MatCategory.CATALYST;
            } else if (consumed > 0 && produced == 0) {
                cat = MatCategory.EXTERNAL_INPUT;
            } else if (consumed == 0 && produced > 0) {
                cat = MatCategory.BYPRODUCT;
            } else {
                cat = MatCategory.INTERMEDIATE;
            }
            byCategory.get(cat).add(new MaterialEntry(name, ref,
                    consumed, produced, missing, inventory, cat,
                    producerSteps.getOrDefault(name, List.of()),
                    consumerSteps.getOrDefault(name, List.of()),
                    avgChance));
        }

        byCategory.values().removeIf(List::isEmpty);
        this.materialByCategory = byCategory;

        // Total content height for scroll
        int h = 0;
        for (var entry : byCategory.entrySet()) {
            h += SEC_HDR_H + 2;
            h += entry.getValue().size() * (ENTRY_H + ENTRY_GAP);
            h += 8; // gap (includes space for separator between categories)
        }
        h += TOTALS_H + 10;
        this.materialPanelContentH = h;
    }

    private int panelContentVisibleH() {
        int headerH = 24;
        int topGap = 4;    // gap below header
        int bottomPad = 4; // reserve space for panel border at bottom
        return height() - headerH - topGap - bottomPad;
    }

    private boolean isInMaterialPanel(double mx, double my) {
        if (!showMaterialPanel) return false;
        int px = width() - PANEL_W;
        return mx >= px && mx <= width() && my >= 0 && my <= height();
    }

    private boolean isOnScrollbarThumb(double mx, double my) {
        if (!showMaterialPanel) return false;
        int maxScroll = Math.max(0, materialPanelContentH - panelContentVisibleH());
        if (maxScroll <= 0) return false;
        int px = width() - PANEL_W;
        int headerH = 24;
        int contentY = headerH + 4;
        int visibleH = panelContentVisibleH();
        int sbX = px + PANEL_W - SCROLLBAR_W - 3;
        int sbY = contentY + 1;
        int sbH = visibleH - 2;
        int thumbH = Math.max(16, (int) ((float) visibleH / materialPanelContentH * sbH));
        float progress = maxScroll > 0 ? matScrollTargetY / maxScroll : 0;
        int thumbY = sbY + (int) (progress * (sbH - thumbH));
        return mx >= sbX && mx <= sbX + SCROLLBAR_W && my >= thumbY && my <= thumbY + thumbH;
    }

    // ─────── Material Panel ───────

    /**
     * Draws the entire material panel: background chrome, header, and
     * scrollable content with manual scroll offset and clipping.
     */
    private void drawMaterialPanel(SceneCanvas canvas, Font font,
                                    int mouseX, int mouseY, float pt) {
        int px = width() - PANEL_W;
        int py = 0;
        int ph = height(); // full height, independent panel

        // Background panel
        canvas.fill(px, py, PANEL_W, ph, PANEL_BG);
        canvas.texture(Background.Outlined.inset, px, py, PANEL_W, ph);

        // Header
        int headerH = 24;
        canvas.texture(Background.inset, px + 2, py + 2, PANEL_W - 4, headerH);
        canvas.drawString("材料统计", px + 8, py + 8, PANEL_HDR, true);

        // ─── Scrollable content area ───
        int contentY = py + headerH + 4;
        int visibleH = panelContentVisibleH();

        // Clip to the content area using absolute screen coordinates
        Pos absP = absolutePos();
        canvas.withClip(absP.x() + px, absP.y() + contentY, PANEL_W, visibleH, () -> {
            canvas.pushTransform();
            canvas.translate(px + PANEL_PAD, contentY - matScrollY);
            renderMaterialContent(canvas, font, mouseX - px - PANEL_PAD, (int) (mouseY - contentY + matScrollY), pt,
                    matScrollY, matScrollY + visibleH);
            // Flush material panel icons in one batch
            flushDeferredIcons(canvas);
            canvas.popTransform();
        });

        // ─── Scrollbar ───
        int maxScroll = Math.max(0, materialPanelContentH - visibleH);
        if (maxScroll > 0) {
            int sbX = px + PANEL_W - SCROLLBAR_W - 3;
            int sbY = contentY + 1;
            int sbH = visibleH - 2;
            // Track background
            canvas.texture(Background.inset, sbX, sbY, SCROLLBAR_W, sbH);
            // Thumb
            int thumbH = Math.max(16, (int) ((float) visibleH / materialPanelContentH * sbH));
            float progress = maxScroll > 0 ? matScrollY / maxScroll : 0;
            int thumbY = sbY + (int) (progress * (sbH - thumbH));
            canvas.texture(CirrusTextures.Toggle.Button.up, sbX + 1, thumbY, SCROLLBAR_W - 2, thumbH);
        }
    }

    /**
     * Renders all material panel entries.
     * Coordinates are in the content's local space (0,0 = top-left of scrollable content).
     *
     * @param visMinY  top of visible window in content-local Y
     * @param visMaxY  bottom of visible window in content-local Y
     */
    private void renderMaterialContent(SceneCanvas canvas, Font font,
                                        int mouseX, int mouseY, float pt,
                                        float visMinY, float visMaxY) {
        int drawY = 0;
        int innerX = 0;
        boolean hasScrollbar = materialPanelContentH > (int)(visMaxY - visMinY);
        int innerW = PANEL_W - 2 * PANEL_PAD - (hasScrollbar ? SCROLLBAR_W + 4 : 0);
        hoveredMatName = null;

        int catIdx = 0;
        int catCount = materialByCategory.size();
        for (var catEntry : materialByCategory.entrySet()) {
            MatCategory cat = catEntry.getKey();
            List<MaterialEntry> entries = catEntry.getValue();

            // Section header — frustum cull
            int sectionTop = drawY;
            int sectionBot = drawY + SEC_HDR_H;
            if (sectionBot >= visMinY && sectionTop <= visMaxY) {
                canvas.texture(Background.inset, innerX, drawY, innerW, SEC_HDR_H);
                canvas.drawString(cat.label + " (" + entries.size() + ")",
                        innerX + 6, drawY + 5, cat.color, true);
            }
            drawY += SEC_HDR_H + 2;

            // Entries in single column (full width)
            for (MaterialEntry entry : entries) {
                int entryBot = drawY + ENTRY_H;
                if (entryBot >= visMinY && drawY <= visMaxY) {
                    drawMaterialEntry(canvas, font, entry, innerX, drawY, innerW, pt,
                            mouseX, mouseY);
                }
                drawY += ENTRY_H + ENTRY_GAP;
            }

            // Gap with separator between categories
            catIdx++;
            drawY += 3;
            if (catIdx < catCount && drawY + 1 >= visMinY && drawY <= visMaxY) {
                canvas.fill(innerX + 4, drawY, innerW - 8, 1, 0xFF555555);
            }
            drawY += 5;
        }

        // ─── Totals section ───
        drawY += 4;
        int totalsBot = drawY + 6 + TOTALS_H;
        if (totalsBot >= visMinY && drawY <= visMaxY) {
            canvas.fill(innerX + 4, drawY, innerW - 8, 1, 0xFF555555);
            drawY += 6;
            canvas.texture(Background.inset, innerX, drawY, innerW, TOTALS_H);

            int txLeft = innerX + 6;
            int txRight = innerX + innerW - 8;

            int rowY0 = drawY + 6;
            int rowY1 = drawY + 20;
            int rowY2 = drawY + 36;

            canvas.texture(Icon.Sign.Energy.time, txLeft, rowY0, 9, 9);
            canvas.drawString("总时间", txLeft + 12, rowY0, TEXT_D, false);
            String timeStr = formatDuration(totalTimeTicks);
            canvas.drawString(timeStr, txRight - font.width(timeStr), rowY0, TEXT_W, false);

            canvas.texture(Icon.Sign.Energy.eu, txLeft, rowY1, 9, 9);
            canvas.drawString("总能量", txLeft + 12, rowY1, TEXT_D, false);
            String euStr = fmtNum(totalEU) + " EU";
            canvas.drawString(euStr, txRight - font.width(euStr), rowY1, REQMT_ACCENT, true);

            canvas.fill(txLeft, rowY2 + 3, 6, 3, TEXT_G);
            canvas.drawString("步骤数", txLeft + 10, rowY2, TEXT_D, false);
            String stepStr = String.valueOf(allSteps.size());
            canvas.drawString(stepStr, txRight - font.width(stepStr), rowY2, TEXT_G, false);
        }
    }

    private void drawMaterialEntry(SceneCanvas canvas, Font font,
                                    MaterialEntry entry, int x, int y, int w,
                                    float pt, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ENTRY_H;
        if (hovered) hoveredMatName = entry.name();

        // ── Level 1: Outer entry box ──
        if (hovered) {
            canvas.texture(Background.Border.light, x, y, w, BOX_H);
        } else {
            canvas.texture(Background.Slot.dark, x, y, w, BOX_H);
        }

        int pad = 3;

        // ── Level 2: Icon slot ──
        int slotX = x + pad;
        int slotY = y + pad;
        canvas.texture(Background.Slot.dark, slotX, slotY, ICON_SLOT_SZ, ICON_SLOT_SZ);

        // Icon 26×26 centered within the slot
        int iconX = slotX + (ICON_SLOT_SZ - PANEL_ICON_SZ) / 2;
        int iconY = slotY + (ICON_SLOT_SZ - PANEL_ICON_SZ) / 2;
        drawPanelSlotIcon(canvas, entry.refSlot(), iconX, iconY, pt);

        // ── Level 2: Indicator + number columns (tightly stacked) ──
        int indX = slotX + ICON_SLOT_SZ + 2;
        int cellH = IND_CELL_SZ;
        int row1Y = slotY + (ICON_SLOT_SZ - 2 * cellH) / 2;
        int row2Y = row1Y + cellH;
        int textX = indX + IND_CELL_SZ + 2;
        int textW = w - (textX - x) - pad;

        // Row 1: available icon + available/produced number (green)
        canvas.texture(Background.inset, indX, row1Y, IND_CELL_SZ, cellH);
        canvas.texture(Icon.Sign.Amount.available, indX + 1, row1Y + 1, IND_CELL_SZ - 2, cellH - 2);

        canvas.texture(Background.inset, textX, row1Y, textW, cellH);
        String availStr = switch (entry.category()) {
            case EXTERNAL_INPUT -> "0";
            case INTERMEDIATE -> fmtNum(entry.produced());
            case BYPRODUCT -> fmtNum(entry.produced());
            case CATALYST -> fmtNum(entry.needed());
        };
        canvas.drawString(cachedTruncate(availStr, textW - 6, font), textX + 3, row1Y + 4, BAR_GREEN, false);

        // Row 2: missing icon + missing/needed number (red)
        canvas.texture(Background.inset, indX, row2Y, IND_CELL_SZ, cellH);
        canvas.texture(Icon.Sign.Amount.missing, indX + 1, row2Y + 1, IND_CELL_SZ - 2, cellH - 2);

        canvas.texture(Background.inset, textX, row2Y, textW, cellH);
        String needStr = switch (entry.category()) {
            case EXTERNAL_INPUT -> fmtNum(entry.needed());
            case INTERMEDIATE -> entry.missing() > 0 ? fmtNum(entry.missing()) : "0";
            case BYPRODUCT -> "0";
            case CATALYST -> "0";
        };
        canvas.drawString(cachedTruncate(needStr, textW - 6, font), textX + 3, row2Y + 4, BAR_RED, false);

        // ── Progress bar with border frame (top border shared with box bottom) ──
        int barFrameY = y + BOX_H;
        int barFrameH = BAR_H + 1; // bar + 1px bottom border
        // Border frame: white border (left, right, bottom), top shared with box
        canvas.fill(x, barFrameY, w, barFrameH, 0xFFFFFFFF);           // border (white)
        canvas.fill(x + 1, barFrameY, w - 2, barFrameH - 1, 0xFF494949); // inner bg
        // Bar fill
        int barX = x + 1;
        int barY = barFrameY;
        int barW = w - 2;
        switch (entry.category()) {
            case EXTERNAL_INPUT -> canvas.fill(barX, barY, barW, BAR_H, BAR_RED);
            case INTERMEDIATE -> {
                long total = Math.max(entry.needed(), entry.produced());
                if (total > 0) {
                    int greenW = (int) (barW * Math.min(1.0, (double) entry.produced() / total));
                    if (greenW > 0) canvas.fill(barX, barY, greenW, BAR_H, BAR_GREEN);
                    if (entry.missing() > 0) {
                        int redW = (int) (barW * ((double) entry.missing() / total));
                        canvas.fill(barX + greenW, barY, Math.max(1, redW), BAR_H, BAR_RED);
                    }
                }
            }
            case BYPRODUCT -> {
                int alpha = entry.chance() < 1.0 ? 0x88 : 0xBB;
                canvas.fill(barX, barY, barW, BAR_H, withAlpha(BAR_ORANGE, alpha));
            }
            case CATALYST -> canvas.fill(barX, barY, barW, BAR_H, withAlpha(BAR_PURPLE, 0x88));
        }
    }

    /** Draw slot icon at PANEL_ICON_SZ (26×26) for material panel. */
    private void drawPanelSlotIcon(SceneCanvas canvas, MaterialSlot slot,
                                    int ix, int iy, float pt) {
        float iconScale = PANEL_ICON_SZ / 16.0f;
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            deferredItems.add(new DeferredItem(stack, ix, iy, iconScale));
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            deferredFluids.add(new DeferredFluid(slot.fluidStack(), ix, iy, iconScale, pt));
        } else {
            int c = slot.type().color;
            canvas.texture(Background.Slot.dark, ix, iy, PANEL_ICON_SZ, PANEL_ICON_SZ);
            String initial = slot.type().name().substring(0, 1);
            canvas.drawCenteredString(initial, ix + PANEL_ICON_SZ / 2,
                    iy + (PANEL_ICON_SZ - 9) / 2, c);
        }
    }

    private static String formatDuration(long ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 60) return String.format("%.1fs (%dt)", seconds, ticks);
        double minutes = seconds / 60;
        if (minutes < 60) return String.format("%.1fm (%dt)", minutes, ticks);
        return String.format("%.1fh (%dt)", minutes / 60, ticks);
    }

    // ═══════════════════ Hover ═══════════════════

    private void updateHover(int mouseX, int mouseY) {
        hoveredStepId = null;
        hoveredTarget = false;
        // mouseX/mouseY are already in local widget coordinates (Scene does sceneToLocal before render)
        double lx = mouseX, ly = mouseY;
        if (lx < 0 || ly < 0 || lx > width() || ly > height()) return;
        // Don't highlight step cards when mouse is over the material panel
        if (showMaterialPanel && isInMaterialPanel(lx, ly)) return;

        double wx = panX + lx / zoom;
        double wy = panY + ly / zoom;

        // Check target header
        if (targetNode != null
                && wx >= MARGIN && wx <= MARGIN + CARD_W
                && wy >= targetHeaderY && wy <= targetHeaderY + TARGET_H) {
            hoveredTarget = true;
            return;
        }

        for (StepEntry se : allSteps) {
            if (wx >= se.worldX && wx <= se.worldX + CARD_W
                    && wy >= se.worldY && wy <= se.worldY + se.cardHeight) {
                hoveredStepId = se.node.id();
                break;
            }
        }
    }

    // ═══════════════════ Machine Display ═══════════════════

    /**
     * Returns the machine to display on a card header.
     * If there's a specific machine name set on the node, find the matching MachineInfo.
     * If machineOptions has multiple entries and no specific selection, cycle through them.
     */
    private @Nullable MachineInfo getDisplayMachine(ProcessNode node) {
        List<MachineInfo> opts = node.machineOptions();
        if (opts.isEmpty()) return null;
        if (opts.size() == 1) return opts.get(0);

        // If the node's machine name matches one of the options, use it
        for (MachineInfo mi : opts) {
            if (mi.name().equals(node.machine())) return mi;
        }
        // No match — cycle through all options
        int idx = (int) ((machineCycleTicks / CYCLE_INTERVAL_TICKS) % opts.size());
        return opts.get(idx);
    }

    // ═══════════════════ Tooltip ═══════════════════

    /**
     * Builds a tooltip for the hovered step card showing all possible machines
     * and step details.
     */
    private void updateTooltip(int mouseX, int mouseY) {
        if (!widgetHovered) return;
        Tooltip tip = Tooltip.create();
        // mouseX/mouseY are already in local widget coordinates
        // Suppress step card tooltips when mouse is over material panel,
        // but allow material entry tooltips (hoveredMatName) to show
        if (showMaterialPanel) {
            if (isInMaterialPanel(mouseX, mouseY)) {
                if (hoveredMatName == null) {
                    scene().setHoverTooltip(tip);
                    return;
                }
            }
        }
        if (hoveredTarget && targetNode != null) {
            var b = FlowChartTooltipComponent.builder();
            b.section("合成目标", TARGET_BD);
            b.gap(2);

            // Target output
            if (!targetNode.outputs().isEmpty()) {
                b.slots("产物", 0xFFCC8888, targetNode.outputs());
            }

            // Machine & tier
            b.kv("机器", 0xFF888888, targetNode.machine(), targetNode.tierColor());
            b.kv("等级", 0xFF888888, targetNode.tierName(), targetNode.tierColor());

            b.divider();

            // Inputs
            if (!targetNode.inputs().isEmpty()) {
                b.section("输入", 0xFF88CC88);
                b.slots(null, 0, targetNode.inputs());
            }

            b.divider();

            // Stats
            b.kv("时长", targetNode.durationFormatted());
            b.kv("速率", 0xFF888888, targetNode.euFormatted(), REQMT_ACCENT);
            long totalEU = (long) targetNode.euPerTick() * targetNode.durationTicks();
            b.kv("总 EU", 0xFF888888, fmtNum(totalEU) + " EU", 0xFFFFCC44);
            b.kv("合成数量", 0xFF888888, "\u00d7 " + craftCount, 0xFFFFFFFF);

            tip.add(b.build());
        } else if (hoveredStepId != null) {
            ProcessNode node = findNode(hoveredStepId);
            if (node != null) {
                var b = FlowChartTooltipComponent.builder();
                // Recipe name as section header with tier color accent
                b.section(node.name(), node.tierColor());
                b.gap(2);

                // Machine info — show icons if available
                if (!node.machineOptions().isEmpty()) {
                    for (MachineInfo mi : node.machineOptions()) {
                        boolean sel = mi.name().equals(node.machine());
                        ItemStack icon = mi.icon();
                        int fallback = sel ? 0xFFFFFF55 : 0xFF444466;
                        if (sel) {
                            b.iconText(icon, fallback, mi.name(), 0xFFFFFF55,
                                    "当前", 0xFF88CC88);
                        } else {
                            b.iconText(icon, fallback, mi.name(), 0xFF888888);
                        }
                    }
                } else {
                    b.kv("机器", 0xFF888888, node.machine(), node.tierColor());
                }

                b.divider();

                // Input / Output sections with accent bar headers
                if (!node.inputs().isEmpty()) {
                    b.section("输入", 0xFF88CC88);
                    b.slots(null, 0, node.inputs());
                }
                if (!node.outputs().isEmpty()) {
                    b.section("输出", 0xFFCC8888);
                    b.slots(null, 0, node.outputs());
                }

                b.divider();

                // Stats as clean key-value pairs
                b.kv("时长", node.durationFormatted());
                b.kv("速率", 0xFF888888, node.euFormatted(), REQMT_ACCENT);
                long stepTotalEU = (long) node.euPerTick() * node.durationTicks();
                b.kv("总 EU", 0xFF888888, fmtNum(stepTotalEU) + " EU", 0xFFFFCC44);
                b.kv("等级", 0xFF888888, node.tierName(), node.tierColor());

                tip.add(b.build());
            }
        } else if (hoveredMatName != null) {
            // Material entry tooltip
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
                        b.kv("需求量", 0xFF888888, fmtNum(found.needed()), BAR_RED);
                        b.text("全部需外部提供", TEXT_D);
                        b.bar(1.0f, BAR_RED);
                    }
                    case INTERMEDIATE -> {
                        b.kv("消耗量", 0xFF888888, fmtNum(found.needed()), 0xFF88CC88);
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), 0xFFCC8888);
                        if (found.missing() > 0)
                            b.kv("缺少", 0xFFCC4444, fmtNum(found.missing()), 0xFFFF6666);
                        if (found.inventory() > 0)
                            b.kv("盈余", 0xFF44CC66, "+" + fmtNum(found.inventory()), 0xFF66EE88);
                        float ratio = found.needed() > 0
                                ? (float) found.produced() / found.needed() : 1.0f;
                        b.bar(Math.min(1.0f, ratio), ratio >= 1.0f ? BAR_GREEN : BAR_RED);
                    }
                    case BYPRODUCT -> {
                        b.kv("产出量", 0xFF888888, fmtNum(found.produced()), BAR_ORANGE);
                        if (found.chance() < 1.0)
                            b.kv("概率", 0xFF888888,
                                    String.format("%.1f%%", found.chance() * 100), 0xFFCCCC44);
                        b.bar((float) found.chance(), BAR_ORANGE);
                    }
                    case CATALYST -> {
                        b.kv("用量", 0xFF888888, fmtNum(found.needed()), BAR_PURPLE);
                        b.text("使用后归还", TEXT_D);
                        b.bar(1.0f, BAR_PURPLE);
                    }
                }

                if (!found.sources().isEmpty() || !found.consumers().isEmpty()) {
                    b.divider();
                    if (!found.sources().isEmpty())
                        b.kv("产自", 0xFF88CC88,
                                "步骤 " + joinSteps(found.sources()), 0xFFAADDAA);
                    if (!found.consumers().isEmpty())
                        b.kv("消耗于", 0xFFCC8888,
                                "步骤 " + joinSteps(found.consumers()), 0xFFDDAAAA);
                }

                tip.add(b.build());
            }
        }
        scene().setHoverTooltip(tip);
    }

    private @Nullable ProcessNode findNode(String id) {
        for (StepEntry se : allSteps) {
            if (se.node.id().equals(id)) return se.node;
        }
        return null;
    }

    // ═══════════════════ Graph Analysis ═══════════════════

    private static List<ProcessNode> topoSort(ProcessGraph graph) {
        Map<String, Integer> inDeg = new HashMap<>();
        for (ProcessNode n : graph.nodes()) inDeg.put(n.id(), 0);
        for (ProcessEdge e : graph.edges()) inDeg.merge(e.toNodeId(), 1, Integer::sum);

        Deque<String> queue = new ArrayDeque<>();
        for (var entry : inDeg.entrySet())
            if (entry.getValue() == 0) queue.add(entry.getKey());

        List<ProcessNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nid = queue.poll();
            ProcessNode node = graph.node(nid);
            if (node != null) result.add(node);
            for (ProcessEdge e : graph.outgoingEdges(nid)) {
                int nd = inDeg.merge(e.toNodeId(), -1, Integer::sum);
                if (nd == 0) queue.add(e.toNodeId());
            }
        }
        return result;
    }

    // ═══════════════════ Utilities ═══════════════════

    private static String joinSteps(List<Integer> steps) {
        if (steps.size() == 1) return String.valueOf(steps.get(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(steps.size(), 3); i++) {
            if (i > 0) sb.append(',');
            sb.append(steps.get(i));
        }
        if (steps.size() > 3) sb.append("..");
        return sb.toString();
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

    /**
     * Frame-cached truncation: avoids repeated font.width() + substring
     * operations when the same text/width pair is rendered multiple times
     * per frame (e.g. when overlapping cards share material names).
     */
    private String cachedTruncate(String text, int maxPxW, Font font) {
        // Combine text hash and maxPxW into a single cache key
        long key = ((long) text.hashCode() << 32) | (maxPxW & 0xFFFFFFFFL);
        return truncationCache.computeIfAbsent(key, k -> truncate(text, maxPxW, font));
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int brighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8)  & 0xFF) + amount);
        int b = Math.min(255, ( color        & 0xFF) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
