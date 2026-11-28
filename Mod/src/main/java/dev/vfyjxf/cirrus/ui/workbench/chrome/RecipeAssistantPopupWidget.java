package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotDnd;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.copilot.PromptCardWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.PromptDeltaFormat;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.PromptSubjectResolver;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPromptOption;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Interactive recipe-assisted selection popup: search, filter, sort, compare graphical formulas and quantitative costs, then pin or ban in one click.
 */
public class RecipeAssistantPopupWidget extends Widget implements OreUiTooltipProvider {

    public static final float popupWidth = 560f;
    public static final float popupHeight = 336f;
    public static final int pad = 8;
    public static final int headerHeight = 32;
    public static final int filterBarHeight = 44;
    public static final int footerHeight = 26;
    public static final int cardHeight = 62;
    public static final int cardGap = 5;
    public static final long outsideClickGraceMillis = 120L;

    public enum Mode {
        recipeCompare, targetCatalogPicker, guidedDecisions
    }

    public enum FilterMode {
        all("All"), handFree("Hand / Free"), machineOnly("Machine"), lowInputs("<=2 Inputs");

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum SortMode {
        balanced("Balanced"), fastWork("Fast Work"), lowWait("Low Wait"), fewestInputs("Min Inputs");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record RecipeEntryView(
        CompiledRecipe recipe,
        boolean active,
        boolean handCraft,
        boolean machineNeededInPlan,
        double estWorkSeconds,
        double estWaitSeconds,
        long batchYield,
        int inputCount,
        String machineName,
        String namespace
    ) {}

    private record CatalogItemHit(Rect rect, ComponentStackKey resource) {}

    private record GuidedOptionHit(Rect cardRect, Rect applyRect, int optionIndex) {}

    private final WorkbenchShellContext shellContext;
    private Mode mode = Mode.recipeCompare;
    private boolean open;
    private @Nullable ComponentStackKey targetResource;
    private String searchQuery = "";
    private FilterMode filterMode = FilterMode.all;
    private SortMode sortMode = SortMode.balanced;
    private boolean persistToWorld;
    private int selectedIndex;
    private int scrollOffset;
    private int catalogScroll;
    private int catalogSelectedIndex;
    private long pickerAmount = 1L;
    private int guidedPromptIndex;
    private int lastHoveredGuidedOption = -1;
    private long openedAtMillis;
    private PromptCardWidget.@Nullable PreviewListener previewListener;
    private final List<Rect> filterRects = new ArrayList<>();
    private final List<Rect> sortRects = new ArrayList<>();
    private final List<Rect> pinRects = new ArrayList<>();
    private final List<Rect> banRects = new ArrayList<>();
    private final List<Rect> cardRects = new ArrayList<>();
    private final List<CatalogItemHit> catalogHits = new ArrayList<>();
    private final List<Rect> amountStepRects = new ArrayList<>();
    private final List<GuidedOptionHit> guidedOptionHits = new ArrayList<>();
    private @Nullable Rect setTargetBtnRect;
    private @Nullable Rect addBasketBtnRect;
    private @Nullable Rect guidedPrevRect;
    private @Nullable Rect guidedNextRect;
    private @Nullable Rect guidedSkipRect;
    private @Nullable Rect searchFieldRect;

    public RecipeAssistantPopupWidget(WorkbenchShellContext shellContext) {
        this.shellContext = Objects.requireNonNull(shellContext, "shellContext");
        addStyleClass("workbench-recipe-assistant");
        useStyle(positionAbsolute(), sizeOf(popupWidth, popupHeight));
        setInteractive(true);
        setFocusable(true);
        setVisible(false);
        setClickGroup(this);
        onClickOutside(ignored -> {
            if (open && System.currentTimeMillis() - openedAtMillis >= outsideClickGraceMillis) {
                hide();
            }
        });
        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!open || mode == Mode.guidedDecisions || Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            setSearchQuery(searchQuery + codePoint);
            ctx.consume();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, ctx) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                hide();
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (mode == Mode.guidedDecisions) {
                if (key == GLFW.GLFW_KEY_LEFT) {
                    stepGuidedPrompt(-1);
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (key == GLFW.GLFW_KEY_RIGHT) {
                    stepGuidedPrompt(1);
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    if (applyGuidedOption(0)) {
                        CirrusUiSound.playClickSound();
                    }
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                setSearchQuery(searchQuery.substring(0, searchQuery.length() - 1));
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                if (mode == Mode.targetCatalogPicker) {
                    moveCatalogSelection(1);
                } else {
                    moveSelection(1);
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                if (mode == Mode.targetCatalogPicker) {
                    moveCatalogSelection(-1);
                } else {
                    moveSelection(-1);
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                boolean acted = mode == Mode.targetCatalogPicker ? commitPickedTarget(false, null) : pinSelected();
                if (acted) {
                    CirrusUiSound.playClickSound();
                }
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, ctx) -> {
            if (!open || scrollY == 0.0) {
                return EventDispatch.pass;
            }
            int delta = scrollY > 0.0 ? -1 : 1;
            var local = sceneToLocal(mouseX, mouseY);
            if (mode == Mode.targetCatalogPicker && local.x < catalogPaneWidth()) {
                int max = Math.max(0, catalogItems().size() - catalogVisibleRows());
                catalogScroll = Math.max(0, Math.min(max, catalogScroll + delta));
                ctx.consume();
                return EventDispatch.consumed;
            }
            int maxScroll = Math.max(0, candidates().size() - visibleCapacity());
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseClicked((input, ctx) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            ctx.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, ctx) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            if (input.isRightClick()) {
                if (searchFieldRect != null && searchFieldRect.contains(local.x(), local.y())) {
                    if (!searchQuery.isEmpty()) {
                        setSearchQuery("");
                        CirrusUiSound.playClickSound();
                    }
                    ctx.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            if (handleClick(local.x(), local.y())) {
                CirrusUiSound.playClickSound();
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public RecipeAssistantPopupWidget previewListener(PromptCardWidget.@Nullable PreviewListener listener) {
        this.previewListener = listener;
        return this;
    }

    public Mode mode() {
        return mode;
    }

    public void openFor(@Nullable ComponentStackKey resource) {
        if (resource == null) {
            return;
        }
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        this.mode = Mode.recipeCompare;
        this.targetResource = resource;
        this.searchQuery = "";
        this.filterMode = FilterMode.all;
        this.sortMode = SortMode.balanced;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        this.open = true;
        this.openedAtMillis = System.currentTimeMillis();
        setVisible(true);
        setActive(true);
        if (lifecycle().mounted()) {
            scene().requestFocus(this);
        }
    }

    public void openTargetCatalogPicker() {
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        this.mode = Mode.targetCatalogPicker;
        this.searchQuery = "";
        this.filterMode = FilterMode.all;
        this.sortMode = SortMode.balanced;
        this.catalogScroll = 0;
        this.catalogSelectedIndex = 0;
        this.scrollOffset = 0;
        this.pickerAmount = 1L;
        List<ComponentStackKey> items = catalogItems();
        this.targetResource = items.isEmpty() ? null : items.get(0);
        this.open = true;
        this.openedAtMillis = System.currentTimeMillis();
        setVisible(true);
        setActive(true);
        if (lifecycle().mounted()) {
            scene().requestFocus(this);
        }
    }

    public void openGuidedDecisions() {
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        this.mode = Mode.guidedDecisions;
        this.guidedPromptIndex = 0;
        this.lastHoveredGuidedOption = -1;
        this.open = true;
        this.openedAtMillis = System.currentTimeMillis();
        setVisible(true);
        setActive(true);
        if (lifecycle().mounted()) {
            scene().requestFocus(this);
        }
    }

    public void hide() {
        this.open = false;
        this.lastHoveredGuidedOption = -1;
        if (previewListener != null) {
            previewListener.onPreview(null);
        }
        setVisible(false);
        setActive(false);
    }

    public boolean isOpen() {
        return open;
    }

    public @Nullable ComponentStackKey targetResource() {
        return targetResource;
    }

    public long pickerAmount() {
        return pickerAmount;
    }

    public void setPickerAmount(long amount) {
        this.pickerAmount = Math.max(1L, Math.min(999_999L, amount));
    }

    public List<ComponentStackKey> catalogItems() {
        List<PaletteItem> items = CommandPaletteEngine.queryResources(searchQuery, shellContext.activeGraph(), 1000);
        List<ComponentStackKey> out = new ArrayList<>(items.size());
        for (PaletteItem item : items) {
            ComponentStackKey res = switch (item.primaryAction()) {
                case PaletteAction.SetTarget setTarget -> setTarget.resource();
                case PaletteAction.AddToBasket add -> add.resource();
                default -> item.iconResource();
            };
            if (res != null) {
                out.add(res);
            }
        }
        return List.copyOf(out);
    }

    public List<ResolvedPrompt> guidedPrompts() {
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        CompiledRecipeGraph graph = shellContext.activeGraph();
        if (snapshot == null || snapshot.result() == null || graph == null) {
            return List.of();
        }
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        List<ResolvedPrompt> prompts = new ArrayList<>(
            PromptSubjectResolver.resolveAll(snapshot.result().decisionPrompts(), plan, graph)
        );
        prompts.sort(Comparator.comparingDouble((ResolvedPrompt p) -> p.raw().leverageScore()).reversed());
        return List.copyOf(prompts);
    }

    public boolean commitPickedTarget(boolean appendToBasket, @Nullable CompiledRecipeKey pinRecipeKey) {
        ComponentStackKey chosen = targetResource;
        if (chosen == null) {
            List<ComponentStackKey> items = catalogItems();
            if (items.isEmpty()) {
                return false;
            }
            chosen = items.get(0);
        }
        TargetDemand demand = new TargetDemand(chosen, Math.max(1L, pickerAmount));
        List<TargetDemand> nextTargets;
        if (appendToBasket) {
            List<TargetDemand> merged = new ArrayList<>(shellContext.activeSnapshot().targets());
            boolean updated = false;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).resource().equals(chosen)) {
                    long sum = merged.get(i).amount() + demand.amount();
                    merged.set(i, new TargetDemand(chosen, sum < 0L ? Long.MAX_VALUE : sum));
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                merged.add(demand);
            }
            nextTargets = List.copyOf(merged);
        } else {
            nextTargets = List.of(demand);
        }
        shellContext.setTargets(nextTargets, null);
        if (pinRecipeKey != null) {
            PlannerWorkbenchController controller = shellContext.activeController();
            if (controller != null) {
                controller.refine(new PinResourceRecipeFeedback(chosen, pinRecipeKey, persistToWorld), null);
            }
        }
        hide();
        return true;
    }

    public boolean applyGuidedOption(int optionIndex) {
        List<ResolvedPrompt> prompts = guidedPrompts();
        if (prompts.isEmpty()) {
            hide();
            return false;
        }
        int pIdx = Math.max(0, Math.min(prompts.size() - 1, guidedPromptIndex));
        ResolvedPrompt prompt = prompts.get(pIdx);
        if (optionIndex < 0 || optionIndex >= prompt.options().size()) {
            return false;
        }
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        ResolvedPromptOption option = prompt.options().get(optionIndex);
        Optional<HumanFeedback> followUp = option.raw().followUp();
        HumanFeedback feedback;
        if (followUp.isPresent()) {
            HumanFeedback rawFb = followUp.get();
            feedback = rawFb instanceof DeclareOwnedMachineFeedback owned
                    ? new DeclareOwnedMachineFeedback(owned.machine(), owned.ownedCount(), persistToWorld)
                    : rawFb;
        } else {
            feedback = new AnswerPromptFeedback(prompt.raw().promptId(), option.raw().optionId(), persistToWorld);
        }
        controller.refine(feedback, null);
        if (previewListener != null) {
            previewListener.onPreview(null);
        }
        if (prompts.size() <= 1) {
            hide();
        } else {
            guidedPromptIndex = Math.min(guidedPromptIndex, Math.max(0, prompts.size() - 2));
        }
        return true;
    }

    private void stepGuidedPrompt(int delta) {
        List<ResolvedPrompt> prompts = guidedPrompts();
        if (prompts.isEmpty()) {
            guidedPromptIndex = 0;
            return;
        }
        guidedPromptIndex = Math.floorMod(guidedPromptIndex + delta, prompts.size());
        lastHoveredGuidedOption = -1;
    }

    private void moveCatalogSelection(int delta) {
        List<ComponentStackKey> items = catalogItems();
        if (items.isEmpty()) {
            catalogSelectedIndex = 0;
            targetResource = null;
            return;
        }
        catalogSelectedIndex = Math.floorMod(catalogSelectedIndex + delta, items.size());
        targetResource = items.get(catalogSelectedIndex);
        scrollOffset = 0;
        int cap = catalogVisibleRows();
        if (catalogSelectedIndex < catalogScroll) {
            catalogScroll = catalogSelectedIndex;
        } else if (catalogSelectedIndex >= catalogScroll + cap) {
            catalogScroll = Math.max(0, catalogSelectedIndex - cap + 1);
        }
    }

    private int catalogPaneWidth() {
        return Math.min(220, Math.max(150, width() * 38 / 100));
    }

    private int catalogVisibleRows() {
        int listH = Math.max(20, height() - headerHeight - footerHeight - 36);
        return Math.max(1, listH / 22);
    }

    public String searchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(@Nullable String query) {
        this.searchQuery = query == null ? "" : query;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        if (mode == Mode.targetCatalogPicker) {
            this.catalogScroll = 0;
            this.catalogSelectedIndex = 0;
            List<ComponentStackKey> items = catalogItems();
            this.targetResource = items.isEmpty() ? null : items.get(0);
        }
    }

    public FilterMode filterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode mode) {
        this.filterMode = Objects.requireNonNull(mode, "mode");
        this.selectedIndex = 0;
        this.scrollOffset = 0;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode mode) {
        this.sortMode = Objects.requireNonNull(mode, "mode");
        this.selectedIndex = 0;
        this.scrollOffset = 0;
    }

    public boolean persistToWorld() {
        return persistToWorld;
    }

    public void setPersistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
    }

    public List<RecipeEntryView> candidates() {
        CompiledRecipeGraph graph = shellContext.activeGraph();
        if (graph == null || targetResource == null) {
            return List.of();
        }
        List<CompiledRecipe> raw = graph.producersOf(targetResource);
        if (raw.isEmpty()) {
            return List.of();
        }
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        CompiledRecipeKey activeKey = activeRecipeFor(plan, graph, targetResource);
        Set<MachineEntry> provisionedMachines = provisionedMachinesOf(plan);
        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);

        List<RecipeEntryView> filtered = new ArrayList<>(raw.size());
        for (CompiledRecipe recipe : raw) {
            boolean hand = recipe.machineRequirement().compatibleMachines().isEmpty();
            MachineEntry machine = hand ? null : recipe.machineRequirement().compatibleMachines().get(0);
            String machineName = hand ? "Hand / Crafting" : CoPilotPaint.machineName(machine).getString();
            String ns = recipe.key().explicitRecipeId().map(ResourceLocation::getNamespace)
                    .orElse(recipe.key().systemId().namespace());
            boolean needsMach = machine != null && provisionedMachines.contains(machine);
            boolean active = activeKey != null && activeKey.equals(recipe.key());
            long yield = recipe.outputs().isEmpty() ? 1L : Math.max(1L, recipe.outputs().get(0).amountPerBatch());
            int inCount = recipe.inputs().size();
            double estWork = estimateWorkSeconds(recipe, hand);
            double estWait = estimateWaitSeconds(recipe, hand);

            if (!matchesFilter(hand, inCount)) {
                continue;
            }
            if (!needle.isEmpty() && !matchesSearch(recipe, machineName, ns, needle)) {
                continue;
            }
            filtered.add(
                new RecipeEntryView(recipe, active, hand, needsMach, estWork, estWait, yield, inCount, machineName, ns)
            );
        }

        Comparator<RecipeEntryView> order = switch (sortMode) {
            case balanced -> Comparator.comparing(RecipeEntryView::active).reversed()
                    .thenComparingDouble(v -> v.estWorkSeconds() + v.estWaitSeconds() * 0.35 + v.inputCount() * 0.5);
            case fastWork -> Comparator.comparingDouble(RecipeEntryView::estWorkSeconds)
                    .thenComparingDouble(RecipeEntryView::estWaitSeconds);
            case lowWait -> Comparator.comparingDouble(RecipeEntryView::estWaitSeconds)
                    .thenComparingDouble(RecipeEntryView::estWorkSeconds);
            case fewestInputs -> Comparator.comparingInt(RecipeEntryView::inputCount)
                    .thenComparingDouble(RecipeEntryView::estWorkSeconds);
        };
        filtered.sort(order);
        return List.copyOf(filtered);
    }

    public boolean pinRecipe(CompiledRecipeKey key) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null || targetResource == null || key == null) {
            return false;
        }
        controller.refine(new PinResourceRecipeFeedback(targetResource, key, persistToWorld), null);
        hide();
        return true;
    }

    public boolean banRecipe(CompiledRecipeKey key) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null || key == null) {
            return false;
        }
        RecipeRule rule = new RecipeRule(
            CoPilotDnd.ruleId("ban_recipe", key.shortString()),
            persistToWorld ? GuidanceScope.worldSave : GuidanceScope.session,
            RuleStrength.hardBan,
            key
        );
        controller.refine(new ApplyRulesFeedback(List.of(rule), persistToWorld), null);
        return true;
    }

