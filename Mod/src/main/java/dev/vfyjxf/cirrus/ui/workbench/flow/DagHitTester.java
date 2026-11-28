package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure hit testing for canvas nodes, inline recipe slots and bezier edges.
 */
public final class DagHitTester {

    public static final double clickSlopPx = 3.0;
    public static final float edgeTolerancePx = 6.0f;
    public static final int bezierSamples = 24;
    public static final int slotSize = 18;
    public static final int slotGap = 2;
    public static final int slotPad = 6;
    public static final int panelGap = 2;
    public static final int panelHeight = 22;
    public static final int machineWidth = 16;

    private DagHitTester() {}

    public enum SlotSide {
        input, output, catalyst
    }

    public record RectF(float x, float y, float width, float height) {

        public static RectF of(float x, float y, float width, float height) {
            return new RectF(x, y, width, height);
        }

        public float maxX() {
            return x + width;
        }

        public float maxY() {
            return y + height;
        }

        public float centerX() {
            return x + width * 0.5f;
        }

        public float centerY() {
            return y + height * 0.5f;
        }

        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        public boolean intersects(RectF other) {
            return other != null && x < other.maxX() && maxX() > other.x && y < other.maxY() && maxY() > other.y;
        }

        public RectF inflated(float amount) {
            return new RectF(x - amount, y - amount, width + amount * 2.0f, height + amount * 2.0f);
        }
    }

    public record InlineSlot(
        String stepId,
        SlotSide side,
        int index,
        RectF bounds,
        @Nullable ComponentStackKey resource
    ) {}

    public static @Nullable DagNodeBox nodeAt(double worldX, double worldY, List<DagNodeBox> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        for (DagNodeBox node : nodes) {
            for (DagNodeBox inner : node.innerLoopNodes()) {
                if (contains(inner, worldX, worldY)) {
                    return inner;
                }
            }
        }
        for (DagNodeBox node : nodes) {
            if (contains(node, worldX, worldY)) {
                return node;
            }
        }
        return null;
    }

    public static @Nullable DagNodeBox outerNodeOf(@Nullable DagNodeBox hit, List<DagNodeBox> nodes) {
        if (hit == null) {
            return null;
        }
        for (DagNodeBox node : nodes) {
            if (node == hit || node.stepId().equals(hit.stepId())) {
                return node;
            }
            for (DagNodeBox inner : node.innerLoopNodes()) {
                if (inner == hit || inner.stepId().equals(hit.stepId())) {
                    return node;
                }
            }
        }
        return hit;
    }

    public static boolean contains(DagNodeBox node, double worldX, double worldY) {
        return node != null
                && worldX >= node.x()
                && worldX <= node.maxX()
                && worldY >= node.y()
                && worldY <= node.maxY();
    }

    public static List<InlineSlot> inlineSlots(
        DagNodeBox node,
        List<ComponentStackKey> inputs,
        List<ComponentStackKey> outputs
    ) {
        if (node == null) {
            return List.of();
        }
        return inlineSlots(node, inputs == null ? 0 : inputs.size(), outputs == null ? 0 : outputs.size());
    }

    public static List<InlineSlot> inlineSlots(DagNodeBox node, int inputCount, int outputCount) {
        if (node == null) {
            return List.of();
        }
        List<InlineSlot> slots = new ArrayList<>(Math.max(0, inputCount) + Math.max(0, outputCount));
        float panelY = node.maxY() + panelGap;
        float slotY = panelY + (panelHeight - slotSize) * 0.5f;
        for (int index = 0; index < inputCount; index++) {
            float x = node.x() + slotPad + index * (slotSize + slotGap);
            slots.add(
                new InlineSlot(node.stepId(), SlotSide.input, index, new RectF(x, slotY, slotSize, slotSize), null)
            );
        }
        for (int index = 0; index < outputCount; index++) {
            float x = node.maxX() - slotPad - (outputCount - index) * (slotSize + slotGap) + slotGap;
            slots.add(
                new InlineSlot(node.stepId(), SlotSide.output, index, new RectF(x, slotY, slotSize, slotSize), null)
            );
        }
        return slots;
    }

    public static RectF inlinePanelBounds(DagNodeBox node) {
        if (node == null) {
            return new RectF(0, 0, 0, 0);
        }
        return new RectF(node.x(), node.maxY() + panelGap, node.width(), panelHeight);
    }

