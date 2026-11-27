package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectPhase;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionPluginReport;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

/**
 * Compact OreUI recipe projection transform pipeline with full stage names, graphical icons, and concrete per-decorator visual effects.
 */
final class ProjectionPipelineWidget extends Widget implements OreUiTooltipProvider {

    private static final int pad = 8;
    private static final int titleRowHeight = 16;
    private static final int stageCardHeight = 56;
    private static final int stageHeaderHeight = 15;
    private static final int minStageWidth = 78;
    private static final int maxStageWidth = 122;
    private static final int arrowGap = 22;
    private static final int tapHeight = 14;
    private static final int compactChainHeight = 26;
    private static final int richChainHeight = 58;

    private enum StageIcon {
        original, projection, content, resolve, projected
    }

    private final @Nullable RecipeProjection original;
    private final @Nullable ProjectionReport report;
    private final List<ProjectionPlugin> plugins;
    private final @Nullable Namespace selectedPluginId;
    private final Predicate<ProjectionPlugin> pluginEnabled;
    private final Consumer<Namespace> onSelectPlugin;
    private final boolean hasVisualEffects;
    private final List<PluginHit> pluginHits = new ArrayList<>();
    private final List<StageHit> stageHits = new ArrayList<>();

    private record PluginHit(Rect rect, ProjectionPlugin plugin, int effectCount, boolean enabled) {}

    private record StageHit(Rect rect, StageSpec spec) {}

    private record ConcreteEffect(
        boolean inputChannel,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        boolean schema
    ) {}

    private record MiniSlotDraw(RecipeContent content, int x, int y, int slotSize) {}

