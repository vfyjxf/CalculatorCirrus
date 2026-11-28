package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLodLevel;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Draws the five edge kinds with mitered chamfered conduits, distinct resource hues, vector arrowheads, and port jacks.
 */
public final class DagEdgeRenderer {

    public static final int particleSamples = 24;
    public static final int cardPadding = 5;
    public static final int cardRow = 11;
    public static final float controlMin = 28.0f;
    public static final float loopControlMin = 20.0f;
    public static final float loopSag = 24.0f;
    public static final int minimapSamples = 8;

    private static final String keySource = "cirrus.ui.workbench.flow.edge.source";
    private static final String keyTarget = "cirrus.ui.workbench.flow.edge.target";
    private static final String keyProduced = "cirrus.ui.workbench.flow.edge.produced";
    private static final String keyConsumed = "cirrus.ui.workbench.flow.edge.consumed";

    private DagEdgeRenderer() {}

    public record EdgeState(
        boolean hovered,
        boolean tracedUpstream,
        boolean tracedDownstream,
        boolean criticalPath,
        boolean dimmed
    ) {

        public static final EdgeState idle = new EdgeState(false, false, false, false, false);

        public static EdgeState of(
            boolean hovered,
            boolean upstream,
            boolean downstream,
            boolean critical,
            boolean dimmed
        ) {
            return new EdgeState(hovered, upstream, downstream, critical, dimmed);
        }
    }

    public static void drawEdge(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        CirrusPalette palette,
        DagLodLevel lod,
        EdgeState state,
        long timeMs,
        boolean reducedMotion
    ) {
        drawEdge(
            canvas,
            edge,
            palette,
            lod,
            state,
            timeMs,
            reducedMotion,
            0.0f,
            0.0f,
            DagEdgeStyle.orthogonal,
            DagLayoutMode.horizontalLr,
            0.5f
        );
    }

    public static void drawEdge(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        CirrusPalette palette,
        DagLodLevel lod,
        EdgeState state,
        long timeMs,
        boolean reducedMotion,
        float offsetX,
        float offsetY
    ) {
        drawEdge(
            canvas,
            edge,
            palette,
            lod,
            state,
            timeMs,
            reducedMotion,
            offsetX,
            offsetY,
            DagEdgeStyle.orthogonal,
            DagLayoutMode.horizontalLr,
            0.5f
        );
    }

    public static void drawEdge(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        CirrusPalette palette,
        DagLodLevel lod,
        EdgeState state,
        long timeMs,
        boolean reducedMotion,
        float offsetX,
        float offsetY,
        DagEdgeStyle style,
        DagLayoutMode layoutMode
    ) {
        drawEdge(canvas, edge, palette, lod, state, timeMs, reducedMotion, offsetX, offsetY, style, layoutMode, 0.5f);
    }

