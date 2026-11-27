package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPromptOption;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.CellRowWidget;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import dev.vfyjxf.cloudlib.ui.widget.ToggleWidget;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionRelative;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Rich decision card for one DecisionPrompt: subject icon, leverage bar, cost delta tags, a body per prompt type and a scope rocker.
 */
public class PromptCardWidget extends WidgetGroup<Widget> implements OreUiTooltipProvider {

    public static final int hoverPreviewMs = 120;
    public static final int fullWidth = 296;
    public static final int compactWidth = 216;
    public static final int headerBandHeight = 18;
    public static final int headerIcon = 24;
    public static final int optionHeight = 23;
    public static final int pad = 7;
    public static final int leverageHeight = 6;
    public static final int footerHeight = 22;
    public static final int rockerWidth = 28;
    public static final int rockerHeight = 12;

    public enum PreviewKind {
        banRecipe, ownMachine, buildMachine, pickVariant, treatFree
    }

    public record PreviewSubject(
        PreviewKind kind,
        Set<String> stepIds,
        @Nullable String machineId,
        @Nullable CompiledRecipeKey recipeKey,
        @Nullable ResourceKey resource
    ) {
        public PreviewSubject {
            stepIds = stepIds == null ? Set.of() : Set.copyOf(stepIds);
        }
    }

    public interface PreviewListener {
        void onPreview(@Nullable PreviewSubject subject);
    }

    public record RecipePreview(List<ResourceKey> inputs, List<ResourceKey> outputs) {
        public RecipePreview {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }

        public boolean isEmpty() {
            return inputs.isEmpty() && outputs.isEmpty();
        }
    }

    private final CoPilotHost host;
    private final ResolvedPrompt prompt;
    private final boolean compact;
    private final CellRowWidget leverageRow;
    private final RingWidget costRing;
    private final ToggleWidget rocker;

    private static final int tagSlotSize = 20;
    private static final int tagSlotGap = 3;
    private static final int tagSearchHeight = 16;
    private static final int maxVisibleTagRows = 4;
    private static final int inlineControlHeight = 16;
    private static final int altRecipeRowHeight = 18;

    private @Nullable PreviewListener previewListener;
    private @Nullable Consumer<String> candidateRequestListener;
    private @Nullable CompiledRecipeGraph graph;
    private boolean persistToWorld;
    private boolean allCandidatesExpanded;
    private String tagSearchQuery = "";
    private boolean tagSearchFocused;
    private int tagScrollY;
    private @Nullable Rect tagSearchRect;
    private @Nullable Rect tagSearchClearRect;
    private @Nullable Rect tagGridViewportRect;
    private @Nullable ResourceKey pickedTagMember;
    private int ownedMachineCount = 1;
    private long declaredStockAmount = -1L;
    private @Nullable PreviewSubject firedPreview;
    private @Nullable String answeredOptionId;
    private int hoveredOption = -1;
    private long hoverStartedAtMs;
    private int renderedMouseX = -1;
    private int renderedMouseY = -1;
    private final long createdAtMs = OreUiMotion.nowMs();
    private final OreUiMotion.SmoothValue scopeIndicatorX = new OreUiMotion.SmoothValue();

    public PromptCardWidget(CoPilotHost host, ResolvedPrompt prompt, boolean compact, boolean defaultPersistToWorld) {
        this.host = Objects.requireNonNull(host, "host");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.compact = compact;
        this.persistToWorld = defaultPersistToWorld;
        useStyle(
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            heightOf(preferredHeight()),
            flexShrink(0),
            positionRelative()
        );

        this.leverageRow = CellRowWidget
                .of(
                    PromptDeltaFormat.leverageCells,
                    () -> PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore())
                )
                .setZones(
                    List.of(
                        new CellRowWidget.Zone(0.5, InfoBarWidget.styleInfo),
                        new CellRowWidget.Zone(0.75, InfoBarWidget.styleWarn),
                        new CellRowWidget.Zone(1.0, InfoBarWidget.styleDanger)
                    )
                );
        leverageRow.useStyle(
            positionAbsolute(),
            insetLeft(pad + headerIcon + 9),
            insetRight(pad + 2),
            insetTop(headerBandHeight + 23),
            heightOf(leverageHeight)
        );
        leverageRow.setVisible(false);
        addWidget(leverageRow);

        this.costRing = RingWidget.of(this::costShare).setThickness(3);
        costRing.addStyleClass("warn");
        costRing.useStyle(positionAbsolute(), insetRight(pad + 4), insetTop(bodyTop() + 4), sizeOf(22, 22));
        costRing.setVisible(
            kind() == DecisionPromptKind.baseOrSupplyBoundary
                    || kind() == DecisionPromptKind.reusableCatalystCheck
                    || kind() == DecisionPromptKind.fieldTripConfirm
        );
        addWidget(costRing);

