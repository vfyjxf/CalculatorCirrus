package dev.vfyjxf.cirrus.ui.widget.flowchart;

import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.CompoundLayout;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.EdgeRoute;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.GroupBox;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.LayoutResult;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.MirrorLink;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.NodePosition;
import dev.vfyjxf.cirrus.ui.widget.flowchart.layout.SugiyamaLayout.PortInfo;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialType;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A self-contained widget that renders an interactive process flow chart.
 * <p>
 * Features:
 * <ul>
 *   <li>Renders process cards with machine name, tier badge, inputs/outputs</li>
 *   <li>Renders material flow edges as colour-coded Bézier curves with arrowheads</li>
 *   <li>Pan via left-mouse-drag, zoom via scroll wheel</li>
 *   <li>Hover highlighting for cards and edges</li>
 *   <li>Minimap overlay showing the viewport position</li>
 * </ul>
 */
public class ProcessFlowWidget extends Widget {

    // ─────────────────── Palette ───────────────────

    private static final int BG_COLOR         = 0xFF0E0E16;
    private static final int GRID_COLOR       = 0xFF1A1A24;
    private static final int CARD_BG          = 0xFF1E1E2E;
    private static final int CARD_BG_HOVER    = 0xFF2A2A3E;
    private static final int CARD_BORDER      = 0xFF44446A;
    private static final int CARD_BORDER_HOVER= 0xFF6666BB;
    private static final int DIVIDER_COLOR    = 0xFF3A3A55;
    private static final int TEXT_WHITE       = 0xFFEEEEEE;
    private static final int TEXT_GRAY        = 0xFFAAAAAA;
    private static final int TEXT_DIM         = 0xFF777777;
    private static final int HEADER_BG        = 0xFF252540;
    private static final int ARROW_HEAD_SIZE  = 6;
    private static final int MINIMAP_BG       = 0xAA111122;
    private static final int MINIMAP_BORDER   = 0xFF444466;
    private static final int MINIMAP_VIEWPORT = 0x6644AAFF;

    // ─────────────────── Layout constants ───────────────────

    private static final int CARD_RADIUS   = 5;
    private static final int HEADER_HEIGHT = 18;
    private static final int NAME_ROW_H    = 14;
    private static final int SECTION_LABEL_H = 11;
    private static final int SLOT_ROW_H    = 18;  // 16px icon + 2px gap
    private static final int STATS_BAR_H   = 13;
    private static final int PORT_RADIUS   = 4;
    private static final float EDGE_THICKNESS = 1.8f;
    private static final float EDGE_GLOW_WIDTH = 3.0f;
    private static final int GRID_SPACING  = 40;

    // ─────────────────── State ───────────────────

    private ProcessGraph graph;
    private LayoutResult layout;

    // Viewport: panX/panY is the world-coordinate of the top-left corner of the visible area
    private double panX = 0;
    private double panY = 0;
    private float zoom = 1.0f;
    private static final float MIN_ZOOM = 0.15f;
    private static final float MAX_ZOOM = 3.0f;

    // Drag state
    private boolean dragging = false;
    private double dragStartX, dragStartY;
    private double dragPanStartX, dragPanStartY;

    // Hover state
    private @Nullable String hoveredNodeId = null;
    private @Nullable EdgeRoute hoveredEdge = null;
    private int hoveredMirrorLinkId = -1;
    private @Nullable MirrorLink hoveredMirrorLink = null;

    // Group colour cache: nodeId → group ARGB colour (for edge tinting)
    private Map<String, Integer> nodeGroupColors = Map.of();
    // The single final-target node (unique sink of the graph), or null
    private @Nullable String targetNodeId = null;

    // ─────────────────── Construction ───────────────────

    public ProcessFlowWidget() {
        // Register input handlers
        onMouseClicked((input, ctx) -> {
            if (input.isLeftClick()) {
                // Check for satellite badge click → jump to remote node
                MirrorLink clicked = getMirrorLinkAt(input.mouseX(), input.mouseY());
                if (clicked != null) {
                    panToNode(clicked.remoteNodeId());
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                // Otherwise start panning
                this.dragging = true;
                this.dragStartX = input.mouseX();
                this.dragStartY = input.mouseY();
                this.dragPanStartX = panX;
                this.dragPanStartY = panY;
                ctx.cancel();
            } else if (input.key().getValue() == 2) { // middle click → reset view
                resetView();
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });

        onMouseReleased((input, ctx) -> {
            if (input.isLeftClick()) {
                this.dragging = false;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });

        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            if (this.dragging) {
                panX = dragPanStartX - (input.mouseX() - dragStartX) / zoom;
                panY = dragPanStartY - (input.mouseY() - dragStartY) / zoom;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });

        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, ctx) -> {
            float oldZoom = zoom;
            if (scrollY > 0) {
                zoom = Math.min(MAX_ZOOM, zoom * 1.15f);
            } else if (scrollY < 0) {
                zoom = Math.max(MIN_ZOOM, zoom / 1.15f);
            }
            // Zoom toward mouse cursor: adjust panX/panY so the world point under the cursor stays fixed
            if (oldZoom != zoom) {
                double worldMouseX = panX + mouseX / oldZoom;
                double worldMouseY = panY + mouseY / oldZoom;
                panX = worldMouseX - mouseX / zoom;
                panY = worldMouseY - mouseY / zoom;
            }
            ctx.cancel();
            return EventDispatch.consumed;
        });
    }

