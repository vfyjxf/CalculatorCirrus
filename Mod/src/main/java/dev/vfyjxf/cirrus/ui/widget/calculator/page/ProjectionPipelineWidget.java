package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ChangeReason;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.PipelineStepTrace;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionChange;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionGuard;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionStage;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplate;
import dev.vfyjxf.cirrus.ui.workbench.projection.ProjectionDiffTemplates;
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
import org.eclipse.collections.api.factory.Lists;
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
 * Responsive vertical GAS recipe projection pipeline visualizer grouped by:
 * {@code Stage (Prepare → Transform → Finalize → Validate)} →
 * {@code Per-Effect Step Card (effect id, owning plugin badge, priority, guard status)} →
 * {@code Multi-line wrapped change rows (full addresses, item/fluid icons, readable before→after text)}.
 *
 * <p>Never clips long labels to a single line and never hides diffs behind {@code +N} overflow badges.
 */
final class ProjectionPipelineWidget extends Widget implements OreUiTooltipProvider {

    private static final int pad = 8;
    private static final int headerH = 18;
    private static final int endpointBarH = 22;
    private static final int sectionGap = 6;
    private static final int stageHeaderH = 19;
    private static final int collapsedStageH = 21;
    private static final int stageConduitH = 9;
    private static final int stageBodyPadX = 6;
    private static final int stageBodyPadY = 5;
    private static final int effectCardGap = 5;
    private static final int effectHeaderPadX = 6;
    private static final int effectHeaderPadY = 3;
    private static final int effectBodyPadX = 5;
    private static final int effectBodyPadY = 4;
    private static final int entryGapY = 4;
    private static final int entryPadX = 6;
    private static final int entryPadY = 4;
    private static final int textLineH = 10;
    private static final int slotDim = 18;
    private static final int visualSlotColW = 54;
    private static final int pluginSectionTopGap = 7;
    private static final int pluginChipH = 20;
    private static final int pluginChipGapX = 5;
    private static final int pluginChipGapY = 4;
    private static final int defaultPreferredWidth = 540;

    private final @Nullable RecipeProjection original;
    private final @Nullable ProjectionResult result;
    private final List<ProjectionPlugin> plugins;
    private final @Nullable Namespace selectedPluginId;
    private final Predicate<ProjectionPlugin> pluginEnabled;
    private final Consumer<@Nullable Namespace> onSelectPlugin;
    private final List<StageModel> stageModels;

    private int laidOutWidth = -1;
    private int currentHeight;
    private final List<PluginHit> pluginHits = new ArrayList<>();
    private final List<EffectHit> effectHits = new ArrayList<>();
    private final List<EntryHit> entryHits = new ArrayList<>();
    private final List<StageHit> stageHits = new ArrayList<>();

    private record PluginHit(Rect rect, @Nullable ProjectionPlugin plugin, int changeCount, boolean enabled) {}

    private record EffectHit(Rect rect, EffectCardModel card) {}

    private record EntryHit(Rect rect, EffectCardModel owner, StepEntry entry) {}

    private record StageHit(Rect rect, StageModel model) {}

    private record MiniSlotDraw(RecipeContent content, int x, int y, int slotSize) {}

    private sealed interface StepEntry {
        record SlotEntry(
            ProjectionDiffTemplate template,
            ProjectionDiffTemplate.DiffInput input,
            String headerText,
            String detailText,
            String badge
        ) implements StepEntry {
            SlotAddress address() {
                return input.address();
            }

            SlotDiff.DiffStatus status() {
                return input.status();
            }

            @Nullable
            RecipeContent before() {
                return input.before();
            }

            @Nullable
            RecipeContent after() {
                return input.after();
            }

            @Nullable
            SlotAddress movedFrom() {
                return input.movedFrom();
            }
        }

        record MetaEntry(String kindBadge, String text) implements StepEntry {}

        record GuardEntry(ProjectionGuard.Finding finding, String text) implements StepEntry {}
    }

    private record EffectCardModel(
        PipelineStepTrace trace,
        String displayName,
        String effectIdText,
        String pluginLabel,
        boolean blocked,
        boolean skippedByGuard,
        List<StepEntry> entries
    ) {}

    private record StageModel(
        ProjectionStage stage,
        String title,
        String subtitle,
        int changeCount,
        int guardFailureCount,
        boolean blocked,
        boolean active,
        List<EffectCardModel> effectCards
    ) {}

    ProjectionPipelineWidget(
        @Nullable RecipeProjection original,
        @Nullable ProjectionResult result,
        List<ProjectionPlugin> plugins,
        @Nullable ProjectionPlugin selectedPlugin,
        Predicate<ProjectionPlugin> pluginEnabled,
        Consumer<@Nullable Namespace> onSelectPlugin
    ) {
        this(original, result, plugins, selectedPlugin, pluginEnabled, onSelectPlugin, defaultPreferredWidth);
    }