    public boolean pinSelected() {
        List<RecipeEntryView> list = candidates();
        if (list.isEmpty()) {
            return false;
        }
        int idx = Math.max(0, Math.min(list.size() - 1, selectedIndex));
        return pinRecipe(list.get(idx).recipe().key());
    }

    private void moveSelection(int delta) {
        List<RecipeEntryView> list = candidates();
        if (list.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, list.size());
        int cap = visibleCapacity();
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + cap) {
            scrollOffset = Math.max(0, selectedIndex - cap + 1);
        }
    }

    private int visibleCapacity() {
        int bodyH = Math.max(cardHeight, height() - headerHeight - filterBarHeight - footerHeight - 10);
        return Math.max(1, bodyH / (cardHeight + cardGap));
    }

    private Rect closeRect() {
        return new Rect(width() - pad - 16, 8, 16, 16);
    }

    private Rect sessionScopeRect() {
        int fy = height() - footerHeight + 5;
        return new Rect(pad, fy, 64, 16);
    }

    private Rect worldScopeRect() {
        int fy = height() - footerHeight + 5;
        return new Rect(pad + 68, fy, 74, 16);
    }

    private boolean handleClick(double localX, double localY) {
        if (closeRect().contains(localX, localY)) {
            hide();
            return true;
        }
        if (sessionScopeRect().contains(localX, localY)) {
            persistToWorld = false;
            return true;
        }
        if (worldScopeRect().contains(localX, localY)) {
            persistToWorld = true;
            return true;
        }
        if (mode == Mode.guidedDecisions) {
            if (guidedPrevRect != null && guidedPrevRect.contains(localX, localY)) {
                stepGuidedPrompt(-1);
                return true;
            }
            if (guidedNextRect != null && guidedNextRect.contains(localX, localY)) {
                stepGuidedPrompt(1);
                return true;
            }
            if (guidedSkipRect != null && guidedSkipRect.contains(localX, localY)) {
                PlannerWorkbenchController controller = shellContext.activeController();
                if (controller != null) {
                    controller.state().setGuidedPauseDismissed(true);
                }
                hide();
                return true;
            }
            for (GuidedOptionHit hit : guidedOptionHits) {
                if (hit.applyRect().contains(localX, localY) || hit.cardRect().contains(localX, localY)) {
                    return applyGuidedOption(hit.optionIndex());
                }
            }
            return false;
        }
        if (mode == Mode.targetCatalogPicker) {
            for (int i = 0; i < catalogHits.size(); i++) {
                CatalogItemHit hit = catalogHits.get(i);
                if (hit.rect().contains(localX, localY)) {
                    catalogSelectedIndex = catalogScroll + i;
                    targetResource = hit.resource();
                    scrollOffset = 0;
                    return true;
                }
            }
            long[] presets = {-1L, 1L, 16L, 64L, 1000L};
            for (int i = 0; i < amountStepRects.size() && i < presets.length; i++) {
                if (amountStepRects.get(i).contains(localX, localY)) {
                    if (presets[i] < 0L) {
                        setPickerAmount(Math.max(1L, pickerAmount - 1L));
                    } else if (presets[i] == 1000L) {
                        setPickerAmount(pickerAmount + 1L);
                    } else {
                        setPickerAmount(presets[i]);
                    }
                    return true;
                }
            }
            if (setTargetBtnRect != null && setTargetBtnRect.contains(localX, localY)) {
                return commitPickedTarget(false, null);
            }
            if (addBasketBtnRect != null && addBasketBtnRect.contains(localX, localY)) {
                return commitPickedTarget(true, null);
            }
            List<RecipeEntryView> list = candidates();
            for (int i = 0; i < cardRects.size(); i++) {
                int dataIndex = scrollOffset + i;
                if (dataIndex >= list.size()) {
                    break;
                }
                if (i < banRects.size() && banRects.get(i).contains(localX, localY)) {
                    return commitPickedTarget(true, list.get(dataIndex).recipe().key());
                }
                if (i < pinRects.size() && pinRects.get(i).contains(localX, localY)) {
                    return commitPickedTarget(false, list.get(dataIndex).recipe().key());
                }
                if (cardRects.get(i).contains(localX, localY)) {
                    selectedIndex = dataIndex;
                    return true;
                }
            }
            return false;
        }
        FilterMode[] filters = FilterMode.values();
        for (int i = 0; i < filterRects.size() && i < filters.length; i++) {
            if (filterRects.get(i).contains(localX, localY)) {
                setFilterMode(filters[i]);
                return true;
            }
        }
        SortMode[] sorts = SortMode.values();
        for (int i = 0; i < sortRects.size() && i < sorts.length; i++) {
            if (sortRects.get(i).contains(localX, localY)) {
                setSortMode(sorts[i]);
                return true;
            }
        }
        List<RecipeEntryView> list = candidates();
        for (int i = 0; i < cardRects.size(); i++) {
            int dataIndex = scrollOffset + i;
            if (dataIndex >= list.size()) {
                break;
            }
            if (i < banRects.size() && banRects.get(i).contains(localX, localY)) {
                return banRecipe(list.get(dataIndex).recipe().key());
            }
            if (i < pinRects.size() && pinRects.get(i).contains(localX, localY)) {
                return pinRecipe(list.get(dataIndex).recipe().key());
            }
            if (cardRects.get(i).contains(localX, localY)) {
                selectedIndex = dataIndex;
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!open) {
            return null;
        }
        List<RecipeEntryView> list = candidates();
        for (int i = 0; i < cardRects.size(); i++) {
            int dataIndex = scrollOffset + i;
            if (dataIndex >= list.size()) {
                break;
            }
            if (cardRects.get(i).contains((int) localX, (int) localY)) {
                RecipeEntryView view = list.get(dataIndex);
                List<OreUiRichTooltip.ItemBadge> ins = new ArrayList<>();
                for (InputSlot in : view.recipe().inputs()) {
                    if (!in.candidates().isEmpty()) {
                        ins.add(OreUiRichTooltip.ItemBadge.of(in.candidates().get(0), "\u00d7" + in.amountPerBatch()));
                    }
                }
                List<OreUiRichTooltip.ItemBadge> outs = new ArrayList<>();
                for (OutputSlot out : view.recipe().outputs()) {
                    outs.add(OreUiRichTooltip.ItemBadge.of(out.resource(), "\u00d7" + out.amountPerBatch()));
                }
                return OreUiRichTooltip.builder(view.machineName()).icon(targetResource)
                        .badge(
                            view.active() ? "ACTIVE" : view.namespace().toUpperCase(Locale.ROOT),
                            view.active() ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                        ).subtitle(
                            view.recipe().key().explicitRecipeId().map(ResourceLocation::toString)
                                    .orElseGet(() -> view.recipe().key().systemId().toString())
                        )
                        .itemFlow(Component.literal("Recipe Formula"), ins, outs)
                        .keyValue(
                            "Est. Work",
                            WorkbenchFormatters.formatSeconds(view.estWorkSeconds()),
                            OreUiRichTooltip.Tone.costH
                        )
                        .keyValue(
                            "Est. Wait",
                            WorkbenchFormatters.formatSeconds(view.estWaitSeconds()),
                            OreUiRichTooltip.Tone.costP
                        ).footer("Click Pin to apply to plan \u00b7 Click Ban to exclude").build();
            }
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open || width() <= 0 || height() <= 0) {
            return;
        }
        CirrusPalette p = shellContext.palette();
        int w = width();
        int h = height();

        long elapsed = Math.max(0L, System.currentTimeMillis() - openedAtMillis);
        float progress = OreUiMotion.cubicOut(elapsed, OreUiMotion.overlayAppearMs, OreUiMotion.reducedMotion());
        float slideY = (1f - progress) * 6f;
        int sw = Math.max(0, Math.round(shellContext.shellWidth()));
        int sh = Math.max(0, Math.round(shellContext.shellHeight()));
        if (sw > 0 && sh > 0) {
            canvas.fill(-posX(), -posY(), sw, sh, OreUiPaint.withAlpha(p.dropShadow(), 0.48f * progress));
        }

        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            OreUiPaint.popover(canvas, 0, 0, w, h, p);
            if (mode == Mode.targetCatalogPicker) {
                renderTargetCatalogPicker(canvas, p, w, h, mouseX, mouseY);
            } else if (mode == Mode.guidedDecisions) {
                renderGuidedDecisions(canvas, p, w, h, mouseX, mouseY);
            } else {
                renderHeader(canvas, p, w, mouseX, mouseY);
                renderFilterAndSortBar(canvas, p, w, mouseX, mouseY);
                renderRecipeCards(canvas, p, w, h, mouseX, mouseY);
                renderFooter(canvas, p, w, h, mouseX, mouseY);
            }
        } finally {
            canvas.popTransform();
        }
    }

