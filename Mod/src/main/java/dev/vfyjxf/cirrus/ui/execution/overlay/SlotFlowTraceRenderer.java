package dev.vfyjxf.cirrus.ui.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotTrace;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Animated material-flow renderer for slot-based execution traces over container screens.
 */
public final class SlotFlowTraceRenderer {

    private static final int slotFrameInset = 1;
    private static final int slotFrameSize = 18;
    private static final int bezierSegments = 14;
    private static final long feedCycleMs = 1500L;
    private static final long activeFeedCycleMs = 1000L;
    private static final long pulsePeriodMs = 1800L;

    public record SlotAnchor(SlotLayout.Reference reference, Rect2i rect, ItemStack currentStack) {
        public SlotAnchor {
            currentStack = normalizedStack(currentStack);
        }

        public float centerX() {
            return rect.getX() + rect.getWidth() * 0.5f;
        }

        public float centerY() {
            return rect.getY() + rect.getHeight() * 0.5f;
        }
    }

    private record ResolvedSlot(
        ExecutionOverlayTraceSnapshot.SlotHighlight highlight,
        SlotAnchor anchor,
        int semanticColor
    ) {}

    private record MoveKey(SlotLayout.Reference from, SlotLayout.Reference to) {}

    public void reset() {}

    public void render(
        SceneCanvas canvas,
        AbstractContainerScreen<?> screen,
        MainPanel panel,
        ExecutionOverlayTraceSnapshot trace,
        CirrusPalette palette
    ) {
        if (!trace.available()) {
            return;
        }
        List<SlotAnchor> anchors = resolveContainerAnchors(screen, trace);
        if (anchors.isEmpty()) {
            return;
        }
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();
        int guiWidth = Math.max(176, screen.getXSize());
        renderTraceForContainer(canvas, guiLeft, guiTop, guiWidth, anchors, trace, palette, panel.isAutoRunning());
    }

