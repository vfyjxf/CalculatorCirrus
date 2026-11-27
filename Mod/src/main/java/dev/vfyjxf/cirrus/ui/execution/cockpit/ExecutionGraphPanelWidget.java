package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionCheckpointStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionHistoryStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Rich visual Execution Flow Graph featuring dependency lineage highlighting, per-stage stock readiness, station badges, hover quick actions, and inline error callouts.
 */
public final class ExecutionGraphPanelWidget extends Widget implements OreUiTooltipProvider {

    public static final int headerH = 20;
    private static final int pad = 6;
    private static final int gutterW = 20;
    private static final int normalCardH = 54;
    private static final int errorCardExtraH = 18;
    private static final int cardGap = 6;
    private static final int slotDim = 16;

    private final MainPanel cockpit;
    private int scrollY;
    private int lastAutoScrolledStage = -1;

    private @Nullable Rect bannerPrimaryRect;
    private @Nullable Rect bannerSecondaryRect;
    private @Nullable Rect historyClearRect;
    private final List<StageHit> stageHits = new ArrayList<>();
    private final List<HistoryHit> historyHits = new ArrayList<>();

    private record HistoryHit(Rect cardRect, Rect loadRect, Rect removeRect, ExecutionHistoryStore.Entry entry) {}

    private record StageHit(
        Rect cardRect,
        Rect bpRect,
        @Nullable Rect inspectRect,
        @Nullable Rect jumpRect,
        @Nullable Rect skipRect,
        @Nullable Rect retryRect,
        ExecutionStageModel.Stage stage
    ) {}

