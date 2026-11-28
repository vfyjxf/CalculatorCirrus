package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z6 global command & recipe spotlight: prefix filter chips, iconized search list, and a live right-hand recipe & decision preview pane.
 */
public class CommandPaletteOverlayWidget extends Widget implements OreUiTooltipProvider {
    public static final int queryLimit = 20;
    public static final int headerHeight = 20;
    public static final int rowHeight = 16;
    public static final int categoryRowHeight = 11;
    public static final int footerHeight = 16;
    public static final int iconSize = 12;
    public static final int pad = 6;
    public static final int scrimAlpha = 0x66;
    public static final int minPaletteHeight = headerHeight + rowHeight + footerHeight + pad;
    public static final long outsideClickGraceMillis = 120L;

    public record Row(int itemIndex, @Nullable PaletteItem item, @Nullable String categoryKey) {}

    private final WorkbenchShellContext shellContext;
    private boolean open;
    private String query = "";
    private List<PaletteItem> items = List.of();
    private int selected;
    private int scrollOffset;
    private int preferredHeight;
    private long openedAtMillis;
    private long clockOffsetMillis;
    private @Nullable Boolean reducedMotionOverride;
    private @Nullable Widget previousFocus;
    private @Nullable String pendingFocusStepId;
    private @Nullable String pendingPromptId;
    private @Nullable String lastDispatchedCommandId;
    private @Nullable Supplier<@Nullable CompiledRecipeGraph> graphSupplier;
    private @Nullable ChromeKeyRouter router;
    private final List<ModeChipHit> modeChipHits = new ArrayList<>();
    private final List<PreviewActionHit> previewActionHits = new ArrayList<>();

    private record ModeChipHit(Rect rect, String prefix) {}

    private record PreviewActionHit(Rect rect, Runnable action) {}