    ProjectionPipelineWidget(
        @Nullable RecipeProjection original,
        @Nullable ProjectionResult result,
        List<ProjectionPlugin> plugins,
        @Nullable ProjectionPlugin selectedPlugin,
        Predicate<ProjectionPlugin> pluginEnabled,
        Consumer<@Nullable Namespace> onSelectPlugin,
        int preferredWidth
    ) {
        this.original = original;
        this.result = result;
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
        this.selectedPluginId = selectedPlugin == null ? null : selectedPlugin.id();
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        this.onSelectPlugin = Objects.requireNonNull(onSelectPlugin, "onSelectPlugin");
        this.stageModels = buildStageModels();

        int estimateWidth = Math.max(240, preferredWidth);
        this.currentHeight = computeHeight(null, estimateWidth);
        useStyle(heightOf(currentHeight), minWidth(0), flexShrink(0));
        setInteractive(true);
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            for (PluginHit hit : pluginHits) {
                if (hit.rect().contains(local.x(), local.y())) {
                    this.onSelectPlugin.accept(hit.plugin() == null ? null : hit.plugin().id());
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            for (EffectHit hit : effectHits) {
                if (hit.rect().contains(local.x(), local.y())) {
                    this.onSelectPlugin.accept(hit.card().trace().plugin().id());
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
    }

    int stageCount() {
        return stageModels.size();
    }

    int effectCardCount() {
        int total = 0;
        for (StageModel model : stageModels) {
            total += model.effectCards().size();
        }
        return total;
    }

    int visibleItemCount() {
        int total = 0;
        for (StageModel model : stageModels) {
            for (EffectCardModel card : model.effectCards()) {
                total += card.entries().size();
            }
        }
        return total;
    }

    int entryCountByTemplate(ProjectionDiffTemplate.Kind kind) {
        int total = 0;
        for (StageModel model : stageModels) {
            for (EffectCardModel card : model.effectCards()) {
                for (StepEntry entry : card.entries()) {
                    if (entry instanceof StepEntry.SlotEntry se && se.template().kind() == kind) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    int measuredHeight() {
        return currentHeight;
    }

    private List<StageModel> buildStageModels() {
        List<StageModel> out = new ArrayList<>(ProjectionStage.values().length);
        for (ProjectionStage stage : ProjectionStage.values()) {
            List<EffectCardModel> cards = new ArrayList<>();
            int stageChanges = 0;
            int stageGuardFailures = 0;
            boolean stageBlocked = false;

            if (result != null) {
                for (PipelineStepTrace step : result.stepsInStage(stage)) {
                    List<StepEntry> entries = new ArrayList<>();
                    int failedGuards = 0;
                    for (ProjectionGuard.Finding f : step.guardFindings()) {
                        if (!f.passed()) {
                            failedGuards++;
                            String reason = f.reasonCode().isEmpty() ? "guard_failed" : f.reasonCode();
                            entries.add(new StepEntry.GuardEntry(f, shortPath(f.guardId()) + " · " + reason));
                        }
                    }
                    for (ProjectionChange change : step.changes()) {
                        entries.add(toStepEntry(step, change));
                    }
                    if (entries.isEmpty() && !step.blocked()) {
                        continue;
                    }
                    stageChanges += step.changes().size();
                    stageGuardFailures += failedGuards;
                    if (step.blocked()) {
                        stageBlocked = true;
                    }
                    boolean skippedByGuard = !step.applied() && !step.blocked() && failedGuards > 0;
                    String pluginLabel = Component.translatable(step.plugin().labelKey()).getString();
                    cards.add(
                        new EffectCardModel(
                            step,
                            humanizeEffectName(step.effectId()),
                            step.effectId().toString(),
                            pluginLabel,
                            step.blocked(),
                            skippedByGuard,
                            List.copyOf(entries)
                        )
                    );
                }
            }

            boolean active = !cards.isEmpty() || stageBlocked || stageGuardFailures > 0;
            out.add(
                new StageModel(
                    stage,
                    stageTitle(stage),
                    stageSubtitle(stage),
                    stageChanges,
                    stageGuardFailures,
                    stageBlocked,
                    active,
                    List.copyOf(cards)
                )
            );
        }
        return List.copyOf(out);
    }

    private static StepEntry toStepEntry(PipelineStepTrace step, ProjectionChange change) {
        if (change instanceof ProjectionChange.SlotChange sc) {
            SlotDiff.DiffStatus status = switch (sc.reason()) {
                case added -> SlotDiff.DiffStatus.added;
                case removed -> SlotDiff.DiffStatus.removed;
                case moved -> SlotDiff.DiffStatus.moved;
                case transformed -> SlotDiff.DiffStatus.transformed;
                default -> SlotDiff.DiffStatus.adjusted;
            };
            SlotDiff synthetic = new SlotDiff(
                sc.address(),
                status,
                sc.before(),
                sc.after(),
                sc.movedFrom(),
                Lists.immutable.of(step.effectId()),
                Lists.immutable.of(step.plugin())
            );
            ProjectionDiffTemplate.DiffInput input = new ProjectionDiffTemplate.DiffInput(
                sc.address(),
                status,
                sc.before(),
                sc.after(),
                sc.movedFrom(),
                synthetic.deltaBadge()
            );
            ProjectionDiffTemplate template = ProjectionDiffTemplates.resolve(input);
            return new StepEntry.SlotEntry(
                template,
                input,
                template.formatHeader(input),
                template.formatDetail(input),
                template.formatBadge(input)
            );
        }
        if (change instanceof ProjectionChange.AttributeChange ac) {
            String target = ac.targetSlot() == null ? "Recipe" : ac.targetSlot().shortLabel();
            String before = ac.before() == null ? "(none)" : String.valueOf(ac.before());
            String after = ac.after() == null ? "(removed)" : String.valueOf(ac.after());
            return new StepEntry.MetaEntry(
                "ATTR",
                target + " · " + shortPath(ac.key().id()) + ": " + before + " \u2192 " + after
            );
        }
        ProjectionChange.TagChange tc = (ProjectionChange.TagChange) change;
        String target = tc.targetSlot() == null ? "Recipe" : tc.targetSlot().shortLabel();
        return new StepEntry.MetaEntry(
            "TAG",
            target + " · " + (tc.added() ? "+" : "-") + tc.tag()
        );
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        for (EntryHit hit : entryHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                return entryTooltip(hit.owner(), hit.entry());
            }
        }
        for (EffectHit hit : effectHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                EffectCardModel c = hit.card();
                OreUiRichTooltip.Tone tone = c.blocked()
                        ? OreUiRichTooltip.Tone.danger
                        : c.skippedByGuard() ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.accent;
                return OreUiRichTooltip.builder(Component.literal(c.displayName()))
                        .badge(
                            c.blocked() ? "BLOCKED" : c.skippedByGuard() ? "SKIPPED" : "PRIO " + c.trace().priority(),
                            tone
                        )
                        .subtitle(c.effectIdText())
                        .keyValue("Plugin", c.pluginLabel(), OreUiRichTooltip.Tone.secondary)
                        .keyValue("Stage", stageTitle(c.trace().stage()), OreUiRichTooltip.Tone.accent)
                        .keyValue("Changes", String.valueOf(c.trace().changes().size()), OreUiRichTooltip.Tone.positive)
                        .footer("Click to select plugin and inspect its parameters")
                        .build();
            }
        }
        for (PluginHit hit : pluginHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                ProjectionPlugin plugin = hit.plugin();
                if (plugin == null) {
                    return OreUiRichTooltip.builder(CirrusLangs.Calculator.Database.pipelineAllChanges.component())
                            .badge("ALL +" + hit.changeCount(), OreUiRichTooltip.Tone.accent)
                            .keyValue("Plugins", String.valueOf(plugins.size()), OreUiRichTooltip.Tone.secondary)
                            .keyValue("Total Changes", String.valueOf(hit.changeCount()), OreUiRichTooltip.Tone.positive)
                            .footer("Click to show combined changes from all plugins")
                            .build();
                }
                return OreUiRichTooltip.builder(Component.translatable(plugin.labelKey()))
                        .badge(
                            hit.enabled() ? "ACTIVE +" + hit.changeCount() : "DISABLED",
                            hit.enabled() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.muted
                        )
                        .subtitle(plugin.id().toString())
                        .keyValue("Changes Applied", String.valueOf(hit.changeCount()), OreUiRichTooltip.Tone.accent)
                        .footer("Click to filter changes to this plugin & configure parameters")
                        .build();
            }
        }
        for (StageHit hit : stageHits) {
            if (hit.rect().contains((int) localX, (int) localY)) {
                StageModel m = hit.model();
                OreUiRichTooltip.Tone tone = m.blocked()
                        ? OreUiRichTooltip.Tone.danger
                        : m.active() ? OreUiRichTooltip.Tone.accent : OreUiRichTooltip.Tone.muted;
                return OreUiRichTooltip.builder(Component.literal(m.title()))
                        .badge(m.blocked() ? "BLOCKED" : m.active() ? "ACTIVE" : "PASS", tone)
                        .subtitle(m.subtitle())
                        .keyValue("Active Effects", String.valueOf(m.effectCards().size()), OreUiRichTooltip.Tone.accent)
                        .keyValue("Total Changes", String.valueOf(m.changeCount()), OreUiRichTooltip.Tone.secondary)
                        .build();
            }
        }
        return null;
    }

    private OreUiRichTooltip entryTooltip(EffectCardModel owner, StepEntry entry) {
        return switch (entry) {
            case StepEntry.SlotEntry se -> {
                RecipeContent primary = se.after() != null ? se.after() : se.before();
                Component title = primary == null
                        ? Component.literal(se.headerText())
                        : ProjectionDiffTemplates.contentTitle(primary);
                OreUiRichTooltip.Tone tone = statusTone(se.status());
                String badgeText = se.status().name().toUpperCase() + (se.badge().isEmpty() ? "" : " " + se.badge());
                OreUiRichTooltip.Builder b = OreUiRichTooltip.builder(title)
                        .badge(badgeText, tone)
                        .subtitle(se.headerText());
                b.keyValue("Effect", owner.effectIdText(), OreUiRichTooltip.Tone.accent);
                b.keyValue("Plugin", owner.pluginLabel(), OreUiRichTooltip.Tone.secondary);
                b.keyValue(
                    "Before",
                    se.before() == null ? "(empty)" : ProjectionDiffTemplates.describeContent(se.before()),
                    OreUiRichTooltip.Tone.secondary
                );
                b.keyValue(
                    "After",
                    se.after() == null ? "(removed)" : ProjectionDiffTemplates.describeContent(se.after()),
                    tone
                );
                if (se.movedFrom() != null) {
                    b.keyValue("Moved From", se.movedFrom().shortLabel(), OreUiRichTooltip.Tone.costP);
                }
                yield b.build();
            }
            case StepEntry.MetaEntry me -> OreUiRichTooltip.builder(Component.literal(me.text()))
                    .badge(me.kindBadge(), OreUiRichTooltip.Tone.secondary)
                    .keyValue("Effect", owner.effectIdText(), OreUiRichTooltip.Tone.accent)
                    .keyValue("Plugin", owner.pluginLabel(), OreUiRichTooltip.Tone.secondary)
                    .build();
            case StepEntry.GuardEntry ge -> {
                ProjectionGuard.Finding f = ge.finding();
                yield OreUiRichTooltip.builder(Component.literal(f.guardId().toString()))
                        .badge(
                            f.blocking() ? "BLOCKED" : "SKIPPED",
                            f.blocking() ? OreUiRichTooltip.Tone.danger : OreUiRichTooltip.Tone.warning
                        )
                        .keyValue("Effect", owner.effectIdText(), OreUiRichTooltip.Tone.accent)
                        .keyValue("Plugin", owner.pluginLabel(), OreUiRichTooltip.Tone.secondary)
                        .keyValue(
                            "Reason",
                            f.reasonCode().isEmpty() ? "guard_failed" : f.reasonCode(),
                            f.blocking() ? OreUiRichTooltip.Tone.danger : OreUiRichTooltip.Tone.warning
                        )
                        .build();
            }
        };
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        int neededHeight = computeHeight(canvas, width);
        if (laidOutWidth != width || neededHeight != currentHeight) {
            laidOutWidth = width;
            if (neededHeight != currentHeight) {
                currentHeight = neededHeight;
                useStyle(heightOf(currentHeight), minWidth(0), flexShrink(0));
            }
        }

        pluginHits.clear();
        effectHits.clear();
        entryHits.clear();
        stageHits.clear();
        CirrusPalette palette = CirrusUiTheme.palette(this);
        List<MiniSlotDraw> pendingSlots = new ArrayList<>();

        OreUiPaint.searchField(canvas, 0, 0, width, height, palette, false);

        int y = pad;
        int innerW = Math.max(80, width - pad * 2);

        renderHeaderStrip(canvas, palette, pad, y, innerW);
        y += headerH + 4;

        renderEndpointSummaryBar(canvas, palette, pad, y, innerW);
        y += endpointBarH + sectionGap;

        boolean runnable = result == null || result.runnable();
        int spineX = pad + 12;
        for (int i = 0; i < stageModels.size(); i++) {
            StageModel model = stageModels.get(i);
            int stageH = computeStageHeight(canvas, model, innerW);
            Rect cardRect = new Rect(pad, y, innerW, stageH);
            boolean hovered = cardRect.contains(mouseX, mouseY);
            renderStageLane(canvas, cardRect, model, palette, hovered, mouseX, mouseY, pendingSlots);
            stageHits.add(new StageHit(cardRect, model));
            y += stageH;

            if (i < stageModels.size() - 1) {
                StageModel next = stageModels.get(i + 1);
                boolean conduitActive = runnable && (model.active() || next.active());
                int conduitColor = !runnable
                        ? palette.ruleHardBan()
                        : next.active()
                                ? stageAccent(next, palette)
                                : model.active() ? stageAccent(model, palette) : palette.hairlineBorder();
                drawVerticalConduit(canvas, spineX, y, stageConduitH, conduitColor, conduitActive, i, palette);
                y += stageConduitH;
            }
        }

        if (!plugins.isEmpty()) {
            y += pluginSectionTopGap;
            canvas.fill(pad, y - 4, innerW, 1, OreUiPaint.cardDivider(palette));
            renderPluginStrip(canvas, palette, pad, y, innerW, mouseX, mouseY);
        }

        renderPendingMiniSlots(canvas, pendingSlots, palette, partialTicks);
    }

    private int computeHeight(@Nullable SceneCanvas canvas, int totalWidth) {
        int innerW = Math.max(80, totalWidth - pad * 2);
        int y = pad + headerH + 4 + endpointBarH + sectionGap;
        for (int i = 0; i < stageModels.size(); i++) {
            y += computeStageHeight(canvas, stageModels.get(i), innerW);
            if (i < stageModels.size() - 1) {
                y += stageConduitH;
            }
        }
        if (!plugins.isEmpty()) {
            y += pluginSectionTopGap + computePluginStripHeight(canvas, innerW);
        }
        return y + pad;
    }

    private int computeStageHeight(@Nullable SceneCanvas canvas, StageModel model, int cardWidth) {
        if (!model.active() || model.effectCards().isEmpty()) {
            return collapsedStageH;
        }
        int innerCardW = Math.max(60, cardWidth - stageBodyPadX * 2);
        int bodyH = 0;
        for (int i = 0; i < model.effectCards().size(); i++) {
            bodyH += computeEffectCardHeight(canvas, model.effectCards().get(i), innerCardW);
            if (i < model.effectCards().size() - 1) {
                bodyH += effectCardGap;
            }
        }
        return stageHeaderH + stageBodyPadY * 2 + bodyH;
    }

    private int computeEffectCardHeight(@Nullable SceneCanvas canvas, EffectCardModel card, int cardW) {
        int hdrH = computeEffectHeaderHeight(canvas, card, cardW);
        int entryW = Math.max(48, cardW - effectBodyPadX * 2);
        int entriesH = 0;
        for (int i = 0; i < card.entries().size(); i++) {
            entriesH += computeEntryHeight(canvas, card.entries().get(i), entryW);
            if (i < card.entries().size() - 1) {
                entriesH += entryGapY;
            }
        }
        return hdrH + effectBodyPadY * 2 + entriesH;
    }

    private int computeEffectHeaderHeight(@Nullable SceneCanvas canvas, EffectCardModel card, int cardW) {
        int rightBadgeW = textWidth(canvas, effectStatusBadge(card)) + 10;
        int pluginPillW = textWidth(canvas, Component.literal(card.pluginLabel())) + 10;
        int nameW = textWidth(canvas, Component.literal(card.displayName()));
        int prioW = card.trace().priority() == 0
                ? 0
                : textWidth(canvas, Component.literal("prio " + card.trace().priority())) + 8;
        int singleRowNeeded = effectHeaderPadX * 2 + 12 + nameW + 8 + pluginPillW + prioW + 8 + rightBadgeW;
        if (singleRowNeeded <= cardW) {
            return 19;
        }
        // Two-row wrapped header: line 1 = effect name + status badge; line 2 = plugin pill + effect id/priority
        return 32;
    }

    private int computeEntryHeight(@Nullable SceneCanvas canvas, StepEntry entry, int rowW) {
        return switch (entry) {
            case StepEntry.SlotEntry se -> {
                int visColW = se.template().visualColumnWidth(se.input());
                int badgeW = se.badge().isEmpty() ? 0 : textWidth(canvas, Component.literal(se.badge())) + 12;
                int textAvail = Math.max(48, rowW - entryPadX * 2 - 4 - visColW - 6 - badgeW);
                int headerLines = wrapText(canvas, se.headerText(), textAvail).size();
                int detailLines = se.detailText().isEmpty() ? 0 : wrapText(canvas, se.detailText(), textAvail).size();
                int extraH = se.template().extraBodyHeight(se.input());
                int textH = (headerLines + detailLines) * textLineH + (detailLines > 0 ? 1 : 0) + extraH;
                int minVisH = se.template().minVisualHeight(se.input());
                yield Math.max(minVisH + entryPadY * 2, textH + entryPadY * 2);
            }
            case StepEntry.MetaEntry me -> {
                int badgeW = textWidth(canvas, Component.literal(me.kindBadge())) + 10;
                int textAvail = Math.max(48, rowW - entryPadX * 2 - 4 - badgeW - 6);
                int lines = wrapText(canvas, me.text(), textAvail).size();
                yield Math.max(20, lines * textLineH + entryPadY * 2);
            }
            case StepEntry.GuardEntry ge -> {
                String tag = ge.finding().blocking() ? "BLOCK" : "SKIP";
                int badgeW = textWidth(canvas, Component.literal(tag)) + 10;
                int textAvail = Math.max(48, rowW - entryPadX * 2 - 4 - badgeW - 6);
                int lines = wrapText(canvas, ge.text(), textAvail).size();
                yield Math.max(20, lines * textLineH + entryPadY * 2);
            }
        };
    }

    private int computePluginStripHeight(@Nullable SceneCanvas canvas, int innerW) {
        int prefixW = textWidth(canvas, CirrusLangs.Calculator.Database.pipelineDecoratorChain.component()) + 8;
        int availW = Math.max(80, innerW - prefixW);
        int rows = 1;
        int cursorX = 0;
        if (plugins.size() > 1) {
            int allW = Math.min(availW, measureAllChipWidth(canvas));
            cursorX += allW + pluginChipGapX;
        }
        for (ProjectionPlugin plugin : plugins) {
            int cw = Math.min(availW, measurePluginChipWidth(canvas, plugin));
            if (cursorX > 0 && cursorX + cw > availW) {
                rows++;
                cursorX = 0;
            }
            cursorX += cw + pluginChipGapX;
        }
        return rows * pluginChipH + Math.max(0, rows - 1) * pluginChipGapY;
    }

    private void renderHeaderStrip(SceneCanvas canvas, CirrusPalette palette, int x, int y, int width) {
        int rightX = x + width;
        boolean runnable = result == null || result.runnable();
        Component statusComp = runnable ? Component.literal("RUNNABLE") : Component.literal("BLOCKED");
        int statusW = ChromeText.width(canvas, statusComp) + 10;
        OreUiPaint.tagPill(
            canvas,
            rightX - statusW,
            y + 1,
            OreUiPaint.minPillHeight,
            statusComp,
            runnable ? palette.costS() : palette.ruleHardBan(),
            palette
        );
        rightX -= statusW + 5;

        int totalChanges = result == null ? 0 : result.allChanges().size();
        Component changesComp = Component.literal(totalChanges == 0 ? "No edits" : "+" + totalChanges + " changes");
        int changesW = ChromeText.width(canvas, changesComp) + 10;
        if (rightX - changesW > x + 80) {
            OreUiPaint.tagPill(
                canvas,
                rightX - changesW,
                y + 1,
                OreUiPaint.minPillHeight,
                changesComp,
                totalChanges > 0 ? palette.focusRing() : palette.textMuted(),
                palette
            );
            rightX -= changesW + 6;
        }

        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Calculator.Database.pipelineTitle.component(),
            x,
            ChromeText.centeredY(y, headerH, canvas),
            Math.max(40, rightX - x),
            palette.textPrimary()
        );
    }

    private void renderEndpointSummaryBar(SceneCanvas canvas, CirrusPalette palette, int x, int y, int width) {
        canvas.fill(x, y, width, endpointBarH, palette.recessedSurface());
        canvas.strokeRect(x, y, width, endpointBarH, OreUiPaint.cardDivider(palette), 1);

        int baseIn = original == null ? 0 : original.inputs().size();
        int baseOut = original == null ? 0 : original.outputs().size();
        RecipeProjection finalProj = result == null ? original : result.projected();
        int outIn = finalProj == null ? 0 : finalProj.inputs().size();
        int outOut = finalProj == null ? 0 : finalProj.outputs().size();
        boolean runnable = result == null || result.runnable();
        boolean changed = result != null && !result.allChanges().isEmpty();

        String basePrefix = CirrusLangs.Calculator.Database.pipelineBase.component().getString();
        String outPrefix = CirrusLangs.Calculator.Database.pipelineOutput.component().getString();
        Component leftText = Component.literal(basePrefix + ": " + baseIn + " in \u2192 " + baseOut + " out");
        Component rightText = Component.literal(outPrefix + ": " + outIn + " in \u2192 " + outOut + " out");

        int arrowSpace = 28;
        int halfW = Math.max(40, (width - arrowSpace - 12) / 2);

        drawEndpointGlyph(canvas, true, x + 6, y + (endpointBarH - 7) / 2, palette.textSecondary());
        ChromeText.drawFitted(
            canvas,
            leftText,
            x + 17,
            ChromeText.centeredY(y, endpointBarH, canvas),
            Math.max(20, halfW - 14),
            palette.textSecondary()
        );

        int arrowStart = x + 6 + halfW + 2;
        int arrowEnd = arrowStart + arrowSpace - 4;
        int midY = y + endpointBarH / 2;
        OreUiRecipeGrid.drawRecipeArrow(canvas, arrowStart, arrowEnd, midY, palette, changed && runnable);
        if (!runnable) {
            canvas.fill(arrowStart, midY, Math.max(2, arrowEnd - arrowStart), 1, palette.ruleHardBan());
        }

        int rightStart = arrowEnd + 6;
        int rightAccent = runnable ? (changed ? palette.costS() : palette.textSecondary()) : palette.ruleHardBan();
        drawEndpointGlyph(canvas, false, rightStart, y + (endpointBarH - 7) / 2, rightAccent);
        ChromeText.drawFitted(
            canvas,
            rightText,
            rightStart + 11,
            ChromeText.centeredY(y, endpointBarH, canvas),
            Math.max(20, x + width - (rightStart + 11) - 6),
            rightAccent
        );
    }

    private void renderStageLane(
        SceneCanvas canvas,
        Rect rect,
        StageModel model,
        CirrusPalette palette,
        boolean hovered,
        int mouseX,
        int mouseY,
        List<MiniSlotDraw> pendingSlots
    ) {
        int x = rect.x();
        int y = rect.y();
        int w = rect.width();
        int h = rect.height();
        int accent = stageAccent(model, palette);

        if (!model.active() || model.effectCards().isEmpty()) {
            canvas.fill(x, y, w, h, palette.recessedSurface());
            canvas.strokeRect(x, y, w, h, hovered ? palette.focusRing() : OreUiPaint.cardDivider(palette), 1);
            drawStageGlyph(
                canvas,
                model.stage(),
                x + 7,
                y + (h - 7) / 2,
                model.blocked() ? palette.ruleHardBan() : palette.textMuted()
            );
            Component statusText = model.blocked()
                    ? CirrusLangs.Calculator.Database.pipelineBlocked.component()
                    : CirrusLangs.Calculator.Database.pipelinePassThrough.component();
            int statusW = ChromeText.width(canvas, statusText) + 8;
            ChromeText.drawFitted(
                canvas,
                Component.literal(model.title()),
                x + 19,
                ChromeText.centeredY(y, h, canvas),
                Math.max(30, w - 24 - statusW),
                model.blocked() ? palette.ruleHardBan() : palette.textSecondary()
            );
            ChromeText.drawRight(
                canvas,
                statusText,
                x + w - 7,
                ChromeText.centeredY(y, h, canvas),
                model.blocked() ? palette.ruleHardBan() : palette.textMuted()
            );
            return;
        }

        // Active expanded stage container
        OreUiPaint.raisedCard(canvas, x, y, w, h, palette, false, false);
        OreUiPaint.headerBand(canvas, x, y, w, stageHeaderH, palette, false);
        canvas.fill(x, y, 2, h, accent);

        drawStageGlyph(canvas, model.stage(), x + 7, y + (stageHeaderH - 7) / 2, accent);

        Component badgeComp = stageBadgeText(model);
        int badgeW = ChromeText.width(canvas, badgeComp) + 10;
        int badgeX = x + w - badgeW - 5;
        OreUiPaint.tagPill(
            canvas,
            badgeX,
            y + Math.max(2, (stageHeaderH - 13) / 2),
            13,
            badgeComp,
            accent,
            palette
        );

        Component titleComp = Component.literal(model.title());
        int titleW = ChromeText.width(canvas, titleComp);
        int titleX = x + 19;
        int maxTitleW = Math.max(30, badgeX - titleX - 6);
        ChromeText.drawFitted(
            canvas,
            titleComp,
            titleX,
            ChromeText.centeredY(y + 1, stageHeaderH, canvas),
            maxTitleW,
            accent
        );

        int subX = titleX + Math.min(titleW, maxTitleW) + 8;
        int subAvail = badgeX - subX - 6;
        if (subAvail > 44) {
            ChromeText.drawFitted(
                canvas,
                Component.literal(model.subtitle()),
                subX,
                ChromeText.centeredY(y + 1, stageHeaderH, canvas),
                subAvail,
                palette.textMuted()
            );
        }

        // Stacked per-effect cards inside this stage
        int cardX = x + stageBodyPadX;
        int cardY = y + stageHeaderH + stageBodyPadY;
        int cardW = Math.max(60, w - stageBodyPadX * 2);
        for (EffectCardModel card : model.effectCards()) {
            int ch = computeEffectCardHeight(canvas, card, cardW);
            Rect effectRect = new Rect(cardX, cardY, cardW, ch);
            renderEffectCard(canvas, effectRect, card, palette, mouseX, mouseY, pendingSlots);
            effectHits.add(new EffectHit(effectRect, card));
            cardY += ch + effectCardGap;
        }
    }

    private void renderEffectCard(
        SceneCanvas canvas,
        Rect rect,
        EffectCardModel card,
        CirrusPalette palette,
        int mouseX,
        int mouseY,
        List<MiniSlotDraw> pendingSlots
    ) {
        int x = rect.x();
        int y = rect.y();
        int w = rect.width();
        int h = rect.height();
        boolean selected = Objects.equals(card.trace().plugin().id(), selectedPluginId);
        int pluginColor = pluginAccent(card.trace().plugin(), palette);
        int statusColor = card.blocked()
                ? palette.ruleHardBan()
                : card.skippedByGuard() ? palette.costH() : pluginColor;

        int bg = selected
                ? OreUiPaint.withAlpha(palette.cardSurfaceSelected(), 0.72f)
                : palette.recessedSurface();
        int border = selected ? OreUiPaint.withAlpha(palette.focusRing(), 0.85f) : OreUiPaint.cardDivider(palette);
        canvas.fill(x, y, w, h, bg);
        canvas.strokeRect(x, y, w, h, border, 1);

        int hdrH = computeEffectHeaderHeight(canvas, card, w);
        canvas.fill(x + 1, y + 1, w - 2, hdrH - 1, OreUiPaint.cardFooter(palette));
        canvas.fill(x + 1, y + hdrH, w - 2, 1, OreUiPaint.cardDivider(palette));

        // Plugin color dot
        canvas.fill(x + effectHeaderPadX, y + 7, 5, 5, statusColor);

        // Right-aligned status/edit-count badge
        Component badge = effectStatusBadge(card);
        int badgeW = ChromeText.width(canvas, badge) + 10;
        int badgeX = x + w - badgeW - effectHeaderPadX;
        OreUiPaint.tagPill(canvas, badgeX, y + 3, 13, badge, statusColor, palette);

        Component nameComp = Component.literal(card.displayName());
        Component pluginComp = Component.literal(card.pluginLabel());
        int pluginPillW = ChromeText.width(canvas, pluginComp) + 10;

        if (hdrH <= 22) {
            // Single-row effect header: [●] effect_name  [Plugin Pill]  prio N  [2 edits]
            int cx = x + effectHeaderPadX + 9;
            int nameW = ChromeText.width(canvas, nameComp);
            ChromeText.draw(canvas, nameComp, cx, y + 5, palette.textPrimary());
            cx += nameW + 7;
            OreUiPaint.tagPill(canvas, cx, y + 3, 13, pluginComp, pluginColor, palette);
            cx += pluginPillW + 6;
            if (card.trace().priority() != 0 && cx + 28 < badgeX) {
                ChromeText.draw(
                    canvas,
                    Component.literal("prio " + card.trace().priority()),
                    cx,
                    y + 5,
                    palette.textMuted()
                );
            }
        } else {
            // Two-row effect header for narrower widths / long effect names (zero clipping)
            int nameX = x + effectHeaderPadX + 9;
            ChromeText.drawFitted(
                canvas,
                nameComp,
                nameX,
                y + 5,
                Math.max(24, badgeX - nameX - 6),
                palette.textPrimary()
            );
            int row2Y = y + 17;
            OreUiPaint.tagPill(canvas, x + effectHeaderPadX, row2Y, 12, pluginComp, pluginColor, palette);
            if (card.trace().priority() != 0) {
                ChromeText.draw(
                    canvas,
                    Component.literal("prio " + card.trace().priority()),
                    x + effectHeaderPadX + pluginPillW + 6,
                    row2Y + 2,
                    palette.textMuted()
                );
            }
        }

        // Multi-line wrapped change rows inside this effect card
        int ex = x + effectBodyPadX;
        int ey = y + hdrH + effectBodyPadY;
        int ew = Math.max(48, w - effectBodyPadX * 2);
        for (StepEntry entry : card.entries()) {
            int eh = computeEntryHeight(canvas, entry, ew);
            Rect entryRect = new Rect(ex, ey, ew, eh);
            boolean entryHovered = entryRect.contains(mouseX, mouseY);
            renderStepEntry(canvas, entryRect, entry, palette, entryHovered, pendingSlots);
            entryHits.add(new EntryHit(entryRect, card, entry));
            ey += eh + entryGapY;
        }
    }

    private void renderStepEntry(
        SceneCanvas canvas,
        Rect rect,
        StepEntry entry,
        CirrusPalette palette,
        boolean hovered,
        List<MiniSlotDraw> pendingSlots
    ) {
        int x = rect.x();
        int y = rect.y();
        int w = rect.width();
        int h = rect.height();

        switch (entry) {
            case StepEntry.SlotEntry se -> {
                ProjectionDiffTemplate template = se.template();
                ProjectionDiffTemplate.DiffInput input = se.input();
                WorkbenchThemeMode theme = CirrusUiTheme.mode(this);
                int accent = template.accentColor(input, palette);
                int bg = hovered ? palette.cardSurfaceHover() : OreUiPaint.cardFooter(palette);
                canvas.fill(x, y, w, h, bg);
                canvas.strokeRect(
                    x,
                    y,
                    w,
                    h,
                    hovered ? palette.focusRing() : OreUiPaint.withAlpha(accent, 0.45f),
                    1
                );
                canvas.fill(x, y, 2, h, accent);

                // Left template-specific visual column (value meter emblem, fluid tank, moved slot, stack slots, etc.)
                int vx = x + entryPadX;
                int visColW = template.visualColumnWidth(input);
                if (visColW > 0) {
                    template.paintVisual(
                        canvas,
                        input,
                        vx,
                        y,
                        visColW,
                        h,
                        palette,
                        theme,
                        (content, sx, sy, sSize) -> pendingSlots.add(new MiniSlotDraw(content, sx, sy, sSize))
                    );
                }

                // Right delta badge pill (vertically centered)
                int badgeReserve = 0;
                if (!se.badge().isEmpty()) {
                    Component badgeComp = Component.literal(se.badge());
                    int bw = ChromeText.width(canvas, badgeComp) + 10;
                    int bx = x + w - bw - entryPadX;
                    int by = y + Math.max(2, (h - 13) / 2);
                    OreUiPaint.tagPill(canvas, bx, by, 13, badgeComp, accent, palette);
                    badgeReserve = bw + 6;
                }

                // Center multi-line wrapped text block + optional template body decoration (e.g. comparative gauge)
                int tx = vx + (visColW > 0 ? visColW + 4 : 0);
                int textAvail = Math.max(48, (x + w - entryPadX - badgeReserve) - tx);
                List<String> headerLines = wrapText(canvas, se.headerText(), textAvail);
                List<String> detailLines = se.detailText().isEmpty()
                        ? List.of()
                        : wrapText(canvas, se.detailText(), textAvail);
                int extraH = template.extraBodyHeight(input);
                int totalTextH = (headerLines.size() + detailLines.size()) * textLineH
                        + (detailLines.isEmpty() ? 0 : 1)
                        + extraH;
                int ty = y + Math.max(entryPadY, (h - totalTextH) / 2);

                int headerColor = se.address().channel() == SlotChannel.input ? palette.costP() : palette.costS();
                for (String line : headerLines) {
                    ChromeText.draw(canvas, Component.literal(line), tx, ty, headerColor);
                    ty += textLineH;
                }
                if (!detailLines.isEmpty()) {
                    ty += 1;
                    for (String line : detailLines) {
                        ChromeText.draw(canvas, Component.literal(line), tx, ty, palette.textSecondary());
                        ty += textLineH;
                    }
                }
                if (extraH > 0) {
                    template.paintBodyExtra(canvas, input, tx, ty, textAvail, extraH, palette, theme);
                }
            }
            case StepEntry.MetaEntry me -> {
                int accent = "TAG".equals(me.kindBadge()) ? palette.costC() : palette.focusRing();
                canvas.fill(x, y, w, h, hovered ? palette.cardSurfaceHover() : OreUiPaint.cardFooter(palette));
                canvas.strokeRect(
                    x,
                    y,
                    w,
                    h,
                    hovered ? palette.focusRing() : OreUiPaint.withAlpha(accent, 0.45f),
                    1
                );
                canvas.fill(x, y, 2, h, accent);

                Component badgeComp = Component.literal(me.kindBadge());
                int bw = ChromeText.width(canvas, badgeComp) + 10;
                OreUiPaint.tagPill(canvas, x + entryPadX, y + Math.max(2, (h - 13) / 2), 13, badgeComp, accent, palette);

                int tx = x + entryPadX + bw + 6;
                int textAvail = Math.max(48, (x + w - entryPadX) - tx);
                List<String> lines = wrapText(canvas, me.text(), textAvail);
                int ty = y + Math.max(entryPadY, (h - lines.size() * textLineH) / 2);
                for (String line : lines) {
                    ChromeText.draw(canvas, Component.literal(line), tx, ty, palette.textSecondary());
                    ty += textLineH;
                }
            }
            case StepEntry.GuardEntry ge -> {
                boolean blocking = ge.finding().blocking();
                int accent = blocking ? palette.ruleHardBan() : palette.costH();
                canvas.fill(x, y, w, h, hovered ? palette.cardSurfaceHover() : OreUiPaint.cardFooter(palette));
                canvas.strokeRect(
                    x,
                    y,
                    w,
                    h,
                    hovered ? palette.focusRing() : OreUiPaint.withAlpha(accent, 0.72f),
                    1
                );
                canvas.fill(x, y, 2, h, accent);

                Component badgeComp = Component.literal(blocking ? "BLOCK" : "SKIP");
                int bw = ChromeText.width(canvas, badgeComp) + 10;
                OreUiPaint.tagPill(canvas, x + entryPadX, y + Math.max(2, (h - 13) / 2), 13, badgeComp, accent, palette);

                int tx = x + entryPadX + bw + 6;
                int textAvail = Math.max(48, (x + w - entryPadX) - tx);
                List<String> lines = wrapText(canvas, ge.text(), textAvail);
                int ty = y + Math.max(entryPadY, (h - lines.size() * textLineH) / 2);
                for (String line : lines) {
                    ChromeText.draw(canvas, Component.literal(line), tx, ty, accent);
                    ty += textLineH;
                }
            }
        }
    }

    private void renderPluginStrip(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        Component prefix = CirrusLangs.Calculator.Database.pipelineDecoratorChain.component();
        int prefixW = ChromeText.width(canvas, prefix) + 8;
        ChromeText.draw(canvas, prefix, x, ChromeText.centeredY(y, pluginChipH, canvas), palette.textMuted());

        int startX = x + prefixW;
        int availW = Math.max(80, width - prefixW);
        int cx = startX;
        int cy = y;

        if (plugins.size() > 1) {
            int totalChanges = result == null ? 0 : result.allChanges().size();
            int allW = Math.min(availW, measureAllChipWidth(canvas));
            Rect allRect = new Rect(cx, cy, allW, pluginChipH);
            boolean allSelected = selectedPluginId == null;
            boolean allHovered = allRect.contains(mouseX, mouseY);
            drawDecoratorChip(
                canvas,
                allRect,
                CirrusLangs.Calculator.Database.pipelineAll.component(),
                totalChanges,
                palette.focusRing(),
                true,
                allSelected,
                allHovered,
                palette
            );
            pluginHits.add(new PluginHit(allRect, null, totalChanges, true));
            cx += allW + pluginChipGapX;
        }

        for (ProjectionPlugin plugin : plugins) {
            boolean enabled = pluginEnabled.test(plugin);
            boolean selected = Objects.equals(plugin.id(), selectedPluginId);
            int changeCount = result == null ? 0 : result.pluginSummary(plugin).totalChangeCount();
            int chipW = Math.min(availW, measurePluginChipWidth(canvas, plugin));
            if (cx > startX && cx + chipW > startX + availW) {
                cx = startX;
                cy += pluginChipH + pluginChipGapY;
            }
            Rect rect = new Rect(cx, cy, chipW, pluginChipH);
            boolean hovered = rect.contains(mouseX, mouseY);
            drawDecoratorChip(
                canvas,
                rect,
                Component.translatable(plugin.labelKey()),
                changeCount,
                enabled ? pluginAccent(plugin, palette) : palette.textDisabled(),
                enabled,
                selected,
                hovered,
                palette
            );
            pluginHits.add(new PluginHit(rect, plugin, changeCount, enabled));
            cx += chipW + pluginChipGapX;
        }
    }

    private static void drawDecoratorChip(
        SceneCanvas canvas,
        Rect rect,
        Component label,
        int changeCount,
        int dotColor,
        boolean enabled,
        boolean selected,
        boolean hovered,
        CirrusPalette palette
    ) {
        if (selected) {
            OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hovered, true);
        } else {
            OreUiPaint.raisedButton(
                canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, !enabled, hovered, false, false
            );
        }

        canvas.fill(rect.x() + 6, rect.y() + (rect.height() - 5) / 2, 5, 5, dotColor);

        Component countBadge = Component.literal("+" + changeCount);
        int badgeW = ChromeText.width(canvas, countBadge) + 8;
        int badgeX = rect.right() - badgeW - 4;
        int badgeY = rect.y() + Math.max(2, (rect.height() - 12) / 2);
        int badgeColor = changeCount > 0 ? palette.focusRing() : palette.textMuted();
        canvas.fill(
            badgeX,
            badgeY,
            badgeW,
            12,
            OreUiPaint.withAlpha(badgeColor, OreUiPaint.isDark(palette) ? 0.22f : 0.12f)
        );
        canvas.strokeRect(badgeX, badgeY, badgeW, 12, OreUiPaint.withAlpha(badgeColor, 0.50f), 1);
        canvas.drawString(countBadge, badgeX + 4, ChromeText.centeredY(badgeY, 12, canvas), badgeColor, false);

        ChromeText.drawFitted(
            canvas,
            label,
            rect.x() + 15,
            ChromeText.centeredY(rect.y(), rect.height(), canvas),
            Math.max(12, badgeX - rect.x() - 19),
            enabled ? palette.textPrimary() : palette.textMuted()
        );
    }

    private void drawVerticalConduit(
        SceneCanvas canvas,
        int centerX,
        int topY,
        int span,
        int color,
        boolean active,
        int index,
        CirrusPalette palette
    ) {
        int stemH = Math.max(2, span - 3);
        canvas.fill(centerX - 1, topY, 2, stemH, active ? color : OreUiPaint.withAlpha(color, 0.65f));
        if (active && !OreUiMotion.reducedMotion() && stemH >= 4) {
            float t = OreUiMotion.cycle01(OreUiMotion.nowMs(), 1350L, index * 0.23f, false);
            int py = topY + Math.round(t * Math.max(0, stemH - 2));
            canvas.fill(centerX - 1, py, 2, 2, OreUiPaint.lighten(color, 0.55f));
        }
        int tipY = topY + span - 1;
        canvas.fill(centerX - 2, tipY - 2, 5, 1, color);
        canvas.fill(centerX - 1, tipY - 1, 3, 1, color);
        canvas.fill(centerX, tipY, 1, 1, color);
    }

    /**
     * Wraps {@code text} into multiple lines fitting inside {@code maxWidth}, breaking cleanly at
     * spaces or path separators ({@code /}, {@code :}, {@code _}) without ever dropping text.
     */
    private static List<String> wrapText(@Nullable SceneCanvas canvas, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        int limit = Math.max(36, maxWidth);
        if (rawStringWidth(canvas, text) <= limit) {
            return List.of(text);
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        int len = text.length();
        while (start < len) {
            while (start < len && text.charAt(start) == ' ') {
                start++;
            }
            if (start >= len) {
                break;
            }
            int end = start;
            int lastBreak = -1;
            while (end < len) {
                String candidate = text.substring(start, end + 1);
                if (rawStringWidth(canvas, candidate) > limit && end > start) {
                    break;
                }
                char c = text.charAt(end);
                if (c == ' ' || c == '/' || c == ':' || c == '_') {
                    lastBreak = end + (c == ' ' ? 0 : 1);
                }
                end++;
            }
            if (end < len && lastBreak > start) {
                lines.add(text.substring(start, lastBreak).trim());
                start = lastBreak;
            } else {
                lines.add(text.substring(start, Math.max(start + 1, end)).trim());
                start = Math.max(start + 1, end);
            }
        }
        return lines.isEmpty() ? List.of(text) : List.copyOf(lines);
    }

    private int measureAllChipWidth(@Nullable SceneCanvas canvas) {
        int totalChanges = result == null ? 0 : result.allChanges().size();
        int nameW = textWidth(canvas, CirrusLangs.Calculator.Database.pipelineAll.component());
        int badgeW = textWidth(canvas, Component.literal("+" + totalChanges)) + 8;
        return 15 + nameW + 8 + badgeW + 6;
    }

    private int measurePluginChipWidth(@Nullable SceneCanvas canvas, ProjectionPlugin plugin) {
        int changeCount = result == null ? 0 : result.pluginSummary(plugin).totalChangeCount();
        int nameW = textWidth(canvas, Component.translatable(plugin.labelKey()));
        int badgeW = textWidth(canvas, Component.literal("+" + changeCount)) + 8;
        return 15 + nameW + 8 + badgeW + 6;
    }

    private static int textWidth(@Nullable SceneCanvas canvas, Component text) {
        if (canvas != null) {
            return ChromeText.width(canvas, text);
        }
        return Math.max(8, text.getString().length() * 6);
    }

    private static int rawStringWidth(@Nullable SceneCanvas canvas, String text) {
        if (canvas != null) {
            return ChromeText.width(canvas, Component.literal(text));
        }
        return Math.max(6, text.length() * 6);
    }

    private static String shortPath(Namespace id) {
        String p = id.path();
        int slash = p.lastIndexOf('/');
        return slash >= 0 && slash + 1 < p.length() ? p.substring(slash + 1) : p;
    }

    private static String humanizeEffectName(Namespace id) {
        String tail = shortPath(id);
        String cleaned = tail.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return id.toString();
        }
        StringBuilder sb = new StringBuilder(cleaned.length());
        boolean capNext = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == ' ') {
                sb.append(c);
                capNext = true;
            } else if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String stageTitle(ProjectionStage stage) {
        return switch (stage) {
            case prepare -> "Prepare";
            case transform -> "Transform";
            case finalize -> "Finalize";
            case validate -> "Validate";
        };
    }

    private static String stageSubtitle(ProjectionStage stage) {
        return switch (stage) {
            case prepare -> "Unpack & normalize";
            case transform -> "Modify slots & amounts";
            case finalize -> "Filter & finalize";
            case validate -> "Guards & runnability";
        };
    }

    private static Component effectStatusBadge(EffectCardModel card) {
        if (card.blocked()) {
            return Component.literal("BLOCKED");
        }
        if (card.skippedByGuard()) {
            return Component.literal("SKIPPED");
        }
        int n = card.trace().changes().size();
        return Component.literal(n == 1 ? "1 edit" : n + " edits");
    }

    private Component stageBadgeText(StageModel model) {
        if (model.blocked()) {
            return CirrusLangs.Calculator.Database.pipelineBlocked.component();
        }
        int fx = model.effectCards().size();
        int ch = model.changeCount();
        if (ch == 0 && model.guardFailureCount() > 0) {
            return Component.literal(fx + " fx · " + model.guardFailureCount() + " guard");
        }
        String fxLabel = fx == 1 ? "1 effect" : fx + " effects";
        String chLabel = ch == 1 ? "1 change" : ch + " changes";
        return Component.literal(fxLabel + " · " + chLabel);
    }

    private int pluginAccent(ProjectionPlugin plugin, CirrusPalette palette) {
        if (plugin.id().equals(ProjectionPlugin.builtin.id())) {
            return palette.costS();
        }
        int idx = 0;
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).id().equals(plugin.id())) {
                idx = i;
                break;
            }
        }
        return switch (idx % 4) {
            case 0 -> palette.focusRing();
            case 1 -> palette.costC();
            case 2 -> palette.costH();
            default -> palette.costP();
        };
    }