    ProjectionPipelineWidget(
        @Nullable RecipeProjection original,
        @Nullable ProjectionReport report,
        List<ProjectionPlugin> plugins,
        @Nullable ProjectionPlugin selectedPlugin,
        Predicate<ProjectionPlugin> pluginEnabled,
        Consumer<Namespace> onSelectPlugin
    ) {
        this.original = original;
        this.report = report;
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
        this.selectedPluginId = selectedPlugin == null ? null : selectedPlugin.id();
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        this.onSelectPlugin = Objects.requireNonNull(onSelectPlugin, "onSelectPlugin");
        this.hasVisualEffects = hasAnyPluginEffects();
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

    private boolean hasAnyPluginEffects() {
        if (report == null || plugins.isEmpty()) {
            return false;
        }
        for (ProjectionPlugin plugin : plugins) {
            if (report.pluginReport(plugin).effectCount() > 0) {
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
                            hit.enabled() ? "ACTIVE +" + hit.effectCount() : "DISABLED",
                            hit.enabled() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.muted
                        ).subtitle(hit.plugin().id().toString())
                        .keyValue("Transforms Applied", String.valueOf(hit.effectCount()), OreUiRichTooltip.Tone.accent)
                        .footer("Click to inspect & configure decorator parameters").build();
            }
        }
        for (StageHit hit : stageHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                StageSpec spec = hit.spec();
                return OreUiRichTooltip.builder(spec.title()).badge(spec.active() ? "ACTIVE" : "PASS", spec.accent())
                        .keyValue(Component.literal("Stage Output"), spec.detail(), spec.accent())
                        .text(
                            spec.active()
                                    ? "Active pipeline stage modifying or validating this projection."
                                    : "Pass-through stage with no transforms under current context.",
                            OreUiRichTooltip.Tone.secondary
                        ).build();
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
        int stageCount = 5;
        int totalArrows = (stageCount - 1) * arrowGap;
        int availW = Math.max(stageCount * 56 + totalArrows, width - pad * 2);
        int stageW = Math.max(minStageWidth, Math.min(maxStageWidth, (availW - totalArrows) / stageCount));
        if (stageW * stageCount + totalArrows > width - pad * 2) {
            stageW = Math.max(48, (width - pad * 2 - totalArrows) / stageCount);
        }
        int totalFlowW = stageW * stageCount + totalArrows;
        int startX = pad + Math.max(0, (width - pad * 2 - totalFlowW) / 2);

        int baseIn = original == null ? 0 : original.inputs().size();
        int baseOut = original == null ? 0 : original.outputs().size();
        RecipeProjection finalProj = report == null ? original : report.projection();
        int outIn = finalProj == null ? 0 : finalProj.inputs().size();
        int outOut = finalProj == null ? 0 : finalProj.outputs().size();
        int projCount = phaseCount(ProjectionEffectPhase.projectionTransformer);
        int contentCount = phaseCount(ProjectionEffectPhase.contentTransformer);
        int resolveCount = phaseCount(ProjectionEffectPhase.resolveEffect);
        boolean runnable = report == null || report.runnable();
        int guardsFailed = report == null ? 0 : report.guardFindings().size();

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
                new StageSpec(
                    StageIcon.projection,
                    CirrusLangs.Calculator.Database.Effect.projectionTransformer.component(),
                    stageSummary(projCount),
                    projCount > 0 ? palette.craftStep().accent() : palette.textMuted(),
                    projCount > 0,
                    null,
                    ProjectionEffectPhase.projectionTransformer
                ),
                new StageSpec(
                    StageIcon.content,
                    CirrusLangs.Calculator.Database.Effect.contentTransformer.component(),
                    stageSummary(contentCount),
                    contentCount > 0 ? palette.convertStep().accent() : palette.textMuted(),
                    contentCount > 0,
                    null,
                    ProjectionEffectPhase.contentTransformer
                ),
                new StageSpec(
                    StageIcon.resolve,
                    CirrusLangs.Calculator.Database.Effect.resolveEffect.component(),
                    !runnable
                            ? CirrusLangs.Calculator.Database.pipelineBlocked.component()
                            : guardsFailed > 0
                                    ? Component.literal(resolveCount + " \u00b7 " + guardsFailed + " guard")
                                    : stageSummary(resolveCount),
                    !runnable ? palette.ruleHardBan() : resolveCount > 0 ? palette.costH() : palette.textMuted(),
                    !runnable || resolveCount > 0,
                    null,
                    ProjectionEffectPhase.resolveEffect
                ),
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

        // Flush item sprites last so SceneCanvas textures never paint over GuiGraphics item draws.
        renderPendingMiniSlots(canvas, pendingSlots, palette, partialTicks);
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
        boolean runnable = report == null || report.runnable();
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

        int totalEffects = report == null ? 0 : report.effects().size();
        Component effectsComp = Component.literal(totalEffects == 0 ? "No edits" : "+" + totalEffects + " transforms");
        int effectsW = ChromeText.width(canvas, effectsComp) + 10;
        if (rightX - effectsW > pad + 90) {
            OreUiPaint.tagPill(
                canvas,
                rightX - effectsW,
                y,
                OreUiPaint.minPillHeight,
                effectsComp,
                totalEffects > 0 ? palette.focusRing() : palette.textMuted(),
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

        // Middle stage cards: show concrete micro-effect icons when active, or a crisp status pill.
        List<ConcreteEffect> phaseEffects = spec.phase() == null ? List.of() : collectPhaseEffects(spec.phase());
        if (!phaseEffects.isEmpty()) {
            ConcreteEffect first = phaseEffects.get(0);
            drawMiniEffectSample(canvas, first, x + 5, y + stageHeaderHeight + 4, w - 10, palette, pendingSlots);
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
            case projection -> {
                canvas.fill(x + 3, y, 1, 7, color);
                canvas.fill(x + 2, y + 1, 3, 5, color);
                canvas.fill(x + 1, y + 2, 5, 3, color);
                canvas.fill(x, y + 3, 7, 1, color);
            }
            case content -> {
                canvas.fill(x, y + 1, 5, 1, color);
                canvas.fill(x + 4, y, 1, 3, color);
                canvas.fill(x + 2, y + 5, 5, 1, color);
                canvas.fill(x + 2, y + 4, 1, 3, color);
            }
            case resolve -> {
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

    private void drawMiniEffectSample(
        SceneCanvas canvas,
        ConcreteEffect effect,
        int x,
        int y,
        int maxW,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        int slotDim = 18;
        int cx = x;
        if (effect.before() != null && effect.after() != null) {
            OreUiPaint.slot(canvas, cx, y, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(effect.before(), cx, y, slotDim));
            cx += slotDim + 2;
            if (cx + 12 + slotDim <= x + maxW + 1) {
                int midY = y + slotDim / 2;
                OreUiRecipeGrid.drawRecipeArrow(canvas, cx, cx + 10, midY, palette, true);
                cx += 12;
                OreUiPaint.slot(canvas, cx, y, slotDim, palette);
                pendingSlots.add(new MiniSlotDraw(effect.after(), cx, y, slotDim));
            }
        } else if (effect.after() != null) {
            int plusY = y + 3;
            OreUiPaint.tagPill(canvas, cx, plusY, 12, Component.literal("+"), palette.costS(), palette);
            cx += 16;
            if (cx + slotDim <= x + maxW + 1) {
                OreUiPaint.slot(canvas, cx, y, slotDim, palette);
                pendingSlots.add(new MiniSlotDraw(effect.after(), cx, y, slotDim));
            }
        } else if (effect.before() != null) {
            OreUiPaint.slot(canvas, cx, y, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(effect.before(), cx, y, slotDim));
            cx += slotDim + 3;
            if (cx + 14 <= x + maxW + 1) {
                OreUiPaint.tagPill(canvas, cx, y + 3, 12, Component.literal("\u00d7"), palette.ruleHardBan(), palette);
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
            if (cursor + slotDim > limit - 28) {
                break;
            }
            ClausedContent<InputContent> claused = projection.inputs().get(i);
            RecipeContent content = claused.content();
            OreUiPaint.slot(canvas, cursor, y, slotDim, palette);
            if (!content.isEmpty()) {
                pendingSlots.add(new MiniSlotDraw(content, cursor, y, slotDim));
            }
            cursor += slotDim - 1;
        }
        if (inLimit > 0) {
            cursor += 2;
        }

        if (cursor + 12 < limit - slotDim) {
            int arrowMidY = y + slotDim / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, cursor, cursor + 10, arrowMidY, palette, false);
            cursor += 12;
        }

        int outLimit = Math.min(2, projection.outputs().size());
        for (int i = 0; i < outLimit; i++) {
            if (cursor + slotDim > limit + 1) {
                break;
            }
            ClausedContent<OutputContent> claused = projection.outputs().get(i);
            RecipeContent content = claused.content();
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
                // Skip slot rendering when client content renderer is unavailable in headless mode.
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
        float dockX = endX - 1.5f;
        float ribbonEndX = Math.max(startX + 2f, dockX - 7.4f);

        canvas.fill(x - 1, centerY - 3, 4, 6, casing);
        canvas.fill(x, centerY - 2, 2, 4, color);

        if (active) {
            canvas.coloredQuad(
                startX,
                centerY - 2.2f,
                ribbonEndX,
                centerY - 2.2f,
                ribbonEndX,
                centerY + 2.2f,
                startX,
                centerY + 2.2f,
                casing,
                casing,
                casing,
                casing
            );
            canvas.coloredQuad(
                startX,
                centerY - 1.2f,
                ribbonEndX,
                centerY - 1.2f,
                ribbonEndX,
                centerY + 1.2f,
                startX,
                centerY + 1.2f,
                color,
                color,
                color,
                color
            );
            canvas.coloredQuad(
                startX,
                centerY - 0.5f,
                ribbonEndX,
                centerY - 0.5f,
                ribbonEndX,
                centerY + 0.2f,
                startX,
                centerY + 0.2f,
                highlight,
                highlight,
                highlight,
                highlight
            );
            if (!OreUiMotion.reducedMotion() && ribbonEndX - startX >= 4f) {
                float phase = OreUiMotion.cycle01(OreUiMotion.nowMs(), 1450L, (x % 97) / 97f, false);
                float px = OreUiMotion.lerp(startX, ribbonEndX, phase);
                float p0 = Math.max(startX, px - 4.5f);
                float p1 = Math.min(ribbonEndX, px + 1.5f);
                int pulseColor = OreUiPaint.withAlpha(OreUiPaint.lighten(color, 0.55f), 0.90f);
                canvas.coloredQuad(
                    p0,
                    centerY - 1.3f,
                    p1,
                    centerY - 1.3f,
                    p1,
                    centerY + 1.3f,
                    p0,
                    centerY + 1.3f,
                    pulseColor,
                    pulseColor,
                    pulseColor,
                    pulseColor
                );
            }
        } else {
            for (double dx = startX + 1.0; dx < ribbonEndX; dx += 5.0) {
                float sx = (float) dx;
                float segEnd = (float) Math.min(ribbonEndX, dx + 3.0);
                canvas.coloredQuad(
                    sx,
                    centerY - 1.5f,
                    segEnd,
                    centerY - 1.5f,
                    segEnd,
                    centerY + 1.5f,
                    sx,
                    centerY + 1.5f,
                    casing,
                    casing,
                    casing,
                    casing
                );
                canvas.coloredQuad(
                    sx,
                    centerY - 0.8f,
                    segEnd,
                    centerY - 0.8f,
                    segEnd,
                    centerY + 0.8f,
                    sx,
                    centerY + 0.8f,
                    color,
                    color,
                    color,
                    color
                );
            }
        }

        DagEdgeRenderer.drawTriangleArrow(canvas, startX, centerY, dockX + 0.7f, centerY, 9.6f, 4.6f, casing);
        DagEdgeRenderer.drawTriangleArrow(canvas, startX, centerY, dockX, centerY, 8.2f, 3.8f, color);
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
        for (int i = 1; i <= 3; i++) {
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
            ProjectionPluginReport pluginReport = report == null
                    ? ProjectionPluginReport.empty(plugin)
                    : report.pluginReport(plugin);
            int effectCount = pluginReport.effectCount();
            List<ConcreteEffect> concrete = collectPluginEffects(pluginReport);
            Component name = Component.translatable(plugin.labelKey());
            Component countBadge = Component.literal("+" + effectCount);
            int badgeW = ChromeText.width(canvas, countBadge) + 8;
            int headerMinW = Math.max(104, 18 + ChromeText.width(canvas, name) + 8 + badgeW + 8);
            int arrowW = index > 0 ? 18 : 0;
            int nodeW;
            if (plugins.size() == 1 && !concrete.isEmpty()) {
                nodeW = Math.max(headerMinW, availChainW);
            } else if (!concrete.isEmpty()) {
                int perPlugin = Math
                        .max(headerMinW, (availChainW - arrowW * Math.max(0, plugins.size() - 1)) / plugins.size());
                nodeW = Math.min(perPlugin, Math.max(headerMinW, concrete.size() * 94 + 14));
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
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    !enabled,
                    hovered,
                    false,
                    false
                );
            }
            int headerH = hasVisualEffects ? 20 : nodeH;
            if (hasVisualEffects) {
                OreUiPaint.headerBand(canvas, rect.x(), rect.y(), rect.width(), headerH - 2, palette, selected);
            }
            int dotColor = enabled ? (effectCount > 0 ? palette.costS() : palette.focusRing()) : palette.textDisabled();
            canvas.fill(rect.x() + 6, rect.y() + Math.max(3, (headerH - 2 - 5) / 2), 5, 5, dotColor);
            int badgeX = rect.right() - badgeW - 6;
            int badgeY = rect.y() + Math.max(2, (headerH - 2 - 11) / 2);
            int badgeColor = effectCount > 0 ? palette.focusRing() : palette.textMuted();
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

            // Concrete visual per-decorator effect chips directly inside the decorator card body.
            if (hasVisualEffects) {
                renderDecoratorEffectsStrip(
                    canvas,
                    concrete,
                    rect.x() + 6,
                    rect.y() + headerH + 3,
                    Math.max(20, rect.width() - 12),
                    Math.max(20, rect.height() - headerH - 6),
                    palette,
                    pendingSlots
                );
            }

            pluginHits.add(new PluginHit(rect, plugin, effectCount, enabled));
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

    private void renderDecoratorEffectsStrip(
        SceneCanvas canvas,
        List<ConcreteEffect> effects,
        int x,
        int y,
        int maxW,
        int height,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        if (effects.isEmpty()) {
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

        for (int i = 0; i < effects.size(); i++) {
            ConcreteEffect eff = effects.get(i);
            int remainingAfter = effects.size() - (i + 1);
            int overflowReserve = remainingAfter > 0 ? 34 : 0;
            int chipW = measureEffectChipWidth(canvas, eff, slotDim);
            if (cx + chipW > limit - overflowReserve && rendered > 0) {
                break;
            }
            if (cx + chipW > limit) {
                chipW = Math.max(34, limit - cx);
            }
            drawEffectChip(canvas, eff, cx, pillY, chipW, pillH, slotDim, palette, pendingSlots);
            cx += chipW + 5;
            rendered++;
        }

        int extra = effects.size() - rendered;
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

    private static int measureEffectChipWidth(SceneCanvas canvas, ConcreteEffect eff, int slotDim) {
        if (eff.schema()) {
            return ChromeText.width(canvas, Component.literal("Schema")) + 14;
        }
        String tag = eff.inputChannel() ? "IN" : "OUT";
        int tagW = ChromeText.width(canvas, Component.literal(tag)) + 6;
        if (eff.before() != null && eff.after() != null) {
            String delta = deltaBadgeText(eff.before(), eff.after());
            int deltaW = delta.isEmpty() ? 0 : ChromeText.width(canvas, Component.literal(delta)) + 10;
            return 5 + tagW + slotDim + 12 + slotDim + (deltaW > 0 ? 4 + deltaW : 0) + 5;
        }
        if (eff.after() != null) {
            String add = "+" + Math.max(1L, eff.after().amount());
            return 5 + tagW + 10 + slotDim + 4 + ChromeText.width(canvas, Component.literal(add)) + 6;
        }
        return 5 + tagW + slotDim + 14;
    }

    private static void drawEffectChip(
        SceneCanvas canvas,
        ConcreteEffect eff,
        int x,
        int y,
        int w,
        int h,
        int slotDim,
        CirrusPalette palette,
        List<MiniSlotDraw> pendingSlots
    ) {
        int accent = eff.schema()
                ? palette.textSecondary()
                : eff.before() == null
                        ? palette.costS()
                        : eff.after() == null ? palette.ruleHardBan() : palette.focusRing();
        canvas.fill(x, y, w, h, OreUiPaint.cardFooter(palette));
        canvas.strokeRect(x, y, w, h, OreUiPaint.withAlpha(accent, 0.55f), 1);

        if (eff.schema()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("Schema"),
                x + 6,
                ChromeText.centeredY(y, h, canvas),
                Math.max(8, w - 12),
                palette.textSecondary()
            );
            return;
        }

        int cx = x + 4;
        int slotY = y + Math.max(1, (h - slotDim) / 2);
        Component role = Component.literal(eff.inputChannel() ? "IN" : "OUT");
        int roleW = ChromeText.width(canvas, role);
        ChromeText.draw(
            canvas,
            role,
            cx,
            ChromeText.centeredY(y, h, canvas),
            eff.inputChannel() ? palette.costP() : palette.costS()
        );
        cx += roleW + 4;

        if (eff.before() != null && eff.after() != null) {
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(eff.before(), cx, slotY, slotDim));
            cx += slotDim + 2;
            int midY = slotY + slotDim / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, cx, cx + 9, midY, palette, true);
            cx += 10;
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(eff.after(), cx, slotY, slotDim));
            cx += slotDim + 4;
            String delta = deltaBadgeText(eff.before(), eff.after());
            if (!delta.isEmpty() && cx + 14 <= x + w - 3) {
                Component dc = Component.literal(delta);
                ChromeText.drawFitted(
                    canvas,
                    dc,
                    cx,
                    ChromeText.centeredY(y, h, canvas),
                    Math.max(8, x + w - cx - 3),
                    palette.focusRing()
                );
            }
        } else if (eff.after() != null) {
            ChromeText.draw(canvas, Component.literal("+"), cx, ChromeText.centeredY(y, h, canvas), palette.costS());
            cx += 8;
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(eff.after(), cx, slotY, slotDim));
            cx += slotDim + 4;
            Component addComp = Component.literal("+" + OreUiSlotAmount.format(Math.max(1L, eff.after().amount())));
            if (eff.after().amount() <= 1L) {
                addComp = Component.literal("+1");
            }
            if (cx + 10 <= x + w - 3) {
                ChromeText.drawFitted(
                    canvas,
                    addComp,
                    cx,
                    ChromeText.centeredY(y, h, canvas),
                    Math.max(8, x + w - cx - 3),
                    palette.costS()
                );
            }
        } else if (eff.before() != null) {
            OreUiPaint.slot(canvas, cx, slotY, slotDim, palette);
            pendingSlots.add(new MiniSlotDraw(eff.before(), cx, slotY, slotDim));
            cx += slotDim + 4;
            ChromeText.draw(
                canvas,
                Component.literal("\u00d7"),
                cx,
                ChromeText.centeredY(y, h, canvas),
                palette.ruleHardBan()
            );
        }
    }

    private static String deltaBadgeText(RecipeContent before, RecipeContent after) {
        long b = before.amount();
        long a = after.amount();
        if (b > 0L && a > 0L && a != b) {
            if (a > b && a % b == 0L) {
                return "\u00d7" + (a / b);
            }
            long diff = a - b;
            return (diff > 0L ? "+" : "") + diff;
        }
        return "";
    }

    private List<ConcreteEffect> collectPluginEffects(ProjectionPluginReport pluginReport) {
        return mergeEffects(pluginReport.effects());
    }

    private List<ConcreteEffect> collectPhaseEffects(ProjectionEffectPhase phase) {
        if (report == null) {
            return List.of();
        }
        List<ProjectionEffect> matching = new ArrayList<>();
        for (ProjectionEffect effect : report.effects()) {
            if (effect.source().phase() == phase) {
                matching.add(effect);
            }
        }
        return mergeEffects(matching);
    }

    private static List<ConcreteEffect> mergeEffects(@Nullable Iterable<ProjectionEffect> effects) {
        if (effects == null) {
            return List.of();
        }
        Map<String, ConcreteEffect> merged = new LinkedHashMap<>();
        List<ConcreteEffect> schemas = new ArrayList<>();
        for (ProjectionEffect effect : effects) {
            if (effect instanceof InputEffect in) {
                String key = "I:" + in.index();
                ConcreteEffect prev = merged.get(key);
                RecipeContent firstBefore = prev == null ? in.before() : prev.before();
                merged.put(key, new ConcreteEffect(true, firstBefore, in.after(), false));
            } else if (effect instanceof OutputEffect out) {
                String key = "O:" + out.index();
                ConcreteEffect prev = merged.get(key);
                RecipeContent firstBefore = prev == null ? out.before() : prev.before();
                merged.put(key, new ConcreteEffect(false, firstBefore, out.after(), false));
            } else {
                schemas.add(new ConcreteEffect(true, null, null, true));
            }
        }
        if (merged.isEmpty() && schemas.isEmpty()) {
            return List.of();
        }
        List<ConcreteEffect> out = new ArrayList<>(merged.size() + (schemas.isEmpty() ? 0 : 1));
        out.addAll(merged.values());
        if (!schemas.isEmpty()) {
            out.add(schemas.get(0));
        }
        return out;
    }

    private Component stageSummary(int count) {
        if (count <= 0) {
            return CirrusLangs.Calculator.Database.pipelinePassThrough.component();
        }
        return CirrusLangs.Calculator.Database.pipelineChanges.component(count);
    }

    private int phaseCount(ProjectionEffectPhase phase) {
        if (report == null) {
            return 0;
        }
        int count = 0;
        for (ProjectionEffect effect : report.effects()) {
            if (effect.source().phase() == phase) {
                count++;
            }
        }
        return count;
    }

    private record StageSpec(
        StageIcon icon,
        Component title,
        Component detail,
        int accent,
        boolean active,
        @Nullable RecipeProjection previewProjection,
        @Nullable ProjectionEffectPhase phase
    ) {}
}