    /**
     * Sets the graph and computes layout.
     */
    public ProcessFlowWidget setGraph(ProcessGraph graph) {
        this.graph = graph;
        this.layout = new CompoundLayout()
                .cardWidth(280)
                .layerGap(180)
                .nodeGap(50)
                .maxSweeps(30)
                .layout(graph);

        // Build node→group colour lookup
        Map<String, Integer> colors = new HashMap<>();
        for (var g : graph.groups()) {
            for (String nid : g.childNodeIds()) {
                colors.put(nid, g.color());
            }
        }
        this.nodeGroupColors = colors;

        // Identify the single sink (final target recipe)
        List<ProcessNode> sinks = graph.sinks();
        this.targetNodeId = sinks.size() == 1 ? sinks.get(0).id() : null;

        resetView();
        return this;
    }

    private void resetView() {
        if (layout == null) return;
        // Centre the graph in the viewport
        panX = -50;
        panY = -50;
        // Try to fit the graph into the widget area
        if (width() > 0 && height() > 0 && layout.totalWidth() > 0 && layout.totalHeight() > 0) {
            float zoomX = (float) (width() / (layout.totalWidth() + 100));
            float zoomY = (float) (height() / (layout.totalHeight() + 100));
            zoom = Math.max(MIN_ZOOM, Math.min(1.0f, Math.min(zoomX, zoomY)));
            panX = -(width() / zoom - layout.totalWidth()) / 2.0;
            panY = -(height() / zoom - layout.totalHeight()) / 2.0;
        }
    }

    // ═══════════════════ Rendering ═══════════════════

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (graph == null || layout == null) {
            canvas.fill(0, 0, width(), height(), BG_COLOR);
            canvas.drawCenteredString("No process graph loaded", width() / 2, height() / 2 - 4, TEXT_DIM);
            return;
        }

        Font font = Minecraft.getInstance().font;

        // Update hover state
        updateHover(mouseX, mouseY);

        // Background
        canvas.fill(0, 0, width(), height(), BG_COLOR);

        // Clipped + transformed drawing area
        canvas.withClip(0, 0, width(), height(), () -> {
            canvas.pushTransform();
            canvas.scale(zoom);
            canvas.translate((float) -panX, (float) -panY);

            // Draw grid
            drawGrid(canvas);

            // Draw group boxes (behind everything else)
            drawGroupBoxes(canvas, font);

            // Draw edges (behind cards)
            drawEdges(canvas, font);

            // Draw cards
            drawCards(canvas, font, partialTicks);

            // Draw satellite mirror-link badges
            drawMirrorLinks(canvas, font);

            // Draw satellite trace line when a mirror-link pair is hovered
            drawSatelliteTrace(canvas);

            canvas.popTransform();
        });

