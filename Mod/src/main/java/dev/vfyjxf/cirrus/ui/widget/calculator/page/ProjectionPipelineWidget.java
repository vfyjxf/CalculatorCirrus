package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.display.ProjectionDisplayModel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagEdgeRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Compact OreUI visualization of the GAS recipe projection pipeline:
 * {@code Base → Prepare → Transform → Finalize → Validate → Output},
 * paired with address-tagged per-plugin diff chips driven directly by {@link ProjectionDisplayModel}.
 */
final class ProjectionPipelineWidget extends Widget implements OreUiTooltipProvider {

    private static final int pad = 8;
    private static final int titleRowHeight = 16;
    private static final int stageCardHeight = 56;
    private static final int stageHeaderHeight = 15;
    private static final int minStageWidth = 66;
    private static final int maxStageWidth = 104;
    private static final int arrowGap = 16;
    private static final int tapHeight = 14;
    private static final int compactChainHeight = 26;
    private static final int richChainHeight = 58;

    private enum StageIcon {
        original, prepare, transform, finalizeStage, validate, projected
    }

    private final @Nullable RecipeProjection original;
    private final @Nullable ProjectionResult result;
    private final @Nullable ProjectionDisplayModel displayModel;
    private final List<ProjectionPlugin> plugins;
    private final @Nullable Namespace selectedPluginId;
    private final Predicate<ProjectionPlugin> pluginEnabled;
    private final Consumer<Namespace> onSelectPlugin;
    private final boolean hasVisualEffects;
    private final List<PluginHit> pluginHits = new ArrayList<>();
    private final List<StageHit> stageHits = new ArrayList<>();

    private record PluginHit(Rect rect, ProjectionPlugin plugin, int changeCount, boolean enabled) {}

    private record StageHit(Rect rect, StageSpec spec) {}

    private record MiniSlotDraw(RecipeContent content, int x, int y, int slotSize) {}