    public void renderTraceForContainer(
        SceneCanvas canvas,
        int guiLeft,
        int guiTop,
        int guiWidth,
        List<SlotAnchor> anchors,
        ExecutionOverlayTraceSnapshot trace,
        CirrusPalette palette,
        boolean autoRunning
    ) {
        if (!trace.available() || anchors.isEmpty()) {
            return;
        }
        Map<SlotLayout.Reference, SlotAnchor> anchorByRef = new LinkedHashMap<>();
        for (SlotAnchor anchor : anchors) {
            anchorByRef.put(anchor.reference(), anchor);
        }

        boolean dark = OreUiPaint.isDark(palette);
        boolean reduced = OreUiMotion.reducedMotion();
        long nowMs = OreUiMotion.nowMs();
        float pulse = OreUiMotion.pulse01(nowMs, pulsePeriodMs, reduced);
        float flowPhase = OreUiMotion.cycle01(nowMs, autoRunning ? activeFeedCycleMs : feedCycleMs, 0f, reduced);

        int feedColor = palette.focusRing();
        int readyColor = palette.costS();
        int craftColor = OreUiMotion.lerpColor(palette.costH(), palette.focusRing(), 0.25f);
        int missingColor = palette.ruleHardBan();

        List<ResolvedSlot> resolvedSlots = new ArrayList<>();
        Map<SlotLayout.Reference, ResolvedSlot> slotByRef = new LinkedHashMap<>();
        Set<SlotLayout.Reference> currentRefs = new HashSet<>();

        int totalGridSlots = 0;
        int readyGridSlots = 0;
        int missingGridSlots = 0;
        boolean hasExecutingGrid = false;
        boolean hasOutputCollect = false;
        ResolvedSlot outputSlot = null;
        float gridSumY = 0f;
        float gridMaxX = Float.NEGATIVE_INFINITY;
        int gridCount = 0;

        for (ExecutionOverlayTraceSnapshot.SlotHighlight highlight : trace.highlights()) {
            SlotAnchor anchor = anchorByRef.get(highlight.slot());
            if (anchor == null) {
                continue;
            }
            int semantic = roleColor(highlight.role(), feedColor, readyColor, craftColor, missingColor);
            ResolvedSlot resolved = new ResolvedSlot(highlight, anchor, semantic);
            resolvedSlots.add(resolved);
            slotByRef.put(highlight.slot(), resolved);
            currentRefs.add(highlight.slot());

            switch (highlight.role()) {
                case SlotTrace.Roles.inputReady -> {
                    totalGridSlots++;
                    readyGridSlots++;
                    gridSumY += anchor.centerY();
                    gridMaxX = Math.max(gridMaxX, anchor.rect().getX() + anchor.rect().getWidth());
                    gridCount++;
                }
                case SlotTrace.Roles.inputPreview -> {
                    totalGridSlots++;
                    gridSumY += anchor.centerY();
                    gridMaxX = Math.max(gridMaxX, anchor.rect().getX() + anchor.rect().getWidth());
                    gridCount++;
                }
                case SlotTrace.Roles.inputMissing -> {
                    totalGridSlots++;
                    missingGridSlots++;
                    gridSumY += anchor.centerY();
                    gridMaxX = Math.max(gridMaxX, anchor.rect().getX() + anchor.rect().getWidth());
                    gridCount++;
                }
                case SlotTrace.Roles.inputExecuting -> {
                    hasExecutingGrid = true;
                    totalGridSlots++;
                    readyGridSlots++;
                    gridSumY += anchor.centerY();
                    gridMaxX = Math.max(gridMaxX, anchor.rect().getX() + anchor.rect().getWidth());
                    gridCount++;
                }
                case SlotTrace.Roles.output, SlotTrace.Roles.outputPreview -> outputSlot = resolved;
                case SlotTrace.Roles.inventoryTarget -> hasOutputCollect = true;
                default -> {}
            }
        }

        if (resolvedSlots.isEmpty()) {
            return;
        }

        // Layer 1: slot backplates & animated washes
        for (ResolvedSlot slot : resolvedSlots) {
            drawSlotBackplate(canvas, slot, dark, pulse);
        }

        // Layer 2: curved Bezier flow conduits with fan-out separation and traveling energy comets
        renderFlowConduits(
            canvas,
            trace,
            anchorByRef,
            slotByRef,
            palette,
            dark,
            reduced,
            flowPhase,
            feedColor,
            readyColor,
            craftColor
        );

        // Preview bridge from crafting grid to output when preparing inputs
        if (outputSlot != null && gridCount > 0 && !hasExecutingGrid) {
            boolean alreadyMovesToOutput = false;
            for (ExecutionOverlayTraceSnapshot.SlotMove move : trace.moves()) {
                if (move.to().equals(outputSlot.anchor().reference())) {
                    alreadyMovesToOutput = true;
                    break;
                }
            }
            if (!alreadyMovesToOutput) {
                float startX = gridMaxX + 2f;
                float startY = gridSumY / gridCount;
                float endX = outputSlot.anchor().rect().getX() - 2f;
                float endY = outputSlot.anchor().centerY();
                if (endX > startX + 4f) {
                    drawPreviewConvergenceBridge(
                        canvas,
                        startX,
                        startY,
                        endX,
                        endY,
                        craftColor,
                        dark,
                        reduced,
                        flowPhase
                    );
                }
            }
        }

        // Layer 3: animated slot frames, ghost items, micro-progress bars, role badges & arrival ripples
        for (ResolvedSlot slot : resolvedSlots) {
            drawSlotForeground(canvas, slot, palette, dark, pulse, flowPhase, nowMs, reduced);
        }

        // Layer 4: floating OreUI flow ribbon docked above the container
        drawFlowRibbon(
            canvas,
            guiLeft,
            guiTop,
            guiWidth,
            trace,
            palette,
            dark,
            nowMs,
            reduced,
            autoRunning,
            totalGridSlots,
            readyGridSlots,
            missingGridSlots,
            hasExecutingGrid,
            hasOutputCollect,
            feedColor,
            readyColor,
            craftColor,
            missingColor
        );
    }