    public ExecutionGraphPanelWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        useStyle(flexGrow(2), flexShrink(1), flexBasisPx(0f), minHeight(24), minWidth(0));
        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());
            if (historyClearRect != null && historyClearRect.contains(lx, ly)) {
                ExecutionHistoryStore.clearAll();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            for (HistoryHit hit : historyHits) {
                if (hit.removeRect().contains(lx, ly)) {
                    ExecutionHistoryStore.remove(hit.entry().id());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.loadRect().contains(lx, ly) || hit.cardRect().contains(lx, ly)) {
                    cockpit.restoreFromHistory(hit.entry().id());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
            }
            if (bannerPrimaryRect != null && bannerPrimaryRect.contains(lx, ly)) {
                cockpit.resumeFromCheckpoint();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (bannerSecondaryRect != null && bannerSecondaryRect.contains(lx, ly)) {
                cockpit.dismissCheckpointBanner();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            for (StageHit hit : stageHits) {
                if (hit.bpRect().contains(lx, ly)) {
                    cockpit.toggleStageBreakpoint(hit.stage().stageIndex());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.retryRect() != null && hit.retryRect().contains(lx, ly)) {
                    cockpit.retryCurrentStage();
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.skipRect() != null && hit.skipRect().contains(lx, ly)) {
                    cockpit.skipCurrentStage();
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.jumpRect() != null && hit.jumpRect().contains(lx, ly)) {
                    cockpit.jumpToStage(hit.stage().stageIndex());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.inspectRect() != null && hit.inspectRect().contains(lx, ly)) {
                    cockpit.inspectStage(hit.stage().stageIndex());
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (hit.cardRect().contains(lx, ly)) {
                    if (clickCount >= 2) {
                        cockpit.jumpToStage(hit.stage().stageIndex());
                    } else {
                        cockpit.inspectStage(hit.stage().stageIndex());
                    }
                    CirrusUiSound.playClickSound();
                    ctx.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollYDelta, ctx) -> {
            ExecutionStageModel model = cockpit.stageModel();
            int listH = Math.max(0, height() - listStartY());
            int maxS = maxScroll(model.stages(), listH);
            if (maxS <= 0) {
                return EventDispatch.pass;
            }
            scrollY = Math.max(0, Math.min(maxS, scrollY - (int) Math.round(scrollYDelta * 26.0)));
            ctx.consume();
            return EventDispatch.consumed;
        });
    }

    private static int stageHeight(ExecutionStageModel.Stage stage) {
        return stage.errorSummary() != null ? normalCardH + errorCardExtraH : normalCardH;
    }

    private static int totalContentHeight(List<ExecutionStageModel.Stage> stages) {
        if (stages.isEmpty()) {
            return 0;
        }
        int total = 8;
        for (int i = 0; i < stages.size(); i++) {
            total += stageHeight(stages.get(i));
            if (i + 1 < stages.size()) {
                total += cardGap;
            }
        }
        return total;
    }

    private static int maxScroll(List<ExecutionStageModel.Stage> stages, int viewportH) {
        return Math.max(0, totalContentHeight(stages) - viewportH);
    }

    private int bannerHeight() {
        return cockpit.isCheckpointBannerVisible() ? 36 : 0;
    }

    private int listStartY() {
        return headerH + bannerHeight() + 2;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (StageHit hit : stageHits) {
            if (hit.bpRect().contains(localX, localY)) {
                boolean hasBp = hit.stage().breakpoint();
                return OreUiRichTooltip.builder(
                    hasBp ? CirrusLangs.Execution.Cockpit.bpRemove.get() : CirrusLangs.Execution.Cockpit.bpAdd.get()
                ).badge(
                    hasBp ? "BREAKPOINT ON" : "BREAKPOINT",
                    hasBp ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.accent
                ).text(CirrusLangs.Execution.Cockpit.bpDesc.get(), OreUiRichTooltip.Tone.secondary).build();
            }
            if (hit.inspectRect() != null && hit.inspectRect().contains(localX, localY)) {
                return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.stageActionInspect.get())
                        .badge("SPOTLIGHT", OreUiRichTooltip.Tone.accent).build();
            }
            if (hit.jumpRect() != null && hit.jumpRect().contains(localX, localY)) {
                return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.stageActionJump.get())
                        .badge("JUMP", OreUiRichTooltip.Tone.accent).build();
            }
            if (hit.skipRect() != null && hit.skipRect().contains(localX, localY)) {
                return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.stageActionSkip.get())
                        .badge("SKIP", OreUiRichTooltip.Tone.warning).build();
            }
            if (hit.retryRect() != null && hit.retryRect().contains(localX, localY)) {
                return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.stageActionRetry.get())
                        .badge("RETRY", OreUiRichTooltip.Tone.positive).build();
            }
            if (hit.cardRect().contains(localX, localY)) {
                return buildStageTooltip(hit.stage());
            }
        }
        return null;
    }

    private OreUiRichTooltip buildStageTooltip(ExecutionStageModel.Stage stage) {
        OreUiRichTooltip.Builder b = OreUiRichTooltip
                .builder("#" + (stage.stageIndex() + 1) + " " + stage.title().getString());
        if (!stage.outputs().isEmpty()) {
            var preview = RecipeContentSlotWidget.previewStack(stage.outputs().get(0));
            if (preview != null) {
                b.icon(preview);
            }
        }
        Component badgeText = switch (stage.status()) {
            case completed -> CirrusLangs.Execution.Cockpit.stageStatusDone.get();
            case active -> CirrusLangs.Execution.Cockpit.stageStatusActive.get();
            case paused -> CirrusLangs.Execution.Cockpit.stageStatusPaused.get();
            case pending -> CirrusLangs.Execution.Cockpit.stageStatusPending.get();
        };
        OreUiRichTooltip.Tone tone = switch (stage.status()) {
            case completed -> OreUiRichTooltip.Tone.positive;
            case active -> OreUiRichTooltip.Tone.accent;
            case paused -> OreUiRichTooltip.Tone.warning;
            case pending -> OreUiRichTooltip.Tone.muted;
        };
        b.badge(badgeText, tone);
        b.subtitle(stage.stationLabel().getString());
        if (!stage.inputs().isEmpty() || !stage.outputs().isEmpty()) {
            b.itemFlow(
                CirrusLangs.Execution.Cockpit.stageKvFormula.get(),
                toItemBadges(stage.inputs()),
                toItemBadges(stage.outputs())
            );
        }
        if (stage.times() > 1) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.stageKvTimes.get(),
                Component.literal("\u00d7" + stage.times()),
                OreUiRichTooltip.Tone.primary
            );
        }
        int missingInputs = cockpit.countMissingInputsForStage(stage);
        if (!stage.isCompleted()) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.matTitle.get(),
                missingInputs > 0
                        ? CirrusLangs.Execution.Cockpit.stageShort.get(missingInputs)
                        : CirrusLangs.Execution.Cockpit.stageReady.get(),
                missingInputs > 0 ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.positive
            );
        }
        if (!stage.upstreamStages().isEmpty()) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.stageDeps.get(formatStageList(stage.upstreamStages())),
                Component.literal(stage.upstreamStages().size() + " links"),
                OreUiRichTooltip.Tone.accent
            );
        }
        if (stage.errorSummary() != null) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.stageKvState.get(),
                stage.errorSummary(),
                OreUiRichTooltip.Tone.danger
            );
        } else if (stage.phaseHint() != null) {
            b.keyValue(CirrusLangs.Execution.Cockpit.stageKvState.get(), stage.phaseHint(), tone);
        }
        if (stage.breakpoint()) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.stageKvBp.get(),
                CirrusLangs.Execution.Cockpit.bpEnabled.get(),
                OreUiRichTooltip.Tone.warning
            );
        }
        b.footer(CirrusLangs.Execution.Cockpit.stageFooter.get());
        return b.build();
    }

    private static List<OreUiRichTooltip.ItemBadge> toItemBadges(List<RecipeContent> list) {
        List<OreUiRichTooltip.ItemBadge> out = new ArrayList<>(list.size());
        for (RecipeContent content : list) {
            var stack = RecipeContentSlotWidget.previewStack(content);
            if (stack == null) {
                continue;
            }
            String amt = OreUiSlotAmount.format(content.amount());
            out.add(OreUiRichTooltip.ItemBadge.of(stack, amt.isEmpty() ? null : Component.literal(amt)));
        }
        return out;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        stageHits.clear();
        historyHits.clear();
        historyClearRect = null;
        bannerPrimaryRect = null;
        bannerSecondaryRect = null;

        canvas.fill(0, 0, w, h, p.canvasVoid());

        boolean historyMode = cockpit.showingHistory() || (!cockpit.hasTarget() && ExecutionHistoryStore.size() > 0);
        if (historyMode) {
            renderHistoryPanel(canvas, p, w, h, mouseX, mouseY, partialTicks);
            return;
        }

        // OreUI section header
        OreUiPaint.headerBand(canvas, 0, 0, w, headerH, p, false);
        ExecutionStageModel model = cockpit.stageModel();
        Component title = model.isEmpty()
                ? CirrusLangs.Execution.Cockpit.graphTitle.get()
                : CirrusLangs.Execution.Cockpit.graphTitleProgress
                        .get(model.completedStageCount(), model.totalStageCount());
        ChromeText.drawFitted(
            canvas,
            title,
            pad,
            ChromeText.centeredY(0, headerH, canvas),
            Math.max(40, w - pad * 2 - 74),
            p.textPrimary()
        );
        if (w >= 210 && !model.isEmpty()) {
            Component hint = CirrusLangs.Execution.Cockpit.graphHint.get();
            int hw = ChromeText.width(canvas, hint);
            ChromeText.draw(canvas, hint, w - pad - hw, ChromeText.centeredY(0, headerH, canvas), p.textMuted());
        }

        if (cockpit.isCheckpointBannerVisible()) {
            drawCheckpointBanner(canvas, p, w, headerH + 2, mouseX, mouseY);
        }

        int listTop = listStartY();
        int listH = Math.max(0, h - listTop - 2);
        if (listH <= 4) {
            return;
        }

        List<ExecutionStageModel.Stage> stages = model.stages();
        int[] cardOffsets = new int[stages.size()];
        int curOff = 0;
        for (int i = 0; i < stages.size(); i++) {
            cardOffsets[i] = curOff;
            curOff += stageHeight(stages.get(i)) + cardGap;
        }

        // Smoothly follow active stage when it changes
        int activeIdx = model.activeStageIndex();
        if (activeIdx >= 0 && activeIdx < stages.size() && activeIdx != lastAutoScrolledStage && listH > normalCardH) {
            lastAutoScrolledStage = activeIdx;
            int targetTop = cardOffsets[activeIdx];
            int targetBot = targetTop + stageHeight(stages.get(activeIdx));
            if (targetTop < scrollY) {
                scrollY = Math.max(0, targetTop - 4);
            } else if (targetBot > scrollY + listH - 4) {
                scrollY = Math.max(0, targetBot - listH + 8);
            }
        }

        int maxS = maxScroll(stages, listH);
        if (scrollY > maxS) {
            scrollY = maxS;
        }
        int barReserve = maxS > 0 ? 6 : 0;

        canvas.withClip(0, listTop, w, listH, () -> {
            if (model.isEmpty()) {
                drawEmptyGraph(canvas, p, w, listTop, listH);
                return;
            }
            int cardX = pad + gutterW;
            int cardW = Math.max(60, w - cardX - pad - barReserve);
            int spineX = pad + gutterW / 2 - 1;
            int baseY = listTop + 4 - scrollY;

            // Detect hovered stage for dependency lineage highlighting
            int hoveredStageIdx = -1;
            if (mouseY >= listTop && mouseY <= listTop + listH) {
                for (int i = 0; i < stages.size(); i++) {
                    int sy = baseY + cardOffsets[i];
                    int ch = stageHeight(stages.get(i));
                    if (mouseX >= pad && mouseX <= cardX + cardW && mouseY >= sy && mouseY <= sy + ch) {
                        hoveredStageIdx = i;
                        break;
                    }
                }
            }
            int focusStageIdx = hoveredStageIdx >= 0 ? hoveredStageIdx : cockpit.inspectedStageIndex();
            List<Integer> upstreamHighlight = focusStageIdx >= 0 && focusStageIdx < stages.size()
                    ? stages.get(focusStageIdx).upstreamStages()
                    : List.of();
            List<Integer> downstreamHighlight = focusStageIdx >= 0 && focusStageIdx < stages.size()
                    ? model.downstreamStagesOf(focusStageIdx)
                    : List.of();

            // Vertical conduit spine & dependency rail
            for (int i = 0; i < stages.size() - 1; i++) {
                int y0 = baseY + cardOffsets[i] + stageHeight(stages.get(i)) / 2;
                int y1 = baseY + cardOffsets[i + 1] + stageHeight(stages.get(i + 1)) / 2;
                if (y1 < listTop || y0 > listTop + listH) {
                    continue;
                }
                ExecutionStageModel.Stage cur = stages.get(i);
                int edgeColor = cur.status() == ExecutionStageModel.StageStatus.completed
                        ? p.costS()
                        : (cur.status() == ExecutionStageModel.StageStatus.paused
                                ? p.ruleHardBan()
                                : (cur.status() == ExecutionStageModel.StageStatus.active
                                        ? p.focusRing()
                                        : OreUiPaint.cardDivider(p)));
                canvas.fill(spineX, y0, 2, Math.max(1, y1 - y0), edgeColor);
                if (cur.status() == ExecutionStageModel.StageStatus.active && !OreUiMotion.reducedMotion()) {
                    float t = OreUiMotion.cycle01(OreUiMotion.nowMs(), 1200L, 0f, false);
                    int py = Math.round(OreUiMotion.lerp(y0, Math.max(y0, y1 - 6), t));
                    canvas.fill(spineX, py, 2, 6, OreUiPaint.lighten(p.focusRing(), 0.45f));
                }
            }

            // Dependency link rails on left margin when hovering or inspecting a stage with links
            if (focusStageIdx >= 0 && (!upstreamHighlight.isEmpty() || !downstreamHighlight.isEmpty())) {
                int focusY = baseY + cardOffsets[focusStageIdx] + stageHeight(stages.get(focusStageIdx)) / 2;
                int railX = pad + 1;
                for (int up : upstreamHighlight) {
                    int upY = baseY + cardOffsets[up] + stageHeight(stages.get(up)) / 2;
                    int topR = Math.min(upY, focusY);
                    int botR = Math.max(upY, focusY);
                    canvas.fill(railX, topR, 2, Math.max(1, botR - topR), p.costH());
                    canvas.fill(railX, upY, spineX - railX, 1, p.costH());
                    canvas.fill(railX, focusY, spineX - railX, 1, p.costH());
                }
                for (int down : downstreamHighlight) {
                    int downY = baseY + cardOffsets[down] + stageHeight(stages.get(down)) / 2;
                    int topR = Math.min(downY, focusY);
                    int botR = Math.max(downY, focusY);
                    canvas.fill(railX, topR, 2, Math.max(1, botR - topR), p.focusRing());
                    canvas.fill(railX, downY, spineX - railX, 1, p.focusRing());
                }
            }

            ExecutionStageModel.Stage spotlight = cockpit.spotlightStage();
            int spotlightIdx = spotlight == null ? -1 : spotlight.stageIndex();

            // Stage nodes + rich cards
            for (int i = 0; i < stages.size(); i++) {
                ExecutionStageModel.Stage stage = stages.get(i);
                int ch = stageHeight(stage);
                int sy = baseY + cardOffsets[i];
                if (sy + ch >= listTop && sy <= listTop + listH) {
                    Rect bpRect = new Rect(pad + 3, sy + (normalCardH - 14) / 2, 14, 14);
                    Rect cardRect = new Rect(cardX, sy, cardW, ch);
                    boolean isUpstream = upstreamHighlight.contains(i);
                    boolean isDownstream = downstreamHighlight.contains(i);
                    boolean isInspected = i == spotlightIdx;
                    drawStageNode(canvas, p, bpRect, stage, mouseX, mouseY);
                    StageHit hit = drawStageCard(
                        canvas,
                        p,
                        cardRect,
                        bpRect,
                        stage,
                        isInspected,
                        isUpstream,
                        isDownstream,
                        mouseX,
                        mouseY,
                        partialTicks
                    );
                    stageHits.add(hit);
                }
            }
        });

        if (maxS > 0 && listH > 12) {
            int trackX = w - 5;
            canvas.fill(trackX, listTop + 2, 3, listH - 4, OreUiPaint.cardDivider(p));
            int thumbH = Math.max(12, Math.round((listH - 4) * (listH / (float) (listH + maxS))));
            int thumbY = listTop + 2 + Math.round(((listH - 4 - thumbH) * scrollY) / (float) maxS);
            canvas.fill(trackX, thumbY, 3, thumbH, p.focusRing());
        }
    }

    private void drawCheckpointBanner(SceneCanvas canvas, CirrusPalette p, int w, int y, int mouseX, int mouseY) {
        int bw = Math.max(40, w - pad * 2);
        int bh = 32;
        int accent = p.focusRing();

        canvas.fill(pad, y, bw, bh, OreUiPaint.withAlpha(accent, OreUiPaint.isDark(p) ? 0.16f : 0.10f));
        canvas.strokeRect(pad, y, bw, bh, accent, 1);

        ExecutionCheckpointStore.Checkpoint cp = cockpit.activeCheckpoint();
        Component title = cp == null
                ? CirrusLangs.Execution.Cockpit.bannerCpFound.get()
                : CirrusLangs.Execution.Cockpit.bannerCpStage.get(cp.stageIndex() + 1, cp.totalStages());
        ChromeText.drawFitted(canvas, title, pad + 5, y + 3, Math.max(24, bw - 10), p.textPrimary());

        Component primaryLabel = CirrusLangs.Execution.Cockpit.bannerCpResume.get();
        Component secondaryLabel = CirrusLangs.Execution.Cockpit.bannerCpRestart.get();
        int btnY = y + 15;
        int btnH = 14;
        int pW = Math.max(40, ChromeText.width(canvas, primaryLabel) + 10);
        int sW = Math.max(40, ChromeText.width(canvas, secondaryLabel) + 10);
        bannerPrimaryRect = new Rect(pad + 5, btnY, pW, btnH);
        bannerSecondaryRect = new Rect(pad + 5 + pW + 4, btnY, sW, btnH);

        boolean pHov = bannerPrimaryRect.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            bannerPrimaryRect.x(),
            bannerPrimaryRect.y(),
            bannerPrimaryRect.width(),
            bannerPrimaryRect.height(),
            p,
            false,
            pHov,
            true,
            false
        );
        ChromeText.draw(
            canvas,
            primaryLabel,
            bannerPrimaryRect.x() + 5,
            ChromeText.centeredY(btnY, btnH, canvas),
            p.textOnAccent()
        );

        boolean sHov = bannerSecondaryRect.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            bannerSecondaryRect.x(),
            bannerSecondaryRect.y(),
            bannerSecondaryRect.width(),
            bannerSecondaryRect.height(),
            p,
            false,
            sHov,
            false,
            false
        );
        ChromeText.draw(
            canvas,
            secondaryLabel,
            bannerSecondaryRect.x() + 5,
            ChromeText.centeredY(btnY, btnH, canvas),
            sHov ? p.textPrimary() : p.textSecondary()
        );
    }

    private void drawEmptyGraph(SceneCanvas canvas, CirrusPalette p, int w, int top, int h) {
        int cardW = Math.min(w - 16, 220);
        int cardHBox = 54;
        int x = Math.max(8, (w - cardW) / 2);
        int y = top + Math.max(8, (h - cardHBox) / 2);
        OreUiPaint.raisedCard(canvas, x, y, cardW, cardHBox, p, false, false);
        Component line1 = cockpit.calculating()
                ? CirrusLangs.Execution.Cockpit.graphEmptyPlanning.get()
                : CirrusLangs.Execution.Cockpit.graphEmptyIdle.get();
        Component line2 = cockpit.calculating()
                ? CirrusLangs.Execution.Cockpit.graphEmptyPlanningSub.get()
                : CirrusLangs.Execution.Cockpit.graphEmptyIdleSub.get();
        int w1 = ChromeText.width(canvas, line1);
        ChromeText.drawFitted(canvas, line1, x + Math.max(6, (cardW - w1) / 2), y + 12, cardW - 12, p.textPrimary());
        int w2 = ChromeText.width(canvas, line2);
        ChromeText.drawFitted(canvas, line2, x + Math.max(6, (cardW - w2) / 2), y + 29, cardW - 12, p.textMuted());
    }

    private static void drawStageNode(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect bpRect,
        ExecutionStageModel.Stage stage,
        int mouseX,
        int mouseY
    ) {
        boolean hov = bpRect.contains(mouseX, mouseY);
        int cx = bpRect.x() + 2;
        int cy = bpRect.y() + 2;
        int size = 10;
        int bg = switch (stage.status()) {
            case completed -> p.costS();
            case active -> p.focusRing();
            case paused -> p.ruleHardBan();
            case pending -> p.cardSurface();
        };
        if (stage.breakpoint()) {
            canvas.fill(cx - 1, cy - 1, size + 2, size + 2, p.ruleHardBan());
            canvas.fill(cx + 1, cy + 1, size - 2, size - 2, bg);
        } else {
            canvas.fill(cx, cy, size, size, bg);
            canvas.strokeRect(cx, cy, size, size, hov ? p.focusRing() : OreUiPaint.outerInk(p), 1);
        }
        String glyph = switch (stage.status()) {
            case completed -> "\u2713";
            case active -> "\u25b8";
            case paused -> "!";
            case pending -> "";
        };
        if (!glyph.isEmpty()) {
            Component g = Component.literal(glyph);
            int gw = ChromeText.width(canvas, g);
            ChromeText.draw(
                canvas,
                g,
                cx + Math.max(1, (size - gw) / 2),
                ChromeText.centeredY(cy, size, canvas),
                stage.status() == ExecutionStageModel.StageStatus.pending ? p.textMuted() : p.textOnAccent()
            );
        }
    }

    private StageHit drawStageCard(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        Rect bpRect,
        ExecutionStageModel.Stage stage,
        boolean isInspected,
        boolean isUpstream,
        boolean isDownstream,
        int mouseX,
        int mouseY,
        float partialTicks
    ) {
        boolean hov = r.contains(mouseX, mouseY);
        boolean active = stage.status() == ExecutionStageModel.StageStatus.active;
        boolean paused = stage.status() == ExecutionStageModel.StageStatus.paused;
        boolean done = stage.status() == ExecutionStageModel.StageStatus.completed;

        OreUiPaint.raisedCard(canvas, r.x(), r.y(), r.width(), r.height(), p, hov, active || isInspected);
        if (paused) {
            canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), p.ruleHardBan(), 1);
        } else if (isUpstream) {
            canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), p.costH(), 1);
        } else if (isDownstream) {
            canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), p.focusRing(), 1);
        } else if (done && !hov) {
            canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), OreUiPaint.withAlpha(p.costS(), 0.45f), 1);
        }

        // Left status accent stripe
        int stripeColor = done
                ? p.costS()
                : (paused ? p.ruleHardBan() : (active ? p.focusRing() : OreUiPaint.cardDivider(p)));
        canvas.fill(r.x() + 1, r.y() + 1, 2, r.height() - 2, stripeColor);

        // Row 1: #N pill + Station badge + Title (xTimes) + Phase/BP pill
        int topY = r.y() + 3;
        Component idxComp = Component.literal("#" + (stage.stageIndex() + 1));
        int idxColor = done ? p.costS() : (active ? p.focusRing() : (paused ? p.ruleHardBan() : p.textMuted()));
        int idxW = OreUiPaint.tagPill(canvas, r.x() + 5, topY, 13, idxComp, idxColor, p);

        int rightLimit = r.right() - 5;
        if (stage.phaseHint() != null) {
            int pw = ChromeText.width(canvas, stage.phaseHint()) + 10;
            int px = rightLimit - pw;
            if (px > r.x() + idxW + 44) {
                OreUiPaint
                        .tagPill(canvas, px, topY, 13, stage.phaseHint(), paused ? p.ruleHardBan() : p.focusRing(), p);
                rightLimit = px - 4;
            }
        } else if (stage.breakpoint()) {
            Component bpComp = CirrusLangs.Execution.Cockpit.bpTag.get();
            int bw = ChromeText.width(canvas, bpComp) + 10;
            int bx = rightLimit - bw;
            if (bx > r.x() + idxW + 40) {
                OreUiPaint.tagPill(canvas, bx, topY, 13, bpComp, p.ruleHardBan(), p);
                rightLimit = bx - 4;
            }
        }

        // Station badge when width allows
        int titleX = r.x() + 5 + idxW + 4;
        int stationW = ChromeText.width(canvas, stage.stationLabel()) + 10;
        if (rightLimit - titleX - stationW >= 54) {
            int actualStationW = OreUiPaint
                    .tagPill(canvas, titleX, topY, 13, stage.stationLabel(), p.textSecondary(), p);
            titleX += actualStationW + 4;
        }

        String label = stage.times() > 1
                ? stage.title().getString() + " \u00d7" + stage.times()
                : stage.title().getString();
        ChromeText.drawFitted(
            canvas,
            Component.literal(label),
            titleX,
            topY + 2,
            Math.max(16, rightLimit - titleX),
            done ? p.textSecondary() : p.textPrimary()
        );

        // Row 2: Formula strip + Right Stock Readiness pill
        int formulaY = r.y() + 19;
        int stockPillW = 0;
        if (!done) {
            int shortInputs = cockpit.countMissingInputsForStage(stage);
            Component stockComp = shortInputs > 0
                    ? CirrusLangs.Execution.Cockpit.stageShort.get(shortInputs)
                    : CirrusLangs.Execution.Cockpit.stageReady.get();
            stockPillW = ChromeText.width(canvas, stockComp) + 10;
            int sx = r.right() - 5 - stockPillW;
            if (sx > r.x() + 54) {
                OreUiPaint.tagPill(
                    canvas,
                    sx,
                    formulaY + 1,
                    13,
                    stockComp,
                    shortInputs > 0 ? p.ruleHardBan() : p.costS(),
                    p
                );
                stockPillW += 5;
            } else {
                stockPillW = 0;
            }
        }

        if (stage.inputs().isEmpty() && stage.outputs().isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                stage.manual()
                        ? CirrusLangs.Execution.Cockpit.stageManualHint.get()
                        : CirrusLangs.Execution.Cockpit.stagePrepHint.get(),
                r.x() + 6,
                formulaY + 3,
                Math.max(20, r.width() - 12 - stockPillW),
                p.textMuted()
            );
        } else {
            drawFormulaStrip(
                canvas,
                p,
                r.x() + 6,
                formulaY,
                Math.max(40, r.width() - 12 - stockPillW),
                stage,
                active,
                partialTicks
            );
        }

        // Row 3: Dependency lineage / Micro progress bar + Quick Action buttons
        int footY = r.y() + 39;
        int footH = 12;
        int actionLeft = r.right() - 4;
        Rect inspectRect = null;
        Rect jumpRect = null;
        Rect skipRect = null;

        boolean showActions = hov || active || paused || isInspected;
        if (showActions && r.width() >= 110) {
            int bW = 14;
            int bH = 12;
            if ((active || paused) && cockpit.canSkipStage()) {
                skipRect = new Rect(actionLeft - bW, footY, bW, bH);
                drawMicroAction(canvas, p, skipRect, "\u23ed", false, mouseX, mouseY);
                actionLeft = skipRect.x() - 2;
            }
            jumpRect = new Rect(actionLeft - bW, footY, bW, bH);
            drawMicroAction(canvas, p, jumpRect, "\u25b6", false, mouseX, mouseY);
            actionLeft = jumpRect.x() - 2;

            inspectRect = new Rect(actionLeft - bW, footY, bW, bH);
            drawMicroAction(canvas, p, inspectRect, "\u25ce", isInspected, mouseX, mouseY);
            actionLeft = inspectRect.x() - 4;
        }

        int metaX = r.x() + 6;
        int metaW = Math.max(0, actionLeft - metaX);
        if (metaW >= 24) {
            if ((active || paused) && stage.totalOps() > 1) {
                int barW = Math.min(48, Math.max(20, metaW / 2));
                int barY = footY + 4;
                canvas.fill(metaX, barY, barW, 4, p.recessedSurface());
                int fillW = Math.round((float) (barW * stage.opProgressRatio()));
                if (fillW > 0) {
                    canvas.fill(metaX, barY, fillW, 4, paused ? p.ruleHardBan() : p.focusRing());
                }
                Component opTxt = Component.literal(stage.completedOps() + "/" + stage.totalOps());
                int tx = metaX + barW + 4;
                if (tx + 16 < actionLeft) {
                    ChromeText.drawFitted(
                        canvas,
                        opTxt,
                        tx,
                        ChromeText.centeredY(footY, footH, canvas),
                        Math.max(10, actionLeft - tx),
                        p.textMuted()
                    );
                }
            } else if (!stage.upstreamStages().isEmpty()) {
                Component deps = CirrusLangs.Execution.Cockpit.stageDeps.get(formatStageList(stage.upstreamStages()));
                ChromeText.drawFitted(
                    canvas,
                    deps,
                    metaX,
                    ChromeText.centeredY(footY, footH, canvas),
                    metaW,
                    hov || isInspected ? p.costH() : p.textMuted()
                );
            } else if (done) {
                ChromeText.drawFitted(
                    canvas,
                    CirrusLangs.Execution.Cockpit.stageStatusDone.get(),
                    metaX,
                    ChromeText.centeredY(footY, footH, canvas),
                    metaW,
                    p.costS()
                );
            }
        }

        // Optional Row 4: Inline Error / Safety-Stop callout directly inside the blocked card
        Rect retryRect = null;
        Component errSummary = stage.errorSummary();
        if (errSummary != null) {
            int errY = r.y() + normalCardH;
            int errH = errorCardExtraH - 2;
            int errX = r.x() + 4;
            int errW = r.width() - 8;
            canvas.fill(
                errX,
                errY,
                errW,
                errH,
                OreUiPaint.withAlpha(p.ruleHardBan(), OreUiPaint.isDark(p) ? 0.22f : 0.14f)
            );
            canvas.strokeRect(errX, errY, errW, errH, OreUiPaint.withAlpha(p.ruleHardBan(), 0.75f), 1);

            Component retryComp = CirrusLangs.Execution.Cockpit.stageActionRetry.get();
            int rw = Math.max(30, ChromeText.width(canvas, retryComp) + 8);
            retryRect = new Rect(errX + errW - rw - 2, errY + 1, rw, errH - 2);
            boolean rHov = retryRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                retryRect.x(),
                retryRect.y(),
                retryRect.width(),
                retryRect.height(),
                p,
                false,
                rHov,
                true,
                false
            );
            ChromeText.draw(
                canvas,
                retryComp,
                retryRect.x() + 4,
                ChromeText.centeredY(retryRect.y(), retryRect.height(), canvas),
                p.textOnAccent()
            );

            ChromeText.drawFitted(
                canvas,
                Component.literal("! ").append(errSummary),
                errX + 4,
                ChromeText.centeredY(errY, errH, canvas),
                Math.max(16, retryRect.x() - errX - 6),
                p.ruleHardBan()
            );
        }

        return new StageHit(r, bpRect, inspectRect, jumpRect, skipRect, retryRect, stage);
    }

    private void drawFormulaStrip(
        SceneCanvas canvas,
        CirrusPalette p,
        int startX,
        int formulaY,
        int availW,
        ExecutionStageModel.Stage stage,
        boolean active,
        float partialTicks
    ) {
        int stride = slotDim - 1;
        int arrowW = 14;
        int outCount = Math.min(2, stage.outputs().size());
        int reservedRight = outCount * stride + arrowW + 8;
        int maxInArea = Math.max(slotDim, availW - reservedRight);
        int maxInSlots = Math.max(1, maxInArea / stride);
        int totalIn = stage.inputs().size();
        boolean hasOverflow = totalIn > maxInSlots;
        int shownIn = hasOverflow ? Math.max(1, maxInSlots - 1) : totalIn;

        Map<String, OverlayMaterialRequirement> matMap = cockpit.materialMap();
        int cx = startX;
        for (int k = 0; k < shownIn; k++) {
            RecipeContent in = stage.inputs().get(k);
            OreUiPaint.slot(canvas, cx, formulaY, slotDim, p);
            ExecutionOverlayContentHelper.renderRecipeContent(canvas, in, cx + 1, formulaY + 1, 14, 14, partialTicks);
            if (in.amount() > 1L) {
                OreUiSlotAmount.drawInSlot(canvas, cx, formulaY, slotDim, in.amount(), p);
            }
            OverlayMaterialRequirement req = matMap.get(ExecutionOverlayContentHelper.recipeContentKey(in));
            if (req != null && req.missing() > 0L) {
                canvas.strokeRect(
                    cx + 1,
                    formulaY + 1,
                    slotDim - 2,
                    slotDim - 2,
                    OreUiPaint.withAlpha(p.ruleHardBan(), 0.65f),
                    1
                );
            }
            cx += stride;
        }
        if (hasOverflow) {
            OreUiPaint.slot(canvas, cx, formulaY, slotDim, p);
            canvas.strokeRect(
                cx + 1,
                formulaY + 1,
                slotDim - 2,
                slotDim - 2,
                OreUiPaint.withAlpha(p.focusRing(), 0.5f),
                1
            );
            Component ov = Component.literal("+" + (totalIn - shownIn));
            int ow = ChromeText.width(canvas, ov);
            ChromeText.drawFitted(
                canvas,
                ov,
                cx + Math.max(1, (slotDim - ow) / 2),
                ChromeText.centeredY(formulaY, slotDim, canvas),
                slotDim - 2,
                p.focusRing()
            );
            cx += stride;
        }

        if (outCount > 0) {
            cx += 3;
            OreUiRecipeGrid.drawRecipeArrow(canvas, cx, cx + arrowW - 2, formulaY + slotDim / 2, p, active);
            cx += arrowW + 2;
            int primaryOutX = cx;
            for (int k = 0; k < outCount; k++) {
                RecipeContent out = stage.outputs().get(k);
                OreUiPaint.slot(canvas, cx, formulaY, slotDim, p);
                ExecutionOverlayContentHelper
                        .renderRecipeContent(canvas, out, cx + 1, formulaY + 1, 14, 14, partialTicks);
                if (out.amount() > 1L) {
                    OreUiSlotAmount.drawInSlot(canvas, cx, formulaY, slotDim, out.amount(), p);
                }
                cx += stride;
            }
            canvas.strokeRect(
                primaryOutX + 1,
                formulaY + 1,
                slotDim - 2,
                slotDim - 2,
                OreUiPaint.withAlpha(p.costS(), 0.55f),
                1
            );
        }
    }

    private static void drawMicroAction(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect r,
        String glyph,
        boolean active,
        int mouseX,
        int mouseY
    ) {
        boolean hov = r.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, active, hov, false, false);
        Component comp = Component.literal(glyph);
        int cw = ChromeText.width(canvas, comp);
        ChromeText.draw(
            canvas,
            comp,
            r.x() + Math.max(1, (r.width() - cw) / 2),
            ChromeText.centeredY(r.y(), r.height(), canvas),
            active ? p.focusRing() : (hov ? p.textPrimary() : p.textSecondary())
        );
    }

    private void renderHistoryPanel(
        SceneCanvas canvas,
        CirrusPalette p,
        int w,
        int h,
        int mouseX,
        int mouseY,
        float partialTicks
    ) {
        List<ExecutionHistoryStore.Entry> entries = ExecutionHistoryStore.entries();
        OreUiPaint.headerBand(canvas, 0, 0, w, headerH, p, false);
        Component title = CirrusLangs.Execution.Cockpit.historyHeader.get(entries.size());
        int clearW = entries.isEmpty() ? 0 : 42;
        ChromeText.drawFitted(
            canvas,
            title,
            pad,
            ChromeText.centeredY(0, headerH, canvas),
            Math.max(40, w - pad * 2 - clearW - 4),
            p.textPrimary()
        );
        if (!entries.isEmpty()) {
            historyClearRect = new Rect(w - pad - clearW, 3, clearW, headerH - 6);
            boolean clrHov = historyClearRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                historyClearRect.x(),
                historyClearRect.y(),
                historyClearRect.width(),
                historyClearRect.height(),
                p,
                false,
                clrHov,
                false,
                false
            );
            Component clrText = CirrusLangs.Execution.Cockpit.historyClear.get();
            int tw = ChromeText.width(canvas, clrText);
            ChromeText.drawFitted(
                canvas,
                clrText,
                historyClearRect.x() + Math.max(2, (historyClearRect.width() - tw) / 2),
                ChromeText.centeredY(historyClearRect.y(), historyClearRect.height(), canvas),
                Math.max(8, historyClearRect.width() - 4),
                clrHov ? p.ruleHardBan() : p.textSecondary()
            );
        }

        int listTop = headerH + 2;
        int listH = Math.max(0, h - listTop - 2);
        if (listH <= 8) {
            return;
        }
        int rowH = 38;
        int rowGap = 5;
        int totalH = entries.isEmpty() ? 0 : 6 + entries.size() * (rowH + rowGap);
        int maxS = Math.max(0, totalH - listH);
        if (scrollY > maxS) {
            scrollY = maxS;
        }
        int barReserve = maxS > 0 ? 6 : 0;
        canvas.withClip(0, listTop, w, listH, () -> {
            if (entries.isEmpty()) {
                Component empty = CirrusLangs.Execution.Cockpit.historyEmpty.get();
                int ew = ChromeText.width(canvas, empty);
                ChromeText.drawFitted(
                    canvas,
                    empty,
                    Math.max(pad, (w - ew) / 2),
                    listTop + Math.max(12, listH / 2 - 4),
                    Math.max(24, w - pad * 2),
                    p.textMuted()
                );
                return;
            }
            int cardW = Math.max(80, w - pad * 2 - barReserve);
            int y = listTop + 4 - scrollY;
            for (ExecutionHistoryStore.Entry entry : entries) {
                if (y + rowH >= listTop && y <= listTop + listH) {
                    Rect cardRect = new Rect(pad, y, cardW, rowH);
                    boolean hov = cardRect.contains(mouseX, mouseY);
                    OreUiPaint.raisedCard(canvas, pad, y, cardW, rowH, p, hov, false);
                    int accent = entry.completed() ? p.costS() : (entry.resumable() ? p.costH() : p.focusRing());
                    canvas.fill(pad + 1, y + 1, 2, rowH - 2, accent);

                    int slotX = pad + 6;
                    int slotY = y + (rowH - 18) / 2;
                    OreUiPaint.slot(canvas, slotX, slotY, 18, p);
                    RecipeContent preview = ExecutionTargetRecipeState.previewContent(entry.recipe());
                    if (preview.notEmpty()) {
                        ExecutionOverlayContentHelper
                                .renderRecipeContent(canvas, preview, slotX + 1, slotY + 1, 16, 16, partialTicks);
                    }
                    if (entry.times() > 1) {
                        OreUiSlotAmount.drawInSlot(canvas, slotX, slotY, 18, entry.times(), p);
                    }

                    Rect removeRect = new Rect(pad + cardW - 18, y + (rowH - 14) / 2, 14, 14);
                    Component actionText = entry.resumable()
                            ? CirrusLangs.Execution.Cockpit.historyResume.get()
                            : CirrusLangs.Execution.Cockpit.historyLoad.get();
                    int loadW = Math.max(30, Math.min(48, ChromeText.width(canvas, actionText) + 10));
                    Rect loadRect = new Rect(removeRect.x() - loadW - 3, y + (rowH - 15) / 2, loadW, 15);
                    historyHits.add(new HistoryHit(cardRect, loadRect, removeRect, entry));

                    int textX = slotX + 22;
                    int textMaxW = Math.max(20, loadRect.x() - textX - 4);
                    Component nameComp = Component.literal(entry.displayName().getString() + " \u00d7" + entry.times());
                    ChromeText.drawFitted(canvas, nameComp, textX, y + 6, textMaxW, p.textPrimary());

                    Component statusComp = entry.completed()
                            ? CirrusLangs.Execution.Cockpit.historyDone.get()
                            : CirrusLangs.Execution.Cockpit.historyPaused.get();
                    Component metaComp = CirrusLangs.Execution.Cockpit.historyStages
                            .get(entry.stageCount(), statusComp.getString());
                    ChromeText.drawFitted(canvas, metaComp, textX, y + 20, textMaxW, accent);

                    boolean loadHov = loadRect.contains(mouseX, mouseY);
                    OreUiPaint.raisedButton(
                        canvas,
                        loadRect.x(),
                        loadRect.y(),
                        loadRect.width(),
                        loadRect.height(),
                        p,
                        false,
                        loadHov,
                        entry.resumable(),
                        false
                    );
                    int aw = ChromeText.width(canvas, actionText);
                    ChromeText.drawFitted(
                        canvas,
                        actionText,
                        loadRect.x() + Math.max(2, (loadRect.width() - aw) / 2),
                        ChromeText.centeredY(loadRect.y(), loadRect.height(), canvas),
                        Math.max(8, loadRect.width() - 4),
                        entry.resumable() ? p.textOnAccent() : (loadHov ? p.textPrimary() : p.textSecondary())
                    );
                    drawMicroAction(canvas, p, removeRect, "\u00d7", false, mouseX, mouseY);
                }
                y += rowH + rowGap;
            }
        });
    }

    private static String formatStageList(List<Integer> stageIndices) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stageIndices.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('#').append(stageIndices.get(i) + 1);
        }
        return sb.toString();
    }
}
