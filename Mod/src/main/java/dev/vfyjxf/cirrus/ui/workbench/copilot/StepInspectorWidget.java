package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionRelative;

/**
 * Inspector of the selected step: I/O slots, catalysts, machine and five-axis costs, with equivalent-candidate swap and six feedback shortcuts.
 */
public class StepInspectorWidget extends WidgetGroup<Widget> implements OreUiTooltipProvider {

    public static final int pad = 7;
    public static final int headerBandHeight = 18;
    public static final int summaryHeight = 36;
    public static final int rowHeight = 14;
    public static final int actionHeight = 20;
    public static final int slotRowHeight = 32;
    public static final int altHeaderHeight = 18;
    public static final int altRowHeight = 44;
    public static final int maxSlotRows = 5;
    public static final int maxAltRows = 4;

    public enum SlotKind {
        input, catalyst
    }

    public enum Action {
        answer, applyRules, overrideStock, declareOwned, rejectStep, pinRecipe
    }

    public record SlotRow(SlotKind kind, int slotIndex, ResourceKey chosen, List<ResourceKey> alternatives) {
        public SlotRow {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        }
    }

    private final CoPilotHost host;
    private List<ResolvedPrompt> prompts = List.of();
    private @Nullable CompiledRecipeGraph graph;
    private boolean persistToWorld;
    private @Nullable Consumer<ResourceKey> recipeAssistantOpener;

