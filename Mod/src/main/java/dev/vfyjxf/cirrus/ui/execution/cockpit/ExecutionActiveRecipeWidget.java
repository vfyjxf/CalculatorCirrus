package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.page.RecipeActiveContext;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
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

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Pinned concrete recipe spotlight rendering the active (or user-inspected) stage as a full non-abbreviated 3x3 crafting grid or multi-row slot layout with live per-slot stock status.
 */
public final class ExecutionActiveRecipeWidget extends Widget implements OreUiTooltipProvider {

    private static final int headerH = 18;
    private static final int pad = 6;
    private static final int slotSize = 18;
    private static final int slotStride = 17;
    private static final int expandedHeight = 78;

    private record SlotHit(Rect rect, RecipeContent content, boolean output) {}

    private final MainPanel cockpit;
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final OreUiMotion.SmoothValue smoothHeight = new OreUiMotion.SmoothValue();
    private boolean collapsed;
    private int lastAppliedHeight = -1;
    private @Nullable Rect headerRect;
    private @Nullable Rect followRect;

    public ExecutionActiveRecipeWidget(MainPanel cockpit) {
        this.cockpit = cockpit;
        setInteractive(true);
        applyStyleHeight();
        onMouseClick((input, clickCount, ctx) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) Math.round(local.x());
            int ly = (int) Math.round(local.y());
            if (followRect != null && followRect.contains(lx, ly)) {
                cockpit.clearInspectedStage();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (headerRect != null && headerRect.contains(lx, ly)) {
                collapsed = !collapsed;
                applyStyleHeight();
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public boolean collapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed != collapsed) {
            this.collapsed = collapsed;
            applyStyleHeight();
        }
    }

    private int effectiveExpandedHeight() {
        int budget = cockpit.panelHeightBudget();
        return Math.min(expandedHeight, Math.max(headerH + 24, Math.round(budget * 0.24f)));
    }

    private void applyStyleHeight() {
        int target = collapsed ? headerH : effectiveExpandedHeight();
        if (!smoothHeight.initialized()) {
            smoothHeight.snap(target, System.nanoTime());
        }
        applyHeightPx(Math.round(smoothHeight.value()));
    }

    private void applyHeightPx(int heightPx) {
        int clamped = Math.max(headerH, Math.min(effectiveExpandedHeight(), heightPx));
        if (lastAppliedHeight != clamped) {
            lastAppliedHeight = clamped;
            useStyle(heightOf(clamped), flexShrink(0), minWidth(0));
        }
    }

    private ExecutionStageModel.@Nullable Stage displayStage() {
        return cockpit.spotlightStage();
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (followRect != null && followRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(CirrusLangs.Execution.Cockpit.spotlightFollow.get())
                    .badge("SYNC", OreUiRichTooltip.Tone.accent)
                    .text(CirrusLangs.Execution.Cockpit.spotlightTipFollow.get(), OreUiRichTooltip.Tone.secondary)
                    .build();
        }
        if (headerRect != null && headerRect.contains(localX, localY)) {
            return OreUiRichTooltip.builder(
                collapsed
                        ? CirrusLangs.Execution.Cockpit.spotlightTipExpand.get()
                        : CirrusLangs.Execution.Cockpit.spotlightTipFold.get()
            ).badge(collapsed ? "EXPAND" : "FOLD", OreUiRichTooltip.Tone.secondary).build();
        }
        for (SlotHit hit : slotHits) {
            if (hit.rect().contains(localX, localY)) {
                return buildSlotTooltip(hit.content(), hit.output());
            }
        }
        return null;
    }

    private @Nullable OreUiRichTooltip buildSlotTooltip(RecipeContent content, boolean output) {
        if (content.isEmpty()) {
            return null;
        }
        String name = ExecutionOverlayContentHelper.label(content);
        var preview = RecipeContentSlotWidget.previewStack(content);
        OreUiRichTooltip.Builder b = OreUiRichTooltip.builder(name);
        if (preview != null) {
            b.icon(preview);
        }
        b.badge(output ? "OUTPUT" : "INPUT", output ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent);
        if (content.amount() > 0L) {
            b.keyValue(
                CirrusLangs.Execution.Cockpit.matKvRequired.get(),
                Component.literal("\u00d7" + content.amount()),
                OreUiRichTooltip.Tone.primary
            );
        }
        if (!output) {
            OverlayMaterialRequirement req = cockpit.materialRequirementFor(content);
            if (req != null) {
                b.keyValue(
                    CirrusLangs.Execution.Cockpit.matKvOwned.get(),
                    Component.literal(String.valueOf(req.available())),
                    OreUiRichTooltip.Tone.positive
                );
                if (req.missing() > 0L) {
                    b.keyValue(
                        CirrusLangs.Execution.Cockpit.matKvMissing.get(),
                        Component.literal(String.valueOf(req.missing())),
                        OreUiRichTooltip.Tone.warning
                    );
                }
            }
        }
        if (preview != null && preview.isItem()) {
            var loc = preview.kind().getLocation();
            if (loc != null) {
                b.subtitle(loc.toString());
            }
        }
        return b.build();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int targetH = collapsed ? headerH : effectiveExpandedHeight();
        float animatedH = smoothHeight.update(targetH, 18.0f);
        applyHeightPx(Math.round(animatedH));
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        slotHits.clear();
        followRect = null;

        canvas.fill(0, 0, w, h, p.dockSurface());
        canvas.fill(0, h - 1, w, 1, OreUiPaint.cardDivider(p));

        ExecutionStageModel.Stage stage = displayStage();
        boolean inspectingOverride = cockpit.isInspectingDifferentStage();

        // Symmetric section header with left role accent and right fold chevron
        headerRect = new Rect(0, 0, w, headerH);
        boolean headerHov = headerRect.contains(mouseX, mouseY);
        OreUiPaint.headerBand(canvas, 0, 0, w, headerH, p, headerHov);

        Component chevComp = Component.literal(collapsed ? "\u25b8" : "\u25be");
        int chevW = ChromeText.width(canvas, chevComp);
        int chevX = w - pad - chevW;
        ChromeText.draw(
            canvas,
            chevComp,
            chevX,
            ChromeText.centeredY(0, headerH, canvas),
            headerHov ? p.textPrimary() : p.textSecondary()
        );
        int rightLimit = chevX - 6;
        if (inspectingOverride) {
            Component followComp = CirrusLangs.Execution.Cockpit.spotlightFollow.get();
            int fw = ChromeText.width(canvas, followComp) + 10;
            followRect = new Rect(rightLimit - fw, 3, fw, 14);
            boolean fHov = followRect.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                followRect.x(),
                followRect.y(),
                followRect.width(),
                followRect.height(),
                p,
                true,
                fHov,
                false,
                false
            );
            ChromeText.draw(
                canvas,
                followComp,
                followRect.x() + 5,
                ChromeText.centeredY(followRect.y(), followRect.height(), canvas),
                p.focusRing()
            );
            rightLimit = followRect.x() - 4;
        }

        if (stage != null) {
            Component stationComp = stage.stationLabel();
            int sw = ChromeText.width(canvas, stationComp) + 8;
            if (rightLimit - sw > 95) {
                OreUiPaint.tagPill(canvas, rightLimit - sw, 3, 13, stationComp, p.focusRing(), p);
                rightLimit -= sw + 4;
            }
        }

        Component headerTitle;
        if (stage == null) {
            headerTitle = CirrusLangs.Execution.Cockpit.spotlightTitle.get();
        } else if (inspectingOverride) {
            headerTitle = CirrusLangs.Execution.Cockpit.spotlightInspecting.get(stage.stageIndex() + 1).copy()
                    .append(Component.literal(" \u00b7 " + stage.title().getString()));
        } else {
            headerTitle = CirrusLangs.Execution.Cockpit.spotlightTitle.get().copy().append(
                Component.literal(" \u00b7 #" + (stage.stageIndex() + 1) + " " + stage.title().getString())
            );
        }
        ChromeText.drawFitted(
            canvas,
            headerTitle,
            pad,
            ChromeText.centeredY(0, headerH, canvas),
            Math.max(24, rightLimit - pad - 2),
            p.textPrimary()
        );

        if (h <= headerH + 4) {
            return;
        }

        int bodyY = headerH + 4;
        int bodyH = Math.max(0, h - bodyY - 4);
        if (stage == null || (stage.rawGridInputs().isEmpty() && stage.outputs().isEmpty())) {
            Component emptyMsg = CirrusLangs.Execution.Cockpit.spotlightNoGrid.get();
            int mw = ChromeText.width(canvas, emptyMsg);
            ChromeText.drawFitted(
                canvas,
                emptyMsg,
                Math.max(pad, (w - mw) / 2),
                ChromeText.centeredY(bodyY, bodyH, canvas),
                Math.max(24, w - pad * 2),
                p.textMuted()
            );
            return;
        }

        List<@Nullable RecipeContent> gridSlots = resolveConcreteGridSlots(stage);
        int cols = gridSlots.size() <= 3 ? Math.max(1, gridSlots.size()) : 3;
        int rows = Math.max(1, Math.min(3, (gridSlots.size() + cols - 1) / cols));
        if (gridSlots.size() > 3 && gridSlots.size() <= 9) {
            cols = 3;
            rows = 3;
        }

        int gridW = cols * slotStride + 1;
        int gridH = rows * slotStride + 1;
        int arrowW = 20;
        int outCount = Math.min(2, stage.outputs().size());
        int outW = outCount > 0 ? outCount * slotStride + 4 : 0;
        int totalVisualW = gridW + arrowW + outW + 10;

        boolean hasSideMeta = w - pad * 2 - totalVisualW >= 68;
        int startX = hasSideMeta ? pad + 2 : Math.max(pad, (w - totalVisualW) / 2);
        int gridY = bodyY + Math.max(0, (bodyH - gridH) / 2);

        // Recessed OreUI crafting well behind the concrete 3x3 / matrix grid
        OreUiPaint.searchField(canvas, startX - 2, gridY - 2, gridW + 4, gridH + 4, p, false);

        Map<String, OverlayMaterialRequirement> matMap = cockpit.materialMap();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                int sx = startX + col * slotStride;
                int sy = gridY + row * slotStride;
                OreUiPaint.slot(canvas, sx, sy, slotSize, p);
                RecipeContent slotContent = idx < gridSlots.size() ? gridSlots.get(idx) : null;
                if (slotContent != null && slotContent.notEmpty()) {
                    ExecutionOverlayContentHelper
                            .renderRecipeContent(canvas, slotContent, sx + 1, sy + 1, 16, 16, partialTicks);
                    if (slotContent.amount() > 1L) {
                        OreUiSlotAmount.drawInSlot(canvas, sx, sy, slotSize, slotContent.amount(), p);
                    }
                    // Live stock readiness ring on each concrete input slot
                    String key = ExecutionOverlayContentHelper.recipeContentKey(slotContent);
                    OverlayMaterialRequirement req = matMap.get(key);
                    if (req != null) {
                        int ringColor = req.missing() > 0L
                                ? OreUiPaint.withAlpha(p.ruleHardBan(), 0.72f)
                                : OreUiPaint.withAlpha(p.costS(), 0.50f);
                        canvas.strokeRect(sx + 1, sy + 1, slotSize - 2, slotSize - 2, ringColor, 1);
                    }
                    slotHits.add(new SlotHit(new Rect(sx, sy, slotSize, slotSize), slotContent, false));
                }
            }
        }

        // Sleek OreUI arrow + batch multiplier label above it
        int arrowX = startX + gridW + 5;
        int midY = gridY + gridH / 2;
        OreUiRecipeGrid.drawRecipeArrow(canvas, arrowX, arrowX + arrowW - 2, midY, p, !stage.isCompleted());
        if (stage.times() > 1) {
            Component mult = Component.literal("\u00d7" + stage.times());
            int mw = ChromeText.width(canvas, mult);
            ChromeText.draw(canvas, mult, arrowX + Math.max(0, (arrowW - mw) / 2), midY - 12, p.focusRing());
        }

        // Concrete output slot(s)
        int outX = arrowX + arrowW + 4;
        int outY = midY - slotSize / 2;
        for (int k = 0; k < outCount; k++) {
            RecipeContent out = stage.outputs().get(k);
            int sx = outX + k * (slotStride + 2);
            OreUiPaint.slot(canvas, sx, outY, slotSize, p);
            ExecutionOverlayContentHelper.renderRecipeContent(canvas, out, sx + 1, outY + 1, 16, 16, partialTicks);
            if (out.amount() > 1L) {
                OreUiSlotAmount.drawInSlot(canvas, sx, outY, slotSize, out.amount(), p);
            }
            canvas.strokeRect(sx + 1, outY + 1, slotSize - 2, slotSize - 2, OreUiPaint.withAlpha(p.costS(), 0.65f), 1);
            slotHits.add(new SlotHit(new Rect(sx, outY, slotSize, slotSize), out, true));
        }

        // Right-side stage meta readout when panel is wide enough
        if (hasSideMeta) {
            int metaX = outX + outW + 8;
            int metaW = Math.max(36, w - pad - metaX);
            int my = gridY + 2;
            ChromeText.drawFitted(canvas, stage.title(), metaX, my, metaW, p.textPrimary());
            my += 12;

            int shortInputs = cockpit.countMissingInputsForStage(stage);
            Component readiness = shortInputs > 0
                    ? CirrusLangs.Execution.Cockpit.stageShort.get(shortInputs)
                    : CirrusLangs.Execution.Cockpit.stageReady.get();
            OreUiPaint.tagPill(canvas, metaX, my, 13, readiness, shortInputs > 0 ? p.ruleHardBan() : p.costS(), p);
            my += 16;

            if (!stage.upstreamStages().isEmpty()) {
                String deps = formatStageList(stage.upstreamStages());
                ChromeText.drawFitted(
                    canvas,
                    CirrusLangs.Execution.Cockpit.stageDeps.get(deps),
                    metaX,
                    my,
                    metaW,
                    p.textMuted()
                );
                my += 11;
            }
            if (stage.phaseHint() != null) {
                ChromeText.drawFitted(
                    canvas,
                    stage.phaseHint(),
                    metaX,
                    my,
                    metaW,
                    stage.status() == ExecutionStageModel.StageStatus.paused ? p.costH() : p.focusRing()
                );
            }
        }
    }

    private List<@Nullable RecipeContent> resolveConcreteGridSlots(ExecutionStageModel.Stage stage) {
        int rawRenderable = 0;
        for (RecipeContent c : stage.rawGridInputs()) {
            if (c.notEmpty() && ExecutionOverlayContentHelper.hasRenderableVisual(c)) {
                rawRenderable++;
            }
        }
        // Prefer exact 3x3 spatial input grid from RecipeActiveContext only when it has renderable slots matching this stage.
        RecipeActiveContext ctx = cockpit.activeRecipeContext();
        if (ctx != null
                && stage.recipe() != null
                && stage.recipe().equals(ctx.recipe())
                && ctx.displayInputGrid() != null
                && !ctx.displayInputGrid().isEmpty()) {
            List<@Nullable RecipeContent> out = new ArrayList<>(ctx.displayInputGrid().size());
            int ctxRenderable = 0;
            for (ProcessNode.MaterialSlot slot : ctx.displayInputGrid()) {
                RecipeContent c = slot == null ? null : slot.content();
                if (c != null && c.notEmpty() && ExecutionOverlayContentHelper.hasRenderableVisual(c)) {
                    ctxRenderable++;
                }
                out.add(c);
            }
            if (ctxRenderable > 0 && ctxRenderable >= rawRenderable) {
                return out;
            }
        }
        List<@Nullable RecipeContent> raw = new ArrayList<>(stage.rawGridInputs().size());
        raw.addAll(stage.rawGridInputs());
        return raw;
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