    private static int stageAccent(StageModel model, CirrusPalette palette) {
        if (model.blocked()) {
            return palette.ruleHardBan();
        }
        if (!model.active()) {
            return palette.textMuted();
        }
        return switch (model.stage()) {
            case prepare -> palette.costP();
            case transform -> palette.craftStep().accent();
            case finalize -> palette.convertStep().accent();
            case validate -> palette.costH();
        };
    }

    private static OreUiRichTooltip.Tone statusTone(SlotDiff.DiffStatus status) {
        return switch (status) {
            case added -> OreUiRichTooltip.Tone.positive;
            case removed -> OreUiRichTooltip.Tone.danger;
            case transformed -> OreUiRichTooltip.Tone.warning;
            case moved -> OreUiRichTooltip.Tone.costP;
            case adjusted -> OreUiRichTooltip.Tone.accent;
            case unchanged -> OreUiRichTooltip.Tone.secondary;
        };
    }

    private static void drawEndpointGlyph(SceneCanvas canvas, boolean base, int x, int y, int color) {
        if (base) {
            canvas.strokeRect(x, y + 1, 7, 6, color, 1);
            canvas.fill(x + 2, y + 3, 3, 2, color);
        } else {
            canvas.fill(x, y + 2, 5, 4, color);
            canvas.fill(x + 1, y + 1, 5, 4, color);
            canvas.fill(x + 5, y + 2, 2, 3, color);
        }
    }

    private static void drawStageGlyph(SceneCanvas canvas, ProjectionStage stage, int x, int y, int color) {
        switch (stage) {
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
            case finalize -> {
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
                    if (!(content instanceof DurabilityContent)) {
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
                }
            } catch (RuntimeException ignored) {
                // Skip slot rendering when client renderer is unavailable in headless tests.
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
}