    private void renderTargetCatalogPicker(SceneCanvas canvas, CirrusPalette p, int w, int h, int mouseX, int mouseY) {
        catalogHits.clear();
        amountStepRects.clear();
        cardRects.clear();
        pinRects.clear();
        banRects.clear();
        setTargetBtnRect = null;
        addBasketBtnRect = null;

        // Header
        OreUiPaint.headerBand(canvas, 0, 0, w, headerHeight - 1, p, false);
        ChromeButtons.searchLens(canvas, pad + 3, 12, p.focusRing());
        ChromeText.drawFitted(
            canvas,
            Component.literal("Catalog Target & Recipe Picker"),
            pad + 18,
            6,
            Math.max(60, w - 180),
            p.textPrimary()
        );
        List<ComponentStackKey> items = catalogItems();
        ChromeText.drawFitted(
            canvas,
            Component.literal("Search any item (@mod supported) and pick its recipe directly in Planner"),
            pad + 18,
            18,
            Math.max(60, w - 180),
            p.textMuted()
        );
        Component countPill = Component.literal(items.size() + " items");
        int pillW = ChromeText.width(canvas, countPill) + 10;
        Rect close = closeRect();
        OreUiPaint.tagPill(canvas, close.x() - pillW - 8, 9, OreUiPaint.minPillHeight, countPill, p.focusRing(), p);
        drawCloseButton(canvas, p, close, mouseX, mouseY);

        int bodyTop = headerHeight + 5;
        int bodyBottom = h - footerHeight - 5;
        int bodyH = Math.max(40, bodyBottom - bodyTop);
        int leftW = catalogPaneWidth();
        int leftX = pad;

        // Left pane: search field + scrollable full-catalog item list
        int searchH = 18;
        searchFieldRect = new Rect(leftX, bodyTop, leftW, searchH);
        OreUiPaint.searchField(canvas, leftX, bodyTop, leftW, searchH, p, focused());
        String placeholder = "Search all items or @mod...";
        Component queryDisp = searchQuery.isEmpty() ? Component.literal(placeholder) : Component.literal(searchQuery);
        ChromeText.drawFitted(
            canvas,
            queryDisp,
            leftX + 6,
            ChromeText.centeredY(bodyTop, searchH, canvas),
            leftW - 12,
            searchQuery.isEmpty() ? p.textMuted() : p.textPrimary()
        );
        if (focused()) {
            int caretX = leftX + 6 + ChromeText.width(canvas, Component.literal(searchQuery));
            canvas.fill(Math.min(leftX + leftW - 4, caretX), bodyTop + 4, 1, searchH - 8, p.focusRing());
        }

        int listTop = bodyTop + searchH + 4;
        int listH = Math.max(20, bodyBottom - listTop);
        OreUiPaint.searchField(canvas, leftX, listTop, leftW, listH, p, false);
        int rowH = 22;
        int visible = Math.max(1, (listH - 4) / rowH);
        int maxCatScroll = Math.max(0, items.size() - visible);
        catalogScroll = Math.max(0, Math.min(maxCatScroll, catalogScroll));
        CompiledRecipeGraph graph = shellContext.activeGraph();
        if (items.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("No items match"),
                leftX + 8,
                listTop + 10,
                leftW - 16,
                p.textMuted()
            );
        } else {
            for (int i = 0; i < visible && catalogScroll + i < items.size(); i++) {
                ComponentStackKey res = items.get(catalogScroll + i);
                int ry = listTop + 2 + i * rowH;
                Rect r = new Rect(leftX + 2, ry, leftW - 4, rowH - 1);
                catalogHits.add(new CatalogItemHit(r, res));
                boolean selected = res.equals(targetResource);
                boolean hover = r.contains(mouseX, mouseY);
                if (selected) {
                    canvas.fill(r.x(), r.y(), r.width(), r.height(), p.cardSurfaceSelected());
                    canvas.strokeRect(r.x(), r.y(), r.width(), r.height(), p.focusRing(), 1);
                } else if (hover) {
                    canvas.fill(r.x(), r.y(), r.width(), r.height(), p.cardSurfaceHover());
                }
                OreUiPaint.slot(canvas, r.x() + 3, r.y() + 2, 17, p);
                ChromeButtons.drawMiniItem(canvas, res, p, r.x() + 4, r.y() + 3, 15);
                int recCount = graph == null ? 0 : graph.producersOf(res).size();
                String badge = recCount > 0 ? recCount + "r" : "Raw";
                int bw = ChromeText.width(canvas, Component.literal(badge)) + 6;
                ChromeText.drawFitted(
                    canvas,
                    CoPilotPaint.resourceName(res),
                    r.x() + 24,
                    ChromeText.centeredY(r.y(), r.height(), canvas),
                    Math.max(16, r.width() - 28 - bw),
                    selected || hover ? p.textPrimary() : p.textSecondary()
                );
                ChromeText.drawRight(
                    canvas,
                    Component.literal(badge),
                    r.right() - 4,
                    ChromeText.centeredY(r.y(), r.height(), canvas),
                    recCount > 0 ? p.costS() : p.textMuted()
                );
            }
        }