    ProjectionPipelineWidget(
        @Nullable RecipeProjection original,
        @Nullable ProjectionResult result,
        List<ProjectionPlugin> plugins,
        @Nullable ProjectionPlugin selectedPlugin,
        Predicate<ProjectionPlugin> pluginEnabled,
        Consumer<Namespace> onSelectPlugin
    ) {
        this.original = original;
        this.result = result;
        this.displayModel = result == null ? null : result.toDisplayModel();
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
        this.selectedPluginId = selectedPlugin == null ? null : selectedPlugin.id();
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        this.onSelectPlugin = Objects.requireNonNull(onSelectPlugin, "onSelectPlugin");
        this.hasVisualEffects = hasAnyPluginChanges();
        int chainH = this.plugins.isEmpty() ? 0 : tapHeight + (hasVisualEffects ? richChainHeight : compactChainHeight);
        int totalHeight = pad * 2 + titleRowHeight + stageCardHeight + chainH;
        useStyle(heightOf(totalHeight), minWidth(0), flexShrink(0));
        setInteractive(true);
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            for (PluginHit hit : pluginHits) {
                if (hit.rect().contains(local.x(), local.y())) {
                    this.onSelectPlugin.accept(hit.plugin().id());
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
    }

    private boolean hasAnyPluginChanges() {
        if (result == null || plugins.isEmpty()) {
            return false;
        }
        for (ProjectionPlugin plugin : plugins) {
            if (result.pluginSummary(plugin).hasChanges()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (PluginHit hit : pluginHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                return OreUiRichTooltip.builder(Component.translatable(hit.plugin().labelKey()))
                        .badge(
                            hit.enabled() ? "ACTIVE +" + hit.changeCount() : "DISABLED",
                            hit.enabled() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.muted
                        ).subtitle(hit.plugin().id().toString())
                        .keyValue("Changes Applied", String.valueOf(hit.changeCount()), OreUiRichTooltip.Tone.accent)
                        .footer("Click to inspect & configure plugin parameters").build();
            }
        }
        for (StageHit hit : stageHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                StageSpec spec = hit.spec();
                OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(spec.title())
                        .badge(spec.active() ? "ACTIVE" : "PASS", spec.accent())
                        .keyValue(Component.literal("Stage Summary"), spec.detail(), spec.accent());
                if (spec.stageView() != null && !spec.stageView().slotDiffs().isEmpty()) {
                    for (SlotDiff diff : spec.stageView().slotDiffs()) {
                        String badge = diff.deltaBadge().isEmpty() ? diff.status().name() : diff.deltaBadge();
                        builder.keyValue(
                            diff.channel().name().toUpperCase() + " " + diff.address().shortLabel(),
                            badge,
                            OreUiRichTooltip.Tone.accent
                        );
                    }
                } else {
                    builder.text(
                        spec.active()
                                ? "Active pipeline stage modifying or validating this projection."
                                : "Pass-through stage with no transforms under current context.",
                        OreUiRichTooltip.Tone.secondary
                    );
                }
                return builder.build();
            }
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        pluginHits.clear();
        stageHits.clear();
        CirrusPalette palette = CirrusUiTheme.palette(this);

        OreUiPaint.searchField(canvas, 0, 0, width, height, palette, false);
        renderHeaderStrip(canvas, palette, width);

        int stageY = pad + titleRowHeight;
        int stageCount = 6;
        int totalArrows = (stageCount - 1) * arrowGap;
        int availW = Math.max(stageCount * 46 + totalArrows, width - pad * 2);
        int stageW = Math.max(minStageWidth, Math.min(maxStageWidth, (availW - totalArrows) / stageCount));
        if (stageW * stageCount + totalArrows > width - pad * 2) {
            stageW = Math.max(42, (width - pad * 2 - totalArrows) / stageCount);
        }
        int totalFlowW = stageW * stageCount + totalArrows;
        int startX = pad + Math.max(0, (width - pad * 2 - totalFlowW) / 2);

        int baseIn = original == null ? 0 : original.inputs().size();
        int baseOut = original == null ? 0 : original.outputs().size();
        RecipeProjection finalProj = result == null ? original : result.projected();
        int outIn = finalProj == null ? 0 : finalProj.inputs().size();
        int outOut = finalProj == null ? 0 : finalProj.outputs().size();
        boolean runnable = result == null || result.runnable();

        ProjectionDisplayModel.StageView prepView = stageView(ProjectionStage.prepare);
        ProjectionDisplayModel.StageView txView = stageView(ProjectionStage.transform);
        ProjectionDisplayModel.StageView finView = stageView(ProjectionStage.finalize);
        ProjectionDisplayModel.StageView valView = stageView(ProjectionStage.validate);

        StageSpec[] stages = {
                new StageSpec(
                    StageIcon.original,
                    CirrusLangs.Calculator.Database.pipelineBase.component(),
                    Component.literal(baseIn + " in \u2192 " + baseOut + " out"),
                    palette.textSecondary(),
                    true,
                    original,
                    null
                ),
                buildStageSpec(StageIcon.prepare, Component.literal("Prepare"), prepView, palette.costP(), palette),
                buildStageSpec(
                    StageIcon.transform,
                    Component.literal("Transform"),
                    txView,
                    palette.craftStep().accent(),
                    palette
                ),
                buildStageSpec(
                    StageIcon.finalizeStage,
                    Component.literal("Finalize"),
                    finView,
                    palette.convertStep().accent(),
                    palette
                ),
                buildValidateSpec(valView, runnable, palette),
                new StageSpec(
                    StageIcon.projected,
                    CirrusLangs.Calculator.Database.pipelineOutput.component(),
                    Component.literal(outIn + " in \u2192 " + outOut + " out"),
                    runnable ? palette.costS() : palette.ruleHardBan(),
                    true,
                    finalProj,
                    null
                )};

        int[] stageCentersX = new int[stageCount];
        List<MiniSlotDraw> pendingSlots = new ArrayList<>();
        int cursorX = startX;
        for (int index = 0; index < stages.length; index++) {
            StageSpec spec = stages[index];
            Rect cardRect = new Rect(cursorX, stageY, stageW, stageCardHeight);
            stageCentersX[index] = cursorX + stageW / 2;
            boolean hovered = cardRect.contains(mouseX, mouseY);
            drawStageCard(canvas, cardRect, spec, palette, hovered, pendingSlots);
            stageHits.add(new StageHit(cardRect, spec));
            if (index < stages.length - 1) {
                int arrowX = cursorX + stageW;
                int arrowY = stageY + stageCardHeight / 2;
                StageSpec next = stages[index + 1];
                boolean flowActive = runnable && (spec.active() || next.active());
                int wireColor = !runnable
                        ? palette.ruleHardBan()
                        : next.active() && index + 1 < stages.length - 1
                                ? next.accent()
                                : (spec.active() && index > 0 ? spec.accent() : palette.focusRing());
                drawPipelineConduit(canvas, arrowX, arrowY, arrowGap, wireColor, flowActive, palette);
            }
            cursorX += stageW + arrowGap;
        }

        if (!plugins.isEmpty()) {
            int chainY = stageY + stageCardHeight + tapHeight;
            renderDecoratorChain(
                canvas,
                palette,
                startX,
                chainY,
                totalFlowW,
                stageY + stageCardHeight,
                stageCentersX,
                stages,
                mouseX,
                mouseY,
                pendingSlots
            );
        }

        renderPendingMiniSlots(canvas, pendingSlots, palette, partialTicks);
    }

    private StageSpec buildStageSpec(
        StageIcon icon,
        Component title,
        ProjectionDisplayModel.@Nullable StageView view,
        int activeAccent,
        CirrusPalette palette
    ) {
        if (view == null || !view.active()) {
            return new StageSpec(
                icon,
                title,
                CirrusLangs.Calculator.Database.pipelinePassThrough.component(),
                palette.textMuted(),
                false,
                null,
                view
            );
        }
        if (view.blocked()) {
            return new StageSpec(
                icon,
                title,
                CirrusLangs.Calculator.Database.pipelineBlocked.component(),
                palette.ruleHardBan(),
                true,
                null,
                view
            );
        }
        return new StageSpec(
            icon,
            title,
            stageSummary(view.changeCount()),
            activeAccent,
            true,
            null,
            view
        );
    }

    private StageSpec buildValidateSpec(
        ProjectionDisplayModel.@Nullable StageView valView,
        boolean runnable,
        CirrusPalette palette
    ) {
        int guardFailures = result == null ? 0 : result.guardFindings().size();
        int changes = valView == null ? 0 : valView.changeCount();
        boolean active = !runnable || guardFailures > 0 || changes > 0;
        Component detail = !runnable
                ? CirrusLangs.Calculator.Database.pipelineBlocked.component()
                : guardFailures > 0
                        ? Component.literal(guardFailures + " guard")
                        : stageSummary(changes);
        int accent = !runnable
                ? palette.ruleHardBan()
                : active ? palette.costH() : palette.textMuted();
        return new StageSpec(StageIcon.validate, Component.literal("Validate"), detail, accent, active, null, valView);
    }

    private ProjectionDisplayModel.@Nullable StageView stageView(ProjectionStage stage) {
        if (displayModel == null) {
            return null;
        }
        for (ProjectionDisplayModel.StageView sv : displayModel.stages()) {
            if (sv.stage() == stage) {
                return sv;
            }
        }
        return null;
    }

    private void renderHeaderStrip(SceneCanvas canvas, CirrusPalette palette, int width) {
        int y = pad - 1;
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Calculator.Database.pipelineTitle.component(),
            pad,
            y + 2,
            Math.max(40, width - 160),
            palette.textPrimary()
        );
        int rightX = width - pad;
        boolean runnable = result == null || result.runnable();
        Component statusComp = runnable ? Component.literal("RUNNABLE") : Component.literal("BLOCKED");
        int statusW = ChromeText.width(canvas, statusComp) + 10;
        OreUiPaint.tagPill(
            canvas,
            rightX - statusW,
            y,
            OreUiPaint.minPillHeight,
            statusComp,
            runnable ? palette.costS() : palette.ruleHardBan(),
            palette
        );
        rightX -= statusW + 5;

        int totalChanges = result == null ? 0 : result.allChanges().size();
        Component effectsComp = Component.literal(totalChanges == 0 ? "No edits" : "+" + totalChanges + " changes");
        int effectsW = ChromeText.width(canvas, effectsComp) + 10;
        if (rightX - effectsW > pad + 90) {
            OreUiPaint.tagPill(
                canvas,
                rightX - effectsW,
                y,
                OreUiPaint.minPillHeight,
                effectsComp,
                totalChanges > 0 ? palette.focusRing() : palette.textMuted(),
                palette
            );
        }
    }

    private void drawStageCard(
        SceneCanvas canvas,
        Rect rect,
        StageSpec spec,
        CirrusPalette palette,
        boolean hovered,
        List<MiniSlotDraw> pendingSlots
    ) {
        int x = rect.x();
        int y = rect.y();
        int w = rect.width();
        int h = rect.height();
        if (spec.active()) {
            OreUiPaint.raisedCard(canvas, x, y, w, h, palette, hovered, false);
        } else {
            canvas.fill(x, y, w, h, palette.recessedSurface());
            canvas.strokeRect(x, y, w, h, hovered ? palette.focusRing() : OreUiPaint.cardDivider(palette), 1);
        }
        OreUiPaint.headerBand(canvas, x, y, w, stageHeaderHeight, palette, false);
        drawStageGlyph(canvas, spec.icon(), x + 5, y + 4, spec.accent());
        ChromeText.drawFitted(
            canvas,
            spec.title(),
            x + 15,
            ChromeText.centeredY(y + 1, stageHeaderHeight, canvas),
            Math.max(8, w - 19),
            spec.active() ? spec.accent() : palette.textMuted()
        );

        if (spec.previewProjection() != null) {
            drawMiniProjectionStrip(
                canvas,
                spec.previewProjection(),
                x + 5,
                y + stageHeaderHeight + 4,
                w - 10,
                palette,
                pendingSlots
            );
            ChromeText.drawFitted(canvas, spec.detail(), x + 5, y + h - 13, Math.max(8, w - 10), spec.accent());
            return;
        }

        if (spec.stageView() != null && !spec.stageView().slotDiffs().isEmpty()) {
            SlotDiff sample = spec.stageView().slotDiffs().getFirst();
            drawMiniDiffSample(canvas, sample, x + 5, y + stageHeaderHeight + 4, w - 10, palette, pendingSlots);
            ChromeText.drawFitted(canvas, spec.detail(), x + 5, y + h - 13, Math.max(8, w - 10), spec.accent());
            return;
        }

        Component pillText = spec.detail();
        int pillY = y + stageHeaderHeight + Math.max(4, (h - stageHeaderHeight - OreUiPaint.minPillHeight) / 2);
        if (spec.active()) {
            OreUiPaint.tagPill(canvas, x + 5, pillY, OreUiPaint.minPillHeight, pillText, spec.accent(), palette);
        } else {
            ChromeText.drawFitted(canvas, pillText, x + 6, pillY + 2, Math.max(8, w - 12), palette.textMuted());
        }
    }

    private static void drawStageGlyph(SceneCanvas canvas, StageIcon icon, int x, int y, int color) {
        switch (icon) {
            case original -> {
                canvas.strokeRect(x, y + 1, 7, 6, color, 1);
                canvas.fill(x + 2, y + 3, 3, 2, color);
            }
            case prepare -> {
                canvas.fill(x + 3, y, 1, 7, color);
                canvas.fill(x + 2, y + 1, 3, 5, color);
                canvas.fill(x + 1, y + 2, 5, 3, color);
                canvas.fill(x, y + 3, 7, 1, color);
            }
            case transform -> {
                canvas.fill(x, y + 1, 5, 1, color);
                canvas.fill(x + 4, y, 1, 3, color);
                canvas.fill(x + 2, y + 5, 5, 1, color);
                canvas.fill(x + 2, y + 4, 1, 3, color);
            }
            case finalizeStage -> {
                canvas.fill(x, y + 1, 7, 1, color);
                canvas.fill(x + 1, y + 3, 5, 1, color);
                canvas.fill(x + 2, y + 5, 3, 1, color);
            }
            case validate -> {
                canvas.fill(x + 1, y, 5, 1, color);
                canvas.fill(x, y + 1, 7, 3, color);
                canvas.fill(x + 1, y + 4, 5, 2, color);
                canvas.fill(x + 2, y + 6, 3, 1, color);
            }
            case projected -> {
                canvas.fill(x, y + 2, 5, 4, color);
                canvas.fill(x + 1, y + 1, 5, 4, color);
                canvas.fill(x + 5, y + 2, 2, 3, color);
            }
        }
    }

    private void drawMiniDiffSample(
        SceneCanvas canvas,
        SlotDiff diff,
        int x,
        int y,
        int maxW,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        int slotDim = 18;
        int cx = x;
        if (diff.before() != null && diff.after() != null) {
            OreUiPaint.slot(canvas, cx, y, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(diff.before(), cx, y, slotDim));
            cx += slotDim + 2;
            if (cx + 10 + slotDim <= x + maxW + 1) {
                int midY = y + slotDim / 2;
                OreUiRecipeGrid.drawRecipeArrow(canvas, cx, cx + 8, midY, palette, true);
                cx += 10;
                OreUiPaint.slot(canvas, cx, y, slotDim, palette);
                pendingSlots.add(new MiniSlotDraw(diff.after(), cx, y, slotDim));
            }
        } else if (diff.after() != null) {
            OreUiPaint.tagPill(canvas, cx, y + 3, 12, Component.literal("+"), palette.diffAdded(), palette);
            cx += 16;
            if (cx + slotDim <= x + maxW + 1) {
                OreUiPaint.slot(canvas, cx, y, slotDim, palette);
                pendingSlots.add(new MiniSlotDraw(diff.after(), cx, y, slotDim));
            }
        } else if (diff.before() != null) {
            OreUiPaint.slot(canvas, cx, y, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(diff.before(), cx, y, slotDim));
            cx += slotDim + 3;
            if (cx + 14 <= x + maxW + 1) {
                OreUiPaint.tagPill(canvas, cx, y + 3, 12, Component.literal("\u00d7"), palette.diffRemoved(), palette);
            }
        }
    }

    private void drawMiniProjectionStrip(
        SceneCanvas canvas,
        RecipeProjection projection,
        int x,
        int y,
        int maxW,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        int slotDim = 18;
        int cursor = x;
        int limit = x + maxW;

        int inLimit = Math.min(2, projection.inputs().size());
        for (int i = 0; i < inLimit; i++) {
            if (cursor + slotDim > limit - 24) {
                break;
            }
            ProjectionSlot<InputContent> slot = projection.inputs().get(i);
            RecipeContent content = slot.content();
            OreUiPaint.slot(canvas, cursor, y, slotDim, palette);
            if (!content.isEmpty()) {
                pendingSlots.add(new MiniSlotDraw(content, cursor, y, slotDim));
            }
            cursor += slotDim - 1;
        }
        if (inLimit > 0) {
            cursor += 2;
        }
        if (cursor + 10 < limit - slotDim) {
            int arrowMidY = y + slotDim / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, cursor, cursor + 8, arrowMidY, palette, false);
            cursor += 10;
        }
        int outLimit = Math.min(2, projection.outputs().size());
        for (int i = 0; i < outLimit; i++) {
            if (cursor + slotDim > limit + 1) {
                break;
            }
            ProjectionSlot<OutputContent> slot = projection.outputs().get(i);
            RecipeContent content = slot.content();
            OreUiPaint.slot(canvas, cursor, y, slotDim, palette);
            if (!content.isEmpty()) {
                pendingSlots.add(new MiniSlotDraw(content, cursor, y, slotDim));
            }
            cursor += slotDim - 1;
        }
    }

    @SuppressWarnings("unchecked")
    private static void renderPendingMiniSlots(
        SceneCanvas canvas,
        List<MiniSlotDraw> pendingSlots,
        CirrusPalette palette,
        float partialTicks
    ) {
        if (pendingSlots.isEmpty()) {
            return;
        }
        GuiGraphics graphics = canvas.layeredGraphics();
        Font font = canvas.font();
        for (MiniSlotDraw slot : pendingSlots) {
            RecipeContent content = slot.content();
            try {
                RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer
                        .getRenderer(content);
                int count = renderer.visualCount(content);
                if (count > 0) {
                    int visualIndex = ExecutionOverlayContentHelper.currentVisualIndex(content) % count;
                    int inner = Math.max(12, slot.slotSize() - 2);
                    int padPx = Math.max(1, (slot.slotSize() - inner) / 2);
                    renderer.renderAt(
                        graphics,
                        content,
                        visualIndex,
                        slot.x() + padPx,
                        slot.y() + padPx,
                        inner,
                        inner,
                        partialTicks
                    );
                    drawScaledSlotAmount(
                        font,
                        graphics,
                        slot.x(),
                        slot.y(),
                        slot.slotSize(),
                        content.amount(),
                        palette
                    );
                }
            } catch (RuntimeException ignored) {
                // Skip slot rendering in headless test mode.
            }
        }
    }

    private static void drawScaledSlotAmount(
        Font font,
        GuiGraphics graphics,
        int sx,
        int sy,
        int slotSize,
        long amount,
        CirrusPalette palette
    ) {
        String text = OreUiSlotAmount.format(amount);
        if (text.isEmpty()) {
            return;
        }
        int rawW = Math.max(1, font.width(text));
        float preferred = text.length() <= 2 ? 0.78f : (text.length() == 3 ? 0.66f : 0.56f);
        float scale = Math.max(0.48f, Math.min(preferred, (slotSize - 2.5f) / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8f * scale;
        float tx = sx + slotSize - 1.5f - scaledW;
        float ty = sy + slotSize - 1.5f - scaledH;
        int bgX = Math.max(sx + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(sy + 1, (int) Math.floor(ty - 0.5f));
        int bgW = Math.min(sx + slotSize - 1 - bgX, (int) Math.ceil(scaledW + 2.0f));
        int bgH = Math.min(sy + slotSize - 1 - bgY, (int) Math.ceil(scaledH + 1.5f));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 200f);
        if (bgW > 0 && bgH > 0) {
            graphics.fill(bgX, bgY, bgX + bgW, bgY + bgH, OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.66f));
        }
        int ink = amount >= 1_000_000L
                ? OreUiPaint.lighten(palette.focusRing(), 0.25f)
                : amount >= 1_000L ? OreUiPaint.lighten(palette.costH(), 0.25f) : palette.textOnAccent();
        graphics.pose().translate(tx, ty, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, ink, true);
        graphics.pose().popPose();
    }

    private void drawPipelineConduit(
        SceneCanvas canvas,
        int x,
        int centerY,
        int span,
        int color,
        boolean active,
        CirrusPalette palette
    ) {
        boolean dark = OreUiPaint.isDark(palette);
        int casing = dark ? OreUiPaint.outerInk(palette) : palette.cardSurface();
        int highlight = OreUiPaint.lighten(color, dark ? 0.45f : 0.30f);
        float startX = x + 1f;
        float endX = x + span - 1f;
        float dockX = endX - 1.2f;
        float ribbonEndX = Math.max(startX + 2f, dockX - 6.6f);

        canvas.fill(x - 1, centerY - 3, 4, 6, casing);
        canvas.fill(x, centerY - 2, 2, 4, color);

        if (active) {
            canvas.coloredQuad(
                startX, centerY - 2.2f, ribbonEndX, centerY - 2.2f,
                ribbonEndX, centerY + 2.2f, startX, centerY + 2.2f,
                casing, casing, casing, casing
            );
            canvas.coloredQuad(
                startX, centerY - 1.2f, ribbonEndX, centerY - 1.2f,
                ribbonEndX, centerY + 1.2f, startX, centerY + 1.2f,
                color, color, color, color
            );
            canvas.coloredQuad(
                startX, centerY - 0.5f, ribbonEndX, centerY - 0.5f,
                ribbonEndX, centerY + 0.2f, startX, centerY + 0.2f,
                highlight, highlight, highlight, highlight
            );
            if (!OreUiMotion.reducedMotion() && ribbonEndX - startX >= 4f) {
                float phase = OreUiMotion.cycle01(OreUiMotion.nowMs(), 1450L, (x % 97) / 97f, false);
                float px = OreUiMotion.lerp(startX, ribbonEndX, phase);
                float p0 = Math.max(startX, px - 4.0f);
                float p1 = Math.min(ribbonEndX, px + 1.5f);
                int pulseColor = OreUiPaint.withAlpha(OreUiPaint.lighten(color, 0.55f), 0.90f);
                canvas.coloredQuad(
                    p0, centerY - 1.3f, p1, centerY - 1.3f,
                    p1, centerY + 1.3f, p0, centerY + 1.3f,
                    pulseColor, pulseColor, pulseColor, pulseColor
                );
            }
        } else {
            for (double dx = startX + 1.0; dx < ribbonEndX; dx += 5.0) {
                float sx = (float) dx;
                float segEnd = (float) Math.min(ribbonEndX, dx + 3.0);
                canvas.coloredQuad(
                    sx, centerY - 1.5f, segEnd, centerY - 1.5f,
                    segEnd, centerY + 1.5f, sx, centerY + 1.5f,
                    casing, casing, casing, casing
                );
                canvas.coloredQuad(
                    sx, centerY - 0.8f, segEnd, centerY - 0.8f,
                    segEnd, centerY + 0.8f, sx, centerY + 0.8f,
                    color, color, color, color
                );
            }
        }

        DagEdgeRenderer.drawTriangleArrow(canvas, startX, centerY, dockX + 0.7f, centerY, 8.6f, 4.2f, casing);
        DagEdgeRenderer.drawTriangleArrow(canvas, startX, centerY, dockX, centerY, 7.4f, 3.5f, color);
    }

    private void renderDecoratorChain(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int maxWidth,
        int stageBottomY,
        int[] stageCentersX,
        StageSpec[] stages,
        int mouseX,
        int mouseY,
        List<MiniSlotDraw> pendingSlots
    ) {
        int tapMidY = stageBottomY + tapHeight / 2;
        int firstActiveCenter = -1;
        int lastActiveCenter = -1;
        int busColor = palette.focusRing();
        boolean reduced = OreUiMotion.reducedMotion();
        long nowMs = OreUiMotion.nowMs();
        for (int i = 1; i <= 4; i++) {
            if (stages[i].active()) {
                if (firstActiveCenter < 0) {
                    firstActiveCenter = stageCentersX[i];
                    busColor = stages[i].accent();
                }
                lastActiveCenter = stageCentersX[i];
                int tapLen = tapMidY - stageBottomY + 1;
                canvas.fill(stageCentersX[i] - 1, stageBottomY, 2, tapLen, stages[i].accent());
                if (!reduced && tapLen >= 4) {
                    float t = OreUiMotion.cycle01(nowMs, 1450L, i * 0.22f, false);
                    int py = stageBottomY + Math.round(t * (tapLen - 2));
                    canvas.fill(stageCentersX[i] - 1, py, 2, 3, OreUiPaint.lighten(stages[i].accent(), 0.50f));
                }
            }
        }
        int anchorStageX = firstActiveCenter >= 0 ? (firstActiveCenter + lastActiveCenter) / 2 : stageCentersX[2];
        if (firstActiveCenter >= 0 && lastActiveCenter > firstActiveCenter) {
            canvas.fill(firstActiveCenter, tapMidY, lastActiveCenter - firstActiveCenter + 1, 2, busColor);
        }

        Component prefix = CirrusLangs.Calculator.Database.pipelineDecoratorChain.component();
        int prefixW = ChromeText.width(canvas, prefix) + 8;
        int chainH = hasVisualEffects ? richChainHeight : compactChainHeight;
        ChromeText.drawFitted(
            canvas,
            prefix,
            x,
            hasVisualEffects ? y + 6 : ChromeText.centeredY(y, chainH, canvas),
            prefixW,
            palette.textMuted()
        );
        int cursor = x + prefixW;
        int limit = x + maxWidth;
        int availChainW = Math.max(96, limit - cursor);
        int firstChipCenterX = -1;

        for (int index = 0; index < plugins.size(); index++) {
            ProjectionPlugin plugin = plugins.get(index);
            boolean enabled = pluginEnabled.test(plugin);
            boolean selected = Objects.equals(plugin.id(), selectedPluginId);
            ProjectionResult.PluginSummary summary = result == null
                    ? ProjectionResult.PluginSummary.empty(plugin)
                    : result.pluginSummary(plugin);
            int changeCount = summary.totalChangeCount();
            List<ProjectionDisplayModel.ChangeRow> rows = result == null
                    ? List.of()
                    : result.toDisplayModel(ProjectionDisplayModel.Options.defaults.withFilterPlugin(plugin.id()))
                            .changeRows().castToList();

            Component name = Component.translatable(plugin.labelKey());
            Component countBadge = Component.literal("+" + changeCount);
            int badgeW = ChromeText.width(canvas, countBadge) + 8;
            int headerMinW = Math.max(104, 18 + ChromeText.width(canvas, name) + 8 + badgeW + 8);
            int arrowW = index > 0 ? 16 : 0;
            int nodeW;
            if (plugins.size() == 1 && !rows.isEmpty()) {
                nodeW = Math.max(headerMinW, availChainW);
            } else if (!rows.isEmpty()) {
                int perPlugin = Math
                        .max(headerMinW, (availChainW - arrowW * Math.max(0, plugins.size() - 1)) / plugins.size());
                nodeW = Math.min(perPlugin, Math.max(headerMinW, rows.size() * 102 + 14));
            } else {
                nodeW = Math.min(180, headerMinW);
            }
            if (cursor + arrowW + nodeW > limit + 1) {
                nodeW = Math.max(84, limit - cursor - arrowW);
                if (nodeW < 76) {
                    break;
                }
            }
            if (index > 0) {
                drawPipelineConduit(
                    canvas,
                    cursor,
                    y + chainH / 2,
                    arrowW,
                    enabled ? palette.focusRing() : palette.hairlineBorder(),
                    enabled,
                    palette
                );
                cursor += arrowW;
            }
            int nodeH = chainH - 2;
            int nodeY = y + 1;
            Rect rect = new Rect(cursor, nodeY, nodeW, nodeH);
            if (firstChipCenterX < 0) {
                firstChipCenterX = rect.x() + rect.width() / 2;
            }
            boolean hovered = rect.contains(mouseX, mouseY);
            if (selected) {
                OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hovered, true);
            } else {
                OreUiPaint.raisedButton(
                    canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, !enabled, hovered, false, false
                );
            }
            int headerH = hasVisualEffects ? 20 : nodeH;
            if (hasVisualEffects) {
                OreUiPaint.headerBand(canvas, rect.x(), rect.y(), rect.width(), headerH - 2, palette, selected);
            }
            int dotColor = enabled
                    ? (changeCount > 0 ? palette.costS() : palette.focusRing())
                    : palette.textDisabled();
            canvas.fill(rect.x() + 6, rect.y() + Math.max(3, (headerH - 2 - 5) / 2), 5, 5, dotColor);
            int badgeX = rect.right() - badgeW - 6;
            int badgeY = rect.y() + Math.max(2, (headerH - 2 - 11) / 2);
            int badgeColor = changeCount > 0 ? palette.focusRing() : palette.textMuted();
            canvas.fill(
                badgeX,
                badgeY,
                badgeW,
                11,
                OreUiPaint.withAlpha(badgeColor, OreUiPaint.isDark(palette) ? 0.22f : 0.12f)
            );
            canvas.strokeRect(badgeX, badgeY, badgeW, 11, OreUiPaint.withAlpha(badgeColor, 0.50f), 1);
            canvas.drawString(countBadge, badgeX + 4, ChromeText.centeredY(badgeY, 11, canvas), badgeColor, false);

            ChromeText.drawFitted(
                canvas,
                name,
                rect.x() + 15,
                ChromeText.centeredY(rect.y() + 1, headerH - 2, canvas),
                Math.max(10, badgeX - rect.x() - 19),
                enabled ? palette.textPrimary() : palette.textMuted()
            );

            if (hasVisualEffects) {
                renderDecoratorChangeStrip(
                    canvas,
                    rows,
                    rect.x() + 6,
                    rect.y() + headerH + 3,
                    Math.max(20, rect.width() - 12),
                    Math.max(20, rect.height() - headerH - 6),
                    palette,
                    pendingSlots
                );
            }

            pluginHits.add(new PluginHit(rect, plugin, changeCount, enabled));
            cursor += nodeW + 4;
        }

        if (firstChipCenterX >= 0) {
            int linkMinX = Math.min(anchorStageX, firstChipCenterX);
            int linkMaxX = Math.max(anchorStageX, firstChipCenterX);
            int linkColor = firstActiveCenter >= 0 ? busColor : palette.hairlineBorder();
            if (linkMaxX > linkMinX) {
                canvas.fill(linkMinX, tapMidY, linkMaxX - linkMinX + 2, 2, linkColor);
            }
            canvas.fill(firstChipCenterX - 1, tapMidY, 2, Math.max(2, y + 1 - tapMidY), linkColor);
        }
    }

    private void renderDecoratorChangeStrip(
        SceneCanvas canvas,
        List<ProjectionDisplayModel.ChangeRow> rows,
        int x,
        int y,
        int maxW,
        int height,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        if (rows.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                CirrusLangs.Calculator.Database.pipelinePassThrough.component(),
                x,
                ChromeText.centeredY(y, height, canvas),
                maxW,
                palette.textMuted()
            );
            return;
        }
        int slotDim = 18;
        int pillH = Math.min(height, 26);
        int pillY = y + Math.max(0, (height - pillH) / 2);
        int cx = x;
        int limit = x + maxW;
        int rendered = 0;

        for (int i = 0; i < rows.size(); i++) {
            ProjectionDisplayModel.ChangeRow row = rows.get(i);
            int remainingAfter = rows.size() - (i + 1);
            int overflowReserve = remainingAfter > 0 ? 34 : 0;
            int chipW = measureChangeChipWidth(canvas, row, slotDim);
            if (cx + chipW > limit - overflowReserve && rendered > 0) {
                break;
            }
            if (cx + chipW > limit) {
                chipW = Math.max(38, limit - cx);
            }
            drawChangeChip(canvas, row, cx, pillY, chipW, pillH, slotDim, palette, pendingSlots);
            cx += chipW + 5;
            rendered++;
        }

        int extra = rows.size() - rendered;
        if (extra > 0 && cx + 22 <= limit) {
            OreUiPaint.tagPill(
                canvas,
                cx,
                pillY + Math.max(0, (pillH - OreUiPaint.minPillHeight) / 2),
                OreUiPaint.minPillHeight,
                Component.literal("+" + extra),
                palette.textSecondary(),
                palette
            );
        }
    }