    public static void drawEdge(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        CirrusPalette palette,
        DagLodLevel lod,
        EdgeState state,
        long timeMs,
        boolean reducedMotion,
        float offsetX,
        float offsetY,
        DagEdgeStyle style,
        DagLayoutMode layoutMode,
        float channelRatio
    ) {
        if (canvas == null || edge == null || palette == null) {
            return;
        }
        DagEdgeStyle routing = style == null ? DagEdgeStyle.orthogonal : style;
        DagLayoutMode layout = layoutMode == null ? DagLayoutMode.horizontalLr : layoutMode;
        EdgeState visual = state == null ? EdgeState.idle : state;
        DagLodLevel level = lod == null ? DagLodLevel.blueprint : lod;
        float dim = visual.dimmed() ? 0.14f : 1.0f;
        int color = colorOf(edge, palette, visual);
        float thickness = thicknessOf(edge, level, visual)
                + ((visual.tracedUpstream() || visual.tracedDownstream()) ? 0.55f : 0.0f);
        int faded = StepNodeCardRenderer.withAlpha(color, dim);
        boolean dark = OreUiPaint.isDark(palette);
        int casing = StepNodeCardRenderer
                .withAlpha(dark ? OreUiPaint.outerInk(palette) : palette.cardSurface(), dim * (dark ? 0.92f : 0.94f));
        int coreHighlight = StepNodeCardRenderer
                .withAlpha(OreUiPaint.lighten(color, dark ? 0.42f : 0.30f), dim * (visual.hovered() ? 0.95f : 0.58f));

        float[] pts = pathPoints(edge, routing, layout, channelRatio, offsetX, offsetY);
        if (pts.length < 4) {
            return;
        }
        float x0 = pts[0];
        float y0 = pts[1];
        float x1 = pts[pts.length - 2];
        float y1 = pts[pts.length - 1];

        float trim = switch (edge.kind()) {
            case machine -> 4.6f;
            case catalyst -> 4.2f;
            case autoLine, material, loopBack -> visual.hovered() ? 9.8f : 8.4f;
        };
        float[] bodyPts = trimEnd(pts, trim);

        boolean traced = visual.tracedUpstream() || visual.tracedDownstream();
        if (visual.criticalPath() || visual.hovered() || traced) {
            int glowBase = visual.hovered() ? palette.focusRing() : traced ? color : palette.edgeCriticalPath();
            float pulse = 0.85f + 0.15f * OreUiMotion.pulse01(timeMs, 1800L, reducedMotion);
            float glowAlpha = visual.hovered() ? 0.36f : traced ? 0.26f : 0.28f;
            int glow = StepNodeCardRenderer.withAlpha(glowBase, dim * glowAlpha * pulse);
            drawRibbon(canvas, bodyPts, thickness + 4.2f, glow);
        }

        boolean activeFlow = visual.hovered()
                || visual.criticalPath()
                || visual.tracedUpstream()
                || visual.tracedDownstream();
        double dashPhase = (activeFlow && !reducedMotion) ? (Math.floorMod(timeMs, 1100L) / 1100.0) * 11.5 : 0.0;

        switch (edge.kind()) {
            case machine -> {
                drawDashedRibbon(canvas, bodyPts, thickness + 2.0f, casing, 7.0f, 4.5f, dashPhase);
                drawDashedRibbon(canvas, bodyPts, thickness, faded, 6.2f, 5.3f, dashPhase);
            }
            case catalyst -> {
                drawRibbon(canvas, bodyPts, thickness + 1.4f, StepNodeCardRenderer.withAlpha(casing, 0.72f));
                drawRibbon(
                    canvas,
                    bodyPts,
                    Math.max(1.0f, thickness * 0.55f),
                    StepNodeCardRenderer.withAlpha(faded, 0.75f)
                );
                drawBeadMarkers(canvas, bodyPts, palette, faded, dim);
            }
            case autoLine -> {
                float[] railA = offsetPolyline(bodyPts, -1.6f);
                float[] railB = offsetPolyline(bodyPts, 1.6f);
                drawRibbon(canvas, bodyPts, thickness + 3.4f, casing);
                drawRibbon(canvas, railA, 1.25f, faded);
                drawRibbon(canvas, railB, 1.25f, coreHighlight);
            }
            case loopBack, material -> {
                drawRibbon(canvas, bodyPts, thickness + 2.2f, casing);
                drawRibbon(canvas, bodyPts, thickness, faded);
                if (level != DagLodLevel.macro && thickness >= 1.8f) {
                    drawRibbon(canvas, bodyPts, Math.max(0.75f, thickness * 0.34f), coreHighlight);
                }
            }
        }

        if (activeFlow) {
            drawFlowChevrons(
                canvas,
                bodyPts,
                visual.hovered() ? palette.textPrimary() : coreHighlight,
                dim,
                timeMs,
                reducedMotion
            );
        } else
            if (!reducedMotion
                    && !visual.dimmed()
                    && level != DagLodLevel.macro
                    && (edge.kind() == DagEdgeRoute.EdgeKind.autoLine
                            || edge.kind() == DagEdgeRoute.EdgeKind.material)) {
                                drawAmbientPulse(
                                    canvas,
                                    bodyPts,
                                    coreHighlight,
                                    dim,
                                    timeMs,
                                    edge.fromStepId().hashCode()
                                );
                            }

        drawSourceJack(canvas, x0, y0, edge.kind(), palette, faded, dim);
        drawTargetTerminal(canvas, pts, edge.kind(), palette, casing, faded, visual.hovered());

        if (level != DagLodLevel.macro && edge.amountFlow() > 0L) {
            boolean showInline = visual.hovered()
                    || (edge.kind() == DagEdgeRoute.EdgeKind.material && polylineLength(pts) >= 92.0f);
            if (showInline) {
                drawFlowPill(canvas, pts, edge.amountFlow(), palette, color, dim, visual.hovered());
            }
        }
    }