        // HUD overlays (not transformed)
        drawMinimap(canvas);
        drawStatusBar(canvas, font);
    }

    // ─────────────────── Grid ───────────────────

    private void drawGrid(SceneCanvas canvas) {
        // Compute visible world-space bounds
        double visW = width() / zoom;
        double visH = height() / zoom;
        int startX = ((int) (panX / GRID_SPACING) - 1) * GRID_SPACING;
        int startY = ((int) (panY / GRID_SPACING) - 1) * GRID_SPACING;
        int endX = (int) (panX + visW) + GRID_SPACING;
        int endY = (int) (panY + visH) + GRID_SPACING;

        for (int gx = startX; gx <= endX; gx += GRID_SPACING) {
            canvas.line(gx, startY, gx, endY, 0.5f, GRID_COLOR);
        }
        for (int gy = startY; gy <= endY; gy += GRID_SPACING) {
            canvas.line(startX, gy, endX, gy, 0.5f, GRID_COLOR);
        }
    }

    // ─────────────────── Group Boxes ───────────────────

    private static final int GROUP_BG        = 0x10FFFFFF;
    private static final int GROUP_LABEL_H   = 22;
    private static final int GROUP_BORDER_W  = 2;

    private void drawGroupBoxes(SceneCanvas canvas, Font font) {
        if (layout.groupBoxes() == null || layout.groupBoxes().isEmpty()) return;
        for (GroupBox gb : layout.groupBoxes().values()) {
            int x = (int) gb.x();
            int y = (int) gb.y();
            int w = (int) gb.width();
            int h = (int) gb.height();
            int color = gb.color();

            // Outer glow / shadow
            canvas.shadow(x, y, w, h, 8, 4, 12, withAlpha(color, 0x20));

            // Background fill — noticeable tint using the group colour
            canvas.fill(x, y, w, h, withAlpha(color, 0x22));

            // Thick border (3px gradient: outer bright → inner softer)
            for (int i = 0; i < 3; i++) {
                int a = i == 0 ? 0xCC : (i == 1 ? 0x88 : 0x44);
                canvas.strokeRect(x + i, y + i, w - 2 * i, h - 2 * i,
                        withAlpha(color, a));
            }

            // Header bar — solid and strongly tinted
            canvas.fill(x + 3, y + 3,
                    w - 6, GROUP_LABEL_H,
                    withAlpha(color, 0x55));

            // Divider below header
            canvas.fill(x + 3, y + 3 + GROUP_LABEL_H,
                    w - 6, 2, withAlpha(color, 0x99));

            // Label with icon — fully opaque
            String label = gb.label();
            if (label != null && !label.isEmpty()) {
                canvas.drawString("\u25A0 " + label, x + 8, y + 7,
                        brighten(color, 80), true);
            }
        }
    }

    // ─────────────────── Edges ───────────────────

    private void drawEdges(SceneCanvas canvas, Font font) {
        for (EdgeRoute route : layout.edgeRoutes()) {
            boolean isHovered = (hoveredEdge == route);
            boolean connectsHovered = hoveredNodeId != null &&
                    (route.edge().fromNodeId().equals(hoveredNodeId) ||
                     route.edge().toNodeId().equals(hoveredNodeId));

            // Determine edge colour: prefer source-node's group colour, fallback to material type
            int baseColor = edgeColorForRoute(route);
            float thickness = EDGE_THICKNESS;
            float glow = 0;
            int glowColor = 0;

            if (isHovered || connectsHovered) {
                thickness = EDGE_THICKNESS + 0.8f;
                glow = EDGE_GLOW_WIDTH;
                glowColor = withAlpha(baseColor, 0x40);
            } else if (hoveredNodeId != null) {
                baseColor = withAlpha(baseColor, 0x44);
            }

            List<double[]> pts = route.points();
            if (pts.size() < 2) continue;

            // Draw orthogonal (Manhattan) polyline segments
            for (int seg = 0; seg < pts.size() - 1; seg++) {
                double[] p0 = pts.get(seg);
                double[] p1 = pts.get(seg + 1);
                if (glow > 0) {
                    canvas.line((float) p0[0], (float) p0[1],
                            (float) p1[0], (float) p1[1],
                            thickness + glow, glowColor);
                }
                canvas.line((float) p0[0], (float) p0[1],
                        (float) p1[0], (float) p1[1],
                        thickness, baseColor);
            }

            // Arrowhead at target port — points rightward (left-to-right layout)
            double[] last = pts.get(pts.size() - 1);
            float tipX = (float) last[0];
            float tipY = (float) last[1];
            canvas.line(tipX, tipY, tipX - ARROW_HEAD_SIZE, tipY - ARROW_HEAD_SIZE * 0.45f, 2.0f, baseColor);
            canvas.line(tipX, tipY, tipX - ARROW_HEAD_SIZE, tipY + ARROW_HEAD_SIZE * 0.45f, 2.0f, baseColor);

            // Material label — only shown when this edge or a connected node is hovered
            if (isHovered || connectsHovered) {
                int midSeg = pts.size() / 2;
                double[] mp0 = pts.get(midSeg - 1);
                double[] mp1 = pts.get(midSeg);
                float labelX = (float) ((mp0[0] + mp1[0]) / 2);
                float labelY = (float) ((mp0[1] + mp1[1]) / 2) - 5;

                String label = route.edge().materialName();
                if (route.edge().amount() > 1) {
                    label += " x" + route.edge().amount();
                }

                int labelWidth = font.width(label);
                canvas.fill((int) labelX - labelWidth / 2 - 3, (int) labelY - 2,
                        labelWidth + 6, 12, withAlpha(BG_COLOR, 0xDD));
                canvas.drawCenteredString(label, (int) labelX, (int) labelY, withAlpha(baseColor, 0xEE));
            }
        }
    }

    // ─────────────────── Cards ───────────────────

    // ─── Target-node (final recipe) palette ───
    private static final int TARGET_BG          = 0xFF1C1C30;
    private static final int TARGET_BG_HOVER    = 0xFF2A2A44;
    private static final int TARGET_BORDER      = 0xFFCCA020;
    private static final int TARGET_BORDER_HOVER= 0xFFFFD050;
    private static final int TARGET_HEADER_BG   = 0xFF302818;
    private static final int TARGET_GLOW        = 0x30FFCC44;

    private void drawCards(SceneCanvas canvas, Font font,float partialTicks) {
        for (var entry : layout.positions().entrySet()) {
            String nodeId = entry.getKey();
            NodePosition pos = entry.getValue();
            ProcessNode node = graph.node(nodeId);
            if (node == null) continue;

            boolean hovered = nodeId.equals(hoveredNodeId);
            boolean isTarget = nodeId.equals(targetNodeId);
            int x = (int) pos.x();
            int y = (int) pos.y();
            int w = (int) pos.width();
            int h = (int) pos.height();

            // Card shadow — larger for target node
            if (isTarget) {
                canvas.shadow(x, y, w, h, CARD_RADIUS + 2, 8, 16, 0x50000000);
                // Outer glow ring
                canvas.shadow(x, y, w, h, CARD_RADIUS + 2, 6, 12, TARGET_GLOW);
            } else {
                canvas.shadow(x, y, w, h, CARD_RADIUS, 4, 8, 0x40000000);
            }

            // Card body
            int bg, border;
            if (isTarget) {
                bg = hovered ? TARGET_BG_HOVER : TARGET_BG;
                border = hovered ? TARGET_BORDER_HOVER : TARGET_BORDER;
            } else {
                bg = hovered ? CARD_BG_HOVER : CARD_BG;
                border = hovered ? CARD_BORDER_HOVER : CARD_BORDER;
            }
            float borderW = isTarget ? 2.0f : 1.0f;
            canvas.roundedRect(x, y, w, h, CARD_RADIUS, bg, borderW, border);

            // Header background (top portion with machine name)
            int headerBg = isTarget ? TARGET_HEADER_BG : HEADER_BG;
            canvas.roundedRect(x + 1, y + 1, w - 2, HEADER_HEIGHT,
                    CARD_RADIUS - 1, CARD_RADIUS - 1, 0, 0, headerBg, 0, 0);

            // Target badge: "\u2605 FINAL TARGET" label in header
            if (isTarget) {
                canvas.drawString("\u2605 FINAL", x + w - font.width("\u2605 FINAL") - 6,
                        y + 5, 0xFFFFCC44, true);
            }

            // Tier badge (small colored dot + tier name)
            int tierColor = node.tierColor();
            canvas.circle(x + 7, y + HEADER_HEIGHT / 2.0f + 1, 3, tierColor);
            canvas.drawString(node.tierName(), x + 13, y + 5, tierColor, true);

            // Machine name (right-aligned in header)
            String machineName = node.machine();
            int machineWidth = font.width(machineName);
            canvas.drawString(machineName, x + w - machineWidth - 4, y + 5, TEXT_DIM, false);

            // Divider below header
            canvas.fill(x + 2, y + HEADER_HEIGHT, w - 4, 1, DIVIDER_COLOR);

            // Recipe name (word-wrapped up to 2 lines)
            int nameY = y + HEADER_HEIGHT + 1;
            List<String> nameLines = wrapText(node.name(), w - 8, font);
            for (int li = 0; li < nameLines.size(); li++) {
                canvas.drawString(nameLines.get(li), x + 4, nameY + 3 + li * NAME_ROW_H, TEXT_WHITE, li == 0);
            }

            // Divider below recipe name
            int sectionY = nameY + NAME_ROW_H * nameLines.size();
            canvas.fill(x + 2, sectionY, w - 4, 1, DIVIDER_COLOR);

            // Section labels: INPUTS (left) | OUTPUTS (right)
            int halfW = w / 2;
            int labelY = sectionY + 1;
            canvas.drawString("\u25b8 INPUTS", x + 6, labelY + 2, 0xFF88CC88, true);
            canvas.drawString("\u25b8 OUTPUTS", x + halfW + 4, labelY + 2, 0xFFCC8888, true);

            // Vertical separator between columns
            int slotAreaTop = sectionY;
            int slotAreaBot = y + h - STATS_BAR_H - 1;
            canvas.fill(x + halfW, slotAreaTop, 1, slotAreaBot - slotAreaTop, DIVIDER_COLOR);

            // Slot rows
            int slotStartY = labelY + SECTION_LABEL_H;

            // Draw input slots (left column)
            for (int i = 0; i < node.inputs().size(); i++) {
                MaterialSlot slot = node.inputs().get(i);
                int rowY = slotStartY + i * SLOT_ROW_H;
                int typeColor = slot.type().color;
                drawSlotIcon(canvas, font, slot, x + 4, rowY, partialTicks);
                String text = truncate(slot.materialName() + " x" + slot.amount(), halfW - 24, font);
                if (slot.isCatalyst()) text += " [C]";
                canvas.drawString(text, x + 22, rowY + 4, withAlpha(typeColor, 0xDD), false);
            }

            // Draw output slots (right column)
            for (int i = 0; i < node.outputs().size(); i++) {
                MaterialSlot slot = node.outputs().get(i);
                int rowY = slotStartY + i * SLOT_ROW_H;
                int typeColor = slot.type().color;
                drawSlotIcon(canvas, font, slot, x + halfW + 3, rowY, partialTicks);
                String text = truncate(slot.materialName() + " x" + slot.amount(), halfW - 24, font);
                if (slot.chance() < 1.0) text += String.format(" %.0f%%", slot.chance() * 100);
                canvas.drawString(text, x + halfW + 21, rowY + 4, withAlpha(typeColor, 0xDD), false);
            }

            // Bottom divider + stats bar
            int barY = y + h - STATS_BAR_H;
            canvas.fill(x + 2, barY - 1, w - 4, 1, DIVIDER_COLOR);
            String stats = node.durationFormatted() + " \u2502 " + node.euFormatted();
            canvas.drawString(stats, x + 4, barY + 2, TEXT_DIM, false);

            // Draw input ports along LEFT edge (using layout's reordered port indices)
            drawPorts(canvas, pos.x(), pos.y(), pos.height(), node.inputs(), true, hovered, nodeId);

            // Draw output ports along RIGHT edge
            drawPorts(canvas, pos.x() + pos.width(), pos.y(), pos.height(), node.outputs(), false, hovered, nodeId);
        }
    }

    /**
     * Draws port circles along a card edge (left for inputs, right for outputs).
     * Port positions use the layout's reordered indices for perfect alignment with edges.
     */
    private void drawPorts(SceneCanvas canvas, double portX, double cardY, double cardH,
                           List<MaterialSlot> slots, boolean isInput, boolean cardHovered,
                           String nodeId) {
        if (slots.isEmpty()) return;
        int count = slots.size();
        for (int i = 0; i < count; i++) {
            MaterialSlot slot = slots.get(i);
            int reordered = SugiyamaLayout.reorderedPort(layout.portOrder(), nodeId, isInput, i);
            float pxf = (float) portX;
            float pyf = (float) (cardY + SugiyamaLayout.portYOffset(reordered, count, (int) cardH));
            int color = slot.type().color;

            if (cardHovered) {
                canvas.circle(pxf, pyf, PORT_RADIUS + 1.5f, withAlpha(color, 0x33));
            }
            canvas.circle(pxf, pyf, PORT_RADIUS, withAlpha(CARD_BG, 0xFF));
            canvas.circle(pxf, pyf, PORT_RADIUS - 1.0f, color);
        }
    }

    // ─────────────────── Slot Icon Rendering ───────────────────

    /**
     * Draws a 16×16 material icon for a slot: real item/fluid texture when available,
     * otherwise a coloured type-indicator square.
     */
    private static void drawSlotIcon(SceneCanvas canvas, Font font, MaterialSlot slot,
                                     int iconX, int iconY, float partialTicks) {
        ItemStack stack = slot.itemStack();
        if (stack != null && !stack.isEmpty()) {
            canvas.renderItem(stack, iconX, iconY);
            canvas.renderItemDecorations(stack, iconX, iconY);
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            // Render fluid via FluidStackRenderer
            canvas.renderLayered(g -> {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(iconX, iconY, 0);
                FluidStackRenderer.instance.renderBase(g, slot.fluidStack(), 16, 16, partialTicks);
                pose.popPose();
            });
            // Amount overlay for fluid
            String amt = shortAmount(slot.amount());
            int tw = font.width(amt);
            canvas.drawString(amt, iconX + 16 - tw, iconY + 8, 0xFFFFFFFF, true);
        } else {
            // Fallback: coloured type square with initial
            int c = slot.type().color;
            canvas.fill(iconX, iconY, 16, 16, withAlpha(c, 0x55));
            canvas.strokeRect(iconX, iconY, 16, 16, 1, withAlpha(c, 0xAA));
            String initial = slot.type().name().substring(0, 1);
            canvas.drawCenteredString(initial, iconX + 8, iconY + 4, c);
        }
    }

    /** Compact amount formatting for overlays. */
    private static String shortAmount(int amount) {
        if (amount < 1000) return String.valueOf(amount);
        if (amount < 1_000_000) return String.format("%.1fk", amount / 1000.0);
        return String.format("%.1fM", amount / 1_000_000.0);
    }

    // ─────────────────── Satellite Mirror Links ───────────────────

    /** Color constants for mirror-link badges. */
    private static final int MIRROR_BG       = 0xFF1A1A30;
    private static final int MIRROR_BG_HOVER = 0xFF2A2A48;
    private static final int MIRROR_BORDER   = 0xFF5555AA;
    private static final int MIRROR_TEXT     = 0xFFBBBBEE;
    private static final int MIRROR_LINK_OUT = 0xFF66AAFF; // source‐side accent
    private static final int MIRROR_LINK_IN  = 0xFFFFAA66; // target‐side accent

    /**
     * Draws satellite mirror-link badges for edges that span too many layers.
    /**
     * Draws satellite mirror-link badges for edges that span too many layers.
     * Each badge is a mini-card showing direction, remote node name, material info,
     * and remote machine/tier. Click to pan to the remote node.
     */
    private void drawMirrorLinks(SceneCanvas canvas, Font font) {
        if (layout.mirrorLinks() == null || layout.mirrorLinks().isEmpty()) return;

        int badgeW = SugiyamaLayout.MIRROR_BADGE_W;
        int badgeH = SugiyamaLayout.MIRROR_BADGE_H;

        // Also consider hovered host node to highlight connected badges
        int effectiveHoveredId = hoveredMirrorLinkId;
        if (effectiveHoveredId < 0 && hoveredNodeId != null) {
            for (MirrorLink ml : layout.mirrorLinks()) {
                if (ml.hostNodeId().equals(hoveredNodeId)) {
                    effectiveHoveredId = ml.linkId();
                    break;
                }
            }
        }

        for (MirrorLink ml : layout.mirrorLinks()) {
            int bx = (int) ml.badgeX();
            int by = (int) ml.badgeY();
            boolean pairHovered = (effectiveHoveredId >= 0 && ml.linkId() == effectiveHoveredId);

            // Accent color based on direction
            int accent = ml.isSource() ? MIRROR_LINK_OUT : MIRROR_LINK_IN;
            int bg = pairHovered ? MIRROR_BG_HOVER : MIRROR_BG;
            int border = pairHovered ? accent : MIRROR_BORDER;

            // Badge shadow
            canvas.shadow(bx, by, badgeW, badgeH, 3, 2, 5, 0x30000000);

            // Badge body with left accent strip
            canvas.roundedRect(bx, by, badgeW, badgeH, 3, bg, 1.0f, border);
            canvas.fill(bx, by + 2, 3, badgeH - 4, accent);

            // Line 1 (y+4): direction arrow + remote node name
            String dirArrow = ml.isSource() ? "\u2192 " : "\u2190 ";
            ProcessNode remoteNode = graph.node(ml.remoteNodeId());
            String remoteName = remoteNode != null ? remoteNode.name() : ml.remoteNodeId();
            String line1 = truncate(dirArrow + remoteName, badgeW - 14, font);
            canvas.drawString(line1, bx + 7, by + 4, pairHovered ? 0xFFFFFFFF : MIRROR_TEXT, true);

            // Line 2 (y+16): material type dot + material name × amount
            int dotColor = ml.edge().type().color;
            canvas.circle(bx + 10, by + 19.5f, 2.5f, dotColor);
            String matInfo = ml.material() + " x" + ml.amount();
            canvas.drawString(truncate(matInfo, badgeW - 20, font), bx + 16, by + 16, withAlpha(dotColor, 0xDD), false);

            // Line 3 (y+28): remote machine + tier badge
            if (remoteNode != null) {
                String machineInfo = truncate(remoteNode.machine(), badgeW - 40, font);
                canvas.drawString(machineInfo, bx + 7, by + 28, TEXT_DIM, false);
                // Tier indicator
                int tierColor = remoteNode.tierColor();
                String tierStr = remoteNode.tierName();
                int tierW = font.width(tierStr) + 4;
                canvas.fill(bx + badgeW - tierW - 4, by + 27, tierW, 10, withAlpha(tierColor, 0x33));
                canvas.drawString(tierStr, bx + badgeW - tierW - 2, by + 28, tierColor, false);
            }

            // Thin connecting line from badge to host node edge
            NodePosition hostPos = layout.positions().get(ml.hostNodeId());
            if (hostPos != null) {
                float lineEndX = bx + (ml.isSource() ? badgeW : 0);
                float lineEndY = by + badgeH / 2.0f;
                float lineStartX, lineStartY;
                if (ml.isSource()) {
                    lineStartX = (float) hostPos.right();
                    lineStartY = (float) hostPos.bottom();
                } else {
                    lineStartX = (float) hostPos.x();
                    lineStartY = (float) hostPos.bottom();
                }
                float lineAlpha = pairHovered ? 0xAA : 0x55;
                canvas.line(lineStartX, lineStartY, lineStartX, lineEndY, 1.0f, withAlpha(accent, (int) lineAlpha));
                canvas.line(lineStartX, lineEndY, lineEndX, lineEndY, 1.0f, withAlpha(accent, (int) lineAlpha));
            }
        }
    }

    // ─────────────────── Minimap ───────────────────

    private void drawMinimap(SceneCanvas canvas) {
        if (layout == null || layout.totalWidth() <= 0 || layout.totalHeight() <= 0) return;

        int mmWidth = 120;
        int mmHeight = 80;
        int mmX = width() - mmWidth - 8;
        int mmY = 8;

        // Background
        canvas.fill(mmX, mmY, mmWidth, mmHeight, MINIMAP_BG);
        canvas.strokeRect(mmX, mmY, mmWidth, mmHeight, MINIMAP_BORDER);

        // Scale factor: world → minimap
        double scaleX = (mmWidth - 4.0) / (layout.totalWidth() + 100);
        double scaleY = (mmHeight - 4.0) / (layout.totalHeight() + 100);
        double scale = Math.min(scaleX, scaleY);

        // Draw group boxes in minimap
        if (layout.groupBoxes() != null) {
            for (GroupBox gb : layout.groupBoxes().values()) {
                int gx = mmX + 2 + (int) (gb.x() * scale);
                int gy = mmY + 2 + (int) (gb.y() * scale);
                int gw = Math.max(3, (int) (gb.width() * scale));
                int gh = Math.max(2, (int) (gb.height() * scale));
                canvas.strokeRect(gx, gy, gw, gh, withAlpha(gb.color(), 0x66));
            }
        }

        // Draw nodes as tiny dots
        for (var pos : layout.positions().values()) {
            int nx = mmX + 2 + (int) (pos.x() * scale);
            int ny = mmY + 2 + (int) (pos.y() * scale);
            int nw = Math.max(2, (int) (pos.width() * scale));
            int nh = Math.max(1, (int) (pos.height() * scale));
            boolean isTgt = pos.nodeId().equals(targetNodeId);
            if (isTgt) {
                // Target node stands out in minimap
                canvas.fill(nx - 1, ny - 1, nw + 2, nh + 2, 0x40FFCC44);
            }
            ProcessNode node = graph.node(pos.nodeId());
            int color = isTgt ? 0xFFFFCC44 : (node != null ? node.tierColor() : TEXT_GRAY);
            canvas.fill(nx, ny, nw, nh, withAlpha(color, 0xAA));
        }

        // Draw viewport rectangle
        double vpX = panX * scale + mmX + 2;
        double vpY = panY * scale + mmY + 2;
        double vpW = (width() / zoom) * scale;
        double vpH = (height() / zoom) * scale;
        canvas.strokeRect((int) vpX, (int) vpY,
                Math.max(4, (int) vpW), Math.max(4, (int) vpH),
                MINIMAP_VIEWPORT);
    }

    // ─────────────────── Status Bar ───────────────────

    private void drawStatusBar(SceneCanvas canvas, Font font) {
        String status = String.format("Nodes: %d | Edges: %d | Zoom: %.0f%% | Drag to pan, scroll to zoom, middle-click to reset",
                graph.nodeCount(), graph.edgeCount(), zoom * 100);

        int barHeight = 14;
        int barY = height() - barHeight;
        canvas.fill(0, barY, width(), barHeight, 0xCC111122);
        canvas.drawString(status, 4, barY + 3, TEXT_DIM, false);
    }

    // ─────────────────── Hit Testing ───────────────────

    private void updateHover(int mouseX, int mouseY) {
        if (layout == null) {
            hoveredNodeId = null;
            hoveredEdge = null;
            hoveredMirrorLinkId = -1;
            hoveredMirrorLink = null;
            return;
        }

        // Convert screen coords to world coords
        double worldX = panX + mouseX / zoom;
        double worldY = panY + mouseY / zoom;

        // Check satellite mirror-link badges first
        hoveredMirrorLinkId = -1;
        hoveredMirrorLink = null;
        if (layout.mirrorLinks() != null) {
            int bw = SugiyamaLayout.MIRROR_BADGE_W;
            int bh = SugiyamaLayout.MIRROR_BADGE_H;
            for (MirrorLink ml : layout.mirrorLinks()) {
                if (worldX >= ml.badgeX() && worldX <= ml.badgeX() + bw
                        && worldY >= ml.badgeY() && worldY <= ml.badgeY() + bh) {
                    hoveredMirrorLinkId = ml.linkId();
                    hoveredMirrorLink = ml;
                    break;
                }
            }
        }

        // Check cards
        hoveredNodeId = null;
        for (var entry : layout.positions().entrySet()) {
            NodePosition pos = entry.getValue();
            if (worldX >= pos.x() && worldX <= pos.right() &&
                worldY >= pos.y() && worldY <= pos.bottom()) {
                hoveredNodeId = entry.getKey();
                break;
            }
        }

        // Check edges (approximate by checking distance to control points)
        hoveredEdge = null;
        if (hoveredNodeId == null && hoveredMirrorLinkId < 0) {
            double minDist = 10.0 / zoom; // threshold in world space
            for (EdgeRoute route : layout.edgeRoutes()) {
                for (double[] pt : route.points()) {
                    double dx = pt[0] - worldX;
                    double dy = pt[1] - worldY;
                    if (Math.sqrt(dx * dx + dy * dy) < minDist) {
                        hoveredEdge = route;
                        break;
                    }
                }
                if (hoveredEdge != null) break;
            }
        }

        // Build and set the tooltip
        updateTooltip();
    }

    /**
     * Builds a rich graphical tooltip based on the current hover state and pushes it
     * to the scene for rendering, using {@link FlowChartTooltipComponent} for item icons.
     */
    private void updateTooltip() {
        Tooltip tip = Tooltip.create();

        if (hoveredMirrorLink != null) {
            // ── Satellite badge: show remote node details with item grid ──
            ProcessNode remoteNode = graph.node(hoveredMirrorLink.remoteNodeId());
            ProcessNode hostNode = graph.node(hoveredMirrorLink.hostNodeId());
            String direction = hoveredMirrorLink.isSource() ? "\u2192 Output to" : "\u2190 Input from";

            var b = FlowChartTooltipComponent.builder();
            b.text(direction, 0xFFFFAA00);

            if (remoteNode != null) {
                buildNodeSection(b, remoteNode);
            } else {
                b.text(hoveredMirrorLink.remoteNodeId(), 0xFFAAAAAA);
            }

            b.divider();
            b.text("Material: " + hoveredMirrorLink.material() + " x" + hoveredMirrorLink.amount(),
                    hoveredMirrorLink.edge().type().color);

            if (hostNode != null) {
                b.gap(2);
                b.text("Attached to: " + hostNode.name(), 0xFF888888);
            }
            b.gap(2);
            b.text("\u25b6 Click to jump", 0xFFFFFF55);

            tip.add(b.build());

        } else if (hoveredEdge != null) {
            // ── Edge tooltip: material + source/target summary with item grids ──
            ProcessEdge edge = hoveredEdge.edge();
            ProcessNode srcNode = graph.node(edge.fromNodeId());
            ProcessNode tgtNode = graph.node(edge.toNodeId());

            var b = FlowChartTooltipComponent.builder();
            b.text(edge.materialName() + " x" + edge.amount(), edge.type().color);
            b.text("Type: " + edge.type().name(), 0xFF666666);
            b.divider();

            b.text("\u25b8 Source", 0xFF55FF55);
            if (srcNode != null) {
                buildNodeSection(b, srcNode);
            } else {
                b.text("  " + edge.fromNodeId(), 0xFFAAAAAA);
            }

            b.divider();
            b.text("\u25b8 Target", 0xFFFF5555);
            if (tgtNode != null) {
                buildNodeSection(b, tgtNode);
            } else {
                b.text("  " + edge.toNodeId(), 0xFFAAAAAA);
            }

            tip.add(b.build());

        } else if (hoveredNodeId != null) {
            // ── Node card tooltip: header + item grids for I/O ──
            ProcessNode node = graph.node(hoveredNodeId);
            if (node != null) {
                var b = FlowChartTooltipComponent.builder();
                buildNodeSection(b, node);
                tip.add(b.build());
            }
        }

        scene().setHoverTooltip(tip);
    }

    /**
     * Appends a compact node summary to a FlowChartTooltipComponent builder,
     * including header info and item icon grids for inputs/outputs.
     */
    private static void buildNodeSection(FlowChartTooltipComponent.Builder b, ProcessNode node) {
        b.text(node.name(), 0xFFEEEEEE);
        b.text(node.machine() + "  |  Tier: " + node.tierName(), node.tierColor());

        if (!node.inputs().isEmpty()) {
            b.slots("\u25c0 Inputs", 0xFF88CC88, node.inputs());
        }
        if (!node.outputs().isEmpty()) {
            b.slots("\u25b6 Outputs", 0xFFCC8888, node.outputs());
        }

        b.text(node.durationFormatted() + " | " + node.euFormatted(), 0xFF666666);
    }

    // ─────────────────── Utility ───────────────────

    private static String truncate(String text, int maxPixelWidth, Font font) {
        if (font.width(text) <= maxPixelWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        while (text.length() > 1 && font.width(text) + ellipsisWidth > maxPixelWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Brighten an ARGB colour by adding {@code amount} to each channel (clamped to 255). */
    private static int brighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Pick an edge colour from the source node's group colour.
     * Falls back to the material-type colour if the source isn't in any group.
     */
    private int edgeColorForRoute(EdgeRoute route) {
        Integer gc = nodeGroupColors.get(route.edge().fromNodeId());
        if (gc == null) gc = nodeGroupColors.get(route.edge().toNodeId());
        if (gc != null) {
            // Brighten the group colour a bit so it stands out on dark backgrounds
            int r = Math.min(255, ((gc >> 16) & 0xFF) + 40);
            int g = Math.min(255, ((gc >> 8) & 0xFF) + 40);
            int b = Math.min(255, (gc & 0xFF) + 40);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return route.edge().type().color;
    }

    // ─────────────────── Satellite Navigation ───────────────────

    /**
     * Returns the MirrorLink badge at the given screen coordinates, or null.
     */
    private @Nullable MirrorLink getMirrorLinkAt(double screenX, double screenY) {
        if (layout == null || layout.mirrorLinks() == null) return null;
        double worldX = panX + screenX / zoom;
        double worldY = panY + screenY / zoom;
        int bw = SugiyamaLayout.MIRROR_BADGE_W;
        int bh = SugiyamaLayout.MIRROR_BADGE_H;
        for (MirrorLink ml : layout.mirrorLinks()) {
            if (worldX >= ml.badgeX() && worldX <= ml.badgeX() + bw
                    && worldY >= ml.badgeY() && worldY <= ml.badgeY() + bh) {
                return ml;
            }
        }
        return null;
    }

    /**
     * Pans the viewport to centre the given node.
     */
    private void panToNode(String nodeId) {
        if (layout == null) return;
        NodePosition pos = layout.positions().get(nodeId);
        if (pos == null) return;
        panX = pos.centerX() - width() / (2.0 * zoom);
        panY = pos.centerY() - height() / (2.0 * zoom);
        hoveredNodeId = nodeId;
    }

    /**
     * When a satellite mirror-link pair is hovered, draws a visual trace line
     * connecting the two badges across the graph for easy navigation.
     */
    private void drawSatelliteTrace(SceneCanvas canvas) {
        if (hoveredMirrorLinkId < 0 || layout.mirrorLinks() == null) return;

        MirrorLink srcBadge = null, tgtBadge = null;
        for (MirrorLink ml : layout.mirrorLinks()) {
            if (ml.linkId() == hoveredMirrorLinkId) {
                if (ml.isSource()) srcBadge = ml; else tgtBadge = ml;
            }
        }
        if (srcBadge == null || tgtBadge == null) return;

        int badgeW = SugiyamaLayout.MIRROR_BADGE_W;
        int badgeH = SugiyamaLayout.MIRROR_BADGE_H;
        float x1 = (float) (srcBadge.badgeX() + badgeW / 2.0);
        float y1 = (float) (srcBadge.badgeY() + badgeH / 2.0);
        float x2 = (float) (tgtBadge.badgeX() + badgeW / 2.0);
        float y2 = (float) (tgtBadge.badgeY() + badgeH / 2.0);

        // Draw dashed trace line between the two badges
        drawDashedLine(canvas, x1, y1, x2, y2, 10, 6, 1.5f, 0x88FFCC44);

        // Highlight rings around both badges
        int accent = 0xFFFFCC44;
        canvas.strokeRect((int) (srcBadge.badgeX() - 2), (int) (srcBadge.badgeY() - 2),
                badgeW + 4, badgeH + 4, accent);
        canvas.strokeRect((int) (tgtBadge.badgeX() - 2), (int) (tgtBadge.badgeY() - 2),
                badgeW + 4, badgeH + 4, accent);
    }

    /**
     * Draws a dashed line between two points.
     */
    private static void drawDashedLine(SceneCanvas canvas,
                                        float x1, float y1, float x2, float y2,
                                        int dashLen, int gapLen, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float totalLen = (float) Math.sqrt(dx * dx + dy * dy);
        if (totalLen < 1) return;
        float ux = dx / totalLen, uy = dy / totalLen;
        float pos = 0;
        while (pos < totalLen) {
            float end = Math.min(pos + dashLen, totalLen);
            canvas.line(x1 + ux * pos, y1 + uy * pos,
                        x1 + ux * end, y1 + uy * end,
                        thickness, color);
            pos += dashLen + gapLen;
        }
    }

    // ─────────────────── Text Wrapping ───────────────────

    /**
     * Wraps text into multiple lines to fit within the given pixel width.
     * Returns at most 2 lines; the last line is truncated with "..." if needed.
     */
    private static List<String> wrapText(String text, int maxPixelWidth, Font font) {
        if (font.width(text) <= maxPixelWidth) {
            return List.of(text);
        }
        // Extract first line that fits
        String firstLine = wrapText_firstLine(text, maxPixelWidth, font);
        String rest = text.substring(firstLine.length()).trim();
        if (rest.isEmpty()) {
            return List.of(truncate(text, maxPixelWidth, font));
        }
        // Second line: truncate the remainder
        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        lines.add(truncate(rest, maxPixelWidth, font));
        return lines;
    }

    /** Extracts the first line of wrapped text that fits within maxPixelWidth. */
    private static String wrapText_firstLine(String text, int maxPixelWidth, Font font) {
        StringBuilder sb = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String test = sb.isEmpty() ? word : sb + " " + word;
            if (font.width(test) > maxPixelWidth) break;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word);
        }
        return sb.isEmpty() ? truncate(text, maxPixelWidth, font) : sb.toString();
    }
}