        // Right pane: selected target actions + amount stepper + visual recipe selector
        int rightX = leftX + leftW + 8;
        int rightW = Math.max(120, w - rightX - pad);
        OreUiPaint.searchField(canvas, rightX, bodyTop, rightW, bodyH, p, false);
        if (targetResource == null) {
            ChromeText.draw(
                canvas,
                Component.literal("Select an item on the left"),
                rightX + 10,
                bodyTop + 14,
                p.textMuted()
            );
            renderFooter(canvas, p, w, h, mouseX, mouseY);
            return;
        }

        int topBarY = bodyTop + 5;
        CoPilotPaint
                .renderSlotItem(canvas, CoPilotPaint.stackOf(targetResource, pickerAmount), rightX + 6, topBarY, 22, p);
        ChromeText.drawFitted(
            canvas,
            CoPilotPaint.resourceName(targetResource),
            rightX + 34,
            topBarY + 1,
            Math.max(40, rightW - 170),
            p.textPrimary()
        );
        ChromeText.drawFitted(
            canvas,
            Component.literal("Qty: \u00d7" + WorkbenchFormatters.formatAmount(pickerAmount)),
            rightX + 34,
            topBarY + 13,
            Math.max(40, rightW - 170),
            p.focusRing()
        );

        int addW = 58;
        int setW = 70;
        addBasketBtnRect = new Rect(rightX + rightW - addW - 6, topBarY + 2, addW, 18);
        setTargetBtnRect = new Rect(addBasketBtnRect.x() - setW - 4, topBarY + 2, setW, 18);
        drawSmallActionBtn(canvas, p, setTargetBtnRect, "Set Target", true, mouseX, mouseY);
        drawSmallActionBtn(canvas, p, addBasketBtnRect, "+ Basket", false, mouseX, mouseY);