    public static OreUiRichTooltip edgeTooltip(
        DagEdgeRoute edge,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette
    ) {
        Component title = edge.resource() == null
                ? edgeKindLabel(edge.kind())
                : StepNodeCardRenderer.nameOf(edge.resource(), resolver);
        int edgeColor = colorOf(edge, palette, EdgeState.idle);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(title).icon(edge.resource())
                .badge(edgeKindLabel(edge.kind()), edgeColor)
                .subtitle(edge.fromStepId() + " \u2192 " + edge.toStepId());
        if (edge.amountFlow() > 0L || edge.sourceTotalProduced() > 0L) {
            double ratio = edge.sourceTotalProduced() > 0L
                    ? Math.min(1.0, (double) edge.amountFlow() / edge.sourceTotalProduced())
                    : 1.0;
            String readout = WorkbenchFormatters.formatAmount(edge.amountFlow()) + " / "
                    + WorkbenchFormatters.formatAmount(Math.max(edge.amountFlow(), edge.sourceTotalProduced()));
            builder.meter(Component.literal("Flow Share"), ratio, Component.literal(readout), edgeColor);
        }
        builder.keyValue(
            Component.translatable(keySource, ""),
            Component.literal(edge.fromStepId()),
            palette.edgeUpstreamTrace()
        );
        builder.keyValue(
            Component.translatable(keyTarget, ""),
            Component.literal(edge.toStepId()),
            palette.edgeDownstreamTrace()
        );
        if (edge.criticalPath()) {
            builder.pills(
                OreUiRichTooltip.Pill.colored(Component.literal("Critical Path"), palette.edgeCriticalPath())
            );
        }
        return builder.build();
    }

    public static void drawHoverCard(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        double anchorX,
        double anchorY,
        CirrusPalette palette,
        @Nullable PlanVisualResolver resolver,
        @Nullable WorkbenchThemeMode theme,
        int viewWidth,
        int viewHeight
    ) {
        if (canvas == null || edge == null || palette == null) {
            return;
        }
        OreUiRichTooltip tooltip = edgeTooltip(edge, resolver, palette);
        OreUiRichTooltipRenderer.render(
            canvas,
            tooltip,
            (int) Math.round(anchorX),
            (int) Math.round(anchorY),
            viewWidth,
            viewHeight,
            palette
        );
    }

    public static int colorOf(DagEdgeRoute edge, CirrusPalette palette, EdgeState state) {
        EdgeState visual = state == null ? EdgeState.idle : state;
        if (visual.hovered()) {
            return palette.focusRing();
        }
        if (visual.tracedUpstream()) {
            return palette.edgeUpstreamTrace();
        }
        if (visual.tracedDownstream()) {
            return palette.edgeDownstreamTrace();
        }
        return switch (edge.kind()) {
            case material -> resourceConduitColor(edge.resource(), palette);
            case catalyst -> palette.edgeCatalyst();
            case machine -> palette.edgeMachine();
            case autoLine -> palette.linePullStep().accent();
            case loopBack -> palette.cycleLoopStep().accent();
        };
    }

    public static int resourceConduitColor(@Nullable ComponentStackKey resource, CirrusPalette palette) {
        if (resource == null) {
            return palette.edgeMaterial();
        }
        int[] hues = {palette.craftStep().accent(), palette.acquireStep().accent(), palette.costH(), palette.costC(),
                palette.ruleHardBan(), palette.convertStep().accent()};
        String id = resource.displayId().toLowerCase(Locale.ROOT);
        if (id.contains("coal") || id.contains("charcoal") || id.contains("gold") || id.contains("blaze")) {
            return hues[2];
        }
        if (id.contains("redstone") || id.contains("copper") || id.contains("crimson") || id.contains("ruby")) {
            return hues[4];
        }
        if (id.contains("iron") || id.contains("steel") || id.contains("tin") || id.contains("lead")) {
            return hues[0];
        }
        if (id.contains("diamond") || id.contains("lapis") || id.contains("quartz") || id.contains("water")) {
            return hues[5];
        }
        if (id.contains("emerald")
                || id.contains("plank")
                || id.contains("wood")
                || id.contains("log")
                || id.contains("slime")) {
            return hues[1];
        }
        if (id.contains("amethyst") || id.contains("obsidian") || id.contains("ender") || id.contains("nether")) {
            return hues[3];
        }
        int hash = Math.abs(id.hashCode());
        return hues[hash % hues.length];
    }

