package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionHistoryStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
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

/**
 * Top target, queue switcher, batch multiplier, trace/close toggles, and animated progress header for the Execution Cockpit.
 */
public final class ExecutionHeaderWidget extends Widget implements OreUiTooltipProvider {

    public static final int baseHeight = 44;
    public static final int queueRowHeight = 18;
    private static final int pad = 6;
    private static final int slotSize = 18;
    private static final int btnH = 16;

    private final MainPanel cockpit;
    private final OreUiMotion.SmoothValue smoothProgress = new OreUiMotion.SmoothValue();

    private boolean showingQueueRow;
    private @Nullable Rect minusRect;
    private @Nullable Rect countRect;
    private @Nullable Rect plusRect;
    private @Nullable Rect historyRect;
    private @Nullable Rect traceRect;
    private @Nullable Rect closeRect;
    private @Nullable Rect qPrevRect;
    private @Nullable Rect qNextRect;
    private @Nullable Rect qRemoveRect;

    public ExecutionHeaderWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        syncHeight();
        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());
            computeButtons(width());
            int step = input.isShiftDown() ? 10 : 1;
            if (minusRect != null && minusRect.contains(lx, ly)) {
                cockpit.setTargetTimes(Math.max(1, cockpit.targetTimes() - step));
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (plusRect != null && plusRect.contains(lx, ly)) {
                cockpit.setTargetTimes(Math.min(9999, cockpit.targetTimes() + step));
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (historyRect != null && historyRect.contains(lx, ly)) {
                cockpit.toggleHistory();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (traceRect != null && traceRect.contains(lx, ly)) {
                cockpit.setTraceEnabled(!cockpit.traceEnabled());
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (closeRect != null && closeRect.contains(lx, ly)) {
                ExecutionOverlayOpenState.setOpen(false);
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (qPrevRect != null && qPrevRect.contains(lx, ly)) {
                cockpit.selectPreviousQueueEntry();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (qNextRect != null && qNextRect.contains(lx, ly)) {
                cockpit.selectNextQueueEntry();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (qRemoveRect != null && qRemoveRect.contains(lx, ly)) {
                cockpit.removeActiveQueueEntry();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public void syncHeight() {
        boolean wantQueue = ExecutionQueueStore.size() > 1;
        int base = cockpit.isCompactHeight() ? 38 : baseHeight;
        int targetH = wantQueue ? base + queueRowHeight : base;
        if (wantQueue != showingQueueRow || height() == 0 || height() != targetH) {
            showingQueueRow = wantQueue;
            useStyle(heightOf(targetH), flexShrink(0), minWidth(0));
        }
    }

    private void computeButtons(int w) {
        int y = 4;
        int rx = w - pad;
        boolean tight = w < 165;
        int closeW = tight ? 14 : 16;
        int iconBtnW = tight ? 14 : 18;
        int btnGap = tight ? 2 : 3;
        int minLeft = pad + slotSize + 20;

        closeRect = new Rect(Math.max(pad, rx - closeW), y, closeW, btnH);
        rx = closeRect.x() - btnGap;

        if (rx - iconBtnW >= minLeft) {
            traceRect = new Rect(rx - iconBtnW, y, iconBtnW, btnH);
            rx = traceRect.x() - btnGap;
        } else {
            traceRect = null;
        }
        if (rx - iconBtnW >= minLeft) {
            historyRect = new Rect(rx - iconBtnW, y, iconBtnW, btnH);
            rx = historyRect.x() - btnGap;
        } else {
            historyRect = null;
        }
        if (cockpit.hasTarget()) {
            String countStr = "\u00d7" + cockpit.targetTimes();
            int cw = Math.max(20, Math.min(44, countStr.length() * 6 + 6));
            int stepW = tight ? 12 : 14;
            int stepperTotal = stepW * 2 + cw + 2;
            if (rx - stepperTotal >= minLeft) {
                plusRect = new Rect(rx - stepW, y, stepW, btnH);
                rx -= stepW + 1;
                countRect = new Rect(rx - cw, y, cw, btnH);
                rx -= cw + 1;
                minusRect = new Rect(rx - stepW, y, stepW, btnH);
            } else {
                plusRect = null;
                countRect = null;
                minusRect = null;
            }
        } else {
            plusRect = null;
            countRect = null;
            minusRect = null;
        }
        if (showingQueueRow) {
            int qy = (cockpit.isCompactHeight() ? 38 : baseHeight) + 1;
            qRemoveRect = new Rect(Math.max(pad, w - pad - 14), qy, 14, 14);
            qNextRect = new Rect(Math.max(pad, qRemoveRect.x() - 17), qy, 14, 14);
            qPrevRect = new Rect(Math.max(pad, qNextRect.x() - 16), qy, 14, 14);
        } else {
            qPrevRect = null;
            qNextRect = null;
            qRemoveRect = null;
        }
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        computeButtons(width());
        if (minusRect != null && minusRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.minusTitle.get())
                    .badge("-1 / SHIFT -10", OreUiRichTooltip.Tone.secondary)
                    .text(CirrusLangs.Execution.Cockpit.minusDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (plusRect != null && plusRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.plusTitle.get())
                    .badge("+1 / SHIFT +10", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.plusDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (historyRect != null && historyRect.contains(localX, localY)) {
            int count = ExecutionHistoryStore.size();
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.historyTitle.get())
                    .badge(count + " SAVED", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.historyDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (traceRect != null && traceRect.contains(localX, localY)) {
            return OreUiRichTooltip
                    .builder(
                        cockpit.traceEnabled()
                                ? CirrusLangs.Execution.Cockpit.traceOn.get()
                                : CirrusLangs.Execution.Cockpit.traceOff.get()
                    ).badge(cockpit.traceEnabled() ? "ON" : "OFF", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.traceDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (closeRect != null && closeRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.hideTitle.get())
                    .badge("HIDE", OreUiRichTooltip.Tone.muted)
                    .text(CirrusLangs.Execution.Cockpit.hideDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (qRemoveRect != null && qRemoveRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.queueRemove.get())
                    .badge("REMOVE", OreUiRichTooltip.Tone.warning).build();
        }
        if (showingQueueRow && localY >= baseHeight) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.queueTip.get(ExecutionQueueStore.size()))
                    .badge("QUEUE", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.queueTipDesc.get(), OreUiRichTooltip.Tone.secondary).build();
        }
        if (localX >= pad && localX <= pad + slotSize && localY >= 4 && localY <= 4 + slotSize) {
            RecipeContent target = cockpit.targetContent();
            if (target.notEmpty()) {
                return RecipeContentSlotWidget.richTooltipFor(target, "TARGET");
            }
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        syncHeight();
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        OreUiPaint.headerBand(canvas, 0, 0, w, h, p, false);
        computeButtons(w);

        // Target slot + title
        int slotY = 3;
        OreUiPaint.slot(canvas, pad, slotY, slotSize, p);
        RecipeContent target = cockpit.targetContent();
        if (target.notEmpty()) {
            ExecutionOverlayContentHelper.renderRecipeContent(canvas, target, pad + 1, slotY + 1, 16, 16, partialTicks);
            if (cockpit.targetTimes() > 1) {
                OreUiSlotAmount.drawInSlot(canvas, pad, slotY, slotSize, cockpit.targetTimes(), p);
            }
        }
        int titleX = pad + slotSize + 5;
        int titleRight = (minusRect != null
                ? minusRect.x()
                : (historyRect != null ? historyRect.x() : (traceRect != null ? traceRect.x() : w - pad))) - 4;
        String label = cockpit.targetLabel();
        Component titleComp = label == null || label.isBlank()
                ? CirrusLangs.Execution.Cockpit.title.get()
                : Component.literal(label);
        ChromeText.drawFitted(
            canvas,
            titleComp,
            titleX,
            ChromeText.centeredY(slotY, slotSize, canvas),
            Math.max(16, titleRight - titleX),
            p.textPrimary()
        );

        // Multiplier [-][xN][+]
        if (minusRect != null && countRect != null && plusRect != null) {
            drawSmallButton(canvas, p, minusRect, "-", false, mouseX, mouseY, cockpit.targetTimes() > 1);
            OreUiPaint
                    .searchField(canvas, countRect.x(), countRect.y(), countRect.width(), countRect.height(), p, false);
            Component cnt = Component.literal("\u00d7" + cockpit.targetTimes());
            int cw = ChromeText.width(canvas, cnt);
            ChromeText.drawFitted(
                canvas,
                cnt,
                countRect.x() + Math.max(2, (countRect.width() - cw) / 2),
                ChromeText.centeredY(countRect.y(), countRect.height(), canvas),
                Math.max(8, countRect.width() - 4),
                p.textPrimary()
            );
            drawSmallButton(canvas, p, plusRect, "+", false, mouseX, mouseY, true);
        }

        // History, trace & close buttons
        if (historyRect != null) {
            boolean histHov = historyRect.contains(mouseX, mouseY);
            boolean histActive = cockpit.showingHistory();
            OreUiPaint.raisedButton(
                canvas,
                historyRect.x(),
                historyRect.y(),
                historyRect.width(),
                historyRect.height(),
                p,
                histActive,
                histHov,
                false,
                false
            );
            int cx = historyRect.x() + historyRect.width() / 2;
            int cy = historyRect.y() + historyRect.height() / 2;
            int ink = histActive
                    ? p.focusRing()
                    : (ExecutionHistoryStore.size() > 0 ? (histHov ? p.textPrimary() : p.costP()) : p.textMuted());
            canvas.strokeRect(cx - 4, cy - 4, 8, 8, ink, 1);
            canvas.fill(cx, cy - 2, 1, 3, ink);
            canvas.fill(cx, cy, 3, 1, ink);
        }
        if (traceRect != null) {
            boolean traceHov = traceRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                traceRect.x(),
                traceRect.y(),
                traceRect.width(),
                traceRect.height(),
                p,
                cockpit.traceEnabled(),
                traceHov,
                false,
                false
            );
            int cx = traceRect.x() + traceRect.width() / 2;
            int cy = traceRect.y() + traceRect.height() / 2;
            int ink = cockpit.traceEnabled() ? p.focusRing() : (traceHov ? p.textPrimary() : p.textSecondary());
            canvas.strokeRect(cx - 4, cy - 3, 8, 6, ink, 1);
            canvas.fill(cx - 1, cy - 1, 2, 2, ink);
        }
        if (closeRect != null) {
            drawSmallButton(canvas, p, closeRect, "\u00d7", false, mouseX, mouseY, true);
        }

        // Row 2: Live Stage Progress Bar + Status Badge
        ExecutionStageModel model = cockpit.stageModel();
        int row2Y = 25;
        StatusInfo status = resolveStatus(p, model);
        int badgeW = ChromeText.width(canvas, status.label()) + 10;
        int badgeX = w - pad - badgeW;
        OreUiPaint.tagPill(canvas, badgeX, row2Y + 1, 13, status.label(), status.color(), p);

        int barX = pad;
        int barW = Math.max(24, badgeX - pad - 6);
        int barH = 14;
        OreUiPaint.searchField(canvas, barX, row2Y, barW, barH, p, false);
        float targetRatio = (float) model.progressRatio();
        float shownRatio = smoothProgress.update(targetRatio, 16f);
        int fillW = Math.round((barW - 2) * Math.max(0f, Math.min(1f, shownRatio)));
        if (fillW > 0) {
            int barColor = model.completed() ? p.costS() : (model.paused() ? p.costH() : p.focusRing());
            float fillAlpha = OreUiPaint.isDark(p) ? 0.24f : 0.30f;
            canvas.fill(barX + 1, row2Y + 1, fillW, barH - 3, OreUiPaint.withAlpha(barColor, fillAlpha));
            canvas.fill(barX + 1, row2Y + barH - 3, fillW, 2, barColor);
        }
        Component progressComp = model.isEmpty()
                ? (cockpit.calculating()
                        ? CirrusLangs.Execution.Cockpit.progressPlanning.get()
                        : CirrusLangs.Execution.Cockpit.progressWaiting.get())
                : CirrusLangs.Execution.Cockpit.progressStages.get(
                    model.completedStageCount(),
                    model.totalStageCount(),
                    Math.round(model.progressRatio() * 100.0)
                );
        ChromeText.drawFitted(
            canvas,
            progressComp,
            barX + 5,
            ChromeText.centeredY(row2Y, barH, canvas),
            Math.max(12, barW - 10),
            p.textPrimary()
        );

        // Row 3 (auto-shown when multiple plans are queued): Queue Switcher Strip
        if (showingQueueRow && qPrevRect != null && qNextRect != null && qRemoveRect != null) {
            canvas.fill(0, baseHeight - 1, w, 1, OreUiPaint.cardDivider(p));
            int qSize = ExecutionQueueStore.size();
            int qIdx = Math.max(0, ExecutionQueueStore.activeIndex());
            Component qPill = CirrusLangs.Execution.Cockpit.queueLabel.get(qIdx + 1, qSize);
            int pw = OreUiPaint.tagPill(canvas, pad, baseHeight + 2, 13, qPill, p.focusRing(), p);

            ExecutionQueueStore.Entry active = ExecutionQueueStore.activeEntry();
            int qTextX = pad + pw + 5;
            int qTextMax = Math.max(16, qPrevRect.x() - qTextX - 4);
            if (active != null) {
                String rawName = active.displayName().getString();
                String friendly = cockpit.targetLabel();
                String display = (friendly != null && !friendly.isBlank() && rawName.contains(":"))
                        ? friendly
                        : rawName;
                Component qName = Component.literal(display + " \u00d7" + active.times());
                ChromeText.drawFitted(
                    canvas,
                    qName,
                    qTextX,
                    ChromeText.centeredY(baseHeight + 1, 14, canvas),
                    qTextMax,
                    p.textSecondary()
                );
            }
            drawSmallButton(canvas, p, qPrevRect, "\u2039", false, mouseX, mouseY, qSize > 1);
            drawSmallButton(canvas, p, qNextRect, "\u203a", false, mouseX, mouseY, qSize > 1);
            drawSmallButton(canvas, p, qRemoveRect, "\u00d7", false, mouseX, mouseY, qSize > 0);
        }
    }

    private record StatusInfo(Component label, int color) {}

    private StatusInfo resolveStatus(CirrusPalette p, ExecutionStageModel model) {
        if (cockpit.calculating()) {
            return new StatusInfo(CirrusLangs.Execution.Cockpit.statusPlanning.get(), p.focusRing());
        }
        if (!cockpit.hasTarget() || model.isEmpty()) {
            return new StatusInfo(CirrusLangs.Execution.Cockpit.statusIdle.get(), p.textMuted());
        }
        if (model.completed()) {
            return new StatusInfo(CirrusLangs.Execution.Cockpit.statusDone.get(), p.costS());
        }
        if (cockpit.hitBreakpointStageIndex() != null) {
            return new StatusInfo(CirrusLangs.Execution.Cockpit.statusBreakpoint.get(), p.ruleHardBan());
        }
        ExecutionDriverSnapshot snap = cockpit.snapshot();
        if (model.paused()) {
            boolean hasMissing = cockpit.missingMaterialCount() > 0;
            return new StatusInfo(
                hasMissing
                        ? CirrusLangs.Execution.Cockpit.statusMissing.get()
                        : CirrusLangs.Execution.Cockpit.statusPaused.get(),
                hasMissing ? p.ruleHardBan() : p.costH()
            );
        }
        if (snap != null && snap.autoAdvanceEnabled()) {
            return new StatusInfo(CirrusLangs.Execution.Cockpit.statusRunning.get(), p.focusRing());
        }
        return new StatusInfo(CirrusLangs.Execution.Cockpit.statusReady.get(), p.costS());
    }

    private static void drawSmallButton(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        String text,
        boolean active,
        int mouseX,
        int mouseY,
        boolean enabled
    ) {
        boolean hov = enabled && r.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, active, hov, false, false);
        Component comp = Component.literal(text);
        int tw = ChromeText.width(canvas, comp);
        int ink = !enabled ? p.textDisabled() : (active ? p.focusRing() : (hov ? p.textPrimary() : p.textSecondary()));
        ChromeText.draw(
            canvas,
            comp,
            r.x() + Math.max(1, (r.width() - tw) / 2),
            ChromeText.centeredY(r.y(), r.height(), canvas),
            ink
        );
    }
}