        // Quantity quick-step strip
        int qtyY = topBarY + 26;
        String[] qtyLabels = {"-", "\u00d71", "\u00d716", "\u00d764", "+"};
        long[] qtyVals = {-1L, 1L, 16L, 64L, 1000L};
        int qx = rightX + 6;
        for (int i = 0; i < qtyLabels.length; i++) {
            int qw = i == 0 || i == qtyLabels.length - 1 ? 18 : 30;
            Rect qr = new Rect(qx, qtyY, qw, 14);
            amountStepRects.add(qr);
            boolean active = qtyVals[i] == pickerAmount;
            boolean hov = qr.contains(mouseX, mouseY);
            if (active) {
                OreUiPaint.selectedToggle(canvas, qr.x(), qr.y(), qr.width(), qr.height(), p, hov);
            } else {
                OreUiPaint.raisedButton(canvas, qr.x(), qr.y(), qr.width(), qr.height(), p, false, hov, false, false);
            }
            Component comp = Component.literal(qtyLabels[i]);
            ChromeText.draw(
                canvas,
                comp,
                qr.x() + (qr.width() - ChromeText.width(canvas, comp)) / 2,
                active
                        ? ChromeText.centeredY(qr.y(), qr.height(), canvas)
                        : ChromeText.centeredButtonY(qr.y(), qr.height(), canvas),
                active ? p.focusRing() : hov ? p.textPrimary() : p.textSecondary()
            );
            qx += qw + 3;
        }
        canvas.fill(rightX + 4, qtyY + 18, rightW - 8, 1, OreUiPaint.cardDivider(p));

