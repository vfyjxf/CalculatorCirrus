package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.recipe.execution.ExecutionAuditLog;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;

/**
 * Collapsible bottom drawer presenting structured error/safety-stop diagnostics with one-click recovery alongside live execution telemetry and an audit event log.
 */
public final class ExecutionDiagnosticsDrawerWidget extends Widget implements OreUiTooltipProvider {

    public static final int collapsedHeight = 26;
    public static final int expandedHeight = 204;
    private static final int tabRowH = 20;
    private static final int pad = 6;
    private static final int logRowH = 14;

    public enum Tab {
        diagnostics, telemetry
    }

    private record LogHit(Rect rect, ExecutionAuditLog.Event event) {}

    private final MainPanel cockpit;
    private final List<LogHit> logHits = new ArrayList<>();
    private final OreUiMotion.SmoothValue smoothHeight = new OreUiMotion.SmoothValue();

    private boolean expanded;
    private int lastAppliedHeight = -1;
    private Tab activeTab = Tab.diagnostics;
    private ExecutionAuditLog.@Nullable Severity logFilter;
    private int logScrollY;

    private @Nullable Rect headerRect;
    private @Nullable Rect diagTabRect;
    private @Nullable Rect teleTabRect;
    private @Nullable Rect clearRect;
    private @Nullable Rect retryRect;
    private @Nullable Rect skipRect;
    private @Nullable Rect replanRect;
    private @Nullable Rect filterAllRect;
    private @Nullable Rect filterErrRect;
    private @Nullable Rect filterWarnRect;
    private @Nullable Rect filterInfoRect;

    public ExecutionDiagnosticsDrawerWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        applyHeightStyle();

        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());

            if (headerRect != null && headerRect.contains(lx, ly)) {
                setExpanded(!expanded);
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (!expanded) {
                return EventDispatch.pass;
            }
            if (diagTabRect != null && diagTabRect.contains(lx, ly)) {
                activeTab = Tab.diagnostics;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (teleTabRect != null && teleTabRect.contains(lx, ly)) {
                activeTab = Tab.telemetry;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (clearRect != null && clearRect.contains(lx, ly)) {
                cockpit.auditLog().clearEvents();
                logScrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (retryRect != null && retryRect.contains(lx, ly)) {
                cockpit.retryCurrentStage();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (skipRect != null && skipRect.contains(lx, ly)) {
                cockpit.skipCurrentStage();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (replanRect != null && replanRect.contains(lx, ly)) {
                cockpit.replanActiveTarget();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (filterAllRect != null && filterAllRect.contains(lx, ly)) {
                logFilter = null;
                logScrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (filterErrRect != null && filterErrRect.contains(lx, ly)) {
                logFilter = ExecutionAuditLog.Severity.error;
                logScrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (filterWarnRect != null && filterWarnRect.contains(lx, ly)) {
                logFilter = ExecutionAuditLog.Severity.warn;
                logScrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (filterInfoRect != null && filterInfoRect.contains(lx, ly)) {
                logFilter = ExecutionAuditLog.Severity.info;
                logScrollY = 0;
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            // Intercept stray clicks inside the expanded upward sheet so they never click through to underlying panels.
            ctx.consume();
            return EventDispatch.consumed;
        });

        onMouseScrolled((mouseX, mouseY, scrollX, scrollYDelta, ctx) -> {
            if (!expanded || activeTab != Tab.telemetry) {
                return EventDispatch.pass;
            }
            List<ExecutionAuditLog.Event> events = cockpit.auditLog().events(logFilter);
            int maxS = Math.max(0, events.size() * logRowH - 46);
            if (maxS <= 0) {
                return EventDispatch.pass;
            }
            logScrollY = Math.max(0, Math.min(maxS, logScrollY - (int) Math.round(scrollYDelta * 18.0)));
            ctx.consume();
            return EventDispatch.consumed;
        });
    }

    public boolean expanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            applyHeightStyle();
        }
    }

    /** Immediately snaps the drawer height to its current collapsed/expanded target within panel bounds. */
    public void snapHeightToTarget() {
        int target = expanded ? Math.min(expandedHeight, maxExpandedBudget()) : collapsedHeight;
        smoothHeight.snap(target, System.nanoTime());
        applyHeightPx(target);
    }

    public Tab activeTab() {
        return activeTab;
    }

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
    }

    private int maxExpandedBudget() {
        int panelH = cockpit.panelHeightBudget();
        int topClearance = cockpit.topFixedHeight() + 6;
        int available = Math.max(collapsedHeight, panelH - topClearance - 2);
        int proportional = Math.max(collapsedHeight + 48, Math.round(panelH * 0.56f));
        return Math.max(collapsedHeight, Math.min(280, Math.min(available, proportional)));
    }

    private void applyHeightStyle() {
        int target = expanded ? Math.min(expandedHeight, maxExpandedBudget()) : collapsedHeight;
        if (!smoothHeight.initialized()) {
            smoothHeight.snap(target, System.nanoTime());
        }
        applyHeightPx(Math.round(smoothHeight.value()));
    }

    private void applyHeightPx(int heightPx) {
        int clamped = Math.max(collapsedHeight, Math.min(maxExpandedBudget(), heightPx));
        if (lastAppliedHeight != clamped) {
            lastAppliedHeight = clamped;
            useStyle(
                positionAbsolute(),
                insetLeft(1f),
                insetRight(1f),
                insetBottom(1f),
                heightOf(clamped),
                minWidth(0)
            );
        }
    }

    private int desiredExpandedHeight(SceneCanvas canvas, int width, ExecutionAuditLog log) {
        int maxH = maxExpandedBudget();
        if (activeTab != Tab.diagnostics) {
            return Math.min(expandedHeight, maxH);
        }
        ExecutionAuditLog.Event diag = log.activeDiagnostic();
        if (diag == null || diag.detail() == null) {
            return Math.min(expandedHeight, maxH);
        }
        int cardW = Math.max(40, width - pad * 2);
        List<String> lines = ExecutionOverlayUi
                .wrapPlainLines(canvas.font(), diag.detail().getString(), Math.max(40, cardW - 12));
        int needed = collapsedHeight + tabRowH + 8 + 18 + lines.size() * 10 + 22;
        return Math.min(maxH, Math.max(Math.min(expandedHeight, maxH), needed));
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (LogHit hit : logHits) {
            if (hit.rect().contains(localX, localY)) {
                ExecutionAuditLog.Event e = hit.event();
                OreUiRichTooltip.Tone tone = switch (e.severity()) {
                    case error -> OreUiRichTooltip.Tone.danger;
                    case warn -> OreUiRichTooltip.Tone.warning;
                    case info -> OreUiRichTooltip.Tone.accent;
                };
                OreUiRichTooltip.Builder b = OreUiRichTooltip.builder(e.title())
                        .badge(e.severity().name().toUpperCase(Locale.ROOT), tone).subtitle(e.formattedTime());
                if (e.detail() != null) {
                    b.text(e.detail(), OreUiRichTooltip.Tone.secondary);
                }
                if (e.stageIndex() >= 0) {
                    b.keyValue(
                        CirrusLangs.Execution.Cockpit.matSortStage.get(),
                        Component.literal("#" + (e.stageIndex() + 1)),
                        OreUiRichTooltip.Tone.primary
                    );
                }
                return b.build();
            }
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        ExecutionAuditLog log = cockpit.auditLog();
        int targetH = expanded ? desiredExpandedHeight(canvas, Math.max(120, w), log) : collapsedHeight;
        float animatedH = smoothHeight.update(targetH, 18.0f);
        applyHeightPx(Math.round(animatedH));
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        logHits.clear();
        diagTabRect = null;
        teleTabRect = null;
        clearRect = null;
        retryRect = null;
        skipRect = null;
        replanRect = null;
        filterAllRect = null;
        filterErrRect = null;
        filterWarnRect = null;
        filterInfoRect = null;

        canvas.fill(0, 0, w, h, p.dockSurface());
        if (h > collapsedHeight + 2) {
            canvas.strokeRect(0, 0, w, h, OreUiPaint.withAlpha(p.focusRing(), 0.78f), 1);
            canvas.fill(1, 1, Math.max(0, w - 2), 1, p.hairlineHighlight());
        } else {
            canvas.fill(0, 0, w, 1, OreUiPaint.cardDivider(p));
        }

        ExecutionAuditLog.Event activeDiag = log.activeDiagnostic();
        int issueCount = log.errorAndWarnCount();

        // Standalone Health Status Button (not a disguised tab bar)
        int btnX = pad;
        int btnY = 3;
        int btnW = Math.max(40, w - pad * 2);
        int btnH = collapsedHeight - 6;
        headerRect = new Rect(btnX, btnY, btnW, btnH);
        boolean hHov = headerRect.contains(mouseX, mouseY);
        if (expanded) {
            OreUiPaint.selectedToggle(canvas, btnX, btnY, btnW, btnH, p, hHov);
        } else {
            OreUiPaint.raisedButton(canvas, btnX, btnY, btnW, btnH, p, false, hHov, false, false);
        }

        int ledColor;
        if (activeDiag != null) {
            ledColor = activeDiag.severity() == ExecutionAuditLog.Severity.error ? p.ruleHardBan() : p.costH();
        } else if (issueCount > 0) {
            ledColor = p.costH();
        } else {
            ledColor = p.costS();
        }
        canvas.fill(btnX + 6, btnY + btnH / 2 - 3, 5, 5, ledColor);

        Component chev = Component.literal(expanded ? "\u25be" : "\u25b4");
        int chevW = ChromeText.width(canvas, chev);
        int chevX = btnX + btnW - 6 - chevW;
        ChromeText.draw(
            canvas,
            chev,
            chevX,
            ChromeText.centeredY(btnY, btnH, canvas),
            expanded ? p.focusRing() : (hHov ? p.textPrimary() : p.textSecondary())
        );

        int rightLimit = chevX - 5;
        String teleSummary = buildCompactTelemetry(log);
        if (!teleSummary.isEmpty() && btnW >= 165) {
            Component tComp = Component.literal(teleSummary);
            int tw = ChromeText.width(canvas, tComp) + 8;
            if (rightLimit - tw > btnX + 82) {
                OreUiPaint.tagPill(canvas, rightLimit - tw, btnY + 3, 13, tComp, p.focusRing(), p);
                rightLimit -= tw + 5;
            }
        }

        Component statusText;
        int statusColor;
        if (activeDiag != null) {
            statusText = activeDiag.title();
            statusColor = activeDiag.severity() == ExecutionAuditLog.Severity.error ? p.ruleHardBan() : p.costH();
        } else if (issueCount > 0) {
            statusText = CirrusLangs.Execution.Cockpit.drawerErrors.get(issueCount);
            statusColor = p.costH();
        } else {
            statusText = CirrusLangs.Execution.Cockpit.drawerHealthy.get();
            statusColor = p.costS();
        }
        ChromeText.drawFitted(
            canvas,
            statusText,
            btnX + 15,
            ChromeText.centeredY(btnY, btnH, canvas),
            Math.max(24, rightLimit - btnX - 16),
            statusColor
        );

        if (h <= collapsedHeight + 4) {
            return;
        }

        canvas.withClip(0, collapsedHeight, w, Math.max(0, h - collapsedHeight), () -> {
            // Symmetric 50/50 segmented tab bar
            int tabY = collapsedHeight + 2;
            int innerW = Math.max(40, w - pad * 2);
            int halfW = Math.max(24, (innerW - 2) / 2);
            int rightTabW = Math.max(24, innerW - halfW - 2);
            Component diagComp = activeDiag != null
                    ? CirrusLangs.Execution.Cockpit.drawerDiagnostics.get().copy().append(Component.literal(" !"))
                    : CirrusLangs.Execution.Cockpit.drawerDiagnostics.get();
            diagTabRect = new Rect(pad, tabY, halfW, tabRowH - 2);
            drawTabButton(
                canvas,
                p,
                diagTabRect,
                diagComp,
                activeTab == Tab.diagnostics,
                activeDiag != null,
                mouseX,
                mouseY
            );

            Component teleComp = CirrusLangs.Execution.Cockpit.drawerTelemetry.get();
            teleTabRect = new Rect(diagTabRect.right() + 2, tabY, rightTabW, tabRowH - 2);
            drawTabButton(canvas, p, teleTabRect, teleComp, activeTab == Tab.telemetry, false, mouseX, mouseY);

            int bodyY = tabY + tabRowH + 2;
            int bodyH = Math.max(0, h - bodyY - 4);
            if (bodyH < 20) {
                return;
            }
            if (activeTab == Tab.diagnostics) {
                renderDiagnosticsBody(canvas, p, log, w, bodyY, bodyH, mouseX, mouseY);
            } else {
                renderTelemetryBody(canvas, p, log, w, bodyY, bodyH, mouseX, mouseY);
            }
        });
    }

    private void renderDiagnosticsBody(
        SceneCanvas canvas,
        CirrusPalette p,
        ExecutionAuditLog log,
        int w,
        int y,
        int h,
        int mouseX,
        int mouseY
    ) {
        ExecutionAuditLog.Event diag = log.activeDiagnostic();
        if (diag == null) {
            for (ExecutionAuditLog.Event candidate : log.events(null)) {
                if (candidate.severity() == ExecutionAuditLog.Severity.error
                        || candidate.severity() == ExecutionAuditLog.Severity.warn) {
                    diag = candidate;
                    break;
                }
            }
        }
        int cardW = Math.max(40, w - pad * 2);
        if (diag == null) {
            canvas.fill(pad, y, cardW, h, OreUiPaint.withAlpha(p.costS(), OreUiPaint.isDark(p) ? 0.12f : 0.08f));
            canvas.strokeRect(pad, y, cardW, h, OreUiPaint.withAlpha(p.costS(), 0.45f), 1);
            ChromeText.drawFitted(
                canvas,
                CirrusLangs.Execution.Cockpit.drawerNoIssues.get(),
                pad + 8,
                y + 10,
                cardW - 16,
                p.costS()
            );
            String tele = buildCompactTelemetry(log);
            if (!tele.isEmpty()) {
                ChromeText.drawFitted(canvas, Component.literal(tele), pad + 8, y + 26, cardW - 16, p.textMuted());
            }
            return;
        }

        int accent = diag.severity() == ExecutionAuditLog.Severity.error ? p.ruleHardBan() : p.costH();
        canvas.fill(pad, y, cardW, h, OreUiPaint.withAlpha(accent, OreUiPaint.isDark(p) ? 0.16f : 0.10f));
        canvas.strokeRect(pad, y, cardW, h, accent, 1);

        // Title + timestamp
        Component ts = Component.literal(diag.formattedTime());
        int tsW = ChromeText.width(canvas, ts);
        ChromeText.draw(canvas, ts, pad + cardW - 6 - tsW, y + 4, p.textMuted());
        ChromeText.drawFitted(
            canvas,
            Component.literal("! ").append(diag.title()),
            pad + 6,
            y + 4,
            Math.max(20, cardW - 18 - tsW),
            accent
        );

        // CJK-aware wrapped detail body (zero truncation!)
        int detailY = y + 16;
        int btnRowH = 17;
        int maxDetailH = Math.max(10, h - 18 - btnRowH);
        Component detail = diag.detail();
        if (detail != null) {
            List<String> lines = ExecutionOverlayUi
                    .wrapPlainLines(canvas.font(), detail.getString(), Math.max(40, cardW - 12));
            int lineY = detailY;
            for (String line : lines) {
                if (lineY + 9 > detailY + maxDetailH) {
                    break;
                }
                canvas.drawString(line, pad + 6, lineY, p.textPrimary(), false);
                lineY += 10;
            }
        }

        // One-click recovery actions row
        int btnY = y + h - btnRowH - 2;
        int bx = pad + 6;
        int bh = 15;

        Component retryComp = CirrusLangs.Execution.Cockpit.stageActionRetry.get();
        int rw = Math.max(36, ChromeText.width(canvas, retryComp) + 10);
        retryRect = new Rect(bx, btnY, rw, bh);
        drawActionBtn(canvas, p, retryRect, retryComp, true, mouseX, mouseY);
        bx += rw + 4;

        if (cockpit.canSkipStage()) {
            Component skipComp = CirrusLangs.Execution.Cockpit.stageActionSkip.get();
            int sw = Math.max(32, ChromeText.width(canvas, skipComp) + 10);
            if (bx + sw < pad + cardW - 4) {
                skipRect = new Rect(bx, btnY, sw, bh);
                drawActionBtn(canvas, p, skipRect, skipComp, false, mouseX, mouseY);
                bx += sw + 4;
            }
        }

        Component replanComp = CirrusLangs.Execution.Cockpit.drawerReplan.get();
        int pw = Math.max(40, ChromeText.width(canvas, replanComp) + 10);
        if (bx + pw <= pad + cardW - 4) {
            replanRect = new Rect(bx, btnY, pw, bh);
            drawActionBtn(canvas, p, replanRect, replanComp, false, mouseX, mouseY);
        }
    }

    private void renderTelemetryBody(
        SceneCanvas canvas,
        CirrusPalette p,
        ExecutionAuditLog log,
        int w,
        int y,
        int h,
        int mouseX,
        int mouseY
    ) {
        int innerW = Math.max(40, w - pad * 2);
        ExecutionStageModel model = cockpit.stageModel();
        int doneStages = model.completedStageCount();
        int totalStages = model.totalStageCount();
        float stageFrac = totalStages > 0 ? Math.min(1f, doneStages / (float) totalStages) : 0f;
        int remainingSteps = Math.max(0, model.totalRawSteps() - model.currentRawStepIndex());
        double obsRate = log.observedStepsPerSecond();
        double shownRate = obsRate > 0.1d ? obsRate : cockpit.currentSpeedRate();
        String rateStr = String.format(Locale.ROOT, "%.1f/s", shownRate);
        String etaStr = remainingSteps <= 0 || model.completed()
                ? "--"
                : ExecutionAuditLog
                        .formatDuration(log.estimateRemainingSeconds(remainingSteps, cockpit.currentSpeedRate()));
        String elapsedStr = ExecutionAuditLog.formatDuration(log.elapsedSeconds());

        // Graphical Stage Progress Bar
        int progH = 22;
        OreUiPaint.searchField(canvas, pad, y, innerW, progH, p, false);
        Component progLabel = CirrusLangs.Execution.Cockpit.drawerProgress.get();
        String progVal = doneStages + " / " + totalStages + " (" + Math.round(stageFrac * 100f) + "%)";
        Component progValComp = Component.literal(progVal);
        int pvW = ChromeText.width(canvas, progValComp);
        ChromeText.draw(canvas, progValComp, pad + innerW - 5 - pvW, y + 3, p.costS());
        ChromeText.drawFitted(canvas, progLabel, pad + 5, y + 3, Math.max(24, innerW - 14 - pvW), p.textSecondary());
        int trackX = pad + 5;
        int trackY = y + 14;
        int trackW = Math.max(16, innerW - 10);
        canvas.fill(trackX, trackY, trackW, 4, OreUiPaint.cardDivider(p));
        int fillW = Math.round(trackW * stageFrac);
        if (fillW > 0) {
            canvas.fill(trackX, trackY, fillW, 4, p.costS());
        }

        // 2x2 Graphical Metric Cards (full non-abbreviated labels)
        int gridY = y + progH + 4;
        int tileH = 25;
        int tileGap = 3;
        int tileW = Math.max(40, (innerW - tileGap) / 2);
        float speedFrac = (float) Math.max(0.05d, Math.min(1.0d, shownRate / 60.0d));
        drawGaugeMetricCard(
            canvas,
            p,
            pad,
            gridY,
            tileW,
            tileH,
            CirrusLangs.Execution.Cockpit.drawerRate.get(),
            rateStr,
            speedFrac,
            p.focusRing()
        );
        drawKpiTile(
            canvas,
            p,
            pad + tileW + tileGap,
            gridY,
            innerW - tileW - tileGap,
            tileH,
            CirrusLangs.Execution.Cockpit.drawerEta.get(),
            etaStr,
            p.costH()
        );

        int row2Y = gridY + tileH + tileGap;
        drawKpiTile(
            canvas,
            p,
            pad,
            row2Y,
            tileW,
            tileH,
            CirrusLangs.Execution.Cockpit.drawerElapsed.get(),
            elapsedStr,
            p.textPrimary()
        );
        int errCount = log.events(ExecutionAuditLog.Severity.error).size();
        int warnCount = log.events(ExecutionAuditLog.Severity.warn).size();
        int infoCount = log.events(ExecutionAuditLog.Severity.info).size();
        drawSeverityBreakdownCard(
            canvas,
            p,
            pad + tileW + tileGap,
            row2Y,
            innerW - tileW - tileGap,
            tileH,
            errCount,
            warnCount,
            infoCount
        );

        // Non-abbreviated Filter row + Clear button
        int fY = row2Y + tileH + 4;
        int fH = 15;
        int fx = pad;
        filterAllRect = drawFilterChip(
            canvas,
            p,
            fx,
            fY,
            fH,
            CirrusLangs.Execution.Cockpit.drawerFilterAll.get(),
            logFilter == null,
            p.focusRing(),
            mouseX,
            mouseY
        );
        fx = filterAllRect.right() + 3;
        filterErrRect = drawFilterChip(
            canvas,
            p,
            fx,
            fY,
            fH,
            CirrusLangs.Execution.Cockpit.drawerFilterErr.get(),
            logFilter == ExecutionAuditLog.Severity.error,
            p.ruleHardBan(),
            mouseX,
            mouseY
        );
        fx = filterErrRect.right() + 3;
        filterWarnRect = drawFilterChip(
            canvas,
            p,
            fx,
            fY,
            fH,
            CirrusLangs.Execution.Cockpit.drawerFilterWarn.get(),
            logFilter == ExecutionAuditLog.Severity.warn,
            p.costH(),
            mouseX,
            mouseY
        );
        fx = filterWarnRect.right() + 3;
        filterInfoRect = drawFilterChip(
            canvas,
            p,
            fx,
            fY,
            fH,
            CirrusLangs.Execution.Cockpit.drawerFilterInfo.get(),
            logFilter == ExecutionAuditLog.Severity.info,
            p.textSecondary(),
            mouseX,
            mouseY
        );
        if (!log.events(null).isEmpty()) {
            Component clearComp = CirrusLangs.Execution.Cockpit.drawerClear.get();
            int cw = ChromeText.width(canvas, clearComp) + 8;
            if (pad + innerW - cw > filterInfoRect.right() + 3) {
                clearRect = new Rect(pad + innerW - cw, fY, cw, fH);
                boolean cHov = clearRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    clearRect.x(),
                    clearRect.y(),
                    clearRect.width(),
                    clearRect.height(),
                    p,
                    false,
                    cHov,
                    false,
                    false
                );
                ChromeText.draw(
                    canvas,
                    clearComp,
                    clearRect.x() + 4,
                    ChromeText.centeredY(clearRect.y(), clearRect.height(), canvas),
                    cHov ? p.textPrimary() : p.textMuted()
                );
            }
        }

        // Scrollable event list
        int listY = fY + fH + 3;
        int listH = Math.max(0, y + h - listY);
        if (listH < 10) {
            return;
        }
        OreUiPaint.searchField(canvas, pad, listY, innerW, listH, p, false);
        List<ExecutionAuditLog.Event> events = log.events(logFilter);
        int maxS = Math.max(0, events.size() * logRowH - (listH - 4));
        if (logScrollY > maxS) {
            logScrollY = maxS;
        }
        int barReserve = maxS > 0 ? 6 : 0;

        canvas.withClip(pad + 1, listY + 1, innerW - 2 - barReserve, listH - 2, () -> {
            if (events.isEmpty()) {
                ChromeText.drawFitted(
                    canvas,
                    CirrusLangs.Execution.Cockpit.drawerNoEvents.get(),
                    pad + 6,
                    ChromeText.centeredY(listY, listH, canvas),
                    innerW - 12,
                    p.textMuted()
                );
                return;
            }
            int ry = listY + 2 - logScrollY;
            for (ExecutionAuditLog.Event e : events) {
                if (ry + logRowH >= listY && ry <= listY + listH) {
                    int dotColor = switch (e.severity()) {
                        case error -> p.ruleHardBan();
                        case warn -> p.costH();
                        case info -> p.focusRing();
                    };
                    Rect rowRect = new Rect(pad + 2, ry, innerW - 4 - barReserve, logRowH);
                    if (rowRect.contains(mouseX, mouseY)) {
                        canvas.fill(
                            rowRect.x(),
                            rowRect.y(),
                            rowRect.width(),
                            rowRect.height(),
                            OreUiPaint.withAlpha(p.focusRing(), 0.14f)
                        );
                    }
                    canvas.fill(pad + 5, ry + 5, 3, 3, dotColor);
                    Component ts = Component.literal(e.formattedTime());
                    int tsW = ChromeText.width(canvas, ts);
                    ChromeText.draw(canvas, ts, pad + 11, ry + 2, p.textMuted());
                    ChromeText.drawFitted(
                        canvas,
                        e.title(),
                        pad + 14 + tsW,
                        ry + 2,
                        Math.max(16, innerW - 18 - barReserve - tsW),
                        e.severity() == ExecutionAuditLog.Severity.error ? p.ruleHardBan() : p.textPrimary()
                    );
                    logHits.add(new LogHit(rowRect, e));
                }
                ry += logRowH;
            }
        });

        if (maxS > 0 && listH > 12) {
            int sbTrackX = pad + innerW - 5;
            canvas.fill(sbTrackX, listY + 2, 3, listH - 4, OreUiPaint.cardDivider(p));
            int thumbH = Math.max(10, Math.round((listH - 4) * (listH / (float) (listH + maxS))));
            int thumbY = listY + 2 + Math.round(((listH - 4 - thumbH) * logScrollY) / (float) maxS);
            canvas.fill(sbTrackX, thumbY, 3, thumbH, p.focusRing());
        }
    }

    private String buildCompactTelemetry(ExecutionAuditLog log) {
        ExecutionStageModel model = cockpit.stageModel();
        if (model.isEmpty()) {
            return "";
        }
        double obs = log.observedStepsPerSecond();
        double rate = obs > 0.1d ? obs : cockpit.currentSpeedRate();
        int rem = Math.max(0, model.totalRawSteps() - model.currentRawStepIndex());
        String ratePart = String.format(Locale.ROOT, "%.1f/s", rate);
        if (rem <= 0 || model.completed()) {
            return ratePart;
        }
        return ratePart + " \u00b7 "
                + ExecutionAuditLog.formatDuration(log.estimateRemainingSeconds(rem, cockpit.currentSpeedRate()));
    }

    private static void drawGaugeMetricCard(
        SceneCanvas canvas,
        CirrusPalette p,
        int x,
        int y,
        int w,
        int h,
        Component label,
        String value,
        float fillFrac,
        int accent
    ) {
        OreUiPaint.searchField(canvas, x, y, w, h, p, false);
        Component valComp = Component.literal(value);
        int vw = ChromeText.width(canvas, valComp);
        ChromeText.draw(canvas, valComp, x + w - 4 - vw, y + 3, accent);
        ChromeText.drawFitted(canvas, label, x + 4, y + 3, Math.max(12, w - 12 - vw), p.textMuted());
        int barX = x + 4;
        int barY = y + h - 7;
        int barW = Math.max(8, w - 8);
        canvas.fill(barX, barY, barW, 3, OreUiPaint.cardDivider(p));
        int fw = Math.round(barW * Math.max(0f, Math.min(1f, fillFrac)));
        if (fw > 0) {
            canvas.fill(barX, barY, fw, 3, accent);
        }
    }

    private static void drawSeverityBreakdownCard(
        SceneCanvas canvas,
        CirrusPalette p,
        int x,
        int y,
        int w,
        int h,
        int errCount,
        int warnCount,
        int infoCount
    ) {
        OreUiPaint.searchField(canvas, x, y, w, h, p, false);
        int total = errCount + warnCount + infoCount;
        Component countComp = Component.literal(Integer.toString(total));
        int cw = ChromeText.width(canvas, countComp);
        ChromeText.draw(canvas, countComp, x + w - 4 - cw, y + 3, errCount > 0 ? p.ruleHardBan() : p.textPrimary());
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Execution.Cockpit.drawerSeverityBreakdown.get(),
            x + 4,
            y + 3,
            Math.max(12, w - 12 - cw),
            p.textMuted()
        );
        int barX = x + 4;
        int barY = y + h - 7;
        int barW = Math.max(8, w - 8);
        canvas.fill(barX, barY, barW, 3, OreUiPaint.cardDivider(p));
        if (total > 0) {
            int ew = Math.round(barW * (errCount / (float) total));
            int ww = Math.round(barW * (warnCount / (float) total));
            int iw = Math.max(0, barW - ew - ww);
            int cur = barX;
            if (ew > 0) {
                canvas.fill(cur, barY, ew, 3, p.ruleHardBan());
                cur += ew;
            }
            if (ww > 0) {
                canvas.fill(cur, barY, ww, 3, p.costH());
                cur += ww;
            }
            if (iw > 0) {
                canvas.fill(cur, barY, iw, 3, p.focusRing());
            }
        } else {
            canvas.fill(barX, barY, barW, 3, OreUiPaint.withAlpha(p.costS(), 0.45f));
        }
    }

    private static void drawKpiTile(
        SceneCanvas canvas,
        CirrusPalette p,
        int x,
        int y,
        int w,
        int h,
        Component label,
        String value,
        int valColor
    ) {
        OreUiPaint.searchField(canvas, x, y, w, h, p, false);
        ChromeText.drawFitted(canvas, label, x + 4, y + 3, Math.max(8, w - 8), p.textMuted());
        ChromeText.drawFitted(canvas, Component.literal(value), x + 4, y + 13, Math.max(8, w - 8), valColor);
    }

    private static Rect drawFilterChip(
        SceneCanvas canvas,
        CirrusPalette p,
        int x,
        int y,
        int h,
        Component label,
        boolean active,
        int accent,
        int mouseX,
        int mouseY
    ) {
        int cw = Math.max(22, ChromeText.width(canvas, label) + 8);
        Rect r = new Rect(x, y, cw, h);
        boolean hov = r.contains(mouseX, mouseY);
        if (active) {
            OreUiPaint.selectedToggle(canvas, r.x(), r.y(), r.width(), r.height(), p, hov);
        } else {
            OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hov, false, false);
        }
        ChromeText.draw(
            canvas,
            label,
            r.x() + 4,
            ChromeText.centeredY(r.y(), r.height(), canvas),
            active ? accent : (hov ? p.textPrimary() : p.textMuted())
        );
        return r;
    }

    private static void drawTabButton(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        Component label,
        boolean active,
        boolean alert,
        int mouseX,
        int mouseY
    ) {
        boolean hov = r.contains(mouseX, mouseY);
        int accent = alert ? p.ruleHardBan() : p.focusRing();
        if (active) {
            OreUiPaint.selectedToggle(canvas, r.x(), r.y(), r.width(), r.height(), p, hov);
            canvas.fill(r.x() + 1, r.bottom() - 2, Math.max(1, r.width() - 2), 2, accent);
        } else {
            canvas.fill(r.x(), r.y(), r.width(), r.height(), hov ? p.dockSurface() : p.recessedSurface());
            canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), OreUiPaint.cardDivider(p), 1);
        }
        int textColor = alert ? p.ruleHardBan() : (active ? p.textPrimary() : (hov ? p.textPrimary() : p.textMuted()));
        Component fitted = CoPilotPaint.fit(canvas, label, Math.max(8, r.width() - 8));
        int tw = ChromeText.width(canvas, fitted);
        ChromeText.draw(
            canvas,
            fitted,
            r.x() + Math.max(4, (r.width() - tw) / 2),
            ChromeText.centeredY(r.y(), r.height(), canvas),
            textColor
        );
    }

    private static void drawActionBtn(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        Component label,
        boolean primary,
        int mouseX,
        int mouseY
    ) {
        boolean hov = r.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hov, primary, false);
        ChromeText.draw(
            canvas,
            label,
            r.x() + 5,
            ChromeText.centeredY(r.y(), r.height(), canvas),
            primary ? p.textOnAccent() : (hov ? p.textPrimary() : p.textSecondary())
        );
    }
}