    private static String chipRoleTag(ProjectionDisplayModel.ChangeRow row) {
        if (row.isMeta()) {
            return "META";
        }
        String side = row.channel() == SlotChannel.input ? "IN" : "OUT";
        SlotAddress addr = row.address();
        if (addr instanceof SlotAddress.GridCell cell) {
            return side + " " + cell.shortLabel();
        }
        return side;
    }

    private static int measureChangeChipWidth(
        SceneCanvas canvas,
        ProjectionDisplayModel.ChangeRow row,
        int slotDim
    ) {
        if (row.isMeta()) {
            String text = row.metaSummary() == null ? "Meta" : row.metaSummary();
            return Math.min(140, ChromeText.width(canvas, Component.literal(text)) + 14);
        }
        int tagW = ChromeText.width(canvas, Component.literal(chipRoleTag(row))) + 6;
        if (row.before() != null && row.after() != null) {
            int deltaW = row.badge().isEmpty() ? 0 : ChromeText.width(canvas, Component.literal(row.badge())) + 10;
            return 5 + tagW + slotDim + 12 + slotDim + (deltaW > 0 ? 4 + deltaW : 0) + 5;
        }
        if (row.after() != null) {
            int badgeW = ChromeText.width(canvas, Component.literal(row.badge().isEmpty() ? "+1" : row.badge())) + 6;
            return 5 + tagW + 10 + slotDim + 4 + badgeW;
        }
        return 5 + tagW + slotDim + 14;
    }