        // Recipes list for selected item
        List<RecipeEntryView> recipes = candidates();
        int recTop = qtyY + 23;
        if (recipes.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("Base / raw resource (0 crafting recipes) — click Set Target to plan acquisition"),
                rightX + 8,
                recTop + 8,
                rightW - 16,
                p.textMuted()
            );
        } else {
            int gridMaxW = Math.max(60, rightW - 22);
            int maxGridRows = recipes.size() <= 2 ? 2 : 1;
            int ry = recTop;
            for (int dataIndex = scrollOffset; dataIndex < recipes.size(); dataIndex++) {
                RecipeEntryView view = recipes.get(dataIndex);
                List<OreUiRecipeGrid.SlotEntry> inputs = OreUiRecipeGrid.inputsOf(view.recipe());
                List<OreUiRecipeGrid.SlotEntry> outputs = OreUiRecipeGrid.outputsOf(view.recipe());
                OreUiRecipeGrid.Layout grid = OreUiRecipeGrid
                        .measure(inputs.size(), outputs.size(), OreUiRecipeGrid.compactSlotSize, gridMaxW, maxGridRows);
                int dynH = 22 + grid.height() + 20;
                Rect card = new Rect(rightX + 5, ry, rightW - 10, dynH);
                if (card.bottom() > bodyBottom - 3 && !cardRects.isEmpty()) {
                    break;
                }
                cardRects.add(card);
                boolean hov = card.contains(mouseX, mouseY);
                OreUiPaint.raisedCard(
                    canvas,
                    card.x(),
                    card.y(),
                    card.width(),
                    card.height(),
                    p,
                    hov,
                    dataIndex == selectedIndex
                );
                int r1Y = card.y() + 4;
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(view.machineName()),
                    card.x() + 6,
                    r1Y + 2,
                    Math.max(40, card.width() - 128),
                    p.textPrimary()
                );
                Rect addRecBtn = new Rect(card.right() - 48, r1Y, 44, 14);
                Rect planRecBtn = new Rect(addRecBtn.x() - 62, r1Y, 58, 14);
                banRects.add(addRecBtn);
                pinRects.add(planRecBtn);
                drawSmallActionBtn(canvas, p, planRecBtn, "Plan Use", true, mouseX, mouseY);
                drawSmallActionBtn(canvas, p, addRecBtn, "+Add", false, mouseX, mouseY);

                int fy = card.y() + 20;
                OreUiRecipeGrid.render(canvas, inputs, outputs, grid, card.x() + 6, fy, p);
                int my = fy + grid.height() + 4;
                String meta = "Work " + WorkbenchFormatters.formatSeconds(view.estWorkSeconds()) + " \u00b7 Wait "
                        + WorkbenchFormatters.formatSeconds(view.estWaitSeconds()) + " \u00b7 " + view.namespace();
                ChromeText.drawFitted(
                    canvas,
                    Component.literal(meta),
                    card.x() + 6,
                    my,
                    card.width() - 12,
                    p.textMuted()
                );
                ry += dynH + 4;
            }
        }
        renderFooter(canvas, p, w, h, mouseX, mouseY);
    }

    private void renderGuidedDecisions(SceneCanvas canvas, CirrusPalette p, int w, int h, int mouseX, int mouseY) {
        guidedOptionHits.clear();
        guidedPrevRect = null;
        guidedNextRect = null;
        guidedSkipRect = null;

        List<ResolvedPrompt> prompts = guidedPrompts();
        searchFieldRect = null;
        OreUiPaint.headerBand(canvas, 0, 0, w, headerHeight - 1, p, false);
        Rect close = closeRect();
        drawCloseButton(canvas, p, close, mouseX, mouseY);

        if (prompts.isEmpty()) {
            ChromeText.draw(canvas, Component.literal("Guided Decision Workbench"), pad + 4, 11, p.textPrimary());
            ChromeText.draw(
                canvas,
                Component.literal("All high-leverage decisions are resolved — ready to continue!"),
                pad + 10,
                headerHeight + 24,
                p.costS()
            );
            renderFooter(canvas, p, w, h, mouseX, mouseY);
            return;
        }

        int pIdx = Math.max(0, Math.min(prompts.size() - 1, guidedPromptIndex));
        ResolvedPrompt prompt = prompts.get(pIdx);
        ComponentStackKey subjIcon = prompt.primaryResource() != null
                ? prompt.primaryResource()
                : prompt.primaryMachine() != null ? prompt.primaryMachine().contentKey().orElse(null) : null;
        CoPilotPaint
                .renderSlotItem(canvas, subjIcon == null ? null : CoPilotPaint.stackOf(subjIcon, 1L), pad, 6, 20, p);
        Component title = Component.translatable(prompt.raw().titleKey());
        ChromeText.drawFitted(canvas, title, pad + 26, 6, Math.max(60, w - 210), p.textPrimary());
        int impactPct = Math.round(PromptDeltaFormat.leverageFraction(prompt.raw().leverageScore()) * 100f);
        ChromeText.drawFitted(
            canvas,
            Component.literal("Human-in-the-Loop Checkpoint \u00b7 " + impactPct + "% Impact \u00b7 Anchor Locked"),
            pad + 26,
            18,
            Math.max(60, w - 210),
            p.costH()
        );

        // Stepper controls: [◀] 1 / N [▶]
        guidedNextRect = new Rect(close.x() - 22, 8, 16, 16);
        String stepText = (pIdx + 1) + " / " + prompts.size();
        int stepW = ChromeText.width(canvas, Component.literal(stepText)) + 12;
        guidedPrevRect = new Rect(guidedNextRect.x() - stepW - 18, 8, 16, 16);
        drawSmallActionBtn(canvas, p, guidedPrevRect, "\u25c0", false, mouseX, mouseY);
        ChromeText.draw(
            canvas,
            Component.literal(stepText),
            guidedPrevRect.right() + 6,
            ChromeText.centeredY(8, 16, canvas),
            p.textPrimary()
        );
        drawSmallActionBtn(canvas, p, guidedNextRect, "\u25b6", false, mouseX, mouseY);

        int y = headerHeight + 8;
        int innerW = w - pad * 2;
        // Context explanation + optional visual recipe grid
        Component explain = Component.translatable(prompt.raw().explanationKey());
        CompiledRecipeGraph graph = shellContext.activeGraph();
        CompiledRecipe involvedRecipe = (prompt.recipeKey() != null && graph != null)
                ? graph.recipeByKey(prompt.recipeKey()).orElse(null)
                : null;
        int contextH = involvedRecipe != null ? 54 : 30;
        OreUiPaint.searchField(canvas, pad, y, innerW, contextH, p, false);
        ChromeText.drawFitted(canvas, explain, pad + 8, y + 6, innerW - 16, p.textSecondary());
        if (involvedRecipe != null) {
            List<OreUiRecipeGrid.SlotEntry> ins = OreUiRecipeGrid.inputsOf(involvedRecipe);
            List<OreUiRecipeGrid.SlotEntry> outs = OreUiRecipeGrid.outputsOf(involvedRecipe);
            OreUiRecipeGrid.Layout grid = OreUiRecipeGrid
                    .measure(ins.size(), outs.size(), OreUiRecipeGrid.compactSlotSize, innerW - 20, 1);
            OreUiRecipeGrid.render(canvas, ins, outs, grid, pad + 8, y + 20, p);
        } else {
            ChromeText.drawFitted(
                canvas,
                Component.literal("Compare 5-axis trade-offs below; hovering highlights affected DAG steps."),
                pad + 8,
                y + 17,
                innerW - 16,
                p.textMuted()
            );
        }
        y += contextH + 8;

        int hoveredOpt = -1;
        int bottomLimit = h - footerHeight - 6;
        List<ResolvedPromptOption> options = prompt.options();
        int cardH = Math.max(
            46,
            Math.min(62, (bottomLimit - y - Math.max(0, options.size() - 1) * 5) / Math.max(1, options.size()))
        );
        for (int i = 0; i < options.size(); i++) {
            ResolvedPromptOption opt = options.get(i);
            Rect card = new Rect(pad, y, innerW, cardH);
            if (card.bottom() > bottomLimit + 2 && !guidedOptionHits.isEmpty()) {
                break;
            }
            boolean hover = card.contains(mouseX, mouseY);
            if (hover) {
                hoveredOpt = i;
            }
            OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), p, hover, false);
            Rect applyBtn = new Rect(card.right() - 88, card.y() + 6, 80, 18);
            guidedOptionHits.add(new GuidedOptionHit(card, applyBtn, i));
            drawSmallActionBtn(
                canvas,
                p,
                applyBtn,
                prompts.size() > 1 ? "Apply & Next" : "Apply",
                true,
                mouseX,
                mouseY
            );

            Component optLabel = Component.translatable(opt.raw().labelKey());
            ChromeText.drawFitted(
                canvas,
                optLabel,
                card.x() + 8,
                card.y() + 9,
                Math.max(40, applyBtn.x() - card.x() - 14),
                p.textPrimary()
            );

            // Quantitative 5-axis delta chips
            int chipX = card.x() + 8;
            int chipY = card.y() + 26;
            List<PromptDeltaFormat.Chip> chips = PromptDeltaFormat.deltaChips(opt.delta());
            if (chips.isEmpty()) {
                ChromeText.draw(
                    canvas,
                    Component.literal("Baseline / Neutral trade-off"),
                    chipX,
                    chipY + 2,
                    p.textMuted()
                );
            } else {
                for (PromptDeltaFormat.Chip chip : chips) {
                    int cw = CoPilotPaint.chipWidth(canvas, chip.label());
                    if (chipX + cw > card.right() - 8) {
                        break;
                    }
                    CoPilotPaint.chip(canvas, chipX, chipY, chip.label(), p, chip.styleClass());
                    chipX += cw + 4;
                }
            }
            y += cardH + 5;
        }

        if (hoveredOpt != lastHoveredGuidedOption) {
            lastHoveredGuidedOption = hoveredOpt;
            if (previewListener != null) {
                previewListener.onPreview(hoveredOpt >= 0 ? buildGuidedPreview(prompt, options.get(hoveredOpt)) : null);
            }
        }

        // Guided footer: Session / World Save + Skip & Continue
        int fy = h - footerHeight;
        OreUiPaint.footerBand(canvas, 0, fy, w, footerHeight - 2, p, false);
        Rect sRect = sessionScopeRect();
        Rect wRect = worldScopeRect();
        drawScopeButton(canvas, p, sRect, "Session", !persistToWorld, sRect.contains(mouseX, mouseY));
        drawScopeButton(canvas, p, wRect, "World Save", persistToWorld, wRect.contains(mouseX, mouseY));
        guidedSkipRect = new Rect(w - pad - 122, fy + 5, 122, 16);
        drawSmallActionBtn(canvas, p, guidedSkipRect, "Skip & Continue", false, mouseX, mouseY);
    }

    private static PromptCardWidget.@Nullable PreviewSubject buildGuidedPreview(
        ResolvedPrompt prompt,
        ResolvedPromptOption option
    ) {
        Set<String> anchors = prompt.anchoredStepIds();
        for (GuidanceRule rule : option.raw().compiledRules()) {
            if (rule instanceof MachineRule machineRule) {
                return new PromptCardWidget.PreviewSubject(
                    machineRule.willingToBuildNew()
                            ? PromptCardWidget.PreviewKind.buildMachine
                            : PromptCardWidget.PreviewKind.ownMachine,
                    anchors,
                    machineRule.machine(),
                    null,
                    null
                );
            }
            if (rule instanceof TagDefaultRule tagDefault) {
                return new PromptCardWidget.PreviewSubject(
                    PromptCardWidget.PreviewKind.pickVariant,
                    anchors,
                    null,
                    null,
                    tagDefault.preferredMember()
                );
            }
            if (rule instanceof ResourceRule resourceRule) {
                return new PromptCardWidget.PreviewSubject(
                    resourceRule.treatAsFreeBaseSource()
                            ? PromptCardWidget.PreviewKind.treatFree
                            : PromptCardWidget.PreviewKind.banRecipe,
                    anchors,
                    null,
                    null,
                    resourceRule.resource()
                );
            }
        }
        return anchors.isEmpty()
                ? null
                : new PromptCardWidget.PreviewSubject(
                    PromptCardWidget.PreviewKind.banRecipe,
                    anchors,
                    null,
                    null,
                    null
                );
    }

    private static void drawCloseButton(SceneCanvas canvas, CirrusPalette p, Rect close, int mouseX, int mouseY) {
        boolean closeHover = close.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            close.x(),
            close.y(),
            close.width(),
            close.height(),
            p,
            false,
            closeHover,
            false,
            closeHover
        );
        ChromeButtons.cross(canvas, close.x() + 5, close.y() + 4, 6, closeHover ? p.textOnAccent() : p.textSecondary());
    }

    private static void drawSmallActionBtn(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect rect,
        String label,
        boolean primary,
        int mouseX,
        int mouseY
    ) {
        boolean hover = rect.contains(mouseX, mouseY);
        OreUiPaint
                .raisedButton(canvas, rect.x(), rect.y(), rect.width(), rect.height(), p, false, hover, primary, false);
        Component comp = ChromeText.fit(canvas, Component.literal(label), Math.max(8, rect.width() - 6));
        ChromeText.draw(
            canvas,
            comp,
            rect.x() + Math.max(3, (rect.width() - ChromeText.width(canvas, comp)) / 2),
            ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            primary ? p.textOnAccent() : hover ? p.focusRing() : p.textPrimary()
        );
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette p, int w, int mouseX, int mouseY) {
        OreUiPaint.headerBand(canvas, 0, 0, w, headerHeight - 1, p, false);
        int slotX = pad;
        int slotY = 6;
        CoPilotPaint.renderSlotItem(
            canvas,
            targetResource == null ? null : CoPilotPaint.stackOf(targetResource, 1L),
            slotX,
            slotY,
            20,
            p
        );
        Component targetName = targetResource == null
                ? Component.literal("Recipe Assistant")
                : CoPilotPaint.resourceName(targetResource);
        ChromeText.drawFitted(canvas, targetName, slotX + 26, 6, Math.max(40, w - 210), p.textPrimary());
        ChromeText.drawFitted(
            canvas,
            Component.literal("Recipe Decision & Route Comparison"),
            slotX + 26,
            18,
            Math.max(40, w - 210),
            p.textMuted()
        );

        List<RecipeEntryView> shown = candidates();
        Component countPill = Component.literal(shown.size() + " recipes");
        int pillW = ChromeText.width(canvas, countPill) + 10;
        Rect close = closeRect();
        OreUiPaint.tagPill(canvas, close.x() - pillW - 8, 9, OreUiPaint.minPillHeight, countPill, p.focusRing(), p);

        boolean closeHover = close.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            close.x(),
            close.y(),
            close.width(),
            close.height(),
            p,
            false,
            closeHover,
            false,
            closeHover
        );
        ChromeButtons.cross(canvas, close.x() + 5, close.y() + 4, 6, closeHover ? p.textOnAccent() : p.textSecondary());
    }

    private void renderFilterAndSortBar(SceneCanvas canvas, CirrusPalette p, int w, int mouseX, int mouseY) {
        filterRects.clear();
        sortRects.clear();
        int top = headerHeight + 4;
        int searchH = 18;
        searchFieldRect = new Rect(pad, top, w - pad * 2, searchH);
        OreUiPaint.searchField(canvas, pad, top, w - pad * 2, searchH, p, focused());
        String placeholder = "Type to filter by ingredient, machine, or mod...";
        Component display = searchQuery.isEmpty() ? Component.literal(placeholder) : Component.literal(searchQuery);
        ChromeText.drawFitted(
            canvas,
            display,
            pad + 6,
            ChromeText.centeredY(top, searchH, canvas),
            w - pad * 2 - 12,
            searchQuery.isEmpty() ? p.textMuted() : p.textPrimary()
        );
        if (focused()) {
            int caretX = pad + 6 + ChromeText.width(canvas, Component.literal(searchQuery));
            canvas.fill(Math.min(w - pad - 4, caretX), top + 4, 1, searchH - 8, p.focusRing());
        }

        int chipY = top + searchH + 5;
        int chipH = 15;
        int cursor = pad;
        for (FilterMode mode : FilterMode.values()) {
            Component label = Component.literal(mode.label());
            int cw = ChromeText.width(canvas, label) + 10;
            Rect r = new Rect(cursor, chipY, cw, chipH);
            filterRects.add(r);
            boolean active = mode == filterMode;
            boolean hovered = r.contains(mouseX, mouseY);
            if (active) {
                OreUiPaint.raisedCard(canvas, r.x(), r.y(), r.width(), r.height(), p, hovered, true);
            } else {
                OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hovered, false, false);
            }
            ChromeText.draw(
                canvas,
                label,
                r.x() + 5,
                ChromeText.centeredButtonY(r.y(), r.height(), canvas),
                active ? p.focusRing() : p.textSecondary()
            );
            cursor += cw + 4;
        }

        // Right-aligned sort pills.
        int rightX = w - pad;
        SortMode[] sorts = SortMode.values();
        Rect[] sortArr = new Rect[sorts.length];
        for (int i = sorts.length - 1; i >= 0; i--) {
            Component label = Component.literal(sorts[i].label());
            int cw = ChromeText.width(canvas, label) + 10;
            rightX -= cw;
            sortArr[i] = new Rect(rightX, chipY, cw, chipH);
            rightX -= 3;
        }
        for (int i = 0; i < sorts.length; i++) {
            Rect r = sortArr[i];
            sortRects.add(r);
            boolean active = sorts[i] == sortMode;
            boolean hovered = r.contains(mouseX, mouseY);
            if (active) {
                OreUiPaint.raisedCard(canvas, r.x(), r.y(), r.width(), r.height(), p, hovered, true);
            } else {
                OreUiPaint.raisedButton(canvas, r.x(), r.y(), r.width(), r.height(), p, false, hovered, false, false);
            }
            ChromeText.draw(
                canvas,
                Component.literal(sorts[i].label()),
                r.x() + 5,
                ChromeText.centeredButtonY(r.y(), r.height(), canvas),
                active ? p.costH() : p.textMuted()
            );
        }
        if (rightX - 30 > cursor) {
            ChromeText.drawRight(
                canvas,
                Component.literal("Sort:"),
                rightX - 2,
                ChromeText.centeredY(chipY, chipH, canvas),
                p.textMuted()
            );
        }
    }

    private void renderRecipeCards(SceneCanvas canvas, CirrusPalette p, int w, int h, int mouseX, int mouseY) {
        cardRects.clear();
        pinRects.clear();
        banRects.clear();
        int top = headerHeight + filterBarHeight + 5;
        int bottom = h - footerHeight - 5;
        List<RecipeEntryView> list = candidates();
        if (list.isEmpty()) {
            OreUiPaint.searchField(canvas, pad, top, w - pad * 2, Math.max(36, bottom - top), p, false);
            ChromeText.draw(
                canvas,
                Component.literal("No matching recipes under current filter."),
                pad + 10,
                top + 14,
                p.textMuted()
            );
            return;
        }

        RecipeEntryView activeBaseline = null;
        for (RecipeEntryView v : list) {
            if (v.active()) {
                activeBaseline = v;
                break;
            }
        }

        int maxGridRows = list.size() <= 2 ? 3 : 2;
        int gridMaxW = Math.max(64, w - pad * 2 - 14);
        int y = top;
        for (int dataIndex = scrollOffset; dataIndex < list.size(); dataIndex++) {
            RecipeEntryView view = list.get(dataIndex);
            List<OreUiRecipeGrid.SlotEntry> inputs = OreUiRecipeGrid.inputsOf(view.recipe());
            List<OreUiRecipeGrid.SlotEntry> outputs = OreUiRecipeGrid.outputsOf(view.recipe());
            OreUiRecipeGrid.Layout grid = OreUiRecipeGrid
                    .measure(inputs.size(), outputs.size(), OreUiRecipeGrid.defaultSlotSize, gridMaxW, maxGridRows);
            int dynCardH = 22 + grid.height() + 23;
            Rect card = new Rect(pad, y, w - pad * 2, dynCardH);
            if (card.bottom() > bottom && !cardRects.isEmpty()) {
                break;
            }
            cardRects.add(card);
            boolean hovered = card.contains(mouseX, mouseY);
            boolean highlighted = dataIndex == selectedIndex || view.active();
            OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), p, hovered, highlighted);

            int row1Y = card.y() + 5;
            int mx = card.x() + 7;
            if (!view.handCraft() && !view.recipe().machineRequirement().compatibleMachines().isEmpty()) {
                ComponentStackKey machineKey = view.recipe().machineRequirement().compatibleMachines().get(0)
                        .contentKey().orElse(null);
                if (machineKey != null) {
                    ChromeButtons.drawMiniItem(canvas, machineKey, p, mx, row1Y, 12);
                    mx += 16;
                }
            }
            Component machComp = Component.literal(view.machineName());
            ChromeText.drawFitted(canvas, machComp, mx, row1Y + 2, 130, p.textPrimary());
            mx += Math.min(132, ChromeText.width(canvas, machComp) + 8);

            Component statusLabel = view.handCraft()
                    ? Component.literal("HAND")
                    : view.machineNeededInPlan() ? Component.literal("NEED MACH") : Component.literal("READY");
            int statusColor = view.handCraft() ? p.costP() : view.machineNeededInPlan() ? p.costK() : p.costS();
            mx += OreUiPaint.tagPill(canvas, mx, row1Y, OreUiPaint.minPillHeight, statusLabel, statusColor, p) + 4;
            OreUiPaint.tagPill(
                canvas,
                mx,
                row1Y,
                OreUiPaint.minPillHeight,
                Component.literal(view.namespace()),
                p.textMuted(),
                p
            );

            int banW = 34;
            int pinW = view.active() ? 54 : 58;
            Rect banBtn = new Rect(card.right() - banW - 6, row1Y - 1, banW, 15);
            Rect pinBtn = new Rect(banBtn.x() - pinW - 4, row1Y - 1, pinW, 15);
            banRects.add(banBtn);
            pinRects.add(pinBtn);

            boolean banHover = banBtn.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                banBtn.x(),
                banBtn.y(),
                banBtn.width(),
                banBtn.height(),
                p,
                false,
                banHover,
                false,
                banHover
            );
            Component banComp = Component.literal("Ban");
            ChromeText.draw(
                canvas,
                banComp,
                banBtn.x() + (banBtn.width() - ChromeText.width(canvas, banComp)) / 2,
                ChromeText.centeredButtonY(banBtn.y(), banBtn.height(), canvas),
                banHover ? p.textOnAccent() : p.textSecondary()
            );

            boolean pinHover = pinBtn.contains(mouseX, mouseY);
            if (view.active()) {
                OreUiPaint.tagPill(
                    canvas,
                    pinBtn.x(),
                    pinBtn.y() + 1,
                    OreUiPaint.minPillHeight,
                    Component.literal("Active"),
                    p.costS(),
                    p
                );
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    pinBtn.x(),
                    pinBtn.y(),
                    pinBtn.width(),
                    pinBtn.height(),
                    p,
                    false,
                    pinHover,
                    true,
                    false
                );
                Component pinComp = Component.literal("Pin Use");
                ChromeText.draw(
                    canvas,
                    pinComp,
                    pinBtn.x() + (pinBtn.width() - ChromeText.width(canvas, pinComp)) / 2,
                    ChromeText.centeredButtonY(pinBtn.y(), pinBtn.height(), canvas),
                    p.textOnAccent()
                );
            }

            // Middle row: adaptive OreUI slot grid with native in-slot counts.
            int fy = card.y() + 22;
            OreUiRecipeGrid.render(canvas, inputs, outputs, grid, card.x() + 7, fy, p);

            // Bottom row: quantitative human reference metrics & deltas vs active recipe.
            int my = fy + grid.height() + 5;
            int mc = card.x() + 7;
            String workStr = "Work " + WorkbenchFormatters.formatSeconds(view.estWorkSeconds());
            if (activeBaseline != null && !view.active()) {
                double dw = view.estWorkSeconds() - activeBaseline.estWorkSeconds();
                if (Math.abs(dw) >= 0.1) {
                    workStr += (dw < 0 ? " (-" : " (+") + WorkbenchFormatters.formatSeconds(Math.abs(dw)) + ")";
                }
            }
            mc += OreUiPaint.tagPill(canvas, mc, my, OreUiPaint.minPillHeight, Component.literal(workStr), p.costH(), p)
                    + 4;
            String waitStr = "Wait " + WorkbenchFormatters.formatSeconds(view.estWaitSeconds());
            if (activeBaseline != null && !view.active()) {
                double dp = view.estWaitSeconds() - activeBaseline.estWaitSeconds();
                if (Math.abs(dp) >= 0.1) {
                    waitStr += (dp < 0 ? " (-" : " (+") + WorkbenchFormatters.formatSeconds(Math.abs(dp)) + ")";
                }
            }
            mc += OreUiPaint.tagPill(canvas, mc, my, OreUiPaint.minPillHeight, Component.literal(waitStr), p.costP(), p)
                    + 4;
            OreUiPaint.tagPill(
                canvas,
                mc,
                my,
                OreUiPaint.minPillHeight,
                Component.literal(view.inputCount() + " in \u00b7 " + view.recipe().outputs().size() + " out"),
                p.textSecondary(),
                p
            );

            y += dynCardH + cardGap;
        }
    }

    private void renderFooter(SceneCanvas canvas, CirrusPalette p, int w, int h, int mouseX, int mouseY) {
        int fy = h - footerHeight;
        OreUiPaint.footerBand(canvas, 0, fy, w, footerHeight - 2, p, false);
        Rect sRect = sessionScopeRect();
        Rect wRect = worldScopeRect();
        drawScopeButton(canvas, p, sRect, "Session", !persistToWorld, sRect.contains(mouseX, mouseY));
        drawScopeButton(canvas, p, wRect, "World Save", persistToWorld, wRect.contains(mouseX, mouseY));
        ChromeText.drawRight(
            canvas,
            Component.literal("\u2191\u2193 Select \u00b7 \u21b5 Pin \u00b7 Type Filter \u00b7 Esc Close"),
            w - pad,
            ChromeText.centeredY(fy + 1, footerHeight - 2, canvas),
            p.textMuted()
        );
    }

    private static void drawScopeButton(
        SceneCanvas canvas,
        CirrusPalette p,
        Rect rect,
        String label,
        boolean selected,
        boolean hover
    ) {
        if (selected) {
            OreUiPaint.selectedToggle(canvas, rect.x(), rect.y(), rect.width(), rect.height(), p, hover);
        } else {
            OreUiPaint.raisedButton(
                canvas,
                rect.x(),
                rect.y(),
                rect.width(),
                rect.height(),
                p,
                false,
                hover,
                false,
                false
            );
        }
        Component comp = Component.literal(label);
        ChromeText.draw(
            canvas,
            comp,
            rect.x() + (rect.width() - ChromeText.width(canvas, comp)) / 2,
            selected
                    ? ChromeText.centeredY(rect.y(), rect.height(), canvas)
                    : ChromeText.centeredButtonY(rect.y(), rect.height(), canvas),
            selected ? p.focusRing() : p.textSecondary()
        );
    }

    private boolean matchesFilter(boolean hand, int inputCount) {
        return switch (filterMode) {
            case all -> true;
            case handFree -> hand;
            case machineOnly -> !hand;
            case lowInputs -> inputCount <= 2;
        };
    }

    private static boolean matchesSearch(CompiledRecipe recipe, String machineName, String namespace, String needle) {
        if (machineName.toLowerCase(Locale.ROOT).contains(needle)
                || namespace.toLowerCase(Locale.ROOT).contains(needle)
                || recipe.key().shortString().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (InputSlot in : recipe.inputs()) {
            for (ComponentStackKey candidate : in.candidates()) {
                if (candidate.displayId().toLowerCase(Locale.ROOT).contains(needle)
                        || CoPilotPaint.resourceName(candidate).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double estimateWorkSeconds(CompiledRecipe recipe, boolean hand) {
        return hand ? Math.max(0.5, 0.4 * recipe.inputs().size() + 0.4) : 1.0 + 0.25 * recipe.inputs().size();
    }

    private static double estimateWaitSeconds(CompiledRecipe recipe, boolean hand) {
        if (hand) {
            return 0.0;
        }
        String sys = recipe.key().systemId().toString().toLowerCase(Locale.ROOT);
        if (sys.contains("blast")) {
            return 5.0;
        }
        if (sys.contains("smelt")) {
            return 10.0;
        }
        return 4.0;
    }

    private static @Nullable CompiledRecipeKey activeRecipeFor(
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph,
        ComponentStackKey target
    ) {
        if (plan == null) {
            return null;
        }
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep craft) {
                CompiledRecipe recipe = graph.recipeByKey(craft.recipeKey()).orElse(null);
                if (recipe != null) {
                    for (OutputSlot out : recipe.outputs()) {
                        if (target.equals(out.resource())) {
                            return craft.recipeKey();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Set<MachineEntry> provisionedMachinesOf(@Nullable ProductionPlan plan) {
        if (plan == null) {
            return Set.of();
        }
        return Set.copyOf(plan.preparation().machinesToProvision().keySet());
    }
}