    public StepInspectorWidget(CoPilotHost host) {
        this.host = Objects.requireNonNull(host, "host");
        useStyle(flexShrink(0), positionRelative());
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            return handleClick((int) local.x(), (int) local.y()) ? EventDispatch.consumed : EventDispatch.pass;
        });
    }

    public StepInspectorWidget graph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        return this;
    }

    public StepInspectorWidget prompts(List<ResolvedPrompt> prompts) {
        this.prompts = prompts == null ? List.of() : List.copyOf(prompts);
        return this;
    }

    public StepInspectorWidget onOpenRecipeAssistant(@Nullable Consumer<ResourceKey> opener) {
        this.recipeAssistantOpener = opener;
        return this;
    }

    public Rect compareRecipesRect() {
        int top = alternativesTop();
        int btnW = 66;
        return new Rect(Math.max(pad, width() - pad - btnW), top, btnW, 14);
    }

    public boolean persistToWorld() {
        return persistToWorld;
    }

    public StepInspectorWidget setPersistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
        return this;
    }

    public Rect scopeRect() {
        int w = Math.max(1, width());
        int h = Math.max(preferredHeight(), height());
        return new Rect(pad, Math.max(0, h - 22), Math.max(24, w - pad * 2), 16);
    }

    public Optional<ProductionPlan> selectedPlan() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot == null ? Optional.empty() : snapshot.selectedPlan();
    }

    public Set<String> selectedStepIds() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot == null ? Set.of() : snapshot.selectedStepIds();
    }

    public @Nullable PlanStep selectedStep() {
        Set<String> selected = selectedStepIds();
        if (selected.isEmpty()) {
            return null;
        }
        String stepId = selected.iterator().next();
        for (PlanStep step : selectedPlan().map(ProductionPlan::steps).orElse(List.of())) {
            if (step.stepId().equals(stepId)) {
                return step;
            }
        }
        return null;
    }

    public List<SlotRow> slotRows() {
        PlanStep step = selectedStep();
        if (step == null) {
            return List.of();
        }
        List<SlotRow> rows = new ArrayList<>();
        switch (step) {
            case CraftStep craft -> {
                addSlotRows(rows, craft.slotBindings(), resolveRecipe(craft.recipeKey()));
                appendCatalystRows(rows, craft.catalystBindings());
            }
            case ConvertStep convert ->
                addSlotRows(rows, convert.slotBindings(), resolveRecipe(convert.conversionRecipeKey()));
            case CycleLoopStep loop -> {
                for (CraftStep template : loop.singleIterationTemplate()) {
                    addSlotRows(rows, template.slotBindings(), resolveRecipe(template.recipeKey()));
                }
            }
            case AcquireStep acquire -> rows.add(new SlotRow(SlotKind.input, 0, acquire.resource(), List.of()));
            default -> {}
        }
        return List.copyOf(rows.subList(0, Math.min(maxSlotRows, rows.size())));
    }

    private @Nullable CompiledRecipe resolveRecipe(@Nullable CompiledRecipeKey key) {
        CompiledRecipeGraph resolved = resolvedGraph();
        return key == null || resolved == null ? null : resolved.recipeByKey(key).orElse(null);
    }

    private void addSlotRows(List<SlotRow> rows, Map<Integer, SlotBinding> bindings, @Nullable CompiledRecipe recipe) {
        for (Map.Entry<Integer, SlotBinding> entry : bindings.entrySet()) {
            int slotIndex = entry.getKey();
            ResourceKey chosen = entry.getValue().chosenResource();
            List<ResourceKey> alts = entry.getValue().equivalentAlternatives();
            if (alts.isEmpty() && recipe != null) {
                alts = tagSlotAlternatives(recipe, slotIndex, chosen);
            }
            rows.add(new SlotRow(SlotKind.input, slotIndex, chosen, alts));
        }
    }

    private static List<ResourceKey> tagSlotAlternatives(CompiledRecipe recipe, int slotIndex, ResourceKey chosen) {
        InputSlot slot = inputSlotByIndex(recipe, slotIndex);
        if (slot == null || slot.candidates().size() <= 1) {
            return List.of();
        }
        List<ResourceKey> out = new ArrayList<>();
        for (ResourceKey candidate : slot.candidates()) {
            if (!candidate.equals(chosen)) {
                out.add(candidate);
                if (out.size() >= 4) {
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    private static @Nullable InputSlot inputSlotByIndex(@Nullable CompiledRecipe recipe, int slotIndex) {
        if (recipe == null) {
            return null;
        }
        for (InputSlot slot : recipe.inputs()) {
            if (slot.slotIndex() == slotIndex) {
                return slot;
            }
        }
        if (slotIndex >= 0 && slotIndex < recipe.inputs().size()) {
            return recipe.inputs().get(slotIndex);
        }
        return null;
    }

    private void appendCatalystRows(List<SlotRow> rows, Map<Integer, ResourceKey> catalysts) {
        for (Map.Entry<Integer, ResourceKey> entry : catalysts.entrySet()) {
            rows.add(new SlotRow(SlotKind.catalyst, entry.getKey(), entry.getValue(), List.of()));
        }
    }

    public List<Action> availableActions() {
        PlanStep step = selectedStep();
        if (step == null) {
            return List.of();
        }
        List<Action> actions = new ArrayList<>(Action.values().length);
        if (anchoredPrompt() != null) {
            actions.add(Action.answer);
        }
        if (step instanceof CraftStep || step instanceof ConvertStep) {
            actions.add(Action.applyRules);
        }
        if (step instanceof AcquireStep) {
            actions.add(Action.overrideStock);
        }
        if (machineOf(step) != null) {
            actions.add(Action.declareOwned);
        }
        actions.add(Action.rejectStep);
        if (recipeOf(step) != null && outputResource(step) != null) {
            actions.add(Action.pinRecipe);
        }
        return List.copyOf(actions);
    }

    public @Nullable ResolvedPrompt anchoredPrompt() {
        PlanStep step = selectedStep();
        if (step == null) {
            return null;
        }
        for (ResolvedPrompt prompt : prompts) {
            if (prompt.anchoredStepIds().contains(step.stepId())) {
                return prompt;
            }
        }
        return null;
    }

    public boolean perform(Action action) {
        PlanStep step = selectedStep();
        if (action == null || step == null) {
            return false;
        }
        return switch (action) {
            case answer -> answerAnchoredPrompt();
            case applyRules -> banStepRecipe(step);
            case overrideStock -> overrideStepStock(step);
            case declareOwned -> declareOwned(step);
            case rejectStep -> rejectStep(step);
            case pinRecipe -> pinStepRecipe(step);
        };
    }

    private boolean answerAnchoredPrompt() {
        ResolvedPrompt prompt = anchoredPrompt();
        if (prompt == null || prompt.options().isEmpty()) {
            return false;
        }
        host.refine(
            new AnswerPromptFeedback(prompt.raw().promptId(), prompt.options().get(0).raw().optionId(), persistToWorld),
            null
        );
        return true;
    }

    private boolean banStepRecipe(PlanStep step) {
        CompiledRecipeKey key = recipeOf(step);
        if (key == null) {
            return false;
        }
        GuidanceRule rule = new RecipeRule(
            CoPilotDnd.ruleId("ban_recipe", key.shortString()),
            CoPilotDnd.scopeFor(persistToWorld),
            RuleStrength.hardBan,
            key
        );
        host.refine(new ApplyRulesFeedback(List.of(rule), persistToWorld), null);
        return true;
    }

    private boolean overrideStepStock(PlanStep step) {
        if (!(step instanceof AcquireStep acquire)) {
            return false;
        }
        host.refine(new OverrideStockFeedback(acquire.resource(), acquire.amount()), null);
        return true;
    }

    private boolean declareOwned(PlanStep step) {
        String machineId = machineOf(step);
        if (machineId == null) {
            return false;
        }
        host.refine(new DeclareOwnedMachineFeedback(machineId, 1, persistToWorld), null);
        return true;
    }

    private boolean rejectStep(PlanStep step) {
        String planId = selectedPlan().map(ProductionPlan::planId).orElse("");
        if (planId.isEmpty()) {
            return false;
        }
        host.refine(new RejectStepFeedback(planId, step.stepId(), false, persistToWorld), null);
        return true;
    }

    private boolean pinStepRecipe(PlanStep step) {
        CompiledRecipeKey key = recipeOf(step);
        ResourceKey resource = outputResource(step);
        if (key == null || resource == null) {
            return false;
        }
        host.refine(new PinResourceRecipeFeedback(resource, key, persistToWorld), null);
        return true;
    }

    public boolean swapSlot(int slotIndex, ResourceKey alternative) {
        PlanStep step = selectedStep();
        if (step == null || alternative == null) {
            return false;
        }
        CompiledRecipe recipe = resolveRecipe(recipeOf(step));
        InputSlot slot = inputSlotByIndex(recipe, slotIndex);
        if (slot != null && slot.tagId().isPresent()) {
            String tagId = slot.tagId().get();
            GuidanceScope scope = persistToWorld ? GuidanceScope.worldSave : GuidanceScope.session;
            TagDefaultRule rule = new TagDefaultRule(
                "rule:tag:" + tagId,
                scope,
                RuleStrength.hardPin,
                tagId,
                alternative
            );
            host.refine(new ApplyRulesFeedback(List.of(rule), persistToWorld), null);
            return true;
        }
        CompiledRecipeKey key = alternativeRecipe(alternative, step);
        if (key == null) {
            return false;
        }
        host.refine(new PinResourceRecipeFeedback(alternative, key, persistToWorld), null);
        return true;
    }

    private @Nullable CompiledRecipeKey alternativeRecipe(ResourceKey alternative, PlanStep step) {
        CompiledRecipeGraph graph = resolvedGraph();
        if (graph != null) {
            for (CompiledRecipe producer : graph.producersOf(alternative)) {
                return producer.key();
            }
        }
        return recipeOf(step);
    }

    public @Nullable CompiledRecipeGraph resolvedGraph() {
        if (graph != null) {
            return graph;
        }
        if (graph == null) {
            CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
            if (runtime != null) {
                return runtime.currentGraph().orElse(null);
            }
        }
        return null;
    }

    public @Nullable ResourceKey outputResource(PlanStep step) {
        ResourceKey direct = outputOf(step);
        if (direct != null) {
            return direct;
        }
        CompiledRecipeKey key = recipeOf(step);
        CompiledRecipeGraph resolved = resolvedGraph();
        if (key == null || resolved == null) {
            return null;
        }
        return resolved.recipeByKey(key).filter(recipe -> !recipe.outputs().isEmpty())
                .map(recipe -> recipe.outputs().get(0).resource()).orElse(null);
    }

    public static @Nullable String machineOf(PlanStep step) {
        return switch (step) {
            case ProvisionMachineStep provision -> provision.machineId();
            case CraftStep craft -> craft.chosenMachineId().orElse(null);
            case SetupLineStep setup -> setup.primaryMachineId();
            default -> null;
        };
    }

    public static @Nullable CompiledRecipeKey recipeOf(PlanStep step) {
        return switch (step) {
            case CraftStep craft -> craft.recipeKey();
            case ConvertStep convert -> convert.conversionRecipeKey();
            case CycleLoopStep loop ->
                loop.singleIterationTemplate().isEmpty() ? null : loop.singleIterationTemplate().get(0).recipeKey();
            default -> null;
        };
    }

    public static @Nullable ResourceKey outputOf(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> acquire.resource();
            case LinePullStep pull -> pull.resource();
            case SetupLineStep setup -> setup.targetOutput();
            default -> null;
        };
    }

    public @Nullable ResourceKey inspectedTargetResource() {
        PlanStep step = selectedStep();
        if (step != null) {
            return outputResource(step);
        }
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot != null && !snapshot.targets().isEmpty()) {
            TargetDemand demand = snapshot.targets().get(0);
            return demand.resource();
        }
        return null;
    }

    public List<CompiledRecipe> alternativeRecipes() {
        CompiledRecipeGraph resolved = resolvedGraph();
        ResourceKey target = inspectedTargetResource();
        if (resolved == null || target == null) {
            return List.of();
        }
        List<CompiledRecipe> all = resolved.producersOf(target);
        return List.copyOf(all.subList(0, Math.min(maxAltRows, all.size())));
    }

    public int preferredHeight() {
        int altCount = alternativeRecipes().size();
        int altsHeight = altHeaderHeight + Math.max(1, altCount) * altRowHeight + 4;
        return headerBandHeight
                + summaryHeight
                + slotRows().size() * slotRowHeight
                + (availableActions().size() + 1) / 2 * (actionHeight + 3)
                + altsHeight
                + 28;
    }

    public Rect actionRect(int index) {
        int inner = Math.max(40, width() - pad * 2);
        int half = Math.max(20, (inner - 4) / 2);
        int top = actionsTop();
        int column = index % 2;
        int row = index / 2;
        return new Rect(pad + column * (half + 4), top + row * (actionHeight + 3), half, actionHeight);
    }

    public Rect slotRect(int index) {
        return new Rect(pad, slotsTop() + index * slotRowHeight, Math.max(1, width() - pad * 2), slotRowHeight - 2);
    }

    public int slotsTop() {
        PlanStep step = selectedStep();
        return step == null ? headerBandHeight + 28 : headerBandHeight + summaryHeight + 14;
    }

    public int actionsTop() {
        int slots = slotRows().size();
        return slotsTop() + (slots == 0 ? 0 : slots * slotRowHeight + 6);
    }

    public int alternativesTop() {
        int actionRows = (availableActions().size() + 1) / 2;
        return actionsTop() + (actionRows == 0 ? 0 : actionRows * (actionHeight + 3) + 8);
    }

    public Rect alternativeRect(int index) {
        return new Rect(
            pad,
            alternativesTop() + altHeaderHeight + index * altRowHeight,
            Math.max(1, width() - pad * 2),
            altRowHeight - 3
        );
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        List<SlotRow> slots = slotRows();
        for (int i = 0; i < slots.size(); i++) {
            if (slotRect(i).contains((int) localX, (int) localY)) {
                SlotRow row = slots.get(i);
                ResourceKey hoveredAlt = alternativeAt(row, (int) localX);
                if (hoveredAlt != null) {
                    return OreUiRichTooltip.builder(CoPilotPaint.resourceName(hoveredAlt)).icon(hoveredAlt)
                            .badge("SUBSTITUTE", OreUiRichTooltip.Tone.costC)
                            .footer("Click to switch ingredient variant").build();
                }
                OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(CoPilotPaint.resourceName(row.chosen()))
                        .icon(row.chosen())
                        .badge(row.kind().name().toUpperCase(Locale.ROOT), OreUiRichTooltip.Tone.accent).keyValue(
                            Component.literal("Slot Index"),
                            Component.literal("#" + (row.slotIndex() + 1)),
                            OreUiRichTooltip.Tone.primary
                        );
                if (!row.alternatives().isEmpty()) {
                    builder.keyValue(
                        Component.literal("Substitutes"),
                        Component.literal(row.alternatives().size() + " variants"),
                        OreUiRichTooltip.Tone.costC
                    );
                    builder.footer("Click a variant slot on the right to switch");
                }
                return builder.build();
            }
        }
        List<CompiledRecipe> alts = alternativeRecipes();
        for (int i = 0; i < alts.size(); i++) {
            if (alternativeRect(i).contains((int) localX, (int) localY)) {
                CompiledRecipe recipe = alts.get(i);
                String machine = recipe.machineRequirement().compatibleMachineIds().isEmpty()
                        ? "Hand / Crafting"
                        : CoPilotPaint.humanizeId(recipe.machineRequirement().compatibleMachineIds().get(0));
                List<OreUiRichTooltip.ItemBadge> inputs = new ArrayList<>();
                for (InputSlot in : recipe.inputs()) {
                    if (!in.candidates().isEmpty()) {
                        inputs.add(
                            OreUiRichTooltip.ItemBadge.of(
                                in.candidates().get(0),
                                in.amountPerBatch() > 1 ? "\u00d7" + in.amountPerBatch() : null
                            )
                        );
                    }
                }
                List<OreUiRichTooltip.ItemBadge> outputs = new ArrayList<>();
                for (OutputSlot out : recipe.outputs()) {
                    outputs.add(
                        OreUiRichTooltip.ItemBadge
                                .of(out.resource(), out.amountPerBatch() > 1 ? "\u00d7" + out.amountPerBatch() : null)
                    );
                }
                ResourceKey outKey = recipe.outputs().isEmpty() ? null : recipe.outputs().get(0).resource();
                return OreUiRichTooltip
                        .builder(
                            CoPilotPaint.humanizeId(recipe.key().explicitRecipeId().orElse(recipe.key().systemId()))
                        ).icon(outKey).subtitle(machine).badge("ALTERNATIVE", OreUiRichTooltip.Tone.positive)
                        .itemFlow(Component.literal("Recipe"), inputs, outputs)
                        .footer("Click to pin this recipe for target").build();
            }
        }
        return null;
    }

    public boolean selectAlternativeRecipe(CompiledRecipeKey recipeKey) {
        ResourceKey target = inspectedTargetResource();
        if (target == null || recipeKey == null) {
            return false;
        }
        host.refine(new PinResourceRecipeFeedback(target, recipeKey, persistToWorld), null);
        return true;
    }

    public boolean banAlternativeRecipe(CompiledRecipeKey recipeKey) {
        if (recipeKey == null) {
            return false;
        }
        GuidanceScope scope = persistToWorld ? GuidanceScope.worldSave : GuidanceScope.session;
        RecipeRule banRule = new RecipeRule(
            "rule:ban_rec:" + recipeKey.shortString(),
            scope,
            RuleStrength.hardBan,
            recipeKey
        );
        host.refine(new ApplyRulesFeedback(List.of(banRule), persistToWorld), null);
        return true;
    }

    public Rect alternativeBanRect(Rect row) {
        int size = 14;
        return new Rect(row.right() - size - 4, row.y() + 4, size, size);
    }

    private boolean handleClick(int localX, int localY) {
        if (scopeRect().contains(localX, localY)) {
            setPersistToWorld(!persistToWorld);
            CirrusUiSound.playClickSound();
            return true;
        }
        List<Action> actions = availableActions();
        for (int index = 0; index < actions.size(); index++) {
            if (actionRect(index).contains(localX, localY)) {
                boolean handled = perform(actions.get(index));
                if (handled) {
                    CirrusUiSound.playClickSound();
                }
                return handled;
            }
        }
        if (recipeAssistantOpener != null
                && inspectedTargetResource() != null
                && compareRecipesRect().contains(localX, localY)) {
            recipeAssistantOpener.accept(inspectedTargetResource());
            CirrusUiSound.playClickSound();
            return true;
        }
        List<CompiledRecipe> alternatives = alternativeRecipes();
        for (int index = 0; index < alternatives.size(); index++) {
            Rect row = alternativeRect(index);
            if (row.contains(localX, localY)) {
                boolean handled = alternativeBanRect(row).contains(localX, localY)
                        ? banAlternativeRecipe(alternatives.get(index).key())
                        : selectAlternativeRecipe(alternatives.get(index).key());
                if (handled) {
                    CirrusUiSound.playClickSound();
                }
                return handled;
            }
        }
        List<SlotRow> rows = slotRows();
        for (int index = 0; index < rows.size(); index++) {
            Rect row = slotRect(index);
            if (!row.contains(localX, localY)) {
                continue;
            }
            ResourceKey alternative = alternativeAt(rows.get(index), localX);
            if (alternative != null) {
                CirrusUiSound.playClickSound();
                return swapSlot(rows.get(index).slotIndex(), alternative);
            }
        }
        return false;
    }

    private static final int altSlotDim = 18;
    private static final int altSlotGap = 3;
    private static final int maxVisibleAlternatives = 4;

    public @Nullable ResourceKey alternativeAt(SlotRow row, int localX) {
        if (row.alternatives().isEmpty()) {
            return null;
        }
        int altCount = Math.min(maxVisibleAlternatives, row.alternatives().size());
        int cursor = alternativesLeft(row);
        for (int i = 0; i < altCount; i++) {
            if (localX >= cursor && localX < cursor + altSlotDim) {
                return row.alternatives().get(i);
            }
            cursor += altSlotDim + altSlotGap;
        }
        return null;
    }

    public int alternativesLeft(SlotRow row) {
        int altCount = Math.min(maxVisibleAlternatives, Math.max(1, row.alternatives().size()));
        int rectRight = pad + Math.max(1, width() - pad * 2);
        int altAreaW = altCount * (altSlotDim + altSlotGap);
        return Math.max(pad + 86, rectRight - 6 - altAreaW + altSlotGap);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = host.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        PlanStep step = selectedStep();
        OreUiPaint.raisedCard(canvas, 0, 0, width, height, palette, false, false);
        OreUiPaint.headerBand(canvas, 0, 0, width, headerBandHeight, palette, false);
        if (step == null) {
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, CirrusLangs.Workbench.Copilot.tabInspector.get(), width - pad * 2),
                pad,
                ChromeText.centeredY(1, headerBandHeight, canvas),
                palette.textPrimary()
            );
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, CirrusLangs.Workbench.Copilot.selectStepHint.get(), width - pad * 2),
                pad,
                headerBandHeight + 8,
                palette.textSecondary()
            );
            renderAlternatives(canvas, palette, null, width, height, mouseX, mouseY);
            renderScope(canvas, palette, mouseX, mouseY);
            return;
        }
        renderHeader(canvas, palette, step, width);
        renderSlots(canvas, palette, width);
        renderActions(canvas, palette, mouseX, mouseY);
        renderAlternatives(canvas, palette, step, width, height, mouseX, mouseY);
        renderScope(canvas, palette, mouseX, mouseY);
    }

    private void renderScope(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect rect = scopeRect();
        OreUiPaint.footerBand(canvas, 0, rect.y() - 3, width(), height() - rect.y() + 1, palette, false);
        int half = Math.max(20, (rect.width() - 4) / 2);
        renderScopeSegment(
            canvas,
            palette,
            new Rect(rect.x(), rect.y(), half, rect.height()),
            CirrusLangs.Workbench.Copilot.scopeSession.get(),
            !persistToWorld,
            mouseX,
            mouseY
        );
        renderScopeSegment(
            canvas,
            palette,
            new Rect(rect.x() + half + 4, rect.y(), half, rect.height()),
            CirrusLangs.Workbench.Copilot.scopeWorld.get(),
            persistToWorld,
            mouseX,
            mouseY
        );
    }

    private void renderScopeSegment(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        Component label,
        boolean selected,
        int mouseX,
        int mouseY
    ) {
        boolean hover = rect.contains(mouseX, mouseY);
        if (selected) {
            OreUiPaint.selectedToggle(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hover);
        } else {
            OreUiPaint.raisedButton(
                canvas,
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height(),
                palette,
                false,
                hover,
                false,
                false
            );
        }
        Component fitted = CoPilotPaint.fit(canvas, label, Math.max(8, rect.width() - 6));
        int textW = canvas.font().width(fitted);
        canvas.drawString(
            fitted,
            rect.x() + Math.max(3, (rect.width() - textW) / 2),
            selected
                    ? ChromeText.centeredY(rect.y(), rect.height(), canvas)
                    : ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            selected ? palette.focusRing() : (hover ? palette.textPrimary() : palette.textSecondary()),
            false
        );
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette palette, PlanStep step, int width) {
        CirrusPalette.StepTint tint = palette.stepTint(step);
        Component title = humanStepTitle(step);
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, title, width - pad * 2),
            pad,
            ChromeText.centeredY(1, headerBandHeight, canvas),
            tint.accent()
        );

        int sumY = headerBandHeight + 7;
        String machine = machineOf(step);
        ResourceKey iconRes = outputResource(step);
        if (iconRes == null && machine != null) {
            iconRes = ResourceKey.item(machine);
        }
        CoPilotPaint.renderSlotItem(
            canvas,
            iconRes == null ? null : CoPilotPaint.stackOf(iconRes, 1L),
            pad,
            sumY,
            22,
            palette
        );
        int textX = pad + 28;
        int textW = Math.max(12, width - textX - pad);
        Component line1 = machine != null
                ? StepNodeCardRenderer.machineNameOf(machine, null)
                : CirrusLangs.Workbench.Flow.nodeHandCraft.get();
        CoPilotPaint.text(canvas, CoPilotPaint.fit(canvas, line1, textW), textX, sumY + 1, palette.textPrimary());
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, plainCostLine(step), textW),
            textX,
            sumY + 13,
            palette.textSecondary()
        );
    }

    private Component humanStepTitle(PlanStep step) {
        ResourceKey out = outputResource(step);
        if (out != null) {
            return CoPilotPaint.resourceName(out);
        }
        if (step instanceof ProvisionMachineStep provision) {
            return StepNodeCardRenderer.machineNameOf(provision.machineId(), null);
        }
        return Component.literal(CoPilotPaint.humanizeId(step.stepId()));
    }

    private Component plainCostLine(PlanStep step) {
        var cost = step.stepCost();
        double human = cost.humanActiveSeconds();
        double passive = cost.passiveWaitSeconds();
        if (human > 0.05 && passive > 0.05) {
            return CirrusLangs.Workbench.Flow.timeBoth
                    .get(WorkbenchFormatters.formatSeconds(human), WorkbenchFormatters.formatSeconds(passive));
        }
        if (human > 0.05) {
            return CirrusLangs.Workbench.Flow.timeWork.get(WorkbenchFormatters.formatSeconds(human));
        }
        if (passive > 0.05) {
            return CirrusLangs.Workbench.Flow.timeWait.get(WorkbenchFormatters.formatSeconds(passive));
        }
        return CirrusLangs.Workbench.Flow.timeInstant.get();
    }

    private void renderSlots(SceneCanvas canvas, CirrusPalette palette, int width) {
        List<SlotRow> rows = slotRows();
        if (rows.isEmpty()) {
            return;
        }
        PlanStep step = selectedStep();
        CoPilotPaint.text(
            canvas,
            CirrusLangs.Workbench.Copilot.inputsLabel.get(),
            pad,
            slotsTop() - 10,
            palette.textMuted()
        );
        for (int index = 0; index < rows.size(); index++) {
            SlotRow row = rows.get(index);
            Rect rect = slotRect(index);
            OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, false, false);
            int innerTop = rect.y() + 2;
            int innerH = Math.max(14, rect.height() - 5);
            int slotDim = 18;
            int slotY = innerTop + Math.max(0, (innerH - slotDim) / 2);
            long qty = slotRequiredAmount(step, row);
            OreUiSlotAmount.drawSlot(canvas, row.chosen(), qty, rect.x() + 6, slotY, slotDim, palette);
            int rightCursor = rect.right() - 6;
            if (!row.alternatives().isEmpty()) {
                int altCount = Math.min(maxVisibleAlternatives, row.alternatives().size());
                int altStart = alternativesLeft(row);
                int cursor = altStart;
                for (int altIndex = 0; altIndex < altCount; altIndex++) {
                    ResourceKey alternative = row.alternatives().get(altIndex);
                    OreUiSlotAmount.drawSlot(canvas, alternative, 1L, cursor, slotY, altSlotDim, palette);
                    cursor += altSlotDim + altSlotGap;
                }
                rightCursor = altStart - 5;
            }
            String sourceTag = slotSourceLabel(row);
            if (!sourceTag.isEmpty() && rightCursor - (rect.x() + 72) > 46) {
                Component tagComp = Component.literal(sourceTag);
                int tagW = canvas.font().width(tagComp) + 10;
                int tagColor = row.kind() == SlotKind.catalyst ? palette.costC() : slotSourceColor(row, palette);
                OreUiPaint.tagPill(
                    canvas,
                    rightCursor - tagW,
                    innerTop + Math.max(0, (innerH - OreUiPaint.minPillHeight) / 2),
                    OreUiPaint.minPillHeight,
                    tagComp,
                    tagColor,
                    palette
                );
                rightCursor -= tagW + 4;
            }
            int nameMaxW = Math.max(12, rightCursor - (rect.x() + 29));
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, CoPilotPaint.resourceName(row.chosen()), nameMaxW),
                rect.x() + 29,
                ChromeText.centeredY(innerTop, innerH, canvas),
                palette.textPrimary()
            );
        }
    }

    private long slotRequiredAmount(@Nullable PlanStep step, SlotRow row) {
        if (step == null) {
            return 0L;
        }
        if (step instanceof AcquireStep acquire) {
            return acquire.amount();
        }
        CompiledRecipeKey key = recipeOf(step);
        CompiledRecipeGraph resolved = resolvedGraph();
        if (key == null || resolved == null) {
            return 0L;
        }
        long batches = step instanceof CraftStep craft
                ? Math.max(1L, craft.batches())
                : step instanceof ConvertStep convert ? Math.max(1L, convert.batches()) : 1L;
        CompiledRecipe recipe = resolved.recipeByKey(key).orElse(null);
        if (recipe == null || row.slotIndex() < 0 || row.slotIndex() >= recipe.inputs().size()) {
            return 0L;
        }
        long perBatch = Math.max(1L, recipe.inputs().get(row.slotIndex()).amountPerBatch());
        try {
            return Math.multiplyExact(perBatch, batches);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private String slotSourceLabel(SlotRow row) {
        if (row.kind() == SlotKind.catalyst) {
            return "Catalyst";
        }
        ProductionPlan plan = selectedPlan().orElse(null);
        if (plan == null) {
            return "";
        }
        for (PlanStep candidate : plan.steps()) {
            ResourceKey out = outputResource(candidate);
            if (row.chosen().equals(out)) {
                return switch (candidate) {
                    case AcquireStep ignored -> "Gather";
                    case CraftStep ignored -> "Craft";
                    case ConvertStep ignored -> "Convert";
                    case LinePullStep ignored -> "Line";
                    default -> "Step";
                };
            }
        }
        return "Stock";
    }

    private int slotSourceColor(SlotRow row, CirrusPalette palette) {
        String label = slotSourceLabel(row);
        return switch (label) {
            case "Gather" -> palette.acquireStep().accent();
            case "Craft" -> palette.craftStep().accent();
            case "Convert" -> palette.convertStep().accent();
            case "Line" -> palette.linePullStep().accent();
            default -> palette.costS();
        };
    }

    private void renderActions(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        List<Action> actions = availableActions();
        for (int index = 0; index < actions.size(); index++) {
            Rect rect = actionRect(index);
            boolean hover = rect.contains(mouseX, mouseY);
            boolean primary = actions.get(index) == Action.declareOwned || actions.get(index) == Action.pinRecipe;
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
            Component label = CoPilotPaint.fit(canvas, actionLabel(actions.get(index)), Math.max(8, rect.width() - 10));
            int textW = canvas.font().width(label);
            CoPilotPaint.text(
                canvas,
                label,
                rect.x() + Math.max(4, (rect.width() - textW) / 2),
                ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
                primary ? palette.textOnAccent() : palette.textPrimary()
            );
        }
    }

    private void renderAlternatives(
        SceneCanvas canvas,
        CirrusPalette palette,
        @Nullable PlanStep step,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        int top = alternativesTop();
        int limitY = scopeRect().y() - 6;
        if (top + altHeaderHeight >= limitY) {
            return;
        }
        List<CompiledRecipe> alternatives = alternativeRecipes();
        Component header = CirrusLangs.Workbench.Copilot.alternativeRecipes.get(alternatives.size());
        int headerMaxW = width - pad * 2;
        if (recipeAssistantOpener != null && inspectedTargetResource() != null) {
            Rect cmpBtn = compareRecipesRect();
            boolean cmpHover = cmpBtn.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                cmpBtn.x(),
                cmpBtn.y(),
                cmpBtn.width(),
                cmpBtn.height(),
                palette,
                false,
                cmpHover,
                false,
                false
            );
            Component cmpLabel = Component.literal("Compare...");
            int lw = canvas.font().width(cmpLabel);
            CoPilotPaint.text(
                canvas,
                cmpLabel,
                cmpBtn.x() + Math.max(3, (cmpBtn.width() - lw) / 2),
                ChromeText.centeredButtonY(cmpBtn.y(), cmpBtn.height(), canvas),
                cmpHover ? palette.focusRing() : palette.textPrimary()
            );
            headerMaxW = Math.max(16, cmpBtn.x() - pad - 6);
        }
        CoPilotPaint.text(canvas, CoPilotPaint.fit(canvas, header, headerMaxW), pad, top + 3, palette.textSecondary());
        if (alternatives.isEmpty()) {
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, CirrusLangs.Workbench.Copilot.noAlternatives.get(), width - pad * 2),
                pad,
                top + altHeaderHeight + 2,
                palette.textMuted()
            );
            return;
        }
        CompiledRecipeKey activeKey = step == null ? null : recipeOf(step);
        for (int index = 0; index < alternatives.size(); index++) {
            Rect rect = alternativeRect(index);
            if (rect.bottom() > limitY) {
                break;
            }
            CompiledRecipe candidate = alternatives.get(index);
            boolean active = activeKey != null && activeKey.equals(candidate.key());
            boolean hover = rect.contains(mouseX, mouseY);
            OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hover, active);

            Rect banBtn = alternativeBanRect(rect);
            boolean banHover = banBtn.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                banBtn.x(),
                banBtn.y(),
                banBtn.width(),
                banBtn.height(),
                palette,
                false,
                banHover,
                false,
                false
            );
            CoPilotPaint.text(
                canvas,
                Component.literal("\u2715"),
                banBtn.x() + 4,
                ChromeText.centeredButtonY(banBtn.y(), banBtn.height(), canvas),
                banHover ? palette.ruleHardBan() : palette.textMuted()
            );

            Component actionPill = active
                    ? CirrusLangs.Workbench.Copilot.activeRecipe.get()
                    : CirrusLangs.Workbench.Copilot.useRecipe.get();
            int pillW = canvas.font().width(actionPill) + 12;
            int pillX = banBtn.x() - pillW - 4;
            int pillY = rect.y() + 4;
            if (active) {
                OreUiPaint.tagPill(canvas, pillX, pillY, 13, actionPill, palette.costS(), palette);
            } else {
                OreUiPaint
                        .raisedButton(canvas, pillX, pillY, pillW, 14, palette, false, hover && !banHover, true, false);
                CoPilotPaint.text(
                    canvas,
                    actionPill,
                    pillX + 6,
                    ChromeText.centeredButtonY(pillY, 14, canvas),
                    palette.textOnAccent()
                );
            }

            int titleX = rect.x() + 6;
            if (!candidate.machineRequirement().compatibleMachineIds().isEmpty()) {
                String mId = candidate.machineRequirement().compatibleMachineIds().get(0);
                var mStack = CoPilotPaint.stackOf(ResourceKey.item(mId), 1L);
                if (mStack != null && !mStack.isEmpty()) {
                    CoPilotPaint.renderStack(canvas, mStack, titleX, rect.y() + 4, 11);
                    titleX += 14;
                }
            }
            String machineTitle = candidate.machineRequirement().compatibleMachineIds().isEmpty()
                    ? CoPilotPaint.humanizeId(candidate.key().systemId())
                    : StepNodeCardRenderer
                            .machineNameOf(candidate.machineRequirement().compatibleMachineIds().get(0), null)
                            .getString();
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal(machineTitle), Math.max(12, pillX - titleX - 6)),
                titleX,
                rect.y() + 6,
                palette.textPrimary()
            );

            // Contiguous OreUI recipe grid with Minecraft-native in-slot numbers.
            List<OreUiRecipeGrid.SlotEntry> gridIns = OreUiRecipeGrid.inputsOf(candidate);
            List<OreUiRecipeGrid.SlotEntry> gridOuts = OreUiRecipeGrid.outputsOf(candidate);
            OreUiRecipeGrid.Layout gridLayout = OreUiRecipeGrid.measure(
                gridIns.size(),
                gridOuts.size(),
                OreUiRecipeGrid.compactSlotSize,
                Math.max(60, rect.width() - 12),
                1
            );
            OreUiRecipeGrid.render(canvas, gridIns, gridOuts, gridLayout, rect.x() + 6, rect.y() + 20, palette);
        }
    }

    private static String recipeFormulaSummary(CompiledRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        int inLimit = Math.min(2, recipe.inputs().size());
        for (int index = 0; index < inLimit; index++) {
            InputSlot slot = recipe.inputs().get(index);
            if (index > 0) {
                sb.append(" + ");
            }
            sb.append(WorkbenchFormatters.formatAmount(slot.amountPerBatch())).append("× ")
                    .append(CoPilotPaint.resourceName(slot.candidates().get(0)).getString());
        }
        if (recipe.inputs().size() > inLimit) {
            sb.append(" +").append(recipe.inputs().size() - inLimit);
        }
        if (!recipe.outputs().isEmpty()) {
            OutputSlot out = recipe.outputs().get(0);
            sb.append(" → ").append(WorkbenchFormatters.formatAmount(out.amountPerBatch())).append("× ")
                    .append(CoPilotPaint.resourceName(out.resource()).getString());
        }
        return sb.toString();
    }

    public Component actionLabel(Action action) {
        return switch (action) {
            case answer -> CirrusLangs.Workbench.Copilot.tabPrompts.get();
            case applyRules -> CirrusLangs.Workbench.Action.banRecipe.get();
            case overrideStock -> CirrusLangs.Workbench.Action.overrideStock.get();
            case declareOwned -> CirrusLangs.Workbench.Action.declareOwned.get();
            case rejectStep -> CirrusLangs.Workbench.Copilot.skip.get();
            case pinRecipe -> CirrusLangs.Workbench.Action.replaceRecipe.get();
        };
    }
}
