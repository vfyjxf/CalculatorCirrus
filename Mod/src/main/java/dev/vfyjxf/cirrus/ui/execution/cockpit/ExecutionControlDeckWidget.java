package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionCheckpointStore;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.UiScaleMetrics;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

public final class ExecutionControlDeckWidget extends Widget implements OreUiTooltipProvider {

    public static final int preferredHeight = 44;
    public static final int compactHeight = 38;
    private static final int pad = 6;
    private static final int rowH = 17;
    private static final int gap = 3;

    public static final double[] speedRates = {1.0d, 2.0d, 5.0d, 12.0d, 40.0d};
    public static final String[] speedLabels = {"0.5\u00d7", "1\u00d7", "2\u00d7", "4\u00d7", "MAX"};

    private final MainPanel cockpit;
    private @Nullable Rect playRect;
    private @Nullable Rect stepRect;
    private @Nullable Rect skipRect;
    private @Nullable Rect resetRect;
    private final Rect[] speedRects = new Rect[speedRates.length];
    private boolean compactSpeedCycle;
    private int appliedDeckHeight = -1;
    private @Nullable Rect saveCpRect;
    private @Nullable Rect resumeCpRect;

    public ExecutionControlDeckWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        useStyle(heightOf(preferredHeight), flexShrink(0), minWidth(0));
        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());
            computeLayout(width());
            if (playRect != null && playRect.contains(lx, ly)) {
                if (cockpit.canControlExecution()) {
                    cockpit.toggleAutoExecute();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (stepRect != null && stepRect.contains(lx, ly)) {
                if (cockpit.canControlExecution()) {
                    cockpit.advanceOneStage();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (skipRect != null && skipRect.contains(lx, ly)) {
                if (cockpit.canSkipStage()) {
                    cockpit.skipCurrentStage();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (resetRect != null && resetRect.contains(lx, ly)) {
                if (cockpit.canControlExecution()) {
                    cockpit.resetExecution();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (compactSpeedCycle) {
                if (speedRects[0] != null && speedRects[0].contains(lx, ly)) {
                    ExecutionDriverSnapshot snap = cockpit.snapshot();
                    double currentRate = snap == null ? cockpit.currentSpeedRate() : snap.ratePerSecond();
                    int nextIdx = (activeSpeedIndex(currentRate) + 1) % speedRates.length;
                    cockpit.setSpeedRate(speedRates[nextIdx]);
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
            } else {
                for (int i = 0; i < speedRects.length; i++) {
                    if (speedRects[i] != null && speedRects[i].contains(lx, ly)) {
                        cockpit.setSpeedRate(speedRates[i]);
                        CirrusUiSound.playClickSound();
                        ctx.consume();
                        return EventDispatch.consumed;
                    }
                }
            }
            if (saveCpRect != null && saveCpRect.contains(lx, ly)) {
                if (cockpit.canControlExecution()) {
                    cockpit.saveCheckpointNow();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (resumeCpRect != null && resumeCpRect.contains(lx, ly)) {
                if (cockpit.hasResumableCheckpoint()) {
                    cockpit.resumeFromCheckpoint();
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public void syncHeight() {
        int targetH = cockpit.isCompactHeight() ? compactHeight : preferredHeight;
        if (targetH != appliedDeckHeight) {
            appliedDeckHeight = targetH;
            useStyle(heightOf(targetH), flexShrink(0), minWidth(0));
        }
    }

    private void computeLayout(int w) {
        int innerW = Math.max(32, w - pad * 2);
        int activeRowH = cockpit.isCompactHeight() ? 15 : rowH;
        int rowGap = cockpit.isCompactHeight() ? 2 : 3;
        int y1 = 3;
        boolean wide = innerW >= 210;
        int[] row1 = UiScaleMetrics.distributeRow(
            innerW,
            gap,
            new int[]{wide ? 42 : 34, 22, 22, 18},
            new int[]{24, 16, 16, 14},
            new int[]{innerW, wide ? 44 : 30, wide ? 44 : 30, wide ? 38 : 26}
        );
        int x = pad;
        playRect = new Rect(x, y1, row1[0], activeRowH);
        x += row1[0] + gap;
        stepRect = new Rect(x, y1, row1[1], activeRowH);
        x += row1[1] + gap;
        skipRect = new Rect(x, y1, row1[2], activeRowH);
        x += row1[2] + gap;
        resetRect = new Rect(x, y1, Math.max(12, pad + innerW - x), activeRowH);

        int y2 = y1 + activeRowH + rowGap;
        boolean hasCp = cockpit.hasResumableCheckpoint();
        int saveW = wide ? 32 : Math.min(24, Math.max(16, innerW / 5));
        int resumeW = hasCp ? (wide ? 46 : Math.min(26, Math.max(16, innerW / 5))) : 0;
        int rightReserve = hasCp ? saveW + resumeW + gap + 4 : saveW + 4;
        int speedSpan = Math.max(24, innerW - rightReserve);

        compactSpeedCycle = speedSpan < 105;
        for (int i = 0; i < speedRects.length; i++) {
            speedRects[i] = null;
        }
        if (compactSpeedCycle) {
            speedRects[0] = new Rect(pad, y2, speedSpan, activeRowH);
        } else {
            int[] speedWidths = UiScaleMetrics
                    .distributeRow(speedSpan, gap, new int[]{1, 1, 1, 1, 1}, new int[]{14, 14, 14, 14, 16}, null);
            int sx = pad;
            for (int i = 0; i < speedRates.length; i++) {
                speedRects[i] = new Rect(sx, y2, speedWidths[i], activeRowH);
                sx += speedWidths[i] + gap;
            }
        }
        int cx = pad + innerW;
        if (hasCp) {
            resumeCpRect = new Rect(Math.max(pad, cx - resumeW), y2, resumeW, activeRowH);
            cx = resumeCpRect.x() - gap;
        } else {
            resumeCpRect = null;
        }
        saveCpRect = new Rect(Math.max(pad, cx - saveW), y2, saveW, activeRowH);
    }

    public static int activeSpeedIndex(double currentRate) {
        int best = 1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < speedRates.length; i++) {
            double diff = Math.abs(speedRates[i] - currentRate);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        computeLayout(width());
        if (playRect != null && playRect.contains(localX, localY)) {
            boolean running = cockpit.isAutoRunning();
            Component title = running
                    ? CirrusLangs.Execution.Cockpit.tipPauseTitle.get()
                    : (cockpit.stageModel().paused()
                            ? CirrusLangs.Execution.Cockpit.tipResumeTitle.get()
                            : CirrusLangs.Execution.Cockpit.tipRunTitle.get());
            Component desc = running
                    ? CirrusLangs.Execution.Cockpit.tipPauseDesc.get()
                    : CirrusLangs.Execution.Cockpit.tipRunDesc.get();
            return OreUiRichTooltip.builder(title)
                    .badge(
                        running ? "PAUSE" : "RUN",
                        running ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.positive
                    ).text(desc, OreUiRichTooltip.Tone.secondary).build();
        }
        if (stepRect != null && stepRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipStepTitle.get())
                    .badge("STEP", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.tipStepDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (skipRect != null && skipRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipSkipTitle.get())
                    .badge("SKIP", OreUiRichTooltip.Tone.warning)
                    .text(CirrusLangs.Execution.Cockpit.tipSkipDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (resetRect != null && resetRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipResetTitle.get())
                    .badge("RESET", OreUiRichTooltip.Tone.muted)
                    .text(CirrusLangs.Execution.Cockpit.tipResetDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        for (int i = 0; i < speedRects.length; i++) {
            if (speedRects[i] != null && speedRects[i].contains(localX, localY)) {
                return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipSpeedTitle.get(speedLabels[i]))
                        .badge(String.format("%.1f ops/s", speedRates[i]), OreUiRichTooltip.Tone.accent)
                        .text(CirrusLangs.Execution.Cockpit.tipSpeedDesc.get(), OreUiRichTooltip.Tone.secondary)
                        .build();
            }
        }
        if (saveCpRect != null && saveCpRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipSaveCpTitle.get())
                    .badge("CHECKPOINT", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.tipSaveCpDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (resumeCpRect != null && resumeCpRect.contains(localX, localY)) {
            ExecutionCheckpointStore.Checkpoint cp = cockpit.activeCheckpoint();
            Component desc = cp == null
                    ? CirrusLangs.Execution.Cockpit.tipResumeCpDesc.get()
                    : CirrusLangs.Execution.Cockpit.tipResumeCpStage.get(cp.stageIndex() + 1, cp.totalStages());
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.tipResumeCpTitle.get())
                    .badge("RESUME", OreUiRichTooltip.Tone.positive).text(desc, OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        canvas.fill(0, 0, w, h, p.dockSurface());
        canvas.fill(0, h - 1, w, 1, OreUiPaint.cardDivider(p));
        computeLayout(w);

        boolean canRun = cockpit.canControlExecution();
        boolean running = cockpit.isAutoRunning();
        boolean paused = cockpit.stageModel().paused();
        boolean wide = w >= 220;

        // Primary CTA: Auto / Pause / Resume
        if (playRect != null) {
            boolean hov = canRun && playRect.contains(mouseX, mouseY);
            if (canRun && !running) {
                OreUiPaint.raisedButton(
                    canvas,
                    playRect.x(),
                    playRect.y(),
                    playRect.width(),
                    playRect.height(),
                    p,
                    false,
                    hov,
                    true,
                    false
                );
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    playRect.x(),
                    playRect.y(),
                    playRect.width(),
                    playRect.height(),
                    p,
                    running,
                    hov,
                    false,
                    false
                );
            }
            Component txt = running
                    ? (wide ? CirrusLangs.Execution.Cockpit.btnPause.get() : Component.literal("\u23f8"))
                    : (paused
                            ? (wide ? CirrusLangs.Execution.Cockpit.btnResume.get() : Component.literal("\u25b6"))
                            : (wide
                                    ? CirrusLangs.Execution.Cockpit.btnAuto.get()
                                    : CirrusLangs.Execution.Cockpit.btnAutoShort.get()));
            int ink = !canRun ? p.textDisabled() : (!running ? p.textOnAccent() : p.focusRing());
            drawCentered(canvas, playRect, txt, ink);
        }

        if (stepRect != null) {
            drawBtn(
                canvas,
                p,
                stepRect,
                wide ? CirrusLangs.Execution.Cockpit.btnStep.get() : Component.literal("\u23ed"),
                false,
                canRun,
                mouseX,
                mouseY
            );
        }
        if (skipRect != null) {
            drawBtn(
                canvas,
                p,
                skipRect,
                wide ? CirrusLangs.Execution.Cockpit.btnSkip.get() : Component.literal("\u23e9"),
                false,
                cockpit.canSkipStage(),
                mouseX,
                mouseY
            );
        }
        if (resetRect != null) {
            drawBtn(
                canvas,
                p,
                resetRect,
                wide ? CirrusLangs.Execution.Cockpit.btnReset.get() : Component.literal("\u21ba"),
                false,
                canRun,
                mouseX,
                mouseY
            );
        }

        // Row 2: Speed selector
        ExecutionDriverSnapshot snap = cockpit.snapshot();
        double currentRate = snap == null ? cockpit.currentSpeedRate() : snap.ratePerSecond();
        int activeIdx = activeSpeedIndex(currentRate);
        if (compactSpeedCycle) {
            Rect r = speedRects[0];
            if (r != null) {
                boolean hov = r.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, true, hov, false, false);
                drawCentered(
                    canvas,
                    r,
                    Component.literal(speedLabels[activeIdx] + " \u25b8"),
                    hov ? p.textPrimary() : p.focusRing()
                );
            }
        } else {
            for (int i = 0; i < speedRects.length; i++) {
                Rect r = speedRects[i];
                if (r == null) {
                    continue;
                }
                boolean sel = i == activeIdx;
                boolean hov = r.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, sel, hov, false, false);
                drawCentered(
                    canvas,
                    r,
                    Component.literal(speedLabels[i]),
                    sel ? p.focusRing() : (hov ? p.textPrimary() : p.textSecondary())
                );
            }
        }

        // Save Checkpoint & Resume Checkpoint
        if (saveCpRect != null) {
            drawBtn(
                canvas,
                p,
                saveCpRect,
                wide ? CirrusLangs.Execution.Cockpit.btnSaveCp.get() : Component.literal("\u25c9"),
                false,
                canRun,
                mouseX,
                mouseY
            );
        }
        if (resumeCpRect != null) {
            boolean hov = resumeCpRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                resumeCpRect.x(),
                resumeCpRect.y(),
                resumeCpRect.width(),
                resumeCpRect.height(),
                p,
                true,
                hov,
                false,
                false
            );
            drawCentered(
                canvas,
                resumeCpRect,
                wide ? CirrusLangs.Execution.Cockpit.btnResumeCp.get() : Component.literal("\u21a9"),
                OreUiPaint.isDark(p) ? p.costH() : p.costK()
            );
        }
    }

    private static void drawBtn(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        Component label,
        boolean active,
        boolean enabled,
        int mouseX,
        int mouseY
    ) {
        boolean hov = enabled && r.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, active, hov, false, false);
        int ink = !enabled ? p.textDisabled() : (active ? p.focusRing() : (hov ? p.textPrimary() : p.textSecondary()));
        drawCentered(canvas, r, label, ink);
    }

    private static void drawCentered(SceneCanvas canvas, Rect r, Component comp, int ink) {
        int tw = ChromeText.width(canvas, comp);
        ChromeText.drawFitted(
            canvas,
            comp,
            r.x() + Math.max(2, (r.width() - tw) / 2),
            ChromeText.centeredY(r.y(), r.height(), canvas),
            Math.max(8, r.width() - 4),
            ink
        );
    }
}