    private void renderFlowConduits(
        SceneCanvas canvas,
        ExecutionOverlayTraceSnapshot trace,
        Map<SlotLayout.Reference, SlotAnchor> anchorByRef,
        Map<SlotLayout.Reference, ResolvedSlot> slotByRef,
        CirrusPalette palette,
        boolean dark,
        boolean reduced,
        float flowPhase,
        int feedColor,
        int readyColor,
        int craftColor
    ) {
        if (trace.moves().isEmpty()) {
            return;
        }
        Map<SlotLayout.Reference, Integer> outTotals = new HashMap<>();
        Map<SlotLayout.Reference, Integer> inTotals = new HashMap<>();
        for (ExecutionOverlayTraceSnapshot.SlotMove move : trace.moves()) {
            outTotals.merge(move.from(), 1, Integer::sum);
            inTotals.merge(move.to(), 1, Integer::sum);
        }
        Map<SlotLayout.Reference, Integer> outSeen = new HashMap<>();
        Map<SlotLayout.Reference, Integer> inSeen = new HashMap<>();
        Set<Long> pillCells = new HashSet<>();
        Set<MoveKey> seenPairs = new HashSet<>();

        int moveOrdinal = 0;
        for (ExecutionOverlayTraceSnapshot.SlotMove move : trace.moves()) {
            SlotAnchor from = anchorByRef.get(move.from());
            SlotAnchor to = anchorByRef.get(move.to());
            if (from == null || to == null) {
                continue;
            }
            MoveKey pairKey = new MoveKey(move.from(), move.to());
            if (!seenPairs.add(pairKey)) {
                continue;
            }

            int outIdx = outSeen.merge(move.from(), 1, Integer::sum) - 1;
            int outCnt = outTotals.getOrDefault(move.from(), 1);
            int inIdx = inSeen.merge(move.to(), 1, Integer::sum) - 1;
            int inCnt = inTotals.getOrDefault(move.to(), 1);

            ResolvedSlot fromSlot = slotByRef.get(move.from());
            ResolvedSlot toSlot = slotByRef.get(move.to());
            int streamColor = conduitColor(fromSlot, toSlot, feedColor, readyColor, craftColor);

            float sx = from.centerX();
            float sy = from.centerY();
            float ex = to.centerX();
            float ey = to.centerY();
            float dx = ex - sx;
            float dy = ey - sy;
            float dist = (float) Math.hypot(dx, dy);
            if (dist < 2f) {
                continue;
            }

            // Trim endpoints slightly inside slot border so lines start/end cleanly at slot edges
            float ux = dx / dist;
            float uy = dy / dist;
            float trim = Math.min(7.5f, dist * 0.25f);
            float p0x = sx + ux * trim;
            float p0y = sy + uy * trim;
            float p2x = ex - ux * trim;
            float p2y = ey - uy * trim;

            // Compute smooth perpendicular arch with fan-out spread
            float nx = -uy;
            float ny = ux;
            float fanFrom = outCnt > 1 ? (outIdx - (outCnt - 1) * 0.5f) * 9.5f : 0f;
            float fanTo = inCnt > 1 ? (inIdx - (inCnt - 1) * 0.5f) * 8.0f : 0f;
            float baseArch = Math.max(9f, Math.min(30f, dist * 0.20f));
            float dirSign = (dx >= 0f ? -1f : 1f);
            float offset = dirSign * baseArch + fanFrom + fanTo;

            float mx = (p0x + p2x) * 0.5f + nx * offset;
            float my = (p0y + p2y) * 0.5f + ny * offset;

            float phaseStagger = (moveOrdinal * 0.17f) % 1.0f;
            float localPhase = reduced ? 0.52f : ((flowPhase + phaseStagger) % 1.0f);

            drawBezierConduit(canvas, p0x, p0y, mx, my, p2x, p2y, dist, streamColor, palette, dark, localPhase);

            if (move.amount() > 1L && dist >= 26f) {
                float apexX = quadBezier(p0x, mx, p2x, 0.5f);
                float apexY = quadBezier(p0y, my, p2y, 0.5f);
                long cell = (((long) Math.round(apexX / 16f)) << 32) ^ (Math.round(apexY / 12f) & 0xFFFFFFFFL);
                if (pillCells.add(cell)) {
                    drawMicroBadge(
                        canvas,
                        Math.round(apexX),
                        Math.round(apexY),
                        "\u00d7" + move.amount(),
                        streamColor,
                        palette
                    );
                }
            }
            moveOrdinal++;
        }
    }