        this.rocker = ToggleWidget.create(persistToWorld)
                .setColors(host.palette().textDisabled(), host.palette().focusRing()).onToggle(this::setPersistToWorld);
        rocker.useStyle(
            positionAbsolute(),
            insetLeft(rockerLeft()),
            insetTop(preferredHeight() - 2 - footerHeight + (footerHeight - rockerHeight) / 2),
            sizeOf(rockerWidth, rockerHeight)
        );
        rocker.setVisible(false);
        addWidget(rocker);

        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            int lx = (int) local.x();
            int ly = (int) local.y();
            int footerTop = preferredHeight() - 2 - footerHeight;
            if (ly >= footerTop) {
                int midX = cardWidth() / 2;
                setPersistToWorld(lx >= midX);
                CirrusUiSound.playClickSound();
                return EventDispatch.consumed;
            }
            if (handleInteractiveBodyClick(lx, ly)) {
                CirrusUiSound.playClickSound();
                return EventDispatch.consumed;
            }
            tagSearchFocused = false;
            int index = optionIndexAt(lx, ly);
            if (index < 0) {
                return EventDispatch.pass;
            }
            choose(index);
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollYDelta, ctx) -> {
            if (kind() != DecisionPromptKind.materialFamilyAnchor || tagGridViewportRect == null) {
                return EventDispatch.pass;
            }
            int maxScroll = tagGridMaxScroll();
            if (maxScroll <= 0) {
                return EventDispatch.pass;
            }
            int delta = scrollYDelta > 0 ? -(tagSlotSize + tagSlotGap) : (tagSlotSize + tagSlotGap);
            tagScrollY = Math.max(0, Math.min(maxScroll, tagScrollY + delta));
            ctx.consume();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, ctx) -> {
            if (!tagSearchFocused || kind() != DecisionPromptKind.materialFamilyAnchor) {
                return EventDispatch.pass;
            }
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!tagSearchQuery.isEmpty()) {
                    tagSearchQuery = "";
                } else {
                    tagSearchFocused = false;
                }
                tagScrollY = 0;
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (input.isCtrlDown()) {
                    tagSearchQuery = "";
                } else if (!tagSearchQuery.isEmpty()) {
                    tagSearchQuery = tagSearchQuery.substring(0, tagSearchQuery.length() - 1);
                }
                tagScrollY = 0;
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                tagSearchFocused = false;
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!tagSearchFocused || kind() != DecisionPromptKind.materialFamilyAnchor) {
                return EventDispatch.pass;
            }
            if (codePoint >= 32 && codePoint != 127 && tagSearchQuery.length() < 48) {
                tagSearchQuery = tagSearchQuery + Character.toString(codePoint);
                tagScrollY = 0;
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onTick(() -> fireHoverPreview(System.currentTimeMillis()));
    }

    public CoPilotHost host() {
        return host;
    }

    public ResolvedPrompt prompt() {
        return prompt;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        CirrusPalette palette = host.palette();
        double impact = PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore());
        ResourceKey subjectIcon = prompt.primaryResource() != null
                ? prompt.primaryResource()
                : prompt.primaryMachineId() != null ? ResourceKey.item(prompt.primaryMachineId()) : null;
        Component subjectName = prompt.primaryResource() != null
                ? CoPilotPaint.resourceName(prompt.primaryResource())
                : prompt.primaryMachineId() != null
                        ? CoPilotPaint.machineName(subjectIcon, prompt.primaryMachineId())
                        : Component.literal(CoPilotPaint.humanizeId(kind().name()));
        if (kind() == DecisionPromptKind.materialFamilyAnchor) {
            ResourceKey hoveredCandidate = tagCandidateAt((int) localX, (int) localY);
            if (hoveredCandidate != null) {
                String tag = prompt.tagId() == null ? "-" : prompt.tagId();
                return OreUiRichTooltip.builder(CoPilotPaint.resourceName(hoveredCandidate)).icon(hoveredCandidate)
                        .badge("#" + tag, OreUiRichTooltip.Tone.accent).subtitle(hoveredCandidate.resourceId())
                        .text(
                            CirrusLangs.Workbench.Copilot.tagCandidateTooltip.get(tag),
                            OreUiRichTooltip.Tone.secondary
                        ).build();
            }
        }
        int optIndex = optionIndexAt((int) localX, (int) localY);
        if (optIndex >= 0 && optIndex < prompt.options().size()) {
            ResolvedPromptOption opt = prompt.options().get(optIndex);
            OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(Component.translatable(opt.raw().labelKey()))
                    .icon(subjectIcon).badge("CHOICE", OreUiRichTooltip.Tone.accent).subtitle(subjectName);
            List<OreUiRichTooltip.Pill> pills = new ArrayList<>();
            for (PromptDeltaFormat.Chip chip : PromptDeltaFormat.deltaChips(opt.delta())) {
                pills.add(OreUiRichTooltip.Pill.colored(chip.label(), CoPilotPaint.inkFor(palette, chip.styleClass())));
            }
            if (!pills.isEmpty()) {
                builder.pills(pills);
            }
            builder.keyValue(
                Component.literal("Rule Scope"),
                Component.literal(persistToWorld ? "World Save" : "This Session"),
                persistToWorld ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
            );
            builder.footer("Click to apply decision & replan immediately");
            return builder.build();
        }
        int footerTop = preferredHeight() - 2 - footerHeight;
        if (localY >= footerTop) {
            return OreUiRichTooltip.builder(persistToWorld ? "World Save Scope" : "Session Scope")
                    .badge(
                        persistToWorld ? "PERSISTENT" : "TEMPORARY",
                        persistToWorld ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                    )
                    .text(
                        persistToWorld
                                ? "Saves this decision rule into your world facility config."
                                : "Applies only to the active planning tab until promoted.",
                        OreUiRichTooltip.Tone.secondary
                    ).footer("Click Session or World Save to toggle scope").build();
        }
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(subjectName).icon(subjectIcon)
                .badge(
                    Math.round(impact * 100.0) + "% IMPACT",
                    impact >= 0.65 ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.accent
                ).subtitle(CoPilotPaint.humanizeId(kind().name()));
        builder.meter(
            Component.literal("Decision Leverage"),
            impact,
            Component.literal(Math.round(impact * 100.0) + "%"),
            impact >= 0.65 ? OreUiRichTooltip.Tone.warning : OreUiRichTooltip.Tone.accent
        );
        builder.keyValue(
            Component.literal("Options"),
            Component.literal(prompt.options().size() + " available"),
            OreUiRichTooltip.Tone.primary
        );
        builder.footer("Hover an option to preview impact \u00b7 Click to apply");
        return builder.build();
    }

    public boolean compact() {
        return compact;
    }

    public PromptCardWidget previewListener(@Nullable PreviewListener listener) {
        this.previewListener = listener;
        return this;
    }

    public PromptCardWidget candidateRequestListener(@Nullable Consumer<String> listener) {
        this.candidateRequestListener = listener;
        return this;
    }

    public PromptCardWidget graph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        return this;
    }

    public boolean persistToWorld() {
        return persistToWorld;
    }

    public PromptCardWidget setPersistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
        if (rocker.toggled() != persistToWorld) {
            rocker.setToggled(persistToWorld);
        }
        return this;
    }

    public int preferredWidth() {
        return compact ? compactWidth : fullWidth;
    }

    private int cardWidth() {
        int current = width();
        return current > 0 ? current : preferredWidth();
    }

    public int optionTop() {
        return bodyTop() + bodyHeight() + 6;
    }

    public int optionRowCount() {
        return kind() == DecisionPromptKind.materialFamilyAnchor ? 0 : prompt.options().size();
    }

    public int preferredHeight() {
        int optionsH = optionRowCount() * optionHeight;
        return optionTop() + optionsH + footerHeight + (optionsH > 0 ? 8 : 4);
    }

    public Rect optionRect(int index) {
        int width = Math.max(24, cardWidth() - pad * 2);
        return new Rect(pad, optionTop() + index * optionHeight, width, optionHeight - 3);
    }

    public int optionIndexAt(int localX, int localY) {
        for (int index = 0; index < optionRowCount(); index++) {
            if (optionRect(index).contains(localX, localY)) {
                return index;
            }
        }
        return -1;
    }

    public int rockerLeft() {
        int labelWidth = renderedLabelWidth(CirrusLangs.Workbench.Copilot.scopeSession) + 4;
        return Math.max(pad, Math.min(cardWidth() - pad - rockerWidth - labelWidth - 4, pad + 92));
    }

    public @Nullable String answeredOptionId() {
        return answeredOptionId;
    }

    public boolean answered() {
        return answeredOptionId != null;
    }

    public void choose(int index) {
        if (index < 0) {
            return;
        }
        if (index >= prompt.options().size()) {
            openAllCandidates();
            return;
        }
        ResolvedPromptOption option = prompt.options().get(index);
        answeredOptionId = option.raw().optionId();
        Optional<HumanFeedback> followUp = option.raw().followUp();
        if (followUp.isPresent()) {
            host.refine(scoped(followUp.get()), null);
            return;
        }
        host.refine(new AnswerPromptFeedback(prompt.raw().promptId(), option.raw().optionId(), persistToWorld), null);
    }

    public HumanFeedback scoped(HumanFeedback feedback) {
        if (feedback instanceof DeclareOwnedMachineFeedback owned) {
            int count = kind() == DecisionPromptKind.machineAvailability
                    ? Math.max(1, ownedMachineCount)
                    : owned.ownedCount();
            return new DeclareOwnedMachineFeedback(owned.machineId(), count, persistToWorld);
        }
        return feedback;
    }

    public boolean allCandidatesExpanded() {
        return allCandidatesExpanded;
    }

    public int ownedMachineCount() {
        return ownedMachineCount;
    }

    public long declaredStockAmount() {
        if (declaredStockAmount < 0L) {
            long gather = gatherTotal();
            declaredStockAmount = gather > 0L ? gather : 16L;
        }
        return declaredStockAmount;
    }

    public void pickTagCandidate(ResourceKey member) {
        String tagId = prompt.tagId();
        if (tagId == null || member == null) {
            return;
        }
        pickedTagMember = member;
        GuidanceScope scope = persistToWorld ? GuidanceScope.worldSave : GuidanceScope.session;
        TagDefaultRule rule = new TagDefaultRule("rule:tag:" + tagId, scope, RuleStrength.hardPin, tagId, member);
        answeredOptionId = "pick:" + member.shortString();
        host.refine(new ApplyRulesFeedback(List.of(rule), persistToWorld), null);
    }

    public void openAllCandidates() {
        if (kind() == DecisionPromptKind.materialFamilyAnchor) {
            allCandidatesExpanded = !allCandidatesExpanded;
            syncCardHeight();
        }
        String tagId = prompt.tagId();
        if (candidateRequestListener != null && tagId != null) {
            candidateRequestListener.accept(tagId);
        }
    }

    private void syncCardHeight() {
        useStyle(heightOf(preferredHeight()));
        rocker.useStyle(
            positionAbsolute(),
            insetLeft(rockerLeft()),
            insetTop(preferredHeight() - 2 - footerHeight + (footerHeight - rockerHeight) / 2),
            sizeOf(rockerWidth, rockerHeight)
        );
    }

    public @Nullable PreviewSubject previewFor(int index) {
        if (index < 0 || index >= prompt.options().size()) {
            return null;
        }
        ResolvedPromptOption option = prompt.options().get(index);
        Set<String> anchors = prompt.anchoredStepIds();
        for (GuidanceRule rule : option.raw().compiledRules()) {
            if (rule instanceof MachineRule machineRule) {
                PreviewKind kind = machineRule.willingToBuildNew() ? PreviewKind.buildMachine : PreviewKind.ownMachine;
                return new PreviewSubject(kind, anchors, machineRule.machineId(), null, null);
            }
        }
        if (option.raw().followUp().orElse(null) instanceof DeclareOwnedMachineFeedback owned) {
            return new PreviewSubject(PreviewKind.ownMachine, anchors, owned.machineId(), null, null);
        }
        if (option.variantResource() != null) {
            return new PreviewSubject(PreviewKind.pickVariant, anchors, null, null, option.variantResource());
        }
        for (GuidanceRule rule : option.raw().compiledRules()) {
            if (rule instanceof TagDefaultRule tagDefault) {
                return new PreviewSubject(PreviewKind.pickVariant, anchors, null, null, tagDefault.preferredMember());
            }
            if (rule instanceof ResourceRule resourceRule) {
                PreviewKind kind = resourceRule.treatAsFreeBaseSource() ? PreviewKind.treatFree : PreviewKind.banRecipe;
                return new PreviewSubject(kind, anchors, null, null, resourceRule.resource());
            }
        }
        if (prompt.recipeKey() != null) {
            return new PreviewSubject(PreviewKind.banRecipe, anchors, null, prompt.recipeKey(), null);
        }
        if (prompt.primaryMachineId() != null) {
            return new PreviewSubject(PreviewKind.ownMachine, anchors, prompt.primaryMachineId(), null, null);
        }
        if (prompt.primaryResource() != null) {
            return new PreviewSubject(PreviewKind.treatFree, anchors, null, null, prompt.primaryResource());
        }
        return null;
    }

    public @Nullable PreviewSubject fireHoverPreview(long nowMs) {
        int index = renderedMouseX < 0 ? -1 : optionIndexAt(renderedMouseX, renderedMouseY);
        return hoverUpdate(index, nowMs);
    }

    public @Nullable PreviewSubject hoverUpdate(int index, long nowMs) {
        if (index != hoveredOption) {
            hoveredOption = index;
            hoverStartedAtMs = nowMs;
            firedPreview = null;
            if (index < 0 && previewListener != null) {
                previewListener.onPreview(null);
            }
            return null;
        }
        if (index < 0 || nowMs - hoverStartedAtMs < hoverPreviewMs) {
            return null;
        }
        PreviewSubject subject = previewFor(index);
        if (subject == null || subject.equals(firedPreview)) {
            return null;
        }
        firedPreview = subject;
        if (previewListener != null) {
            previewListener.onPreview(subject);
        }
        return subject;
    }

    public int hoveredOption() {
        return hoveredOption;
    }

    public long gatherTotal() {
        ResourceKey resource = prompt.primaryResource();
        Optional<ProductionPlan> plan = host.snapshot().selectedPlan();
        if (resource == null || plan.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (PlanStep step : plan.get().steps()) {
            if (step instanceof AcquireStep acquire && acquire.resource().sameBaseIdentity(resource)) {
                total += acquire.amount();
            }
        }
        return total;
    }

    public double costShare() {
        ResourceKey resource = prompt.primaryResource();
        Optional<ProductionPlan> plan = host.snapshot().selectedPlan();
        if (resource == null || plan.isEmpty()) {
            return 0.0;
        }
        double total = plan.get().totalCost().humanActiveSeconds();
        if (!(total > 0.0)) {
            return 0.0;
        }
        double gathered = 0.0;
        for (PlanStep step : plan.get().steps()) {
            if (step instanceof AcquireStep acquire && acquire.resource().sameBaseIdentity(resource)) {
                gathered += acquire.stepCost().humanActiveSeconds();
            }
        }
        return ParetoGeometry.clamp01(gathered / total);
    }

    public List<ResourceKey> tagCandidates() {
        Set<ResourceKey> candidates = new LinkedHashSet<>();
        for (ResolvedPromptOption option : prompt.options()) {
            if (option.variantResource() != null) {
                candidates.add(option.variantResource());
            }
            for (GuidanceRule rule : option.raw().compiledRules()) {
                if (rule instanceof TagDefaultRule tagDefault) {
                    candidates.add(tagDefault.preferredMember());
                }
            }
        }
        String tagId = prompt.tagId();
        CompiledRecipeGraph resolved = resolvedGraph();
        if (tagId != null && resolved != null) {
            Optional<ProductionPlan> activePlan = host.snapshot().selectedPlan();
            if (activePlan.isPresent()) {
                for (PlanStep step : activePlan.get().steps()) {
                    if (step instanceof CraftStep craft) {
                        resolved.recipeByKey(craft.recipeKey())
                                .ifPresent(recipe -> collectTagSlotCandidates(recipe, tagId, candidates));
                    }
                }
            }
            if (!candidates.isEmpty()) {
                ResourceKey seed = candidates.iterator().next();
                for (CompiledRecipe consumer : resolved.consumersOf(seed)) {
                    collectTagSlotCandidates(consumer, tagId, candidates);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static void collectTagSlotCandidates(CompiledRecipe recipe, String tagId, Set<ResourceKey> out) {
        for (InputSlot slot : recipe.inputs()) {
            if (slot.tagId().isPresent() && tagId.equals(slot.tagId().get())) {
                out.addAll(slot.candidates());
            }
        }
    }

    public List<CompiledRecipe> alternativeRecipes() {
        CompiledRecipeGraph resolved = resolvedGraph();
        CompiledRecipeKey currentKey = prompt.recipeKey();
        if (resolved == null || currentKey == null) {
            return List.of();
        }
        Optional<CompiledRecipe> currentOpt = resolved.recipeByKey(currentKey);
        if (currentOpt.isEmpty() || currentOpt.get().outputs().isEmpty()) {
            return List.of();
        }
        ResourceKey primaryOutput = currentOpt.get().outputs().get(0).resource();
        List<CompiledRecipe> alts = new ArrayList<>();
        for (CompiledRecipe producer : resolved.producersOf(primaryOutput)) {
            if (!producer.key().equals(currentKey)) {
                alts.add(producer);
                if (alts.size() >= 2) {
                    break;
                }
            }
        }
        return List.copyOf(alts);
    }

    public RecipePreview recipePreview() {
        CompiledRecipeKey key = prompt.recipeKey();
        CompiledRecipeGraph resolved = resolvedGraph();
        if (key == null || resolved == null) {
            return new RecipePreview(List.of(), List.of());
        }
        Optional<CompiledRecipe> recipe = resolved.recipeByKey(key);
        if (recipe.isEmpty()) {
            return new RecipePreview(List.of(), List.of());
        }
        List<ResourceKey> inputs = new ArrayList<>();
        for (InputSlot slot : recipe.get().inputs()) {
            inputs.add(slot.candidates().get(0));
        }
        List<ResourceKey> outputs = new ArrayList<>();
        for (OutputSlot slot : recipe.get().outputs()) {
            outputs.add(slot.resource());
        }
        return new RecipePreview(inputs, outputs);
    }

    public @Nullable CompiledRecipeGraph resolvedGraph() {
        if (graph != null) {
            return graph;
        }
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        return runtime == null ? null : runtime.currentGraph().orElse(null);
    }

    private DecisionPromptKind kind() {
        return prompt.raw().kind();
    }

    private int bodyTop() {
        return headerBandHeight + headerIcon + 16;
    }

    private int bodyHeight() {
        return switch (kind()) {
            case machineAvailability -> 54;
            case materialFamilyAnchor -> {
                int searchSpace = showTagSearchBar() ? tagSearchHeight + 4 : 0;
                yield 8 + searchSpace + tagGridVisibleRows() * (tagSlotSize + tagSlotGap) + 2;
            }
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> 64;
            case suspiciousJeiVerify, ruleConflictResolution -> 40 + alternativeRecipes().size() * altRecipeRowHeight;
        };
    }

    private boolean showTagSearchBar() {
        return kind() == DecisionPromptKind.materialFamilyAnchor && tagCandidates().size() > tagGridColumns();
    }

    private List<ResourceKey> filteredTagCandidates() {
        List<ResourceKey> all = tagCandidates();
        if (tagSearchQuery.isBlank()) {
            return all;
        }
        String needle = tagSearchQuery.trim().toLowerCase(Locale.ROOT);
        List<ResourceKey> out = new ArrayList<>();
        for (ResourceKey candidate : all) {
            String id = candidate.resourceId().toLowerCase(Locale.ROOT);
            String name = CoPilotPaint.resourceName(candidate).getString().toLowerCase(Locale.ROOT);
            if (id.contains(needle) || name.contains(needle)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private int tagGridColumns() {
        int innerW = Math.max(tagSlotSize, cardWidth() - pad * 2 - 14);
        return Math.max(1, (innerW + tagSlotGap) / (tagSlotSize + tagSlotGap));
    }

    private int tagGridVisibleRows() {
        int total = tagCandidates().size();
        if (total <= 0) {
            return 1;
        }
        int cols = tagGridColumns();
        int neededRows = (total + cols - 1) / cols;
        return Math.max(1, Math.min(maxVisibleTagRows, neededRows));
    }

    private int tagGridMaxScroll() {
        List<ResourceKey> filtered = filteredTagCandidates();
        int cols = tagGridColumns();
        int totalRows = Math.max(1, (filtered.size() + cols - 1) / cols);
        int visibleRows = tagGridVisibleRows();
        if (totalRows <= visibleRows) {
            return 0;
        }
        return (totalRows - visibleRows) * (tagSlotSize + tagSlotGap);
    }

    private int renderedLabelWidth(LangEntry entry) {
        return entry.get().getString().length() * 5;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        renderedMouseX = mouseX;
        renderedMouseY = mouseY;
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        CirrusPalette palette = host.palette();
        boolean high = PromptDeltaFormat.isHighLeverage(prompt.raw().leverageScore());
        OreUiPaint.raisedCard(canvas, 0, 0, width, height, palette, false, false);
        OreUiPaint.headerBand(canvas, 0, 0, width, headerBandHeight, palette, false);

        int kindAccent = kindAccent(palette);
        Component kindLabel = kindLabel();
        String impactPct = Integer
                .toString(Math.round(PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore()) * 100f));
        Component impactBadge = CirrusLangs.Workbench.Copilot.impactPercent.get(impactPct);
        int pillW = canvas.font().width(impactBadge) + 10;
        int pillX = Math.max(24, width - pillW - 6);
        OreUiPaint.tagPill(canvas, pillX, 3, 13, impactBadge, high ? palette.costH() : palette.focusRing(), palette);
        canvas.fill(7, 7, 5, 5, kindAccent);
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, kindLabel, Math.max(12, pillX - 18)),
            15,
            ChromeText.centeredY(1, headerBandHeight, canvas),
            kindAccent
        );

        int iconX = pad + 1;
        int iconY = headerBandHeight + 8;
        renderSubjectIcon(canvas, palette, iconX, iconY);
        int textLeft = iconX + headerIcon + 7;
        int textWidth = Math.max(10, width - textLeft - pad);
        if (kind() == DecisionPromptKind.materialFamilyAnchor) {
            String tag = prompt.tagId() == null ? "-" : prompt.tagId();
            List<ResourceKey> all = tagCandidates();
            Component countBadge = Component.literal(Integer.toString(all.size()));
            int countW = canvas.font().width(countBadge) + 8;
            int countX = Math.max(textLeft + 24, width - pad - countW);
            OreUiPaint.tagPill(canvas, countX, iconY - 1, 12, countBadge, palette.focusRing(), palette);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal("#" + tag), Math.max(12, countX - textLeft - 4)),
                textLeft,
                iconY,
                palette.textPrimary()
            );
            Component sub = pickedTagMember != null
                    ? CoPilotPaint.resourceName(pickedTagMember)
                    : CirrusLangs.Workbench.Copilot.tagSelectHint.get();
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, sub, textWidth),
                textLeft,
                iconY + 13,
                pickedTagMember != null ? palette.costS() : palette.textSecondary()
            );
        } else {
            CoPilotPaint
                    .text(canvas, CoPilotPaint.fit(canvas, title(), textWidth), textLeft, iconY, palette.textPrimary());
            int barFrameX = pad + headerIcon + 7;
            int barFrameY = headerBandHeight + 21;
            int barFrameW = Math.max(16, width - barFrameX - pad);
            int barFrameH = leverageHeight + 4;
            renderLeverageMeter(canvas, barFrameX, barFrameY, barFrameW, barFrameH, palette);
        }

        // Structured recessed well for the prompt detail body
        int bodyY = bodyTop();
        int bodyH = bodyHeight();
        canvas.fill(pad, bodyY, Math.max(16, width - pad * 2), bodyH, OreUiPaint.cardFooter(palette));
        canvas.strokeRect(pad, bodyY, Math.max(16, width - pad * 2), bodyH, OreUiPaint.cardDivider(palette), 1);

        renderBody(canvas, palette, mouseX, mouseY);
        renderOptions(canvas, palette, mouseX, mouseY);
        renderFooter(canvas, palette, mouseX, mouseY);
    }

    private Component kindLabel() {
        return switch (kind()) {
            case machineAvailability -> CirrusLangs.Workbench.Copilot.promptKindMachine.get();
            case materialFamilyAnchor -> CirrusLangs.Workbench.Copilot.promptKindTag.get();
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm ->
                CirrusLangs.Workbench.Copilot.promptKindLeaf.get();
            case suspiciousJeiVerify, ruleConflictResolution -> CirrusLangs.Workbench.Copilot.promptKindRecipe.get();
        };
    }

    private int kindAccent(CirrusPalette palette) {
        return switch (kind()) {
            case machineAvailability -> palette.provisionMachineStep().accent();
            case materialFamilyAnchor -> palette.craftStep().accent();
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> palette.acquireStep().accent();
            case suspiciousJeiVerify, ruleConflictResolution -> palette.cycleLoopStep().accent();
        };
    }

    private Component title() {
        return Component.translatable(prompt.raw().titleKey());
    }

    private Component subtitle() {
        return Component.translatable(prompt.raw().explanationKey());
    }

    private void renderSubjectIcon(SceneCanvas canvas, CirrusPalette palette, int x, int y) {
        ResourceKey resource = subjectResource();
        if (resource != null) {
            CoPilotPaint.renderSlotItem(canvas, CoPilotPaint.stackOf(resource, 1L), x, y, headerIcon, palette);
            return;
        }
        OreUiPaint.slot(canvas, x, y, headerIcon, palette);
        canvas.fill(
            x + 6,
            y + 6,
            headerIcon - 12,
            headerIcon - 12,
            PromptDeltaFormat.isHighLeverage(prompt.raw().leverageScore()) ? palette.costH() : palette.costC()
        );
    }

    private @Nullable ResourceKey subjectResource() {
        if (kind() == DecisionPromptKind.materialFamilyAnchor) {
            if (pickedTagMember != null) {
                return pickedTagMember;
            }
            List<ResourceKey> candidates = tagCandidates();
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        if (prompt.primaryResource() != null) {
            return prompt.primaryResource();
        }
        if (prompt.primaryMachineId() != null) {
            ResourceKey resolved = machineIcon(prompt.primaryMachineId());
            return resolved != null ? resolved : ResourceKey.item(prompt.primaryMachineId());
        }
        if (prompt.recipeKey() != null) {
            RecipePreview preview = recipePreview();
            if (!preview.outputs().isEmpty()) {
                return preview.outputs().get(0);
            }
        }
        return null;
    }

    private @Nullable ResourceKey machineIcon(@Nullable String machineId) {
        CompiledRecipeGraph resolved = resolvedGraph();
        if (resolved == null) {
            return null;
        }
        for (ResourceKey candidate : resolved.allProducibleResources()) {
            if (candidate.resourceId().equals(machineId)) {
                return candidate;
            }
        }
        return null;
    }

    private void renderBody(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        int left = pad + 5;
        int top = bodyTop() + 4;
        int width = Math.max(24, cardWidth() - pad * 2 - 10);
        switch (kind()) {
            case machineAvailability -> {
                Component machine = CoPilotPaint
                        .machineName(machineIcon(prompt.primaryMachineId()), prompt.primaryMachineId());
                RecipePreview preview = recipePreview();
                boolean showMini = !preview.isEmpty() && width >= 140;
                int titleMaxW = showMini ? Math.max(48, width - 76) : width;
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(canvas, Component.literal(machine.getString()), titleMaxW),
                    left,
                    top,
                    palette.textPrimary()
                );
                if (showMini) {
                    renderMiniInlineFlow(canvas, palette, preview, left + width, top - 1);
                }
                renderChips(canvas, palette, left, top + 13, width);
                renderOwnedMachineStepper(canvas, palette, left, top + 30, width, mouseX, mouseY);
            }
            case materialFamilyAnchor -> {
                int gridY = top;
                tagSearchRect = null;
                tagSearchClearRect = null;
                if (showTagSearchBar()) {
                    renderTagSearchBar(canvas, palette, left, top, width, mouseX, mouseY);
                    gridY = top + tagSearchHeight + 4;
                }
                renderTagMembers(canvas, palette, left, gridY, width, mouseX, mouseY);
            }
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> {
                int textW = Math.max(24, width - 28);
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(
                        canvas,
                        CoPilotPaint.withSubject(
                            CirrusLangs.Workbench.Ledger.acquire,
                            WorkbenchFormatters.formatAmount(gatherTotal())
                        ),
                        textW
                    ),
                    left,
                    top,
                    palette.textPrimary()
                );
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(
                        canvas,
                        CoPilotPaint.withSubject(
                            CirrusLangs.Workbench.Ledger.stockCoverage,
                            CoPilotPaint.percent(costShare())
                        ),
                        textW
                    ),
                    left,
                    top + 11,
                    palette.textSecondary()
                );
                renderChips(canvas, palette, left, top + 23, width);
                renderStockOverrideBar(canvas, palette, left, top + 40, width, mouseX, mouseY);
            }
            case suspiciousJeiVerify, ruleConflictResolution -> {
                renderRecipePreview(canvas, palette, left, top, width);
                renderChips(canvas, palette, left, top + 20, width);
                renderAlternativeRecipes(canvas, palette, left, top + 34, width, mouseX, mouseY);
            }
        }
    }

    private void renderLeverageMeter(SceneCanvas canvas, int x, int y, int width, int height, CirrusPalette palette) {
        OreUiPaint.searchField(canvas, x, y, width, height, palette, false);
        int cells = Math.max(1, PromptDeltaFormat.leverageCells);
        double fraction = PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore());
        int targetLit = Math.max(0, Math.min(cells, (int) Math.round(fraction * cells)));
        boolean reduced = OreUiMotion.reducedMotion();
        long nowMs = OreUiMotion.nowMs();
        float sweep = OreUiMotion.cubicOut(nowMs - createdAtMs, 240L, reduced);
        int lit = Math.max(0, Math.min(targetLit, (int) Math.ceil(targetLit * sweep)));
        float leadPulse = fraction >= 0.75 ? OreUiMotion.pulse01(nowMs, 1400L, reduced) : 0f;
        int gap = 2;
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = Math.max(cells, width - 4);
        int innerH = Math.max(2, height - 4);
        int cellW = Math.max(2, (innerW - gap * (cells - 1)) / cells);
        boolean dark = OreUiPaint.isDark(palette);
        for (int index = 0; index < cells; index++) {
            int cx = innerX + index * (cellW + gap);
            int cw = index == cells - 1 ? Math.max(2, innerX + innerW - cx) : cellW;
            if (index < lit) {
                double ratio = (index + 1) / (double) cells;
                int fill = ratio > 0.75 ? palette.ruleHardBan() : ratio > 0.5 ? palette.costH() : palette.focusRing();
                if (index == lit - 1 && leadPulse > 0f) {
                    fill = OreUiPaint.lighten(fill, 0.18f * leadPulse);
                }
                canvas.fill(cx, innerY, cw, innerH, fill);
                if (innerH >= 4) {
                    canvas.fill(cx, innerY, cw, 1, OreUiPaint.lighten(fill, dark ? 0.32f : 0.24f));
                }
            } else {
                canvas.fill(cx, innerY, cw, innerH, OreUiPaint.meterTrack(palette));
            }
        }
    }

    private void renderMiniInlineFlow(
        SceneCanvas canvas,
        CirrusPalette palette,
        RecipePreview preview,
        int rightX,
        int y
    ) {
        int slotDim = 14;
        int step = slotDim - 1;
        int inCount = Math.min(2, preview.inputs().size());
        int outCount = Math.min(1, preview.outputs().size());
        int totalW = (inCount > 0 ? inCount * step + 1 : 0) + 12 + (outCount > 0 ? outCount * step + 1 : 0);
        int fx = rightX - totalW;
        for (int i = 0; i < inCount; i++) {
            OreUiSlotAmount.drawSlot(canvas, preview.inputs().get(i), 1L, fx, y - 1, slotDim, palette);
            fx += step;
        }
        if (inCount > 0) {
            fx += 1;
        }
        CoPilotPaint.text(canvas, Component.literal("\u2192"), fx + 2, y + 2, palette.focusRing());
        fx += 12;
        for (int i = 0; i < outCount; i++) {
            OreUiSlotAmount.drawSlot(canvas, preview.outputs().get(i), 1L, fx, y - 1, slotDim, palette);
            fx += step;
        }
    }

    private void renderChips(SceneCanvas canvas, CirrusPalette palette, int x, int y, int maxWidth) {
        if (prompt.options().isEmpty()) {
            return;
        }
        int cursor = x;
        int limit = x + maxWidth;
        for (PromptDeltaFormat.Chip chip : PromptDeltaFormat
                .deltaChips(prompt.options().get(0).raw().estimatedDelta())) {
            int chipWidth = CoPilotPaint.chipWidth(canvas, chip.label());
            if (cursor + chipWidth > limit) {
                return;
            }
            CoPilotPaint.chip(canvas, cursor, y, chip.label(), palette, chip.styleClass());
            cursor += chipWidth + 3;
        }
    }

    private void renderTagSearchBar(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        tagSearchRect = new Rect(x, y, width, tagSearchHeight);
        OreUiPaint.searchField(canvas, x, y, width, tagSearchHeight, palette, tagSearchFocused);
        int textRight = x + width - 4;
        if (!tagSearchQuery.isEmpty()) {
            tagSearchClearRect = new Rect(x + width - 14, y + 1, 13, tagSearchHeight - 2);
            boolean clearHov = tagSearchClearRect.contains(mouseX, mouseY);
            CoPilotPaint.text(
                canvas,
                Component.literal("\u00d7"),
                tagSearchClearRect.x() + 3,
                ChromeText.centeredY(y, tagSearchHeight, canvas),
                clearHov ? palette.textPrimary() : palette.textMuted()
            );
            textRight = tagSearchClearRect.x() - 2;
        }
        int textX = x + 5;
        int textW = Math.max(12, textRight - textX);
        if (tagSearchQuery.isEmpty()) {
            Component placeholder = CirrusLangs.Workbench.Copilot.tagSearchPlaceholder.get();
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, placeholder, textW),
                textX,
                ChromeText.centeredY(y, tagSearchHeight, canvas),
                palette.textMuted()
            );
        } else {
            String shown = tagSearchQuery;
            boolean blink = tagSearchFocused && ((OreUiMotion.nowMs() / 500L) % 2L == 0L);
            Component queryComp = Component.literal(blink ? shown + "|" : shown);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, queryComp, textW),
                textX,
                ChromeText.centeredY(y, tagSearchHeight, canvas),
                palette.textPrimary()
            );
        }
    }

    private void renderTagMembers(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int maxWidth,
        int mouseX,
        int mouseY
    ) {
        List<ResourceKey> candidates = filteredTagCandidates();
        int cols = tagGridColumns();
        int visibleRows = tagGridVisibleRows();
        int viewportH = visibleRows * (tagSlotSize + tagSlotGap);
        int maxScroll = tagGridMaxScroll();
        if (tagScrollY > maxScroll) {
            tagScrollY = maxScroll;
        }
        tagGridViewportRect = new Rect(x, y, maxWidth, viewportH);
        int barReserve = maxScroll > 0 ? 5 : 0;
        canvas.withClip(x, y, Math.max(12, maxWidth - barReserve), viewportH, () -> {
            if (candidates.isEmpty()) {
                CoPilotPaint.text(
                    canvas,
                    CoPilotPaint.fit(
                        canvas,
                        CirrusLangs.Workbench.Copilot.tagNoMatch.get(),
                        Math.max(16, maxWidth - barReserve - 4)
                    ),
                    x + 2,
                    ChromeText.centeredY(y, viewportH, canvas),
                    palette.textMuted()
                );
                return;
            }
            for (int index = 0; index < candidates.size(); index++) {
                int col = index % cols;
                int row = index / cols;
                int sx = x + col * (tagSlotSize + tagSlotGap);
                int sy = y + row * (tagSlotSize + tagSlotGap) - tagScrollY;
                if (sy + tagSlotSize < y || sy > y + viewportH) {
                    continue;
                }
                ResourceKey candidate = candidates.get(index);
                boolean hover = mouseX >= sx
                        && mouseX < sx + tagSlotSize
                        && mouseY >= sy
                        && mouseY < sy + tagSlotSize
                        && mouseY >= y
                        && mouseY < y + viewportH;
                boolean picked = Objects.equals(answeredOptionId, "pick:" + candidate.shortString());
                CoPilotPaint.renderSlotItem(canvas, CoPilotPaint.stackOf(candidate, 1L), sx, sy, tagSlotSize, palette);
                if (picked) {
                    canvas.strokeRect(sx, sy, tagSlotSize, tagSlotSize, palette.costS(), 1);
                } else if (hover) {
                    canvas.strokeRect(sx, sy, tagSlotSize, tagSlotSize, palette.focusRing(), 1);
                }
            }
        });
        if (maxScroll > 0 && viewportH > 12) {
            int trackX = x + maxWidth - 3;
            canvas.fill(trackX, y, 3, viewportH, OreUiPaint.cardDivider(palette));
            int totalH = viewportH + maxScroll;
            int thumbH = Math.max(10, Math.round(viewportH * (viewportH / (float) totalH)));
            int thumbY = y + Math.round(((viewportH - thumbH) * tagScrollY) / (float) maxScroll);
            canvas.fill(trackX, thumbY, 3, thumbH, palette.focusRing());
        }
    }

    private @Nullable ResourceKey tagCandidateAt(int localX, int localY) {
        if (kind() != DecisionPromptKind.materialFamilyAnchor) {
            return null;
        }
        int x = pad + 5;
        int y = bodyTop() + 4 + (showTagSearchBar() ? tagSearchHeight + 4 : 0);
        int viewportH = tagGridVisibleRows() * (tagSlotSize + tagSlotGap);
        if (localY < y || localY >= y + viewportH) {
            return null;
        }
        List<ResourceKey> candidates = filteredTagCandidates();
        int cols = tagGridColumns();
        for (int index = 0; index < candidates.size(); index++) {
            int col = index % cols;
            int row = index / cols;
            int sx = x + col * (tagSlotSize + tagSlotGap);
            int sy = y + row * (tagSlotSize + tagSlotGap) - tagScrollY;
            if (localX >= sx && localX < sx + tagSlotSize && localY >= sy && localY < sy + tagSlotSize) {
                return candidates.get(index);
            }
        }
        return null;
    }

    private void renderOwnedMachineStepper(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        Rect[] controls = ownedStepperRects(x, y, width);
        drawMiniButton(canvas, controls[0], "-", palette, controls[0].contains(mouseX, mouseY), false);
        CoPilotPaint.text(
            canvas,
            Component.literal("x" + ownedMachineCount),
            controls[0].right() + 4,
            ChromeText.centeredY(y, inlineControlHeight, canvas),
            palette.textPrimary()
        );
        drawMiniButton(canvas, controls[1], "+", palette, controls[1].contains(mouseX, mouseY), false);
        Component applyLabel = CirrusLangs.Workbench.Copilot.declareOwnedAction.get(ownedMachineCount);
        drawMiniButton(
            canvas,
            controls[2],
            applyLabel.getString(),
            palette,
            controls[2].contains(mouseX, mouseY),
            true
        );
    }

    private Rect[] ownedStepperRects(int x, int y, int width) {
        int btnW = 16;
        int countW = 24;
        Rect minus = new Rect(x, y, btnW, inlineControlHeight);
        Rect plus = new Rect(x + btnW + countW, y, btnW, inlineControlHeight);
        int applyX = plus.right() + 4;
        int applyW = Math.max(36, x + width - applyX);
        Rect apply = new Rect(applyX, y, applyW, inlineControlHeight);
        return new Rect[]{minus, plus, apply};
    }

    private void renderStockOverrideBar(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        Rect[] controls = stockOverrideRects(x, y, width);
        Component value = Component.literal(WorkbenchFormatters.formatAmount(declaredStockAmount()));
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, value, Math.max(18, controls[0].x() - x - 4)),
            x,
            ChromeText.centeredY(y, inlineControlHeight, canvas),
            palette.textPrimary()
        );
        drawMiniButton(canvas, controls[0], "+16", palette, controls[0].contains(mouseX, mouseY), false);
        drawMiniButton(canvas, controls[1], "+64", palette, controls[1].contains(mouseX, mouseY), false);
        drawMiniButton(
            canvas,
            controls[2],
            CirrusLangs.Workbench.Copilot.declareStockApply.get().getString(),
            palette,
            controls[2].contains(mouseX, mouseY),
            true
        );
    }

    private Rect[] stockOverrideRects(int x, int y, int width) {
        int applyW = 36;
        int stepW = 24;
        int applyX = x + width - applyW;
        int plus64X = applyX - stepW - 3;
        int plus16X = plus64X - stepW - 3;
        return new Rect[]{new Rect(plus16X, y, stepW, inlineControlHeight),
                new Rect(plus64X, y, stepW, inlineControlHeight), new Rect(applyX, y, applyW, inlineControlHeight)};
    }

    private void renderAlternativeRecipes(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        List<CompiledRecipe> alts = alternativeRecipes();
        for (int index = 0; index < alts.size(); index++) {
            CompiledRecipe alt = alts.get(index);
            int ry = y + index * altRecipeRowHeight;
            Rect row = new Rect(x, ry, width, altRecipeRowHeight - 2);
            boolean hover = row.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                row.x(),
                row.y(),
                row.width(),
                row.height(),
                palette,
                false,
                hover,
                false,
                false
            );
            Component prefix = CirrusLangs.Workbench.Copilot.switchAltRecipe.get();
            String recipeName = alt.key().explicitRecipeId().orElseGet(() -> alt.key().shortString());
            String label = prefix.getString() + ": " + recipeName;
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal(label), Math.max(16, row.width() - 8)),
                row.x() + 4,
                ChromeText.centeredButtonY(row.y(), row.height(), canvas),
                hover ? palette.focusRing() : palette.textSecondary()
            );
        }
    }

    private void drawMiniButton(
        SceneCanvas canvas,
        Rect rect,
        String label,
        CirrusPalette palette,
        boolean hover,
        boolean primary
    ) {
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hover,
            primary,
            false
        );
        Component fitted = CoPilotPaint.fit(canvas, Component.literal(label), Math.max(6, rect.width() - 4));
        int tw = canvas.font().width(fitted);
        canvas.drawString(
            fitted,
            rect.x() + Math.max(2, (rect.width() - tw) / 2),
            ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            primary ? palette.textOnAccent() : (hover ? palette.focusRing() : palette.textPrimary()),
            false
        );
    }

    private boolean handleInteractiveBodyClick(int lx, int ly) {
        int left = pad + 5;
        int top = bodyTop() + 4;
        int width = Math.max(24, cardWidth() - pad * 2 - 10);
        return switch (kind()) {
            case materialFamilyAnchor -> {
                if (tagSearchClearRect != null && tagSearchClearRect.contains(lx, ly)) {
                    tagSearchQuery = "";
                    tagScrollY = 0;
                    tagSearchFocused = true;
                    if (scene() != null) {
                        scene().requestFocus(this);
                    }
                    yield true;
                }
                if (tagSearchRect != null && tagSearchRect.contains(lx, ly)) {
                    tagSearchFocused = true;
                    if (scene() != null) {
                        scene().requestFocus(this);
                    }
                    yield true;
                }
                tagSearchFocused = false;
                ResourceKey member = tagCandidateAt(lx, ly);
                if (member != null) {
                    pickTagCandidate(member);
                    yield true;
                }
                yield false;
            }
            case machineAvailability -> {
                Rect[] controls = ownedStepperRects(left, top + 30, width);
                if (controls[0].contains(lx, ly)) {
                    ownedMachineCount = Math.max(1, ownedMachineCount - 1);
                    yield true;
                }
                if (controls[1].contains(lx, ly)) {
                    ownedMachineCount = Math.min(64, ownedMachineCount + 1);
                    yield true;
                }
                if (controls[2].contains(lx, ly) && prompt.primaryMachineId() != null) {
                    answeredOptionId = "declare_owned:" + ownedMachineCount;
                    host.refine(
                        new DeclareOwnedMachineFeedback(prompt.primaryMachineId(), ownedMachineCount, persistToWorld),
                        null
                    );
                    yield true;
                }
                yield false;
            }
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> {
                Rect[] controls = stockOverrideRects(left, top + 40, width);
                if (controls[0].contains(lx, ly)) {
                    declaredStockAmount = declaredStockAmount() + 16L;
                    yield true;
                }
                if (controls[1].contains(lx, ly)) {
                    declaredStockAmount = declaredStockAmount() + 64L;
                    yield true;
                }
                if (controls[2].contains(lx, ly) && prompt.primaryResource() != null) {
                    answeredOptionId = "override_stock:" + declaredStockAmount();
                    host.refine(new OverrideStockFeedback(prompt.primaryResource(), declaredStockAmount()), null);
                    yield true;
                }
                yield false;
            }
            case suspiciousJeiVerify, ruleConflictResolution -> {
                List<CompiledRecipe> alts = alternativeRecipes();
                for (int index = 0; index < alts.size(); index++) {
                    Rect row = new Rect(left, top + 34 + index * altRecipeRowHeight, width, altRecipeRowHeight - 2);
                    if (row.contains(lx, ly)) {
                        CompiledRecipe alt = alts.get(index);
                        if (!alt.outputs().isEmpty()) {
                            ResourceKey output = alt.outputs().get(0).resource();
                            answeredOptionId = "pin_alt:" + alt.key().shortString();
                            host.refine(new PinResourceRecipeFeedback(output, alt.key(), persistToWorld), null);
                            yield true;
                        }
                    }
                }
                yield false;
            }
        };
    }

    private void renderRecipePreview(SceneCanvas canvas, CirrusPalette palette, int x, int y, int maxWidth) {
        RecipePreview preview = recipePreview();
        if (preview.isEmpty()) {
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(
                    canvas,
                    Component.literal(prompt.recipeKey() == null ? "-" : prompt.recipeKey().shortString()),
                    maxWidth
                ),
                x,
                y + 4,
                palette.textSecondary()
            );
            return;
        }
        int cursor = x;
        int limit = x + maxWidth;
        for (ResourceKey input : preview.inputs()) {
            if (cursor + CoPilotPaint.iconSmall > limit - 28) {
                break;
            }
            CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(input, 1L), cursor, y, CoPilotPaint.iconSmall, palette);
            cursor += CoPilotPaint.iconSmall + 1;
        }
        cursor += 2;
        if (cursor + 10 <= limit) {
            canvas.fill(cursor, y + 7, 6, 1, palette.textMuted());
            canvas.fill(cursor + 4, y + 5, 1, 5, palette.textMuted());
            cursor += 10;
        }
        for (ResourceKey output : preview.outputs()) {
            if (cursor + CoPilotPaint.iconSmall > limit) {
                break;
            }
            CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(output, 1L), cursor, y, CoPilotPaint.iconSmall, palette);
            cursor += CoPilotPaint.iconSmall + 1;
        }
        if (prompt.systemId() != null) {
            Component sys = Component.literal(prompt.systemId());
            int chipW = CoPilotPaint.chipWidth(canvas, sys);
            if (cursor + 4 + chipW <= limit) {
                CoPilotPaint.chip(canvas, cursor + 4, y + 2, sys, palette, "danger");
            }
        }
    }

    private void renderOptions(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        int hovered = optionIndexAt(mouseX, mouseY);
        for (int index = 0; index < optionRowCount(); index++) {
            Rect rect = optionRect(index);
            boolean isHover = index == hovered;
            boolean isPseudo = index >= prompt.options().size();
            boolean isAnswered = !isPseudo
                    && Objects.equals(answeredOptionId, prompt.options().get(index).raw().optionId());
            if (isAnswered) {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    false,
                    isHover,
                    true,
                    false
                );
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    false,
                    isHover,
                    false,
                    false
                );
            }
            int rawDeltaReserve = isPseudo ? 0 : optionDeltaReserve(canvas, index);
            boolean showDelta = !isPseudo
                    && !isAnswered
                    && rawDeltaReserve > 0
                    && rect.width() - rawDeltaReserve - 12 >= 56;
            int deltaReserve = isAnswered ? 44 : showDelta ? rawDeltaReserve : 0;
            Component label = isPseudo
                    ? (allCandidatesExpanded
                            ? CirrusLangs.Workbench.Copilot.collapseTagCandidates.get()
                            : CirrusLangs.Workbench.Copilot.allTagCandidates.get())
                    : optionLabel(index);
            int ink = isAnswered ? palette.textOnAccent() : isPseudo ? palette.textMuted() : palette.textPrimary();
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, label, Math.max(12, rect.width() - deltaReserve - 12)),
                rect.x() + 6,
                ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
                ink
            );
            if (!isPseudo) {
                if (isAnswered) {
                    CoPilotPaint.textRight(
                        canvas,
                        CirrusLangs.Workbench.Copilot.appliedOption.get(),
                        rect.right() - 6,
                        ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
                        palette.textOnAccent()
                    );
                } else if (showDelta) {
                    renderOptionDelta(canvas, palette, index, rect);
                }
            }
        }
    }

    private int optionDeltaReserve(SceneCanvas canvas, int index) {
        ResolvedPromptOption option = prompt.options().get(index);
        List<PromptDeltaFormat.Chip> chips = PromptDeltaFormat.deltaChips(option.raw().estimatedDelta());
        if (chips.isEmpty()) {
            return 0;
        }
        int pick = Math.min(index, chips.size() - 1);
        return CoPilotPaint.chipWidth(canvas, chips.get(pick).label()) + 10;
    }

    private void renderOptionDelta(SceneCanvas canvas, CirrusPalette palette, int index, Rect rect) {
        ResolvedPromptOption option = prompt.options().get(index);
        List<PromptDeltaFormat.Chip> chips = PromptDeltaFormat.deltaChips(option.raw().estimatedDelta());
        if (chips.isEmpty()) {
            return;
        }
        int pick = Math.min(index, chips.size() - 1);
        PromptDeltaFormat.Chip chip = chips.get(pick);
        int chipWidth = CoPilotPaint.chipWidth(canvas, chip.label());
        int x = rect.x() + rect.width() - chipWidth - 5;
        if (x <= rect.x() + 8) {
            return;
        }
        CoPilotPaint.chip(
            canvas,
            x,
            rect.y() + Math.max(2, (rect.height() - 2 - CoPilotPaint.chipHeight) / 2),
            chip.label(),
            palette,
            chip.styleClass()
        );
    }

    private Component optionLabel(int index) {
        ResolvedPromptOption option = prompt.options().get(index);
        Component base = Component.translatable(option.raw().labelKey());
        if (option.variantResource() != null) {
            base = base.copy().append(Component.literal(": "))
                    .append(CoPilotPaint.resourceName(option.variantResource()));
        }
        if (option.raw().followUp().isPresent()) {
            return base.copy().append(Component.literal(" *"));
        }
        return base;
    }

    private void renderFooter(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        int footerY = preferredHeight() - 3 - footerHeight;
        int w = cardWidth();
        OreUiPaint.footerBand(canvas, 0, footerY, w, footerHeight, palette, false);
        int segH = Math.max(15, footerHeight - 4);
        int segY = footerY + Math.max(2, (footerHeight - segH) / 2);
        int innerW = Math.max(24, w - pad * 2);
        int half = Math.max(12, (innerW - 4) / 2);
        int rightW = Math.max(12, innerW - half - 4);
        renderScopeSegment(
            canvas,
            palette,
            pad,
            segY,
            half,
            segH,
            CirrusLangs.Workbench.Copilot.scopeSession.get(),
            !persistToWorld,
            ChromeText.hit(mouseX, mouseY, pad, segY, half, segH)
        );
        renderScopeSegment(
            canvas,
            palette,
            pad + half + 4,
            segY,
            rightW,
            segH,
            CirrusLangs.Workbench.Copilot.scopeWorld.get(),
            persistToWorld,
            ChromeText.hit(mouseX, mouseY, pad + half + 4, segY, rightW, segH)
        );
        int targetX = persistToWorld ? pad + half + 6 : pad + 2;
        int targetW = Math.max(6, (persistToWorld ? rightW : half) - 4);
        int glideX = Math.round(scopeIndicatorX.update(targetX, 22f));
        canvas.fill(glideX, segY + segH - 2, targetW, 2, palette.focusRing());
    }

    private void renderScopeSegment(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int height,
        Component label,
        boolean selected,
        boolean hovered
    ) {
        if (selected) {
            OreUiPaint.selectedToggle(canvas, x, y, width, height, palette, hovered);
        } else {
            OreUiPaint.raisedButton(canvas, x, y, width, height, palette, false, hovered, false, false);
        }
        Component fitted = CoPilotPaint.fit(canvas, label, Math.max(8, width - 8));
        int textW = canvas.font().width(fitted);
        canvas.drawString(
            fitted,
            x + Math.max(4, (width - textW) / 2),
            selected ? ChromeText.centeredY(y, height, canvas) : ChromeText.centeredButtonY(y, height, canvas),
            selected ? palette.focusRing() : (hovered ? palette.textPrimary() : palette.textSecondary()),
            false
        );
    }
}