    public static float thicknessOf(DagEdgeRoute edge, DagLodLevel lod, EdgeState state) {
        DagLodLevel level = lod == null ? DagLodLevel.blueprint : lod;
        if (level == DagLodLevel.macro) {
            return edge.kind() == DagEdgeRoute.EdgeKind.material ? 2.4f : 1.3f;
        }
        boolean emphasise = state != null && (state.hovered() || state.criticalPath());
        return switch (edge.kind()) {
            case material -> emphasise ? 2.8f : 2.1f;
            case catalyst -> emphasise ? 2.2f : 1.7f;
            case machine -> emphasise ? 2.3f : 1.8f;
            case autoLine -> emphasise ? 2.6f : 2.1f;
            case loopBack -> emphasise ? 2.5f : 2.0f;
        };
    }

    private static float[] pathPoints(
        DagEdgeRoute edge,
        DagEdgeStyle routing,
        DagLayoutMode layout,
        float channelRatio,
        float offsetX,
        float offsetY
    ) {
        if (routing == DagEdgeStyle.curved) {
            int segments = 18;
            float[] pts = new float[(segments + 1) * 2];
            float x0 = edge.x0() + offsetX;
            float y0 = edge.y0() + offsetY;
            float cx0 = edge.cx0() + offsetX;
            float cy0 = edge.cy0() + offsetY;
            float cx1 = edge.cx1() + offsetX;
            float cy1 = edge.cy1() + offsetY;
            float x1 = edge.x1() + offsetX;
            float y1 = edge.y1() + offsetY;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                pts[i * 2] = (float) DagHitTester.bezier(x0, cx0, cx1, x1, t);
                pts[i * 2 + 1] = (float) DagHitTester.bezier(y0, cy0, cy1, y1, t);
            }
            return pts;
        }
        if (routing == DagEdgeStyle.straight) {
            return DagHitTester.straightPoints(edge, layout, offsetX, offsetY);
        }
        return DagHitTester.orthogonalPoints(edge, channelRatio, layout, offsetX, offsetY);
    }

    /**
     * Draws a continuous mitered quad strip along {@code pts} with seamless joins at every bend.
     */
    private static void drawRibbon(SceneCanvas canvas, float[] pts, float thickness, int color) {
        int count = pts.length / 2;
        if (count < 2 || thickness <= 0.05f) {
            return;
        }
        float half = thickness * 0.5f;
        float[] leftX = new float[count];
        float[] leftY = new float[count];
        float[] rightX = new float[count];
        float[] rightY = new float[count];

        for (int i = 0; i < count; i++) {
            float px = pts[i * 2];
            float py = pts[i * 2 + 1];
            float[] nPrev = i > 0 ? segmentNormal(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], px, py) : null;
            float[] nNext = i + 1 < count ? segmentNormal(px, py, pts[(i + 1) * 2], pts[(i + 1) * 2 + 1]) : null;
            float nx;
            float ny;
            float scale = 1.0f;
            if (nPrev != null && nNext != null) {
                float mx = nPrev[0] + nNext[0];
                float my = nPrev[1] + nNext[1];
                float mLen = (float) Math.hypot(mx, my);
                if (mLen > 1e-4f) {
                    nx = mx / mLen;
                    ny = my / mLen;
                    float dot = nx * nNext[0] + ny * nNext[1];
                    scale = dot > 0.35f ? Math.min(1.65f, 1.0f / dot) : 1.0f;
                } else {
                    nx = nNext[0];
                    ny = nNext[1];
                }
            } else if (nNext != null) {
                nx = nNext[0];
                ny = nNext[1];
            } else if (nPrev != null) {
                nx = nPrev[0];
                ny = nPrev[1];
            } else {
                nx = 0f;
                ny = 1f;
            }
            float offset = half * scale;
            leftX[i] = px + nx * offset;
            leftY[i] = py + ny * offset;
            rightX[i] = px - nx * offset;
            rightY[i] = py - ny * offset;
        }

        for (int i = 0; i < count - 1; i++) {
            canvas.coloredQuad(
                leftX[i],
                leftY[i],
                leftX[i + 1],
                leftY[i + 1],
                rightX[i + 1],
                rightY[i + 1],
                rightX[i],
                rightY[i],
                color,
                color,
                color,
                color
            );
        }
    }

    private static float[] offsetPolyline(float[] pts, float offset) {
        int count = pts.length / 2;
        float[] out = new float[pts.length];
        for (int i = 0; i < count; i++) {
            float px = pts[i * 2];
            float py = pts[i * 2 + 1];
            float[] nPrev = i > 0 ? segmentNormal(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], px, py) : null;
            float[] nNext = i + 1 < count ? segmentNormal(px, py, pts[(i + 1) * 2], pts[(i + 1) * 2 + 1]) : null;
            float nx;
            float ny;
            if (nPrev != null && nNext != null) {
                float mx = nPrev[0] + nNext[0];
                float my = nPrev[1] + nNext[1];
                float mLen = (float) Math.hypot(mx, my);
                nx = mLen > 1e-4f ? mx / mLen : nNext[0];
                ny = mLen > 1e-4f ? my / mLen : nNext[1];
            } else if (nNext != null) {
                nx = nNext[0];
                ny = nNext[1];
            } else if (nPrev != null) {
                nx = nPrev[0];
                ny = nPrev[1];
            } else {
                nx = 0f;
                ny = 1f;
            }
            out[i * 2] = px + nx * offset;
            out[i * 2 + 1] = py + ny * offset;
        }
        return out;
    }

    private static float @Nullable [] segmentNormal(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            return null;
        }
        return new float[]{-dy / len, dx / len};
    }

    private static float[] trimEnd(float[] pts, float trimDistance) {
        int count = pts.length / 2;
        if (count < 2 || trimDistance <= 0.1f) {
            return pts;
        }
        double total = polylineLength(pts);
        if (total <= trimDistance + 2.0) {
            return pts;
        }
        double remaining = trimDistance;
        int endIndex = count - 1;
        while (endIndex > 0) {
            float ax = pts[(endIndex - 1) * 2];
            float ay = pts[(endIndex - 1) * 2 + 1];
            float bx = pts[endIndex * 2];
            float by = pts[endIndex * 2 + 1];
            double seg = Math.hypot(bx - ax, by - ay);
            if (seg >= remaining && seg > 1e-4) {
                double ratio = (seg - remaining) / seg;
                float[] trimmed = new float[(endIndex + 1) * 2];
                System.arraycopy(pts, 0, trimmed, 0, endIndex * 2);
                trimmed[endIndex * 2] = (float) (ax + (bx - ax) * ratio);
                trimmed[endIndex * 2 + 1] = (float) (ay + (by - ay) * ratio);
                return trimmed;
            }
            remaining -= seg;
            endIndex--;
        }
        return pts;
    }

    private static void drawDashedRibbon(
        SceneCanvas canvas,
        float[] pts,
        float thickness,
        int color,
        float dash,
        float gap,
        double phaseOffset
    ) {
        double total = polylineLength(pts);
        if (total <= 1e-3) {
            return;
        }
        double period = Math.max(2.0, dash + gap);
        double offset = phaseOffset % period;
        if (offset < 0.0) {
            offset += period;
        }
        for (double d = offset - period; d < total; d += period) {
            double segStart = Math.max(0.0, d);
            double segEnd = Math.min(total, d + dash);
            if (segEnd - segStart <= 0.5) {
                continue;
            }
            float[] a = samplePolyline(pts, segStart / total);
            float[] b = samplePolyline(pts, segEnd / total);
            drawRibbon(canvas, new float[]{a[0], a[1], b[0], b[1]}, thickness, color);
        }
    }

    private static void drawBeadMarkers(SceneCanvas canvas, float[] pts, CirrusPalette palette, int color, float dim) {
        double total = polylineLength(pts);
        int count = Math.max(3, Math.min(10, (int) Math.round(total / 16.0)));
        int ink = StepNodeCardRenderer.withAlpha(OreUiPaint.outerInk(palette), dim);
        for (int index = 1; index <= count; index++) {
            float[] p = samplePolyline(pts, index / (double) (count + 1));
            int px = Math.round(p[0]);
            int py = Math.round(p[1]);
            canvas.fill(px - 2, py - 2, 5, 5, ink);
            canvas.fill(px - 1, py - 1, 3, 3, color);
        }
    }

    private static void drawAmbientPulse(SceneCanvas canvas, float[] pts, int color, float dim, long timeMs, int seed) {
        float length = polylineLength(pts);
        if (length < 40f) {
            return;
        }
        float phase = (Math.abs(seed) % 17) / 17f;
        float t = OreUiMotion.cycle01(timeMs, 2600L, phase, false);
        float trail = Math.max(0f, t - Math.min(0.14f, 12f / length));
        float[] a = samplePolyline(pts, trail);
        float[] b = samplePolyline(pts, t);
        float edgeFade = Math.min(1f, Math.min(t * 6f, (1f - t) * 6f));
        int pulseInk = StepNodeCardRenderer.withAlpha(color, dim * 0.42f * edgeFade);
        drawRibbon(canvas, new float[]{a[0], a[1], b[0], b[1]}, 2.4f, pulseInk);
    }

    private static void drawFlowChevrons(
        SceneCanvas canvas,
        float[] pts,
        int color,
        float dim,
        long timeMs,
        boolean reducedMotion
    ) {
        if (reducedMotion) {
            return;
        }
        float length = polylineLength(pts);
        if (length < 18f) {
            return;
        }
        int count = WorkbenchPaint.flowParticleCount(length);
        int ink = StepNodeCardRenderer.withAlpha(color, dim * 0.95f);
        int trailInk = StepNodeCardRenderer.withAlpha(color, dim * 0.40f);
        for (int index = 0; index < count; index++) {
            double t = WorkbenchPaint.flowParticleT(timeMs, length, index);
            double tBack = Math.max(0.0, t - Math.min(0.08, 4.5 / length));
            double tTail = Math.max(0.0, t - Math.min(0.16, 13.0 / length));
            float[] head = samplePolyline(pts, t);
            float[] tail = samplePolyline(pts, tBack);
            float[] wake = samplePolyline(pts, tTail);
            drawRibbon(canvas, new float[]{wake[0], wake[1], head[0], head[1]}, 2.6f, trailInk);
            drawTriangleArrow(canvas, tail[0], tail[1], head[0], head[1], 4.6f, 2.2f, ink);
        }
    }

    private static void drawSourceJack(
        SceneCanvas canvas,
        float x,
        float y,
        DagEdgeRoute.EdgeKind kind,
        CirrusPalette palette,
        int accent,
        float dim
    ) {
        int px = Math.round(x);
        int py = Math.round(y);
        int ink = StepNodeCardRenderer.withAlpha(OreUiPaint.outerInk(palette), dim);
        if (kind == DagEdgeRoute.EdgeKind.machine || kind == DagEdgeRoute.EdgeKind.catalyst) {
            // Rotated diamond jack for equipment / catalyst edges.
            drawDiamond(canvas, px, py, 3.6f, ink);
            drawDiamond(canvas, px, py, 2.3f, accent);
            return;
        }
        canvas.fill(px - 3, py - 3, 6, 6, ink);
        canvas.fill(px - 2, py - 2, 4, 4, accent);
        canvas.fill(px - 1, py - 1, 2, 2, StepNodeCardRenderer.withAlpha(OreUiPaint.lighten(accent, 0.50f), dim));
    }

    private static void drawTargetTerminal(
        SceneCanvas canvas,
        float[] pts,
        DagEdgeRoute.EdgeKind kind,
        CirrusPalette palette,
        int casing,
        int color,
        boolean hovered
    ) {
        int count = pts.length / 2;
        if (count < 2) {
            return;
        }
        float tipX = pts[(count - 1) * 2];
        float tipY = pts[(count - 1) * 2 + 1];
        float fromX = tipX;
        float fromY = tipY;
        for (int index = count - 2; index >= 0; index--) {
            fromX = pts[index * 2];
            fromY = pts[index * 2 + 1];
            if (Math.hypot(tipX - fromX, tipY - fromY) >= 0.5f) {
                break;
            }
        }
        float dx = tipX - fromX;
        float dy = tipY - fromY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.25f) {
            return;
        }
        float ux = dx / dist;
        float uy = dy / dist;

        float dockX = tipX - ux * 1.2f;
        float dockY = tipY - uy * 1.2f;

        if (kind == DagEdgeRoute.EdgeKind.machine) {
            float cx = dockX - ux * 2.0f;
            float cy = dockY - uy * 2.0f;
            drawDiamond(canvas, cx, cy, hovered ? 5.2f : 4.6f, casing);
            drawDiamond(canvas, cx, cy, hovered ? 3.8f : 3.3f, color);
            return;
        }
        if (kind == DagEdgeRoute.EdgeKind.catalyst) {
            float cx = dockX - ux * 2.0f;
            float cy = dockY - uy * 2.0f;
            float outer = hovered ? 4.4f : 3.8f;
            float mid = hovered ? 3.2f : 2.7f;
            canvas.coloredQuad(
                cx - outer,
                cy - outer,
                cx + outer,
                cy - outer,
                cx + outer,
                cy + outer,
                cx - outer,
                cy + outer,
                casing,
                casing,
                casing,
                casing
            );
            canvas.coloredQuad(
                cx - mid,
                cy - mid,
                cx + mid,
                cy - mid,
                cx + mid,
                cy + mid,
                cx - mid,
                cy + mid,
                color,
                color,
                color,
                color
            );
            return;
        }

        float len = hovered ? 10.6f : 9.2f;
        float halfBase = hovered ? 4.8f : 4.2f;
        drawTriangleArrow(
            canvas,
            fromX,
            fromY,
            dockX + ux * 0.8f,
            dockY + uy * 0.8f,
            len + 1.6f,
            halfBase + 1.0f,
            casing
        );
        drawTriangleArrow(canvas, fromX, fromY, dockX, dockY, len, halfBase, color);
    }

    /**
     * Draws a clean, straight-backed solid isosceles triangle arrowhead.
     */
    public static void drawTriangleArrow(
        SceneCanvas canvas,
        float fromX,
        float fromY,
        float tipX,
        float tipY,
        float arrowLen,
        float halfBase,
        int color
    ) {
        float dx = tipX - fromX;
        float dy = tipY - fromY;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.25f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float nx = -uy;
        float ny = ux;
        float baseX = tipX - ux * arrowLen;
        float baseY = tipY - uy * arrowLen;
        float leftX = baseX + nx * halfBase;
        float leftY = baseY + ny * halfBase;
        float rightX = baseX - nx * halfBase;
        float rightY = baseY - ny * halfBase;
        canvas.coloredQuad(tipX, tipY, leftX, leftY, rightX, rightY, tipX, tipY, color, color, color, color);
    }

    public static void drawStealthChevron(
        SceneCanvas canvas,
        float fromX,
        float fromY,
        float tipX,
        float tipY,
        float arrowLen,
        float halfBase,
        float notchRatio,
        int color
    ) {
        drawTriangleArrow(canvas, fromX, fromY, tipX, tipY, arrowLen, halfBase, color);
    }

    private static void drawDiamond(SceneCanvas canvas, float cx, float cy, float radius, int color) {
        canvas.coloredQuad(
            cx,
            cy - radius,
            cx + radius,
            cy,
            cx,
            cy + radius,
            cx - radius,
            cy,
            color,
            color,
            color,
            color
        );
    }

    private static void drawFlowPill(
        SceneCanvas canvas,
        float[] pts,
        long amount,
        CirrusPalette palette,
        int accent,
        float dim,
        boolean hovered
    ) {
        String label = "\u00d7" + WorkbenchFormatters.formatAmount(amount);
        float[] mid = samplePolyline(pts, 0.5);
        int width = canvas.font().width(label) + 6;
        int height = 11;
        int pillX = Math.round(mid[0]) - width / 2;
        int pillY = Math.round(mid[1]) - height / 2;
        int bg = StepNodeCardRenderer.withAlpha(palette.popoverSurface(), hovered ? 0.98f : dim * 0.90f);
        int border = StepNodeCardRenderer.withAlpha(hovered ? accent : OreUiPaint.withAlpha(accent, 0.75f), dim);
        canvas.fill(pillX, pillY, width, height, bg);
        canvas.strokeRect(pillX, pillY, width, height, border, 1);
        canvas.drawString(
            label,
            pillX + 3,
            pillY + 2,
            StepNodeCardRenderer.withAlpha(hovered ? palette.textPrimary() : accent, dim),
            false
        );
    }

    private static Component edgeKindLabel(DagEdgeRoute.EdgeKind kind) {
        return switch (kind) {
            case material -> Component.literal("MATERIAL");
            case catalyst -> Component.literal("CATALYST");
            case machine -> Component.literal("MACHINE");
            case autoLine -> Component.literal("AUTO LINE");
            case loopBack -> Component.literal("LOOP");
        };
    }

    private static float polylineLength(float[] pts) {
        float total = 0f;
        int count = pts.length / 2;
        for (int index = 0; index < count - 1; index++) {
            total += (float) Math
                    .hypot(pts[(index + 1) * 2] - pts[index * 2], pts[(index + 1) * 2 + 1] - pts[index * 2 + 1]);
        }
        return total;
    }

    private static float[] samplePolyline(float[] pts, double t) {
        int count = pts.length / 2;
        if (count <= 0) {
            return new float[]{0f, 0f};
        }
        if (count == 1) {
            return new float[]{pts[0], pts[1]};
        }
        float total = polylineLength(pts);
        if (total <= 1e-4f) {
            return new float[]{pts[0], pts[1]};
        }
        double target = Math.max(0.0, Math.min(1.0, t)) * total;
        double walked = 0.0;
        for (int index = 0; index < count - 1; index++) {
            float ax = pts[index * 2];
            float ay = pts[index * 2 + 1];
            float bx = pts[(index + 1) * 2];
            float by = pts[(index + 1) * 2 + 1];
            double seg = Math.hypot(bx - ax, by - ay);
            if (walked + seg >= target && seg > 1e-5) {
                double local = (target - walked) / seg;
                return new float[]{(float) (ax + (bx - ax) * local), (float) (ay + (by - ay) * local)};
            }
            walked += seg;
        }
        return new float[]{pts[(count - 1) * 2], pts[(count - 1) * 2 + 1]};
    }

    public static DagEdgeRoute shifted(
        DagEdgeRoute edge,
        Set<String> movedStepIds,
        float dx,
        float dy,
        DagLayoutMode mode
    ) {
        if (edge == null || movedStepIds == null || movedStepIds.isEmpty()) {
            return edge;
        }
        boolean moveFrom = movedStepIds.contains(edge.fromStepId());
        boolean moveTo = movedStepIds.contains(edge.toStepId());
        if (!moveFrom && !moveTo) {
            return edge;
        }
        float fromDx = moveFrom ? dx : 0.0f;
        float fromDy = moveFrom ? dy : 0.0f;
        float toDx = moveTo ? dx : 0.0f;
        float toDy = moveTo ? dy : 0.0f;
        float x0 = edge.x0() + fromDx;
        float y0 = edge.y0() + fromDy;
        float x1 = edge.x1() + toDx;
        float y1 = edge.y1() + toDy;
        float cx0;
        float cy0;
        float cx1;
        float cy1;
        if (edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
            float bend = Math.max(loopControlMin, Math.abs(x1 - x0) * 0.25f);
            cx0 = x0 + bend;
            cy0 = y0 + loopSag;
            cx1 = x1 - bend;
            cy1 = y1 + loopSag;
        } else if (mode == DagLayoutMode.verticalTb) {
            float control = Math.max(controlMin, Math.abs(y1 - y0) * 0.48f);
            cx0 = x0;
            cy0 = y0 + control;
            cx1 = x1;
            cy1 = y1 - control;
        } else if (mode == DagLayoutMode.verticalBt) {
            float control = Math.max(controlMin, Math.abs(y1 - y0) * 0.48f);
            cx0 = x0;
            cy0 = y0 - control;
            cx1 = x1;
            cy1 = y1 + control;
        } else if (mode == DagLayoutMode.horizontalRl) {
            float control = Math.max(controlMin, Math.abs(x1 - x0) * 0.48f);
            cx0 = x0 - control;
            cy0 = y0;
            cx1 = x1 + control;
            cy1 = y1;
        } else {
            float control = Math.max(controlMin, Math.abs(x1 - x0) * 0.48f);
            cx0 = x0 + control;
            cy0 = y0;
            cx1 = x1 - control;
            cy1 = y1;
        }
        return new DagEdgeRoute(
            edge.fromStepId(),
            edge.toStepId(),
            edge.kind(),
            edge.resource(),
            edge.amountFlow(),
            edge.sourceTotalProduced(),
            x0,
            y0,
            cx0,
            cy0,
            cx1,
            cy1,
            x1,
            y1,
            edge.criticalPath(),
            edge.diffStatus()
        );
    }

    public static @Nullable DagEdgeRoute offsetEndpoint(DagEdgeRoute edge, float offsetX, float offsetY) {
        if (edge == null) {
            return null;
        }
        return new DagEdgeRoute(
            edge.fromStepId(),
            edge.toStepId(),
            edge.kind(),
            edge.resource(),
            edge.amountFlow(),
            edge.sourceTotalProduced(),
            edge.x0() + offsetX,
            edge.y0() + offsetY,
            edge.cx0() + offsetX,
            edge.cy0() + offsetY,
            edge.cx1() + offsetX,
            edge.cy1() + offsetY,
            edge.x1() + offsetX,
            edge.y1() + offsetY,
            edge.criticalPath(),
            edge.diffStatus()
        );
    }

    public static void drawMinimapEdge(
        SceneCanvas canvas,
        DagEdgeRoute edge,
        int x,
        int y,
        CirrusPalette palette,
        double scale,
        float contentMinX,
        float contentMinY
    ) {
        if (canvas == null || edge == null) {
            return;
        }
        float x0 = (float) ((edge.x0() - contentMinX) * scale) + x;
        float y0 = (float) ((edge.y0() - contentMinY) * scale) + y;
        float x1 = (float) ((edge.x1() - contentMinX) * scale) + x;
        float y1 = (float) ((edge.y1() - contentMinY) * scale) + y;
        int color = colorOf(edge, palette, EdgeState.idle);
        canvas.line(x0, y0, x1, y1, 1f, StepNodeCardRenderer.withAlpha(color, 0.55f));
    }
}