    public CommandPaletteOverlayWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        WorkbenchShellLayout.Rect region = shellLayout().commandPalette();
        this.preferredHeight = Math.max(minPaletteHeight, Math.round(region.height()));
        addStyleClass("workbench-palette");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(region.width(), region.height()));
        setInteractive(true);
        setFocusable(true);
        setVisible(false);
        setClickGroup(this);
        onClickOutside(ignored -> dismissOnOutsideClick());
        onMount((scene, sceneContext, handle) -> {
            if (open) {
                scene.requestFocus(this);
            }
        });
        onKeyPressed((input, context1) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            if (!handleKeyPressed(input.key().getValue(), input.isCtrlDown(), input.isShiftDown())) {
                return EventDispatch.pass;
            }
            context1.consume();
            return EventDispatch.consumed;
        });
        onCharTyped((codePoint, modifiers, context1) -> {
            if (!open || Character.isISOControl(codePoint)) {
                return EventDispatch.pass;
            }
            appendQueryChar(codePoint);
            context1.consume();
            return EventDispatch.consumed;
        });
        onMouseClicked((input, context1) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            context1.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, context1) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            if (input.isRightClick()) {
                if (local.y() < headerHeight) {
                    if (!query.isEmpty()) {
                        setQueryText("");
                        CirrusUiSound.playClickSound();
                    }
                    context1.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            for (ModeChipHit chip : modeChipHits) {
                if (chip.rect().contains(local.x(), local.y())) {
                    setQueryText(chip.prefix());
                    CirrusUiSound.playClickSound();
                    context1.consume();
                    return EventDispatch.consumed;
                }
            }
            for (PreviewActionHit hit : previewActionHits) {
                if (hit.rect().contains(local.x(), local.y())) {
                    hit.action().run();
                    CirrusUiSound.playClickSound();
                    context1.consume();
                    return EventDispatch.consumed;
                }
            }
            int itemIndex = itemIndexAt(local.x(), local.y());
            if (itemIndex < 0) {
                return EventDispatch.pass;
            }
            select(itemIndex);
            activateSelected(input.isShiftDown());
            context1.consume();
            return EventDispatch.consumed;
        });
    }

    //region state

    public void open() {
        if (open) {
            return;
        }
        open = true;
        openedAtMillis = clock();
        setVisible(true);
        query = "";
        selected = 0;
        scrollOffset = 0;
        refresh();
        if (lifecycle().mounted()) {
            previousFocus = scene().focusingWidget();
            scene().requestFocus(this);
        }
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        setVisible(false);
        WorkbenchShellLayout.Rect region = shellLayout().commandPalette();
        if (region.height() > 0f) {
            preferredHeight = Math.round(region.height());
            useStyle(heightOf(region.height()));
        }
        if (!lifecycle().mounted()) {
            previousFocus = null;
            return;
        }
        Widget restore = previousFocus;
        previousFocus = null;
        if (restore != null && restore.lifecycle().mounted()) {
            scene().requestFocus(restore);
        } else {
            scene().clearFocus();
        }
    }

    public boolean isOpen() {
        return open;
    }

    public String queryText() {
        return query;
    }

    public void setQueryText(String text) {
        query = text == null ? "" : text;
        selected = 0;
        scrollOffset = 0;
        refresh();
    }

    public void appendQueryChar(char codePoint) {
        setQueryText(query + codePoint);
    }

    public void deleteLastChar() {
        if (query.isEmpty()) {
            return;
        }
        setQueryText(query.substring(0, query.length() - 1));
    }

    public void refresh() {
        items = CommandPaletteEngine.query(query, shellContext.activeSnapshot(), graph(), queryLimit);
        if (items.isEmpty()) {
            selected = 0;
            scrollOffset = 0;
        } else {
            selected = Math.max(0, Math.min(selected, items.size() - 1));
            scrollOffset = Math.max(0, Math.min(scrollOffset, selected));
        }
        if (open) {
            updateDynamicHeight();
        }
    }

    public int preferredHeight() {
        return preferredHeight;
    }

    public CommandPaletteOverlayWidget reducedMotion(@Nullable Boolean reducedMotion) {
        this.reducedMotionOverride = reducedMotion;
        return this;
    }

    public float appearProgress(long nowMillis) {
        if (!open) {
            return 0f;
        }
        boolean reduced = reducedMotionOverride != null ? reducedMotionOverride : ChromeMotion.reducedMotion();
        return ChromeMotion.cubicOutProgress(nowMillis - openedAtMillis, ChromeMotion.overlayAppearMillis, reduced);
    }

    public long clock() {
        return System.currentTimeMillis() + clockOffsetMillis;
    }

    public void advanceClock(long deltaMillis) {
        if (deltaMillis > 0L) {
            clockOffsetMillis += deltaMillis;
        }
    }

    private void dismissOnOutsideClick() {
        if (open && clock() - openedAtMillis >= outsideClickGraceMillis) {
            close();
        }
    }

    private int maxRegionHeight() {
        float regionHeight = shellLayout().commandPalette().height();
        if (regionHeight > 0f) {
            return Math.round(regionHeight);
        }
        return height() > 0 ? height() : Math.round(WorkbenchShellLayout.paletteHeight);
    }

    private void updateDynamicHeight() {
        int maxHeight = Math.max(minPaletteHeight, maxRegionHeight());
        int contentHeight;
        if (items.isEmpty()) {
            contentHeight = rowHeight + pad;
        } else {
            int maxContent = Math.max(rowHeight, maxHeight - headerHeight - footerHeight);
            int sum = 0;
            String lastCategory = null;
            for (PaletteItem item : items) {
                if (!item.categoryKey().equals(lastCategory)) {
                    if (sum + categoryRowHeight > maxContent) {
                        break;
                    }
                    sum += categoryRowHeight;
                    lastCategory = item.categoryKey();
                }
                if (sum + rowHeight > maxContent) {
                    break;
                }
                sum += rowHeight;
            }
            contentHeight = Math.max(rowHeight, sum);
        }
        int computed = Math.max(minPaletteHeight, Math.min(maxHeight, headerHeight + contentHeight + footerHeight + 4));
        preferredHeight = computed;
        useStyle(heightOf(computed));
    }

    public List<PaletteItem> results() {
        return items;
    }

    public int selectedIndex() {
        return selected;
    }

    public @Nullable PaletteItem highlightedItem() {
        if (selected < 0 || selected >= items.size()) {
            return null;
        }
        return items.get(selected);
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public void select(int index) {
        if (items.isEmpty()) {
            selected = 0;
            return;
        }
        selected = Math.max(0, Math.min(index, items.size() - 1));
        int capacity = itemCapacity();
        if (selected < scrollOffset) {
            scrollOffset = selected;
        } else if (selected >= scrollOffset + capacity) {
            scrollOffset = Math.max(0, selected - capacity + 1);
        }
    }

    public void moveSelection(int delta) {
        if (items.isEmpty()) {
            return;
        }
        int size = items.size();
        select(((selected + delta) % size + size) % size);
    }

    public @Nullable Supplier<@Nullable CompiledRecipeGraph> graphSupplier() {
        return graphSupplier;
    }

    public CommandPaletteOverlayWidget graphSupplier(@Nullable Supplier<@Nullable CompiledRecipeGraph> supplier) {
        this.graphSupplier = supplier;
        refresh();
        return this;
    }

    public CommandPaletteOverlayWidget router(@Nullable ChromeKeyRouter router) {
        this.router = router;
        return this;
    }

    public @Nullable String pendingFocusStepId() {
        return pendingFocusStepId;
    }

    public @Nullable String pendingPromptId() {
        return pendingPromptId;
    }

    public @Nullable String lastDispatchedCommandId() {
        return lastDispatchedCommandId;
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    //endregion

    //region keys

    public boolean handleKeyPressed(int keyCode, boolean ctrl, boolean shift) {
        if (!open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activateSelected(shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            deleteLastChar();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_K) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        ChromeKeyRouter activeRouter = router;
        return activeRouter != null
                && activeRouter.dispatch(keyCode, new ChromeShortcut.Modifiers(ctrl, shift, false), false);
    }

    public boolean activateSelected(boolean shift) {
        PaletteItem item = highlightedItem();
        if (item == null) {
            close();
            return false;
        }
        PaletteAction action = shift && item.shiftAction() != null ? item.shiftAction() : item.primaryAction();
        boolean dispatched = dispatch(action);
        close();
        return dispatched;
    }

    //endregion

    //region dispatch

    public boolean dispatch(@Nullable PaletteAction action) {
        if (action == null) {
            return false;
        }
        return switch (action) {
            case PaletteAction.SetTarget setTarget -> applyTarget(setTarget.resource(), false);
            case PaletteAction.AddToBasket add -> applyTarget(add.resource(), true);
            case PaletteAction.FocusStep focus -> {
                pendingFocusStepId = focus.stepId();
                ChromeSink.current().focusPlanStep(focus.stepId());
                shellContext.toast(CirrusLangs.Workbench.Palette.categoryCurrentNode.get());
                yield true;
            }
            case PaletteAction.ApplyFeedback feedback -> {
                PlannerWorkbenchController controller = shellContext.activeController();
                if (controller == null) {
                    yield false;
                }
                controller.refine(feedback.feedback(), null);
                yield true;
            }
            case PaletteAction.RunCommand command -> executeCommand(command.commandId());
        };
    }

    public boolean applyTarget(ComponentStackKey resource, boolean addToBasket) {
        if (resource == null) {
            return false;
        }
        List<TargetDemand> next = ChromeTargets.merged(shellContext.activeSnapshot().targets(), resource, addToBasket);
        shellContext.setTargets(next, null);
        return true;
    }

    public boolean executeCommand(@Nullable String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return false;
        }
        lastDispatchedCommandId = commandId;
        if (commandId.startsWith(CommandPaletteEngine.modPreferPrefix)
                || commandId.startsWith(CommandPaletteEngine.modPenalizePrefix)
                || commandId.startsWith(CommandPaletteEngine.modBanPrefix)) {
            return applyModRule(commandId);
        }
        if (commandId.startsWith(CommandPaletteEngine.promptFocusPrefix)) {
            return focusPrompt(commandId.substring(CommandPaletteEngine.promptFocusPrefix.length()));
        }
        return switch (commandId) {
            case "view.dag" -> setFlowView(FlowViewMode.spatialDag);
            case "view.ledger" -> setFlowView(FlowViewMode.preparationLedger);
            case "view.checklist" -> setFlowView(FlowViewMode.executionChecklist);
            case "layout.lr" -> setLayout(DagLayoutMode.horizontalLr);
            case "layout.tb" -> setLayout(DagLayoutMode.verticalTb);
            case "layout.rl" -> setLayout(DagLayoutMode.horizontalRl);
            case "layout.bt" -> setLayout(DagLayoutMode.verticalBt);
            case "layout.swimlane" -> setLayout(DagLayoutMode.systemSwimlane);
            case "preset.auto_smart", "preset.balanced" -> setPreset(ObjectivePreset.autoSmart);
            case "preset.stay_at_base", "preset.no_new_machines" -> setPreset(ObjectivePreset.stayAtBase);
            case "preset.quick_handcraft", "preset.min_human_time" -> setPreset(ObjectivePreset.quickHandcraft);
            case "preset.factory_scale", "preset.invest_automation" -> setPreset(ObjectivePreset.factoryScale);
            case "preset.frugal_materials", "preset.min_raw_materials" -> setPreset(ObjectivePreset.frugalMaterials);
            case "preset.low_cognitive" -> setPreset(ObjectivePreset.lowCognitive);
            case "theme.toggle" -> {
                shellContext.setTheme(
                    shellContext.theme() == WorkbenchThemeMode.obsidianBlueprint
                            ? WorkbenchThemeMode.atelierStone
                            : WorkbenchThemeMode.obsidianBlueprint
                );
                yield true;
            }
            case "zen.toggle" -> {
                shellContext.setZenMode(!shellContext.zenMode());
                yield true;
            }
            case "action.deepen" -> deepen();
            case "action.export_json" -> {
                ChromeSink.current().exportJson();
                shellContext.toast(CirrusLangs.Workbench.Settings.exportStats.get());
                yield true;
            }
            case "action.recompile" -> {
                shellContext.requestReplan();
                yield true;
            }
            case "history.undo" -> history(false);
            case "history.redo" -> history(true);
            case "workspace.planner" -> setMode(WorkbenchMode.planner);
            case "workspace.facilities" -> setMode(WorkbenchMode.facilities);
            case "workspace.database" -> setMode(WorkbenchMode.database);
            case "workspace.settings" -> setMode(WorkbenchMode.settings);
            default -> false;
        };
    }

    private boolean setFlowView(FlowViewMode mode) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        controller.setFlowViewMode(mode);
        return true;
    }

    private boolean setLayout(DagLayoutMode mode) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        controller.setDagLayoutMode(mode);
        return true;
    }

    private boolean setPreset(ObjectivePreset preset) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        controller.setProfile(ChromeProfiles.forPreset(preset, shellContext.activeSnapshot().profile()));
        return true;
    }

    private boolean deepen() {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        controller.setBudget(ChromeProfiles.deepened(shellContext.activeSnapshot().budget()));
        return true;
    }

    private boolean history(boolean redo) {
        PlannerWorkbenchController controller = shellContext.activeController();
        WorkbenchUiSnapshot snapshot = shellContext.activeSnapshot();
        if (controller == null) {
            return false;
        }
        if (redo) {
            if (!snapshot.canRedo()) {
                return false;
            }
            controller.jumpToHistoryIndex(snapshot.undoHistory().size() + 1);
            return true;
        }
        if (!snapshot.canUndo()) {
            return false;
        }
        controller.jumpToHistoryIndex(snapshot.undoHistory().size() - 1);
        return true;
    }

    private boolean setMode(WorkbenchMode mode) {
        shellContext.setMode(mode);
        return true;
    }

    private boolean applyModRule(String commandId) {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        String prefix;
        RuleStrength strength;
        if (commandId.startsWith(CommandPaletteEngine.modBanPrefix)) {
            prefix = CommandPaletteEngine.modBanPrefix;
            strength = RuleStrength.hardBan;
        } else if (commandId.startsWith(CommandPaletteEngine.modPenalizePrefix)) {
            prefix = CommandPaletteEngine.modPenalizePrefix;
            strength = RuleStrength.softPenalize;
        } else {
            prefix = CommandPaletteEngine.modPreferPrefix;
            strength = RuleStrength.softPrefer;
        }
        String namespace = commandId.substring(prefix.length()).trim();
        if (namespace.isEmpty()) {
            return false;
        }
        ModNamespaceRule rule = new ModNamespaceRule(
            "palette." + prefix + namespace,
            GuidanceScope.session,
            strength,
            namespace
        );
        controller.refine(new ApplyRulesFeedback(List.of(rule), false), null);
        return true;
    }

    private boolean focusPrompt(String promptId) {
        pendingPromptId = promptId;
        ChromeSink.current().focusCopilotPrompt(promptId);
        shellContext.setMode(WorkbenchMode.planner);
        shellContext.toast(CirrusLangs.Workbench.Palette.categoryPrompt.get());
        return true;
    }

    //endregion

    //region layout

    public int itemCapacity() {
        int effectiveHeight = open ? preferredHeight : (height() > 0 ? height() : maxRegionHeight());
        int available = Math.max(0, effectiveHeight - headerHeight - footerHeight);
        if (available >= categoryRowHeight + rowHeight) {
            return 1 + (available - categoryRowHeight) / rowHeight;
        }
        return Math.max(1, available / rowHeight);
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        int capacity = itemCapacity();
        String lastCategory = null;
        int index = scrollOffset;
        while (index < items.size() && rows.size() < capacity) {
            PaletteItem item = items.get(index);
            if (!item.categoryKey().equals(lastCategory)) {
                rows.add(new Row(-1, null, item.categoryKey()));
                lastCategory = item.categoryKey();
                if (rows.size() >= capacity) {
                    break;
                }
            }
            rows.add(new Row(index, item, null));
            index++;
        }
        return rows;
    }

    public int listPaneWidth() {
        int w = width();
        return w >= 480 ? Math.round(w * 0.56f) : w;
    }

    public int itemIndexAt(double localX, double localY) {
        int listW = listPaneWidth();
        if (localX < 0 || localX > listW) {
            return -1;
        }
        List<Row> rows = rows();
        int y = headerHeight;
        for (Row row : rows) {
            int currentRowHeight = row.item() == null ? categoryRowHeight : rowHeight;
            if (localY >= y && localY < y + currentRowHeight) {
                return row.item() == null ? -1 : row.itemIndex();
            }
            y += currentRowHeight;
        }
        return -1;
    }

    public static Component categoryLabel(@Nullable String categoryKey) {
        if (categoryKey == null) {
            return Component.empty();
        }
        return switch (categoryKey) {
            case CommandPaletteEngine.categoryStep -> CirrusLangs.Workbench.Palette.categoryCurrentNode.get();
            case CommandPaletteEngine.categoryResource -> CirrusLangs.Workbench.Palette.categorySetTarget.get();
            case CommandPaletteEngine.categoryCommand -> CirrusLangs.Workbench.Palette.categoryCommand.get();
            case CommandPaletteEngine.categoryPrompt -> CirrusLangs.Workbench.Palette.categoryPrompt.get();
            case CommandPaletteEngine.categoryRecent -> CirrusLangs.Workbench.Palette.categoryRecentTarget.get();
            default -> Component.literal(categoryKey);
        };
    }

    private @Nullable CompiledRecipeGraph graph() {
        Supplier<@Nullable CompiledRecipeGraph> supplier = graphSupplier;
        CompiledRecipeGraph provided = supplier == null ? null : supplier.get();
        return provided != null ? provided : shellContext.activeGraph();
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (!open) {
            return null;
        }
        for (ModeChipHit chip : modeChipHits) {
            if (chip.rect().contains((int) localX, (int) localY)) {
                String desc = chip.prefix().isEmpty()
                        ? "Search everything: items, steps, prompts, and commands."
                        : "Filter spotlight by prefix '" + chip.prefix() + "'";
                return OreUiRichTooltip.builder("Quick Filter")
                        .badge(chip.prefix().isEmpty() ? "ALL" : chip.prefix(), OreUiRichTooltip.Tone.accent)
                        .text(desc, OreUiRichTooltip.Tone.secondary).build();
            }
        }
        return null;
    }

    //endregion

    public Component footerHint() {
        PaletteItem item = highlightedItem();
        if (item == null || item.categoryKey() == null) {
            return CirrusLangs.Workbench.Palette.hintEscClose.get();
        }
        return switch (item.categoryKey()) {
            case CommandPaletteEngine.categoryCommand -> CirrusLangs.Workbench.Palette.hintCommand.get();
            case CommandPaletteEngine.categoryResource -> CirrusLangs.Workbench.Palette.hintResource.get();
            case CommandPaletteEngine.categoryPrompt -> CirrusLangs.Workbench.Palette.hintPrompt.get();
            case CommandPaletteEngine.categoryStep -> CirrusLangs.Workbench.Palette.hintStep.get();
            case CommandPaletteEngine.categoryRecent -> CirrusLangs.Workbench.Palette.hintRecent.get();
            default -> CirrusLangs.Workbench.Palette.hintEscClose.get();
        };
    }

    public Component footerRightHint() {
        PaletteItem item = highlightedItem();
        if (item == null) {
            return Component.empty();
        }
        if (CommandPaletteEngine.categoryPrompt.equals(item.categoryKey())) {
            return CirrusLangs.Workbench.Palette.hintPersistWorld.get();
        }
        return CirrusLangs.Workbench.Palette.hintEscClose.get();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open) {
            return;
        }
        CirrusPalette palette = shellContext.palette();
        int width = width();
        int height = open && preferredHeight > 0
                ? Math.min(height() > 0 ? height() : preferredHeight, preferredHeight)
                : height();
        if (width <= 0 || height <= 0) {
            return;
        }
        float progress = appearProgress(clock());
        float slideY = (1f - progress) * ChromeMotion.overlaySlidePx;
        renderScrim(canvas, palette, progress);
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            OreUiPaint.popover(canvas, 0, 0, width, height, palette);
            renderHeader(canvas, palette, width, mouseX, mouseY);
            int listW = listPaneWidth();
            boolean showPreview = width >= 480 && height >= headerHeight + footerHeight + 60;
            List<Row> rows = rows();
            int y = headerHeight;
            int hoveredIndex = itemIndexAt(mouseX, mouseY);
            int maxBodyBottom = Math.max(headerHeight, height - footerHeight);
            for (Row row : rows) {
                if (row.item() == null) {
                    if (y + categoryRowHeight > maxBodyBottom) {
                        break;
                    }
                    ChromeText.draw(
                        canvas,
                        categoryLabel(row.categoryKey()),
                        pad,
                        ChromeText.centeredY(y, categoryRowHeight, canvas),
                        palette.textMuted()
                    );
                    y += categoryRowHeight;
                    continue;
                }
                if (y + rowHeight > maxBodyBottom) {
                    break;
                }
                PaletteItem item = row.item();
                boolean highlighted = row.itemIndex() == selected;
                boolean hovered = row.itemIndex() == hoveredIndex;
                if (highlighted || hovered) {
                    OreUiPaint.raisedCard(canvas, 3, y, listW - 6, rowHeight - 1, palette, hovered, highlighted);
                }
                int tint = categoryTint(palette, item.categoryKey());
                int textX = pad + 2;
                ComponentStackKey iconRes = item.iconResource();
                if (iconRes != null) {
                    ChromeButtons.drawMiniItem(canvas, iconRes, palette, textX, y + 2, 11);
                    textX += 15;
                }
                int rowTextY = ChromeText.centeredY(y, rowHeight, canvas);
                Component category = categoryLabel(item.categoryKey());
                int badgeW = canvas.font().width(category) + 8;
                int badgeX = listW - pad - badgeW;
                OreUiPaint.tagPill(canvas, badgeX, y + Math.max(1, (rowHeight - 11) / 2), 11, category, tint, palette);
                int shortcutW = item.shortcutHint() == null
                        ? 0
                        : ChromeText.width(canvas, Component.literal(item.shortcutHint())) + 8;
                int rightReserve = badgeW + pad * 2 + shortcutW + 6;
                Component itemLabel = iconRes != null
                        ? CoPilotPaint.resourceName(iconRes)
                        : CommandPaletteEngine.categoryPrompt.equals(item.categoryKey())
                                ? Component.translatable(item.displayText())
                                : Component.literal(item.displayText());
                ChromeText.drawFitted(
                    canvas,
                    itemLabel,
                    textX,
                    rowTextY,
                    Math.max(0, listW - textX - rightReserve),
                    highlighted ? palette.textPrimary() : palette.textSecondary()
                );
                if (item.shortcutHint() != null) {
                    ChromeText.drawRight(
                        canvas,
                        Component.literal(item.shortcutHint()),
                        badgeX - 6,
                        rowTextY,
                        palette.textMuted()
                    );
                }
                y += rowHeight;
            }
            if (items.isEmpty()) {
                ChromeText.draw(
                    canvas,
                    CirrusLangs.Workbench.Palette.noResults.get(),
                    pad,
                    ChromeText.centeredY(headerHeight, Math.max(rowHeight, maxBodyBottom - headerHeight), canvas),
                    palette.textDisabled()
                );
            }
            previewActionHits.clear();
            if (showPreview) {
                renderPreviewPane(
                    canvas,
                    palette,
                    listW,
                    headerHeight,
                    width - listW - 1,
                    maxBodyBottom - headerHeight,
                    mouseX,
                    mouseY
                );
            }
            renderFooter(canvas, palette, height, width);
        } finally {
            canvas.popTransform();
        }
    }

    private void renderPreviewPane(
        SceneCanvas canvas,
        CirrusPalette palette,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY
    ) {
        if (w < 120 || h < 60) {
            return;
        }
        canvas.fill(x, y, 1, h, OreUiPaint.cardDivider(palette));
        canvas.fill(x + 1, y, w - 1, h, OreUiPaint.cardFooter(palette));

        PaletteItem item = highlightedItem();
        if (item == null) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("Select an item to preview"),
                x + 10,
                y + 14,
                w - 20,
                palette.textMuted()
            );
            return;
        }

        int px = x + 8;
        int pw = w - 16;
        int py = y + 7;
        ComponentStackKey res = item.iconResource();
        if (res == null && item.primaryAction() instanceof PaletteAction.SetTarget setTarget) {
            res = setTarget.resource();
        } else if (res == null && item.primaryAction() instanceof PaletteAction.AddToBasket addBasket) {
            res = addBasket.resource();
        }

        if (res != null) {
            renderResourcePreview(canvas, palette, res, px, py, pw, h - 14, mouseX, mouseY);
            return;
        }

        Component title = CommandPaletteEngine.categoryPrompt.equals(item.categoryKey())
                ? Component.translatable(item.displayText())
                : Component.literal(item.displayText());
        OreUiPaint.tagPill(
            canvas,
            px,
            py,
            OreUiPaint.minPillHeight,
            categoryLabel(item.categoryKey()),
            categoryTint(palette, item.categoryKey()),
            palette
        );
        ChromeText.drawFitted(canvas, title, px, py + 18, pw, palette.textPrimary());
        ChromeText.drawFitted(canvas, footerHint(), px, py + 34, pw, palette.textSecondary());

        int btnY = y + h - 24;
        Rect runBtn = new Rect(px, btnY, Math.min(116, pw), 17);
        boolean hover = runBtn.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            runBtn.x(),
            runBtn.y(),
            runBtn.width(),
            runBtn.height(),
            palette,
            false,
            hover,
            true,
            false
        );
        Component runLabel = Component.literal("Execute");
        int runTextW = ChromeText.width(canvas, runLabel);
        int runGroupW = runTextW + 5 + 8;
        int runStartX = runBtn.x() + Math.max(4, (runBtn.width() - runGroupW) / 2);
        ChromeText.draw(
            canvas,
            runLabel,
            runStartX,
            ChromeText.centeredButtonY(runBtn.y(), runBtn.height(), canvas),
            palette.textOnAccent()
        );
        ChromeButtons.returnArrow(
            canvas,
            runStartX + runTextW + 5,
            runBtn.y() + Math.max(2, (runBtn.height() - 2 - 6) / 2),
            palette.textOnAccent()
        );
        previewActionHits.add(new PreviewActionHit(runBtn, () -> activateSelected(false)));
    }

    private void renderResourcePreview(
        SceneCanvas canvas,
        CirrusPalette palette,
        ComponentStackKey resource,
        int x,
        int y,
        int w,
        int h,
        int mouseX,
        int mouseY
    ) {
        CoPilotPaint.renderSlotItem(canvas, CoPilotPaint.stackOf(resource, 1L), x, y, 22, palette);
        ChromeText.drawFitted(
            canvas,
            CoPilotPaint.resourceName(resource),
            x + 28,
            y + 1,
            Math.max(20, w - 30),
            palette.textPrimary()
        );
        ChromeText.drawFitted(
            canvas,
            Component.literal(resource.displayId()),
            x + 28,
            y + 12,
            Math.max(20, w - 30),
            palette.textMuted()
        );

        CompiledRecipeGraph resolved = graph();
        List<CompiledRecipe> producers = resolved == null ? List.of() : resolved.producersOf(resource);
        int cy = y + 28;
        int bottomLimit = y + h - 24;
        ChromeText.drawFitted(
            canvas,
            Component.literal("Recipes (" + producers.size() + ")"),
            x,
            cy,
            Math.max(20, w - 76),
            palette.textSecondary()
        );
        if (!producers.isEmpty() && shellContext instanceof WorkbenchShellWidget shell) {
            Component cmp = Component.literal("Compare...");
            int cw = ChromeText.width(canvas, cmp) + 8;
            Rect cmpBtn = new Rect(x + w - cw, cy - 2, cw, 13);
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
            ChromeText.draw(
                canvas,
                cmp,
                cmpBtn.x() + 4,
                ChromeText.centeredButtonY(cmpBtn.y(), cmpBtn.height(), canvas),
                cmpHover ? palette.focusRing() : palette.textPrimary()
            );
            previewActionHits.add(new PreviewActionHit(cmpBtn, () -> {
                close();
                shell.openRecipeAssistant(resource);
            }));
        }
        cy += 14;

        int shown = Math.min(2, producers.size());
        int maxGridRows = shown <= 1 ? 3 : 2;
        for (int i = 0; i < shown; i++) {
            CompiledRecipe recipe = producers.get(i);
            List<OreUiRecipeGrid.SlotEntry> inputs = OreUiRecipeGrid.inputsOf(recipe);
            List<OreUiRecipeGrid.SlotEntry> outputs = OreUiRecipeGrid.outputsOf(recipe);
            OreUiRecipeGrid.Layout grid = OreUiRecipeGrid.measure(
                inputs.size(),
                outputs.size(),
                OreUiRecipeGrid.compactSlotSize,
                Math.max(48, w - 12),
                maxGridRows
            );
            int cardH = 22 + grid.height() + 5;
            if (cy + cardH > bottomLimit) {
                break;
            }
            Rect card = new Rect(x, cy, w, cardH);
            boolean cardHover = card.contains(mouseX, mouseY);
            OreUiPaint.raisedCard(canvas, card.x(), card.y(), card.width(), card.height(), palette, cardHover, false);
            String machine = recipe.machineRequirement().compatibleMachines().isEmpty()
                    ? "Hand Craft"
                    : StepNodeCardRenderer
                            .machineNameOf(recipe.machineRequirement().compatibleMachines().get(0), null).getString();
            Component pinText = Component.literal("Pin");
            int pinW = ChromeText.width(canvas, pinText) + 10;
            Rect pinBtn = new Rect(card.right() - pinW - 4, card.y() + 3, pinW, 13);
            boolean pinHover = pinBtn.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                pinBtn.x(),
                pinBtn.y(),
                pinBtn.width(),
                pinBtn.height(),
                palette,
                false,
                pinHover,
                true,
                false
            );
            ChromeText.draw(
                canvas,
                pinText,
                pinBtn.x() + 5,
                ChromeText.centeredButtonY(pinBtn.y(), pinBtn.height(), canvas),
                palette.textOnAccent()
            );
            previewActionHits.add(new PreviewActionHit(pinBtn, () -> {
                PlannerWorkbenchController controller = shellContext.activeController();
                if (controller != null) {
                    controller.refine(new PinResourceRecipeFeedback(resource, recipe.key(), false), null);
                }
                close();
            }));

            ChromeText.drawFitted(
                canvas,
                Component.literal(machine),
                card.x() + 5,
                card.y() + 5,
                Math.max(16, pinBtn.x() - card.x() - 10),
                palette.textPrimary()
            );

            OreUiRecipeGrid.render(canvas, inputs, outputs, grid, card.x() + 6, card.y() + 20, palette);
            cy += cardH + 4;
        }
        if (producers.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                Component.literal("Leaf / raw resource (no craft recipes)"),
                x,
                cy,
                w,
                palette.textMuted()
            );
        }

        // Bottom quick-action buttons: [Set Target] [+ Basket].
        int btnY = y + h - 18;
        int halfW = Math.max(40, (w - 4) / 2);
        Rect setBtn = new Rect(x, btnY, halfW, 17);
        Rect addBtn = new Rect(x + halfW + 4, btnY, w - halfW - 4, 17);
        boolean setHover = setBtn.contains(mouseX, mouseY);
        boolean addHover = addBtn.contains(mouseX, mouseY);
        OreUiPaint.raisedButton(
            canvas,
            setBtn.x(),
            setBtn.y(),
            setBtn.width(),
            setBtn.height(),
            palette,
            false,
            setHover,
            true,
            false
        );
        Component setLbl = Component.literal("Set Target");
        int setTextW = ChromeText.width(canvas, setLbl);
        int setGroupW = setTextW + 4 + 8;
        int setStartX = setBtn.x() + Math.max(3, (setBtn.width() - setGroupW) / 2);
        ChromeText.drawFitted(
            canvas,
            setLbl,
            setStartX,
            ChromeText.centeredButtonY(setBtn.y(), setBtn.height(), canvas),
            Math.max(12, setBtn.width() - 16),
            palette.textOnAccent()
        );
        if (setStartX + setTextW + 4 + 8 <= setBtn.right() - 2) {
            ChromeButtons.returnArrow(
                canvas,
                setStartX + setTextW + 4,
                setBtn.y() + Math.max(2, (setBtn.height() - 2 - 6) / 2),
                palette.textOnAccent()
            );
        }
        previewActionHits.add(new PreviewActionHit(setBtn, () -> activateSelected(false)));

        OreUiPaint.raisedButton(
            canvas,
            addBtn.x(),
            addBtn.y(),
            addBtn.width(),
            addBtn.height(),
            palette,
            false,
            addHover,
            false,
            false
        );
        Component addLbl = Component.literal("Basket");
        int addTextW = ChromeText.width(canvas, addLbl);
        int addGroupW = 5 + 4 + addTextW;
        int addStartX = addBtn.x() + Math.max(3, (addBtn.width() - addGroupW) / 2);
        int addInk = addHover ? palette.focusRing() : palette.textPrimary();
        ChromeButtons.plus(canvas, addStartX, addBtn.y() + Math.max(2, (addBtn.height() - 2 - 5) / 2), 5, addInk);
        ChromeText.drawFitted(
            canvas,
            addLbl,
            addStartX + 9,
            ChromeText.centeredButtonY(addBtn.y(), addBtn.height(), canvas),
            Math.max(12, addBtn.right() - addStartX - 11),
            addInk
        );
        previewActionHits.add(new PreviewActionHit(addBtn, () -> activateSelected(true)));
    }

    private void renderScrim(SceneCanvas canvas, CirrusPalette palette, float progress) {
        int shellWidth = Math.max(0, Math.round(shellContext.shellWidth()));
        int shellHeight = Math.max(0, Math.round(shellContext.shellHeight()));
        if (shellWidth <= 0 || shellHeight <= 0) {
            return;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(scrimAlpha * progress)));
        int scrim = (palette.dropShadow() & 0x00FFFFFF) | (alpha << 24);
        canvas.fill(-posX(), -posY(), shellWidth, shellHeight, scrim);
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette palette, int width, int mouseX, int mouseY) {
        modeChipHits.clear();
        int headerBg = CirrusUiTheme.isDark(this) ? palette.recessedSurface() : palette.canvasVoid();
        canvas.fill(1, 1, width - 2, headerHeight - 1, headerBg);
        canvas.fill(1, headerHeight - 1, width - 2, 1, palette.hairlineBorder());

        // Right-aligned clickable quick-filter mode chips when wide enough.
        int chipsReserve = 0;
        if (width >= 380) {
            String[][] modes = {{"All", ""}, {"#Step", "#"}, {"@Mod", "@"}, {">Cmd", ">"}, {"!Rule", "!"}};
            int rx = width - pad;
            Rect[] rects = new Rect[modes.length];
            for (int i = modes.length - 1; i >= 0; i--) {
                int cw = ChromeText.width(canvas, Component.literal(modes[i][0])) + 8;
                rx -= cw;
                rects[i] = new Rect(rx, 3, cw, 13);
                rx -= 3;
            }
            chipsReserve = width - rx + 4;
            for (int i = 0; i < modes.length; i++) {
                Rect r = rects[i];
                String prefix = modes[i][1];
                boolean active = prefix.isEmpty()
                        ? (query.isEmpty() || "#@>!".indexOf(query.charAt(0)) < 0)
                        : query.startsWith(prefix);
                boolean hover = r.contains(mouseX, mouseY);
                if (active) {
                    OreUiPaint.raisedCard(canvas, r.x(), r.y(), r.width(), r.height(), palette, hover, true);
                } else {
                    OreUiPaint.raisedButton(
                        canvas,
                        r.x(),
                        r.y(),
                        r.width(),
                        r.height(),
                        palette,
                        false,
                        hover,
                        false,
                        false
                    );
                }
                ChromeText.draw(
                    canvas,
                    Component.literal(modes[i][0]),
                    r.x() + 4,
                    active
                            ? ChromeText.centeredY(r.y() + 2, r.height() - 4, canvas)
                            : ChromeText.centeredButtonY(r.y(), r.height(), canvas),
                    active ? palette.focusRing() : palette.textSecondary()
                );
                modeChipHits.add(new ModeChipHit(r, prefix));
            }
        }

        int inputAvail = Math.max(40, width - pad * 2 - chipsReserve);
        if (query.isEmpty()) {
            ChromeText.drawFitted(
                canvas,
                CirrusLangs.Workbench.Palette.placeholder.get(),
                pad,
                ChromeText.centeredY(0, headerHeight, canvas),
                inputAvail,
                palette.textMuted()
            );
        } else {
            ChromeText.drawFitted(
                canvas,
                Component.literal(query),
                pad,
                ChromeText.centeredY(0, headerHeight, canvas),
                inputAvail,
                palette.textPrimary()
            );
        }
        if (focused()) {
            int caretX = Math.min(pad + inputAvail, pad + ChromeText.width(canvas, Component.literal(query)));
            canvas.fill(caretX, 5, 1, headerHeight - 9, palette.focusRing());
        }
    }

    private void renderFooter(SceneCanvas canvas, CirrusPalette palette, int height, int width) {
        if (height < headerHeight + footerHeight + 1) {
            return;
        }
        int y = height - footerHeight;
        canvas.fill(1, y, width - 2, 1, palette.hairlineBorder());
        int textY = ChromeText.centeredY(y, footerHeight, canvas);
        Component leftHint = footerHint();
        Component rightHint = footerRightHint();
        int rightWidth = ChromeText.width(canvas, rightHint);
        int leftMaxWidth = Math.max(0, width - pad * 2 - (rightWidth > 0 ? rightWidth + 8 : 0));
        ChromeText.drawFitted(canvas, leftHint, pad, textY, leftMaxWidth, palette.textSecondary());
        if (rightWidth > 0) {
            ChromeText.drawRight(canvas, rightHint, width - pad, textY, palette.textMuted());
        }
    }

    private static int categoryTint(CirrusPalette palette, String categoryKey) {
        if (categoryKey == null) {
            return palette.hairlineBorder();
        }
        return switch (categoryKey) {
            case CommandPaletteEngine.categoryStep -> palette.craftStep().accent();
            case CommandPaletteEngine.categoryResource -> palette.acquireStep().accent();
            case CommandPaletteEngine.categoryPrompt -> palette.costC();
            case CommandPaletteEngine.categoryRecent -> palette.textSecondary();
            default -> palette.focusRing();
        };
    }

    private WorkbenchShellLayout shellLayout() {
        return WorkbenchShellLayout.compute(
            shellContext.shellWidth(),
            shellContext.shellHeight(),
            shellContext.breakpoint(),
            shellContext.zenMode(),
            shellContext.leftDockWidth(),
            shellContext.rightDockWidth()
        );
    }
}