    public static RectF machineBounds(DagNodeBox node) {
        if (node == null) {
            return new RectF(0, 0, 0, 0);
        }
        RectF panel = inlinePanelBounds(node);
        return new RectF(
            panel.centerX() - machineWidth * 0.5f,
            panel.y() + (panelHeight - machineWidth) * 0.5f,
            machineWidth,
            machineWidth
        );
    }

    public static @Nullable InlineSlot slotAt(double worldX, double worldY, List<InlineSlot> slots) {
        if (slots == null) {
            return null;
        }
        for (InlineSlot slot : slots) {
            if (slot.bounds().contains(worldX, worldY)) {
                return slot;
            }
        }
        return null;
    }

    public static @Nullable DagEdgeRoute edgeAt(
        double screenX,
        double screenY,
        List<DagEdgeRoute> edges,
        DagViewport viewport,
        float tolerancePx,
        int samples
    ) {
        return edgeAt(
            screenX,
            screenY,
            edges,
            viewport,
            tolerancePx,
            samples,
            DagEdgeStyle.curved,
            DagLayoutMode.horizontalLr
        );
    }

    public static @Nullable DagEdgeRoute edgeAt(
        double screenX,
        double screenY,
        List<DagEdgeRoute> edges,
        DagViewport viewport,
        float tolerancePx,
        int samples,
        DagEdgeStyle style,
        DagLayoutMode layoutMode
    ) {
        if (edges == null || edges.isEmpty() || viewport == null) {
            return null;
        }
        DagEdgeStyle routing = style == null ? DagEdgeStyle.orthogonal : style;
        DagLayoutMode layout = layoutMode == null ? DagLayoutMode.horizontalLr : layoutMode;
        int steps = Math.max(2, samples);
        float tolerance = Math.max(0.5f, tolerancePx);
        DagEdgeRoute best = null;
        double bestDistance = Double.MAX_VALUE;
        Map<DagEdgeRoute, Float> channelRatios = routing == DagEdgeStyle.orthogonal
                ? orthogonalChannelRatios(edges, layout)
                : Map.of();
        for (DagEdgeRoute edge : edges) {
            if (routing == DagEdgeStyle.curved) {
                double previousX = viewport.worldToScreenX(bezier(edge.x0(), edge.cx0(), edge.cx1(), edge.x1(), 0.0));
                double previousY = viewport.worldToScreenY(bezier(edge.y0(), edge.cy0(), edge.cy1(), edge.y1(), 0.0));
                for (int index = 1; index <= steps; index++) {
                    double t = index / (double) steps;
                    double currentX = viewport.worldToScreenX(bezier(edge.x0(), edge.cx0(), edge.cx1(), edge.x1(), t));
                    double currentY = viewport.worldToScreenY(bezier(edge.y0(), edge.cy0(), edge.cy1(), edge.y1(), t));
                    double distance = distanceToSegment(screenX, screenY, previousX, previousY, currentX, currentY);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = edge;
                    }
                    previousX = currentX;
                    previousY = currentY;
                }
            } else {
                float[] pts = routing == DagEdgeStyle.orthogonal
                        ? orthogonalPoints(edge, channelRatios.getOrDefault(edge, 0.5f), layout, 0f, 0f)
                        : straightPoints(edge, layout, 0f, 0f);
                int count = pts.length / 2;
                for (int index = 0; index < count - 1; index++) {
                    double ax = viewport.worldToScreenX(pts[index * 2]);
                    double ay = viewport.worldToScreenY(pts[index * 2 + 1]);
                    double bx = viewport.worldToScreenX(pts[(index + 1) * 2]);
                    double by = viewport.worldToScreenY(pts[(index + 1) * 2 + 1]);
                    double distance = distanceToSegment(screenX, screenY, ax, ay, bx, by);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = edge;
                    }
                }
            }
        }
        return bestDistance <= tolerance ? best : null;
    }

    public static Map<DagEdgeRoute, Float> orthogonalChannelRatios(List<DagEdgeRoute> edges, DagLayoutMode layoutMode) {
        if (edges == null || edges.isEmpty()) {
            return Map.of();
        }
        boolean vertical = layoutMode != null && layoutMode.isVertical();
        Map<String, List<DagEdgeRoute>> groups = new HashMap<>();
        for (DagEdgeRoute edge : edges) {
            if (edge == null || edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
                continue;
            }
            float a = vertical ? edge.y0() : edge.x0();
            float b = vertical ? edge.y1() : edge.x1();
            String bucket = Math.round(Math.min(a, b) / 28f) + ":" + Math.round(Math.max(a, b) / 28f);
            groups.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(edge);
        }
        Map<DagEdgeRoute, Float> ratios = new IdentityHashMap<>(edges.size());
        Comparator<DagEdgeRoute> order = Comparator.comparingInt((DagEdgeRoute e) -> {
            float p0 = vertical ? e.x0() : e.y0();
            float p1 = vertical ? e.x1() : e.y1();
            return Float.compare(p1, p0);
        }).thenComparingDouble((DagEdgeRoute e) -> {
            float p0 = vertical ? e.x0() : e.y0();
            float p1 = vertical ? e.x1() : e.y1();
            return (p0 + p1) * 0.5;
        }).thenComparing(DagEdgeRoute::fromStepId).thenComparing(DagEdgeRoute::toStepId);
        for (List<DagEdgeRoute> bucket : groups.values()) {
            if (bucket.size() <= 1) {
                ratios.put(bucket.get(0), 0.5f);
                continue;
            }
            bucket.sort(order);
            int count = bucket.size();
            float span = Math.min(0.72f, (count - 1) * 0.10f);
            float start = 0.5f - span * 0.5f;
            for (int index = 0; index < count; index++) {
                ratios.put(bucket.get(index), start + span * (index / (float) (count - 1)));
            }
        }
        return ratios;
    }

    public static float[] orthogonalPoints(DagEdgeRoute edge, DagLayoutMode layoutMode, float offsetX, float offsetY) {
        return orthogonalPoints(edge, 0.5f, layoutMode, offsetX, offsetY);
    }

    public static float[] orthogonalPoints(
        DagEdgeRoute edge,
        float channelRatio,
        DagLayoutMode layoutMode,
        float offsetX,
        float offsetY
    ) {
        float x0 = edge.x0() + offsetX;
        float y0 = edge.y0() + offsetY;
        float x1 = edge.x1() + offsetX;
        float y1 = edge.y1() + offsetY;
        if (edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
            float dropY = Math.max(y0, y1) + 22.0f;
            return chamferPolyline(new float[]{x0, y0, x0, dropY, x1, dropY, x1, y1}, 7.0f);
        }
        float ratio = Math.max(0.12f, Math.min(0.88f, Float.isFinite(channelRatio) ? channelRatio : 0.5f));
        if (layoutMode != null && layoutMode.isVertical()) {
            if (Math.abs(x1 - x0) < 1.5f) {
                return new float[]{x0, y0, x1, y1};
            }
            float spanY = y1 - y0;
            // Multi-layer skip edges route cleanly in the clear corridor just before the target card.
            float midY = Math.abs(spanY) > 160.0f
                    ? y1 - Math.signum(spanY) * (14.0f + (1.0f - ratio) * 34.0f)
                    : y0 + spanY * ratio;
            return chamferPolyline(new float[]{x0, y0, x0, midY, x1, midY, x1, y1}, 7.5f);
        }
        if (Math.abs(y1 - y0) < 1.5f) {
            return new float[]{x0, y0, x1, y1};
        }
        float spanX = x1 - x0;
        float midX = Math.abs(spanX) > 240.0f
                ? x1 - Math.signum(spanX) * (14.0f + (1.0f - ratio) * 44.0f)
                : x0 + spanX * ratio;
        return chamferPolyline(new float[]{x0, y0, midX, y0, midX, y1, x1, y1}, 7.5f);
    }

    public static float[] straightPoints(DagEdgeRoute edge, DagLayoutMode layoutMode, float offsetX, float offsetY) {
        float x0 = edge.x0() + offsetX;
        float y0 = edge.y0() + offsetY;
        float x1 = edge.x1() + offsetX;
        float y1 = edge.y1() + offsetY;
        if (edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
            float dropY = Math.max(y0, y1) + 22.0f;
            return chamferPolyline(new float[]{x0, y0, x0, dropY, x1, dropY, x1, y1}, 7.0f);
        }
        if (layoutMode != null && layoutMode.isVertical()) {
            float dy = y1 - y0;
            if (Math.abs(x1 - x0) < 1.5f || Math.abs(dy) < 20.0f) {
                return new float[]{x0, y0, x1, y1};
            }
            float stub = Math.min(11.0f, Math.abs(dy) * 0.22f) * Math.signum(dy);
            return new float[]{x0, y0, x0, y0 + stub, x1, y1 - stub, x1, y1};
        }
        float dx = x1 - x0;
        if (Math.abs(y1 - y0) < 1.5f || Math.abs(dx) < 20.0f) {
            return new float[]{x0, y0, x1, y1};
        }
        float stub = Math.min(11.0f, Math.abs(dx) * 0.22f) * Math.signum(dx);
        return new float[]{x0, y0, x0 + stub, y0, x1 - stub, y1, x1, y1};
    }

    public static float[] chamferPolyline(float[] pts, float maxChamfer) {
        int count = pts.length / 2;
        if (count < 3 || maxChamfer <= 0.5f) {
            return pts;
        }
        List<Float> out = new ArrayList<>(pts.length + 8);
        out.add(pts[0]);
        out.add(pts[1]);
        for (int index = 1; index < count - 1; index++) {
            float px = pts[(index - 1) * 2];
            float py = pts[(index - 1) * 2 + 1];
            float cx = pts[index * 2];
            float cy = pts[index * 2 + 1];
            float nx = pts[(index + 1) * 2];
            float ny = pts[(index + 1) * 2 + 1];
            float dx0 = cx - px;
            float dy0 = cy - py;
            float dx1 = nx - cx;
            float dy1 = ny - cy;
            float len0 = (float) Math.hypot(dx0, dy0);
            float len1 = (float) Math.hypot(dx1, dy1);
            float chamfer = Math.min(maxChamfer, Math.min(len0, len1) * 0.38f);
            if (chamfer < 1.2f) {
                out.add(cx);
                out.add(cy);
                continue;
            }
            out.add(cx - (dx0 / len0) * chamfer);
            out.add(cy - (dy0 / len0) * chamfer);
            out.add(cx + (dx1 / len1) * chamfer);
            out.add(cy + (dy1 / len1) * chamfer);
        }
        out.add(pts[(count - 1) * 2]);
        out.add(pts[(count - 1) * 2 + 1]);
        float[] result = new float[out.size()];
        for (int i = 0; i < out.size(); i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    public static boolean isClick(double deltaX, double deltaY) {
        return Math.abs(deltaX) <= clickSlopPx && Math.abs(deltaY) <= clickSlopPx;
    }

    public static float[] bezierPoint(DagEdgeRoute edge, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return new float[]{(float) bezier(edge.x0(), edge.cx0(), edge.cx1(), edge.x1(), clamped),
                (float) bezier(edge.y0(), edge.cy0(), edge.cy1(), edge.y1(), clamped)};
    }

    public static float pathLength(DagEdgeRoute edge, int samples) {
        if (edge == null) {
            return 0.0f;
        }
        int steps = Math.max(2, samples);
        double length = 0.0;
        double previousX = bezier(edge.x0(), edge.cx0(), edge.cx1(), edge.x1(), 0.0);
        double previousY = bezier(edge.y0(), edge.cy0(), edge.cy1(), edge.y1(), 0.0);
        for (int index = 1; index <= steps; index++) {
            double t = index / (double) steps;
            double currentX = bezier(edge.x0(), edge.cx0(), edge.cx1(), edge.x1(), t);
            double currentY = bezier(edge.y0(), edge.cy0(), edge.cy1(), edge.y1(), t);
            length += Math.hypot(currentX - previousX, currentY - previousY);
            previousX = currentX;
            previousY = currentY;
        }
        return (float) length;
    }

    public static double bezier(double p0, double p1, double p2, double p3, double t) {
        double inverse = 1.0 - t;
        return inverse * inverse * inverse * p0
                + 3.0 * inverse * inverse * t * p1
                + 3.0 * inverse * t * t * p2
                + t * t * t * p3;
    }

    private static double distanceToSegment(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1.0e-9) {
            return Math.hypot(px - x0, py - y0);
        }
        double t = ((px - x0) * dx + (py - y0) * dy) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(px - (x0 + clamped * dx), py - (y0 + clamped * dy));
    }
}