    private static void drawChangeChip(
        SceneCanvas canvas,
        ProjectionDisplayModel.ChangeRow row,
        int x,
        int y,
        int w,
        int h,
        int slotDim,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        int accent = chipAccent(row, palette);
        canvas.fill(x, y, w, h, OreUiPaint.cardFooter(palette));
        canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(accent, 0.55f), 1);

        if (row.isMeta()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal(row.metaSummary() == null ? "Meta" : row.metaSummary()),
                x + 6,
                ChromeText.centeredY(y, h, canvas),
                Math.max(8, w - 12),
                palette.textSecondary()
            );
            return;
        }

        int cx = x + 4;
        int slotY = y + Math.max(1, (h - slotDim) / 2);
        Component role = Component.literal(chipRoleTag(row));
        int roleW = ChromeText.width(canvas, role);
        ChromeText.draw(
            canvas,
            role,
            cx,
            ChromeText.centeredY(y, h, canvas),
            row.channel() == SlotChannel.input ? palette.costP() : palette.costS()
        );
        cx += roleW + 4;

        if (row.before() != null && row.after() != null) {
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(row.before(), cx, slotY, slotDim));
            cx += slotDim + 2;
            int midY = slotY + slotDim / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, cx, cx + 9, midY, palette, true);
            cx += 10;
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(row.after(), cx, slotY, slotDim));
            cx += slotDim + 4;
            if (!row.badge().isEmpty() && cx + 10 <= x + w - 3) {
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(row.badge()),
                    cx,
                    ChromeText.centeredY(y, h, canvas),
                    Math.max(8, x + w - cx - 3),
                    accent
                );
            }
        } else if (row.after() != null) {
            ChromeText.draw(canvas, Component.literal("+"), cx, ChromeText.centeredY(y, h, canvas), accent);
            cx += 8;
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(row.after(), cx, slotY, slotDim));
            cx += slotDim + 4;
            String badge = row.badge().isEmpty() ? "+1" : row.badge();
            if (cx + 10 <= x + w - 3) {
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(badge),
                    cx,
                    ChromeText.centeredY(y, h, canvas),
                    Math.max(8, x + w - cx - 3),
                    accent
                );
            }
        } else if (row.before() != null) {
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(row.before(), cx, slotY, slotDim));
            cx += slotDim + 4;
            ChromeText.draw(
                canvas,
                Component.literal("\u00d7"),
                cx,
                ChromeText.centeredY(y, h, canvas),
                palette.diffRemoved()
            );
        }
    }

    private static int chipAccent(ProjectionDisplayModel.ChangeRow row, CirrusPalette palette) {
        if (row.isMeta()) {
            return palette.textSecondary();
        }
        return switch (row.status()) {
            case added -> palette.diffAdded();
            case removed -> palette.diffRemoved();
            case transformed -> palette.costH();
            case moved -> palette.costP();
            case adjusted -> palette.focusRing();
            case unchanged -> palette.textMuted();
        };
    }

    private Component stageSummary(int count) {
        if (count <= 0) {
            return CirrusLangs.Calculator.Database.pipelinePassThrough.component();
        }
        return CirrusLangs.Calculator.Database.pipelineChanges.component(count);
    }

    private record StageSpec(
        StageIcon icon,
        Component title,
        Component detail,
        int accent,
        boolean active,
        @Nullable RecipeProjection previewProjection,
        ProjectionDisplayModel.@Nullable StageView stageView
    ) {}
}