    private static void drawBezierConduit(
        SceneCanvas canvas,
        float x0,
        float y0,
        float cx,
        float cy,
        float x2,
        float y2,
        float dist,
        int streamColor,
        CirrusPalette palette,
        boolean dark,
        float phase
    ) {
        int casing = OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), dark ? 0.34f : 0.22f);
        int lineColor = OreUiPaint.withAlpha(streamColor, dark ? 0.58f : 0.52f);
        int beadColor = dark ? OreUiPaint.lighten(streamColor, 0.32f) : streamColor;

        float prevX = x0;
        float prevY = y0;
        for (int i = 1; i <= bezierSegments; i++) {
            float t = i / (float) bezierSegments;
            float px = quadBezier(x0, cx, x2, t);
            float py = quadBezier(y0, cy, y2, t);
            canvas.line(prevX, prevY, px, py, 2.2f, casing);
            canvas.line(prevX, prevY, px, py, 1.2f, lineColor);
            prevX = px;
            prevY = py;
        }

        drawChevronOnCurve(canvas, x0, y0, cx, cy, x2, y2, 0.78f, 3.2f, OreUiPaint.withAlpha(beadColor, 0.88f));

        // Restrained sliding flow beads expressing material movement from source to destination.
        int beadCount = dist >= 80f ? 2 : 1;
        for (int b = 0; b < beadCount; b++) {
            float u = (phase + b / (float) beadCount) % 1.0f;
            float edgeFade = Math.min(1f, Math.min(u / 0.12f, (1f - u) / 0.12f));
            if (edgeFade <= 0.04f) {
                continue;
            }
            float headX = quadBezier(x0, cx, x2, u);
            float headY = quadBezier(y0, cy, y2, u);
            float tailU = Math.max(0f, u - 0.06f);
            float tailX = quadBezier(x0, cx, x2, tailU);
            float tailY = quadBezier(y0, cy, y2, tailU);
            canvas.line(tailX, tailY, headX, headY, 1.8f, OreUiPaint.withAlpha(beadColor, 0.55f * edgeFade));
            canvas.circle(headX, headY, 1.55f, OreUiPaint.withAlpha(beadColor, 0.94f * edgeFade));
        }
    }

    private static void drawPreviewConvergenceBridge(
        SceneCanvas canvas,
        float x0,
        float y0,
        float x1,
        float y1,
        int craftColor,
        boolean dark,
        boolean reduced,
        float phase
    ) {
        float mx = (x0 + x1) * 0.5f;
        float my = (y0 + y1) * 0.5f - 2f;
        int soft = OreUiPaint.withAlpha(craftColor, dark ? 0.34f : 0.26f);
        int bright = OreUiPaint.withAlpha(dark ? OreUiPaint.lighten(craftColor, 0.28f) : craftColor, 0.82f);
        int segs = 8;
        for (int i = 0; i < segs; i += 2) {
            float t0 = i / (float) segs;
            float t1 = Math.min(1f, (i + 1.1f) / segs);
            canvas.line(
                quadBezier(x0, mx, x1, t0),
                quadBezier(y0, my, y1, t0),
                quadBezier(x0, mx, x1, t1),
                quadBezier(y0, my, y1, t1),
                1.1f,
                soft
            );
        }
        float u = reduced ? 0.55f : phase;
        float px = quadBezier(x0, mx, x1, u);
        float py = quadBezier(y0, my, y1, u);
        canvas.circle(px, py, 1.25f, bright);
        drawChevronOnCurve(canvas, x0, y0, mx, my, x1, y1, 0.78f, 3.0f, bright);
    }

    private static void drawChevronOnCurve(
        SceneCanvas canvas,
        float x0,
        float y0,
        float cx,
        float cy,
        float x2,
        float y2,
        float t,
        float size,
        int color
    ) {
        float px = quadBezier(x0, cx, x2, t);
        float py = quadBezier(y0, cy, y2, t);
        float tx = 2f * (1f - t) * (cx - x0) + 2f * t * (x2 - cx);
        float ty = 2f * (1f - t) * (cy - y0) + 2f * t * (y2 - cy);
        float len = (float) Math.hypot(tx, ty);
        if (len < 0.001f) {
            return;
        }
        float ux = tx / len;
        float uy = ty / len;
        float nx = -uy;
        float ny = ux;
        float backX = px - ux * size;
        float backY = py - uy * size;
        float wing = size * 0.68f;
        canvas.line(backX + nx * wing, backY + ny * wing, px, py, 1.25f, color);
        canvas.line(backX - nx * wing, backY - ny * wing, px, py, 1.25f, color);
    }

    private static void drawSlotBackplate(SceneCanvas canvas, ResolvedSlot slot, boolean dark, float pulse) {
        Rect2i rect = slot.anchor().rect();
        int color = slot.semanticColor();
        float alpha = switch (slot.highlight().role()) {
            case SlotTrace.Roles.inputReady -> dark ? 0.14f : 0.11f;
            case SlotTrace.Roles.inputMissing -> (dark ? 0.14f : 0.11f) + 0.08f * pulse;
            case SlotTrace.Roles.inputExecuting -> dark ? 0.16f : 0.12f;
            case SlotTrace.Roles.output, SlotTrace.Roles.outputPreview -> dark ? 0.14f : 0.10f;
            case SlotTrace.Roles.inventoryTarget -> dark ? 0.12f : 0.09f;
            case SlotTrace.Roles.inventory -> dark ? 0.09f : 0.07f;
            default -> dark ? 0.09f : 0.07f;
        };
        canvas.fill(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), OreUiPaint.withAlpha(color, alpha));
    }

    private void drawSlotForeground(
        SceneCanvas canvas,
        ResolvedSlot slot,
        CirrusPalette palette,
        boolean dark,
        float pulse,
        float flowPhase,
        long nowMs,
        boolean reduced
    ) {
        Rect2i rect = slot.anchor().rect();
        int x = rect.getX();
        int y = rect.getY();
        int w = rect.getWidth();
        int h = rect.getHeight();
        String role = slot.highlight().role();
        int color = slot.semanticColor();
        int bright = dark ? OreUiPaint.lighten(color, 0.28f) : color;

        boolean emptySlot = slot.anchor().currentStack().isEmpty();
        ItemStack ghost = slot.highlight().stack();
        if (emptySlot
                && !ghost.isEmpty()
                && (SlotTrace.Roles.inputPreview.equals(role)
                        || SlotTrace.Roles.inputMissing.equals(role)
                        || SlotTrace.Roles.output.equals(role)
                        || SlotTrace.Roles.outputPreview.equals(role)
                        || SlotTrace.Roles.inventoryTarget.equals(role))) {
            float ghostAlpha = reduced ? 0.50f : (0.42f + 0.14f * pulse);
            int whiteTint = OreUiPaint.lighten(palette.cardSurface(), 1.0f);
            canvas.color(OreUiPaint.withAlpha(whiteTint, ghostAlpha));
            canvas.renderItem(ghost, x + 1, y + 1);
            canvas.resetColor();
        }

        switch (role) {
            case SlotTrace.Roles.inputReady -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, 0.78f), 1);
                drawCornerPip(canvas, x + w - 5, y + 1, color, palette, true);
            }
            case SlotTrace.Roles.inputMissing -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, 0.60f + 0.28f * pulse), 1);
                drawCornerBrackets(canvas, x, y, w, h, 3, bright);
                drawCornerPip(canvas, x + w - 5, y + 1, color, palette, false);
            }
            case SlotTrace.Roles.inputExecuting -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(bright, 0.80f), 1);
                drawCornerBrackets(canvas, x, y, w, h, 3, bright);
            }
            case SlotTrace.Roles.output, SlotTrace.Roles.outputPreview -> {
                boolean liveOutput = SlotTrace.Roles.output.equals(role);
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, liveOutput ? 0.88f : 0.64f), 1);
                drawCornerBrackets(canvas, x, y, w, h, 3, bright);
            }
            case SlotTrace.Roles.inventory -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, 0.52f), 1);
            }
            case SlotTrace.Roles.inventoryTarget -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, 0.68f), 1);
                drawCornerBrackets(canvas, x, y, w, h, 3, bright);
            }
            default -> {
                canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(color, 0.56f), 1);
            }
        }

        String label = slot.highlight().label();
        if (label != null && label.contains("/")) {
            float ratio = parseFraction(label);
            if (ratio >= 0f) {
                int barW = w - 4;
                int barX = x + 2;
                int barY = y + h - 3;
                canvas.fill(barX, barY, barW, 2, OreUiPaint.withAlpha(palette.recessedSurface(), 0.82f));
                int filled = Math.max(ratio > 0f ? 1 : 0, Math.round(barW * Math.min(1f, ratio)));
                if (filled > 0) {
                    canvas.fill(barX, barY, filled, 2, bright);
                }
            }
        } else if (label != null && !label.isBlank() && (label.startsWith("-") || label.startsWith("+"))) {
            drawCompactCornerTag(canvas, x + w - 1, y + h - 1, label, color, palette);
        }
    }

    private static void drawFlowRibbon(
        SceneCanvas canvas,
        int guiLeft,
        int guiTop,
        int guiWidth,
        ExecutionOverlayTraceSnapshot trace,
        CirrusPalette palette,
        boolean dark,
        long nowMs,
        boolean reduced,
        boolean autoRunning,
        int totalGridSlots,
        int readyGridSlots,
        int missingGridSlots,
        boolean hasExecutingGrid,
        boolean hasOutputCollect,
        int feedColor,
        int readyColor,
        int craftColor,
        int missingColor
    ) {
        int ribbonW = Math.min(236, Math.max(156, guiWidth));
        int ribbonH = 18;
        int rx = Math.max(4, guiLeft + (guiWidth - ribbonW) / 2);
        int ry = Math.max(4, guiTop - ribbonH - 4);

        Component phaseText;
        int phaseColor;
        double progress;
        if (missingGridSlots > 0) {
            phaseText = CirrusLangs.Execution.Cockpit.flowMissing.get(missingGridSlots);
            phaseColor = missingColor;
            progress = totalGridSlots > 0 ? readyGridSlots / (double) totalGridSlots : 0.0;
        } else if (hasOutputCollect && !hasExecutingGrid) {
            phaseText = CirrusLangs.Execution.Cockpit.flowCollecting.get();
            phaseColor = readyColor;
            progress = 0.92;
        } else if (hasExecutingGrid || (totalGridSlots > 0 && readyGridSlots >= totalGridSlots)) {
            phaseText = CirrusLangs.Execution.Cockpit.flowReadyCraft.get();
            phaseColor = craftColor;
            progress = 1.0;
        } else {
            phaseText = CirrusLangs.Execution.Cockpit.flowFeeding.get(readyGridSlots, Math.max(1, totalGridSlots));
            phaseColor = feedColor;
            progress = totalGridSlots > 0 ? readyGridSlots / (double) totalGridSlots : 0.25;
        }

        OreUiPaint.raisedCard(canvas, rx, ry, ribbonW, ribbonH, palette, false, autoRunning);

        int cursorX = rx + 4;
        ItemStack icon = trace.recipeIcon();
        if (!icon.isEmpty()) {
            canvas.pushTransform();
            canvas.translate(cursorX, ry + 2);
            canvas.scale(0.75f);
            canvas.renderItem(icon, 0, 0);
            canvas.popTransform();
            cursorX += 14;
        }

        // Clean static status dot
        canvas.fill(cursorX + 1, ry + 6, 4, 4, phaseColor);
        cursorX += 9;

        // Right-aligned phase pill
        int pillTextW = ChromeText.width(canvas, phaseText);
        int pillW = Math.min(Math.max(40, pillTextW + 8), ribbonW / 2);
        int pillH = 12;
        int pillX = rx + ribbonW - pillW - 4;
        int pillY = ry + 2;
        canvas.fill(pillX, pillY, pillW, pillH, OreUiPaint.withAlpha(phaseColor, dark ? 0.18f : 0.12f));
        canvas.strokeRect(pillX, pillY, pillW, pillH, OreUiPaint.withAlpha(phaseColor, dark ? 0.56f : 0.42f), 1);
        ChromeText.drawFitted(
            canvas,
            phaseText,
            pillX + 4,
            ChromeText.centeredY(pillY, pillH, canvas),
            Math.max(8, pillW - 8),
            dark ? OreUiPaint.lighten(phaseColor, 0.22f) : phaseColor
        );

        // Recipe title in remaining middle span
        int titleMaxW = pillX - cursorX - 4;
        if (titleMaxW >= 18) {
            Component titleComp = !icon.isEmpty()
                    ? icon.getHoverName()
                    : Component.literal(trace.recipeLabel() == null ? "" : trace.recipeLabel());
            ChromeText.drawFitted(
                canvas,
                titleComp,
                cursorX,
                ChromeText.centeredY(ry, ribbonH - 2, canvas),
                titleMaxW,
                palette.textPrimary()
            );
        }

        // Restrained bottom 2px progress strip
        int barX = rx + 2;
        int barY = ry + ribbonH - 3;
        int barW = Math.max(4, ribbonW - 4);
        canvas.fill(barX, barY, barW, 2, OreUiPaint.meterTrack(palette));
        int fillW = Math.max(4, (int) Math.round(barW * Math.max(0.08, Math.min(1.0, progress))));
        canvas.fill(barX, barY, fillW, 2, OreUiPaint.withAlpha(phaseColor, 0.80f));
    }

    private static void drawCornerBrackets(SceneCanvas canvas, int x, int y, int w, int h, int len, int color) {
        int l = Math.min(len, Math.min(w, h) / 2);
        // Top-left
        canvas.fill(x, y, l, 1, color);
        canvas.fill(x, y, 1, l, color);
        // Top-right
        canvas.fill(x + w - l, y, l, 1, color);
        canvas.fill(x + w - 1, y, 1, l, color);
        // Bottom-left
        canvas.fill(x, y + h - 1, l, 1, color);
        canvas.fill(x, y + h - l, 1, l, color);
        // Bottom-right
        canvas.fill(x + w - l, y + h - 1, l, 1, color);
        canvas.fill(x + w - 1, y + h - l, 1, l, color);
    }

    private static void drawCornerPip(
        SceneCanvas canvas,
        int x,
        int y,
        int color,
        CirrusPalette palette,
        boolean check
    ) {
        canvas.fill(x, y, 4, 4, color);
        int ink = palette.textOnAccent();
        if (check) {
            canvas.fill(x + 1, y + 2, 1, 1, ink);
            canvas.fill(x + 2, y + 1, 1, 1, ink);
        } else {
            canvas.fill(x + 1, y + 1, 2, 1, ink);
            canvas.fill(x + 1, y + 3, 2, 1, ink);
        }
    }

    private static void drawMicroBadge(
        SceneCanvas canvas,
        int centerX,
        int centerY,
        String text,
        int accent,
        CirrusPalette palette
    ) {
        boolean dark = OreUiPaint.isDark(palette);
        Component comp = Component.literal(text);
        int textW = Math.max(6, Math.round(ChromeText.width(canvas, comp) * 0.72f));
        int bw = textW + 6;
        int bh = 9;
        int bx = centerX - bw / 2;
        int by = centerY - bh / 2;
        canvas.fill(bx, by, bw, bh, OreUiPaint.withAlpha(palette.recessedSurface(), 0.94f));
        canvas.strokeRect(bx, by, bw, bh, OreUiPaint.withAlpha(accent, dark ? 0.85f : 0.78f), 1);
        canvas.pushTransform();
        canvas.translate(bx + 3, by + 2);
        canvas.scale(0.72f);
        canvas.drawString(comp, 0, 0, dark ? OreUiPaint.lighten(accent, 0.45f) : accent, false);
        canvas.popTransform();
    }

    private static void drawCompactCornerTag(
        SceneCanvas canvas,
        int rightX,
        int bottomY,
        String text,
        int accent,
        CirrusPalette palette
    ) {
        boolean dark = OreUiPaint.isDark(palette);
        Component comp = Component.literal(text);
        int textW = Math.max(6, Math.round(ChromeText.width(canvas, comp) * 0.68f));
        int bw = textW + 4;
        int bh = 8;
        int bx = rightX - bw;
        int by = bottomY - bh;
        canvas.fill(bx, by, bw, bh, OreUiPaint.withAlpha(palette.recessedSurface(), 0.92f));
        canvas.strokeRect(bx, by, bw, bh, OreUiPaint.withAlpha(accent, dark ? 0.78f : 0.72f), 1);
        canvas.pushTransform();
        canvas.translate(bx + 2, by + 1.5f);
        canvas.scale(0.68f);
        canvas.drawString(comp, 0, 0, dark ? OreUiPaint.lighten(accent, 0.42f) : accent, false);
        canvas.popTransform();
    }

    private static int roleColor(String role, int feedColor, int readyColor, int craftColor, int missingColor) {
        return switch (role) {
            case SlotTrace.Roles.inputReady, SlotTrace.Roles.inventoryTarget -> readyColor;
            case SlotTrace.Roles.inputMissing -> missingColor;
            case SlotTrace.Roles.output, SlotTrace.Roles.outputPreview, SlotTrace.Roles.inputExecuting -> craftColor;
            default -> feedColor;
        };
    }

    private static int conduitColor(
        @Nullable ResolvedSlot from,
        @Nullable ResolvedSlot to,
        int feedColor,
        int readyColor,
        int craftColor
    ) {
        if (to != null
                && (SlotTrace.Roles.output.equals(to.highlight().role())
                        || SlotTrace.Roles.outputPreview.equals(to.highlight().role()))) {
            return craftColor;
        }
        if ((from != null && SlotTrace.Roles.output.equals(from.highlight().role()))
                || (to != null && SlotTrace.Roles.inventoryTarget.equals(to.highlight().role()))) {
            return readyColor;
        }
        return feedColor;
    }

    private static float quadBezier(float p0, float p1, float p2, float t) {
        float inv = 1f - t;
        return inv * inv * p0 + 2f * inv * t * p1 + t * t * p2;
    }

    private static float parseFraction(String label) {
        int slash = label.indexOf('/');
        if (slash <= 0 || slash >= label.length() - 1) {
            return -1f;
        }
        try {
            long num = Long.parseLong(label.substring(0, slash).trim());
            long den = Long.parseLong(label.substring(slash + 1).trim());
            if (den <= 0L) {
                return -1f;
            }
            return Math.max(0f, Math.min(1f, num / (float) den));
        } catch (NumberFormatException ignored) {
            return -1f;
        }
    }

    private static List<SlotAnchor> resolveContainerAnchors(
        AbstractContainerScreen<?> screen,
        ExecutionOverlayTraceSnapshot trace
    ) {
        Map<SlotLayout.Reference, SlotAnchor> byRef = new LinkedHashMap<>();
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        for (ExecutionOverlayTraceSnapshot.SlotHighlight highlight : trace.highlights()) {
            addContainerAnchor(screen, left, top, highlight.slot(), byRef);
        }
        for (ExecutionOverlayTraceSnapshot.SlotMove move : trace.moves()) {
            addContainerAnchor(screen, left, top, move.from(), byRef);
            addContainerAnchor(screen, left, top, move.to(), byRef);
        }
        return new ArrayList<>(byRef.values());
    }

    private static void addContainerAnchor(
        AbstractContainerScreen<?> screen,
        int left,
        int top,
        SlotLayout.Reference ref,
        Map<SlotLayout.Reference, SlotAnchor> out
    ) {
        if (out.containsKey(ref)) {
            return;
        }
        Integer idx = slotIndex(ref);
        if (idx == null || idx < 0 || idx >= screen.getMenu().slots.size()) {
            return;
        }
        Slot slot = screen.getMenu().slots.get(idx);
        Rect2i rect = new Rect2i(
            left + slot.x - slotFrameInset,
            top + slot.y - slotFrameInset,
            slotFrameSize,
            slotFrameSize
        );
        out.put(ref, new SlotAnchor(ref, rect, slot.getItem()));
    }

    private static @Nullable Integer slotIndex(SlotLayout.Reference reference) {
        if (reference instanceof SlotLayout.Reference.Index index) {
            return index.index();
        }
        return null;
    }

    private static ItemStack normalizedStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
