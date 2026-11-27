package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeStyle;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.UiScaleMetrics;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.workbench.chrome.BottomTimelineStripWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.DockResizeHandleWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.LeftModeRailWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ProfileWeightsPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.RecipeAssistantPopupWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TargetBasketPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ToastStackWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHandle;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.layout.LayoutScope;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.taffy.tree.Layout;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Full-screen Z0..Z7 shell: positions chrome absolutely from {@link WorkbenchShellLayout} and hosts the four workspaces.
 */
public class WorkbenchShellWidget extends WidgetGroup<Widget> implements WorkbenchShellContext {

    private static final int zStage = 0;
    private static final int zTopChrome = 20;
    private static final int zDock = 30;
    private static final int zBottom = 40;
    private static final int zPopover = 60;
    private static final int zPalette = 80;
    private static final int zToast = 100;
    private static final int zTooltip = 120;
    private static final float dockWidthUnset = -1f;
    private static final long graphPollIntervalNanos = 200_000_000L;

    private final WorkspaceProvider provider;
    private final SessionTabManager tabManager;
    private final Runnable tabListener = this::notifyListeners;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final EnumMap<WorkbenchMode, Widget> workspaces = new EnumMap<>(WorkbenchMode.class);

    private final WidgetGroup<Widget> stage = new WidgetGroup<>();
    private final SessionTabBarWidget sessionTabBar;
    private final TopCommandDockWidget topCommandDock;
    private final LeftModeRailWidget leftModeRail;
    private final BottomTimelineStripWidget bottomTimelineStrip;
    private final CommandPaletteOverlayWidget commandPalette;
    private final ToastStackWidget toastStack;
    private final DockResizeHandleWidget leftResizeHandle;
    private final DockResizeHandleWidget rightResizeHandle;
    private final DockExpandTabWidget leftExpandTab;
    private final DockExpandTabWidget rightExpandTab;
    private final TargetBasketPopoverWidget targetBasket;
    private final ProfileWeightsPopoverWidget profileWeights;
    private final RecipeAssistantPopupWidget recipeAssistant;
    private final OreUiRichTooltipOverlayWidget tooltipOverlay;

    private @Nullable Widget leftDock;
    private @Nullable Widget rightDock;
    private WorkbenchMode mode;
    private WorkbenchThemeMode theme = loadConfiguredTheme();
    private boolean zen;
    private boolean leftDockCollapsed;
    private boolean rightDockCollapsed;
    private float requestedLeftDockWidth = dockWidthUnset;
    private float requestedRightDockWidth = dockWidthUnset;
    private float expandedLeftDockWidth = dockWidthUnset;
    private float expandedRightDockWidth = dockWidthUnset;
    private WorkbenchShellLayout layout = WorkbenchShellLayout.compute(0f, 0f);
    private @Nullable WorkbenchBreakpoint breakpoint;
    private @Nullable Widget activeWorkspace;
    private float appliedWidth = -1f;
    private float appliedHeight = -1f;
    private float pendingWidth = -1f;
    private float pendingHeight = -1f;
    private boolean geometryFlushScheduled;
    private boolean graphPolling;
    private long lastGraphPollNanos;
    private long lastGraphVersion = Long.MIN_VALUE;
    private boolean shutdown;

    public WorkbenchShellWidget(WorkspaceProvider provider, SessionTabManager tabManager, WorkbenchMode initialMode) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.tabManager = Objects.requireNonNull(tabManager, "tabManager");
        this.mode = Objects.requireNonNull(initialMode, "initialMode");
        addStyleClass("workbench-shell");
        useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0));
        setTickable(true);

        stage.useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0), UIStyles.zIndex(zStage));
        sessionTabBar = new SessionTabBarWidget(this);
        topCommandDock = new TopCommandDockWidget(this);
        leftModeRail = new LeftModeRailWidget(this);
        bottomTimelineStrip = new BottomTimelineStripWidget(this);
        commandPalette = new CommandPaletteOverlayWidget(this);
        toastStack = new ToastStackWidget(this);
        leftResizeHandle = new DockResizeHandleWidget(this, true);
        rightResizeHandle = new DockResizeHandleWidget(this, false);
        leftExpandTab = new DockExpandTabWidget(true);
        rightExpandTab = new DockExpandTabWidget(false);
        targetBasket = new TargetBasketPopoverWidget(this);
        profileWeights = new ProfileWeightsPopoverWidget(this);
        recipeAssistant = new RecipeAssistantPopupWidget(this);
        tooltipOverlay = new OreUiRichTooltipOverlayWidget(this);

        sessionTabBar.addStyleClass("workbench-tabbar");
        topCommandDock.addStyleClass("workbench-topbar");
        leftModeRail.addStyleClass("workbench-rail");
        bottomTimelineStrip.addStyleClass("workbench-timeline");
        commandPalette.addStyleClass("workbench-palette");
        toastStack.addStyleClass("workbench-toast");
        targetBasket.addStyleClass("workbench-popover");
        profileWeights.addStyleClass("workbench-popover");
        recipeAssistant.addStyleClass("workbench-popover");

        addWidget(stage);
        addWidget(sessionTabBar).useStyle(UIStyles.zIndex(zTopChrome));
        addWidget(topCommandDock).useStyle(UIStyles.zIndex(zTopChrome));
        addWidget(leftModeRail).useStyle(UIStyles.zIndex(zDock));
        addWidget(bottomTimelineStrip).useStyle(UIStyles.zIndex(zBottom));
        addWidget(commandPalette).useStyle(UIStyles.zIndex(zPalette));
        addWidget(toastStack).useStyle(UIStyles.zIndex(zToast));
        addWidget(leftResizeHandle).useStyle(UIStyles.zIndex(zDock));
        addWidget(rightResizeHandle).useStyle(UIStyles.zIndex(zDock));
        addWidget(leftExpandTab).useStyle(UIStyles.zIndex(zDock));
        addWidget(rightExpandTab).useStyle(UIStyles.zIndex(zDock));
        addWidget(targetBasket).useStyle(UIStyles.zIndex(zPopover));
        addWidget(profileWeights).useStyle(UIStyles.zIndex(zPopover));
        addWidget(recipeAssistant).useStyle(UIStyles.zIndex(zPopover));
        addWidget(tooltipOverlay).useStyle(UIStyles.zIndex(zTooltip));

        onLayout(this::resolveShellLayout);
        onMount(this::onShellMount);
        onTick(this::pollGraphVersion);
        onMouseReleased((input, ctx) -> {
            if (finishExternalDrags(input.mouseX(), input.mouseY())) {
                ctx.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        applyChromeDefaultBackgrounds();
        applyWorkspace();
        tabManager.addListener(tabListener);
    }

    //region facade

    @Override
    public SessionTabManager tabManager() {
        return tabManager;
    }

    @Override
    public @Nullable PlannerWorkbenchController activeController() {
        return tabManager.activeTab();
    }

    @Override
    public WorkbenchUiSnapshot activeSnapshot() {
        PlannerWorkbenchController active = activeController();
        return active == null ? WorkbenchUiSnapshot.empty() : active.snapshot();
    }

    @Override
    public @Nullable CompiledRecipeGraph activeGraph() {
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        return runtime == null ? null : runtime.currentGraph().orElse(null);
    }

    @Override
    public WorkbenchBreakpoint breakpoint() {
        WorkbenchBreakpoint current = breakpoint;
        return current != null ? current : WorkbenchBreakpoint.classify(shellWidth());
    }

    @Override
    public WorkbenchThemeMode theme() {
        return theme;
    }

    @Override
    public void setTheme(WorkbenchThemeMode theme) {
        WorkbenchThemeMode next = Objects.requireNonNull(theme, "theme");
        if (this.theme == next) {
            return;
        }
        this.theme = next;
        persistConfiguredTheme(next);
        applyChromeDefaultBackgrounds();
        refreshWorkspacesTheme(next);
        if (lifecycle().mounted()) {
            CirrusThemeStyle.applyToScene(scene(), next);
        }
        notifyListeners();
    }

    @Override
    public CirrusPalette palette() {
        return CirrusUiTheme.palette(this);
    }

    @Override
    public WorkbenchMode mode() {
        return mode;
    }

    @Override
    public void setMode(WorkbenchMode mode) {
        WorkbenchMode next = Objects.requireNonNull(mode, "mode");
        if (this.mode == next) {
            return;
        }
        this.mode = next;
        applyWorkspace();
        refreshGeometry();
        notifyListeners();
    }

    @Override
    public boolean zenMode() {
        return zen;
    }

    @Override
    public void setZenMode(boolean zen) {
        if (this.zen == zen) {
            return;
        }
        this.zen = zen;
        refreshGeometry();
        notifyListeners();
    }

    /**
     * Installs the Z3 left dock island; {@code null} uninstalls it. Geometry and visibility come only from the current layout rectangle.
     */
    public void setLeftDock(@Nullable Widget dock) {
        if (leftDock == dock) {
            return;
        }
        if (leftDock != null) {
            removeWidget(leftDock);
        }
        leftDock = dock;
        if (dock != null) {
            dock.addStyleClass("workbench-dock");
            dock.defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(theme))));
            addWidget(dock).useStyle(UIStyles.zIndex(zDock));
            applyRegion(dock, layout.leftDock(), true);
        }
    }

    /**
     * Installs the Z3 right dock island; {@code null} uninstalls it.
     */
    public void setRightDock(@Nullable Widget dock) {
        if (rightDock == dock) {
            return;
        }
        if (rightDock != null) {
            removeWidget(rightDock);
        }
        rightDock = dock;
        if (dock != null) {
            dock.addStyleClass("workbench-dock");
            dock.defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(theme))));
            addWidget(dock).useStyle(UIStyles.zIndex(zDock));
            applyRegion(dock, layout.rightDock(), true);
        }
    }

    @Override
    public float leftDockWidth() {
        return layout.leftDock().width();
    }

    @Override
    public void setLeftDockWidth(float width) {
        float next = width == 0f ? 0f : WorkbenchBreakpoint.clampLeftDockWidth(width);
        if (next == requestedLeftDockWidth) {
            return;
        }
        requestedLeftDockWidth = next;
        leftDockCollapsed = next == 0f;
        if (next > 0f) {
            expandedLeftDockWidth = next;
        }
        refreshGeometry();
        notifyListeners();
    }

    @Override
    public boolean leftDockCollapsed() {
        return leftDockCollapsed;
    }

    @Override
    public void setLeftDockCollapsed(boolean collapsed) {
        if (this.leftDockCollapsed == collapsed) {
            return;
        }
        if (collapsed) {
            if (requestedLeftDockWidth > 0f) {
                expandedLeftDockWidth = requestedLeftDockWidth;
            }
            this.leftDockCollapsed = true;
            this.requestedLeftDockWidth = 0f;
        } else {
            this.leftDockCollapsed = false;
            this.requestedLeftDockWidth = expandedLeftDockWidth;
        }
        refreshGeometry();
        notifyListeners();
    }

    @Override
    public float rightDockWidth() {
        return layout.rightDock().width();
    }

    @Override
    public void setRightDockWidth(float width) {
        float next = width == 0f ? 0f : WorkbenchBreakpoint.clampRightDockWidth(width);
        if (next == requestedRightDockWidth) {
            return;
        }
        requestedRightDockWidth = next;
        rightDockCollapsed = next == 0f;
        if (next > 0f) {
            expandedRightDockWidth = next;
        }
        refreshGeometry();
        notifyListeners();
    }

    @Override
    public boolean rightDockCollapsed() {
        return rightDockCollapsed;
    }

    public @Nullable RightCoPilotDockWidget rightDockWidget() {
        return rightDock instanceof RightCoPilotDockWidget dock ? dock : null;
    }

    public @Nullable LeftPlanDockWidget leftDockWidget() {
        return leftDock instanceof LeftPlanDockWidget dock ? dock : null;
    }

    @Override
    public void setRightDockCollapsed(boolean collapsed) {
        if (this.rightDockCollapsed == collapsed) {
            return;
        }
        if (collapsed) {
            if (requestedRightDockWidth > 0f) {
                expandedRightDockWidth = requestedRightDockWidth;
            }
            this.rightDockCollapsed = true;
            this.requestedRightDockWidth = 0f;
        } else {
            this.rightDockCollapsed = false;
            this.requestedRightDockWidth = expandedRightDockWidth;
        }
        refreshGeometry();
        notifyListeners();
    }

    @Override
    public float shellWidth() {
        return layout.width();
    }

    @Override
    public float shellHeight() {
        return layout.height();
    }

    @Override
    public float leftRailWidth() {
        return layout.leftRail().width();
    }

    @Override
    public float topChromeHeight() {
        return layout.sessionTabBar().height() + layout.topDock().height();
    }

    public OreUiRichTooltipOverlayWidget tooltipOverlay() {
        return tooltipOverlay;
    }

    public RecipeAssistantPopupWidget recipeAssistant() {
        return recipeAssistant;
    }

    public void openRecipeAssistant(@Nullable ResourceKey resource) {
        if (resource == null) {
            return;
        }
        recipeAssistant.openFor(resource);
        notifyListeners();
    }

    public void openTargetCatalogPicker() {
        recipeAssistant.openTargetCatalogPicker();
        notifyListeners();
    }

    public void openGuidedDecisionWorkbench() {
        recipeAssistant.openGuidedDecisions();
        notifyListeners();
    }

    @Override
    public void toast(LangEntry message) {
        toast(Objects.requireNonNull(message, "message").get());
    }

    @Override
    public void toast(Component message) {
        toastStack.push(message);
        notifyListeners();
    }

    @Override
    public void openCommandPalette() {
        commandPalette.open();
        notifyListeners();
    }

    @Override
    public void closeCommandPalette() {
        commandPalette.close();
        notifyListeners();
    }

    /**
     * Ends every drag the chrome owns, whatever the pointer was over when the button came up.
     *
     * <p>A widget only sees the release its own strip catches, so a dock resize that ends over the canvas would keep its
     * session armed and paint the separator as still dragging. The screen forwards every pointer release here.
     */
    public void releaseAllDrags() {
        leftResizeHandle.release();
        rightResizeHandle.release();
        if (rightDock instanceof RightCoPilotDockWidget dock) {
            dock.finishActiveDrag(-1.0, -1.0);
        }
        Widget plannerWs = workspaces.get(WorkbenchMode.planner);
        if (plannerWs instanceof FlowStageWidget flowStage) {
            flowStage.dag().finishExternalDrag(-1.0, -1.0);
        }
    }

    public void releaseAllDrags(double mouseX, double mouseY) {
        leftResizeHandle.release();
        rightResizeHandle.release();
        finishExternalDrags(mouseX, mouseY);
    }

    public boolean finishExternalDrags(double mouseX, double mouseY) {
        boolean consumed = false;
        if (rightDock instanceof RightCoPilotDockWidget dock) {
            consumed |= dock.finishActiveDrag(mouseX, mouseY);
        }
        Widget plannerWs = workspaces.get(WorkbenchMode.planner);
        if (plannerWs instanceof FlowStageWidget flowStage) {
            consumed |= flowStage.dag().finishExternalDrag(mouseX, mouseY);
        }
        return consumed;
    }

    /** Closes every floating popover, menu and palette other than {@code except}. */
    public boolean closeTransientOverlays(@Nullable Widget except) {
        boolean closed = false;
        if (commandPalette != except && commandPalette.isOpen()) {
            commandPalette.close();
            closed = true;
        }
        if (targetBasket != except && targetBasket.isOpen()) {
            targetBasket.hide();
            closed = true;
        }
        if (profileWeights != except && profileWeights.isOpen()) {
            profileWeights.hide();
            closed = true;
        }
        if (recipeAssistant != except && recipeAssistant.isOpen()) {
            recipeAssistant.hide();
            closed = true;
        }
        if (topCommandDock != except && topCommandDock.presetMenuOpen()) {
            topCommandDock.closePresetMenu();
            closed = true;
        }
        if (sessionTabBar != except && sessionTabBar.contextMenuTabId() != null) {
            sessionTabBar.closeContextMenu();
            closed = true;
        }
        Widget plannerWs = workspaces.get(WorkbenchMode.planner);
        if (plannerWs instanceof FlowStageWidget flowStage
                && flowStage.dag() != except
                && flowStage.dag().viewSettingsOpen()) {
            flowStage.dag().closeViewSettingsPopover();
            closed = true;
        }
        return closed;
    }

    /** Closes every Z5 popover and floating chrome menu; returns whether one was actually open. */
    public boolean closePopovers() {
        return closeTransientOverlays(commandPalette);
    }

    @Override
    public boolean commandPaletteOpen() {
        return commandPalette.isOpen();
    }

    @Override
    public void requestReplan() {
        PlannerWorkbenchController active = activeController();
        if (active != null) {
            active.replan();
        }
    }

    @Override
    public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        PlannerWorkbenchController active = activeController();
        if (active == null) {
            openTab(copy, primaryRecipe);
            return;
        }
        active.setTargets(copy, primaryRecipe);
        notifyListeners();
    }

    @Override
    public void openTab(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        List<TargetDemand> copy = targets == null ? List.of() : List.copyOf(targets);
        try {
            tabManager.openOrFocusTab(copy, primaryRecipe);
        } catch (IllegalStateException full) {
            toastStack.push(CirrusLangs.Workbench.Tab.maxTabsHint.get());
        }
        notifyListeners();
    }

    @Override
    public void closeActiveTab() {
        String activeTabId = tabManager.activeTabId();
        if (activeTabId != null) {
            tabManager.closeTab(activeTabId);
        }
        notifyListeners();
    }

    @Override
    public void resetActiveTabSession() {
        PlannerWorkbenchController active = activeController();
        if (active != null) {
            active.resetSession();
            toastStack.push(CirrusLangs.Workbench.Topbar.resetSession.get());
        }
        notifyListeners();
    }

    @Override
    public void sendActivePlanToExecution() {
        WorkbenchUiSnapshot snapshot = activeSnapshot();
        ProductionPlan plan = snapshot.selectedPlan().orElse(null);
        RecipeEntry<?> resolvedRecipe = null;
        int resolvedTimes = 1;

        if (plan != null) {
            ResolvedPlanRecipe fromPlan = resolvePrimaryPlanRecipe(plan, snapshot.targets());
            if (fromPlan != null) {
                resolvedRecipe = fromPlan.recipe();
                resolvedTimes = fromPlan.times();
            }
        }
        if (resolvedRecipe == null) {
            PlannerWorkbenchController active = activeController();
            if (active != null && active.state().primaryRecipe() != null) {
                resolvedRecipe = active.state().primaryRecipe();
            }
        }
        if (resolvedRecipe == null) {
            CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.current();
            if (selection != null && selection.recipe() != null) {
                resolvedRecipe = selection.recipe();
                resolvedTimes = Math.max(1, selection.times());
            }
        }
        if (resolvedRecipe == null) {
            toastStack.push(
                ToastStackWidget.Tone.warning,
                CirrusLangs.Workbench.Topbar.sentNoRecipeTitle.get(),
                CirrusLangs.Workbench.Topbar.sentNoRecipe.get(),
                null,
                null
            );
            notifyListeners();
            return;
        }
        int craftSteps = plan == null
                ? 1
                : Math.max(
                    1,
                    (int) plan.steps().stream().filter(s -> s instanceof CraftStep || s instanceof ConvertStep).count()
                );
        ExecutionQueueStore.Entry queued = ExecutionQueueStore
                .enqueue(resolvedRecipe, Math.max(1, resolvedTimes), plan, craftSteps, true);
        ExecutionOverlayOpenState.setOpen(true);
        int queueIndex = Math.max(1, ExecutionQueueStore.activeIndex() + 1);
        Component itemName = queued.displayName();
        toastStack.push(
            ToastStackWidget.Tone.success,
            CirrusLangs.Workbench.Topbar.sentToastTitle.get(),
            CirrusLangs.Workbench.Topbar.sentToastBody.get(itemName, queueIndex),
            CirrusLangs.Workbench.Topbar.sentToastBadge.get(resolvedTimes, craftSteps),
            null
        );
        notifyListeners();
    }

    private record ResolvedPlanRecipe(RecipeEntry<?> recipe, int times) {}

    private @Nullable ResolvedPlanRecipe resolvePrimaryPlanRecipe(ProductionPlan plan, List<TargetDemand> targets) {
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        if (runtime == null) {
            return null;
        }
        CompiledRecipeGraph graph = runtime.currentGraph().orElse(null);
        ResourceKey primaryTarget = targets.isEmpty() ? null : targets.get(0).resource();
        CompiledRecipeKey fallbackKey = null;
        int fallbackBatches = 1;

        for (int i = plan.steps().size() - 1; i >= 0; i--) {
            PlanStep step = plan.steps().get(i);
            CompiledRecipeKey key = null;
            int batches = 1;
            if (step instanceof CraftStep craft) {
                key = craft.recipeKey();
                batches = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, craft.batches()));
            } else if (step instanceof ConvertStep convert) {
                key = convert.conversionRecipeKey();
                batches = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, convert.batches()));
            }
            if (key == null) {
                continue;
            }
            if (fallbackKey == null) {
                fallbackKey = key;
                fallbackBatches = batches;
            }
            if (primaryTarget != null && graph != null) {
                boolean matchesPrimary = graph.recipeByKey(key)
                        .map(r -> r.outputs().stream().anyMatch(o -> primaryTarget.equals(o.resource()))).orElse(false);
                if (matchesPrimary) {
                    RecipeEntry<?> entry = runtime.resolveRecipeEntry(key).orElse(null);
                    if (entry != null) {
                        return new ResolvedPlanRecipe(entry, batches);
                    }
                }
            }
        }
        if (fallbackKey != null) {
            RecipeEntry<?> entry = runtime.resolveRecipeEntry(fallbackKey).orElse(null);
            if (entry != null) {
                return new ResolvedPlanRecipe(entry, fallbackBatches);
            }
        }
        return null;
    }

    @Override
    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    @Override
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        commandPalette.close();
        listeners.clear();
        tabManager.removeListener(tabListener);
        tabManager.close();
    }

    //endregion

    //region graph

    private void onShellMount(Scene scene, SceneContext context, SceneHandle handle) {
        CirrusThemeStyle.applyToScene(scene, theme);
        armGraphPolling(scene, context, handle);
    }

    private void armGraphPolling(Scene scene, SceneContext context, SceneHandle handle) {
        graphPolling = true;
        lastGraphPollNanos = 0L;
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        long initialVersion = runtime == null ? 0L : runtime.publishedVersion();
        lastGraphVersion = initialVersion > 0L ? initialVersion : Long.MIN_VALUE;
        handle.onCleanup(() -> graphPolling = false);
    }

    /** The runtime publishes no listener, so the version is compared on a 200ms throttle. */
    private void pollGraphVersion() {
        if (!graphPolling || shutdown) {
            return;
        }
        long now = System.nanoTime();
        if (lastGraphPollNanos != 0L && now - lastGraphPollNanos < graphPollIntervalNanos) {
            return;
        }
        lastGraphPollNanos = now;
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        if (runtime == null) {
            return;
        }
        long version = runtime.publishedVersion();
        if (version == 0L && lastGraphVersion == Long.MIN_VALUE && !runtime.isCompiling()) {
            runtime.reloadFromRuntimeAsync();
        }
        if (version == lastGraphVersion) {
            return;
        }
        lastGraphVersion = version;
        tabManager.onRuntimeGraphVersionChanged(version);
    }

    //endregion

    //region layout

    /** Called by the host on viewport changes; styles are written only from discrete events, never from layout or render callbacks. */
    public void applyViewport(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            return;
        }
        if (width == appliedWidth && height == appliedHeight) {
            return;
        }
        applyGeometry(width, height);
    }

    private void applyGeometry(float width, float height) {
        appliedWidth = width;
        appliedHeight = height;
        breakpoint = WorkbenchBreakpoint.classify(width, breakpoint);
        layout = buildLayout(width, height);
        applyChromeGeometry();
    }

    private void refreshGeometry() {
        if (appliedWidth <= 0f || appliedHeight <= 0f) {
            return;
        }
        applyGeometry(appliedWidth, appliedHeight);
    }

    private WorkbenchShellLayout buildLayout(float width, float height) {
        WorkbenchShellLayout base = WorkbenchShellLayout
                .compute(width, height, breakpoint, zen, requestedLeftDockWidth, requestedRightDockWidth);
        return mode == WorkbenchMode.planner ? base : base.workspaceOnly();
    }

    private void applyChromeGeometry() {
        applyRegion(stage, layout.stage(), true);
        applyRegion(sessionTabBar, layout.sessionTabBar(), true);
        applyRegion(topCommandDock, layout.topDock(), true);
        applyRegion(leftModeRail, layout.leftRail(), true);
        applyRegion(bottomTimelineStrip, layout.bottomStrip(), true);
        applyRegion(commandPalette, layout.commandPalette(), false);
        applyRegion(toastStack, layout.toastStack(), false);
        WorkbenchShellLayout.Rect leftDockRect = layout.leftDock();
        applyRegion(
            leftResizeHandle,
            new WorkbenchShellLayout.Rect(
                leftDockRect.right(),
                leftDockRect.y(),
                DockResizeHandleWidget.handleWidth,
                leftDockRect.height()
            ),
            true
        );
        WorkbenchShellLayout.Rect rightDockRect = layout.rightDock();
        applyRegion(
            rightResizeHandle,
            new WorkbenchShellLayout.Rect(
                rightDockRect.x() - DockResizeHandleWidget.handleWidth,
                rightDockRect.y(),
                DockResizeHandleWidget.handleWidth,
                rightDockRect.height()
            ),
            true
        );
        if (leftDock != null) {
            applyRegion(leftDock, leftDockRect, true);
        }
        if (rightDock != null) {
            applyRegion(rightDock, rightDockRect, true);
        }
        applyRegion(leftExpandTab, WorkbenchShellLayout.Rect.empty, true);
        applyRegion(rightExpandTab, WorkbenchShellLayout.Rect.empty, true);
        layoutPopover(targetBasket, 0);
        layoutPopover(profileWeights, 1);
        float assistW = UiScaleMetrics.fitWithin(620f, 0f, appliedWidth, 8f);
        float assistH = UiScaleMetrics.fitWithin(372f, 0f, appliedHeight, 12f);
        float assistX = Math.max(4f, (appliedWidth - assistW) * 0.5f);
        float assistY = Math.max(4f, (appliedHeight - assistH) * 0.46f);
        applyRegion(recipeAssistant, new WorkbenchShellLayout.Rect(assistX, assistY, assistW, assistH), false);
        applyRegion(
            tooltipOverlay,
            new WorkbenchShellLayout.Rect(0f, 0f, Math.max(0f, appliedWidth), Math.max(0f, appliedHeight)),
            false
        );
    }

    private void applyRegion(Widget widget, WorkbenchShellLayout.Rect rect, boolean visibilityFollowsRegion) {
        widget.useStyle(
            positionAbsolute(),
            insetLeft(rect.x()),
            insetTop(rect.y()),
            sizeOf(rect.width(), rect.height())
        );
        if (visibilityFollowsRegion) {
            widget.setVisible(!rect.empty());
        }
    }

    private void layoutPopover(Widget popover, int index) {
        WorkbenchShellLayout.Rect anchor = layout.leftDock().empty() ? layout.stage() : layout.leftDock();
        float preferredW = popover instanceof TargetBasketPopoverWidget
                ? TargetBasketPopoverWidget.width
                : ProfileWeightsPopoverWidget.width;
        float preferredH = popover instanceof TargetBasketPopoverWidget
                ? TargetBasketPopoverWidget.height
                : ProfileWeightsPopoverWidget.height;
        float popW = UiScaleMetrics.fitWithin(preferredW, 0f, appliedWidth, 4f);
        float popH = UiScaleMetrics.fitWithin(preferredH, 0f, appliedHeight, 4f);
        float maxX = Math.max(4f, appliedWidth - popW - 4f);
        float maxY = Math.max(4f, appliedHeight - popH - 4f);
        float popoverX = Math.max(4f, Math.min(maxX, anchor.x() + 8f + index * 8f));
        float popoverY = Math.max(4f, Math.min(maxY, layout.sessionTabBar().height() + layout.topDock().height() + 6f));
        popover.useStyle(positionAbsolute(), insetLeft(popoverX), insetTop(popoverY), sizeOf(popW, popH));
    }

    /** Reads the taffy result only; a real size change queues the geometry update after the layout callback. */
    private void resolveShellLayout(Widget widget, LayoutScope scope) {
        Layout taffy = scope.taffyLayout();
        float currentWidth = taffy != null ? taffy.size().width : widget.width();
        float currentHeight = taffy != null ? taffy.size().height : widget.height();
        if (currentWidth <= 0f || currentHeight <= 0f) {
            return;
        }
        if (currentWidth == appliedWidth && currentHeight == appliedHeight) {
            return;
        }
        pendingWidth = currentWidth;
        pendingHeight = currentHeight;
        if (!geometryFlushScheduled && lifecycle().mounted()) {
            geometryFlushScheduled = true;
            scene().postLayout(this::flushPendingGeometry);
        }
    }

    private void flushPendingGeometry() {
        geometryFlushScheduled = false;
        if (pendingWidth == appliedWidth && pendingHeight == appliedHeight) {
            return;
        }
        if (pendingWidth > 0f && pendingHeight > 0f) {
            applyGeometry(pendingWidth, pendingHeight);
        }
    }

    //endregion

    //region workspace

    private void applyWorkspace() {
        Widget next = workspace(mode);
        if (next == null) {
            return;
        }
        if (next == activeWorkspace) {
            return;
        }
        Widget previous = activeWorkspace;
        Scene currentScene = lifecycle().mounted() ? scene() : null;
        if (previous != null) {
            stage.remove(previous);
            if (currentScene != null && previous.lifecycle().unmounted()) {
                currentScene.reuse(previous);
            }
        }
        activeWorkspace = next;
        stage.addWidget(next);
    }

    private @Nullable Widget workspace(WorkbenchMode target) {
        Widget cached = workspaces.get(target);
        if (cached != null) {
            return cached;
        }
        Widget created = switch (target) {
            case planner -> provider.plannerWorkspace(this);
            case facilities -> provider.facilitiesWorkspace(this);
            case database -> provider.databaseWorkspace(this);
            case settings -> provider.settingsWorkspace(this);
        };
        if (created != null) {
            created.addStyleClass("workbench-workspace");
            created.useStyle(sizeFull(), flexGrow(1), minWidth(0), minHeight(0));
            workspaces.put(target, created);
        }
        return created;
    }

    //endregion

    //region render

    private void refreshWorkspacesTheme(WorkbenchThemeMode next) {
        for (Widget workspace : workspaces.values()) {
            if (workspace instanceof FlowStageWidget flowStage) {
                flowStage.themeMode(next);
            } else if (workspace instanceof FacilitiesAndRulesWorkspace facilities) {
                facilities.syncTheme(next);
            } else if (workspace instanceof SettingsAndDiagnosticsWorkspace settings) {
                settings.syncTheme(next);
            }
        }
    }

    private void applyChromeDefaultBackgrounds() {
        UIStyle dockBg = UIStyle.of(background(CirrusTextures.Workbench.dockIsland(theme)));
        UIStyle cardBg = UIStyle.of(background(CirrusTextures.Workbench.cardSurface(theme)));
        sessionTabBar.defaultStyle(dockBg);
        topCommandDock.defaultStyle(cardBg);
        leftModeRail.defaultStyle(dockBg);
        bottomTimelineStrip.defaultStyle(dockBg);
        commandPalette.defaultStyle(dockBg);
        toastStack.defaultStyle(cardBg);
        targetBasket.defaultStyle(cardBg);
        profileWeights.defaultStyle(cardBg);
        if (leftDock != null) {
            leftDock.defaultStyle(dockBg);
        }
        if (rightDock != null) {
            rightDock.defaultStyle(dockBg);
        }
    }

    private static WorkbenchThemeMode loadConfiguredTheme() {
        try {
            WorkbenchThemeMode configured = ExecutionOverlayClientConfig.workbenchTheme();
            return configured == null ? WorkbenchThemeMode.defaultMode() : configured;
        } catch (RuntimeException | LinkageError ignored) {
            return WorkbenchThemeMode.defaultMode();
        }
    }

    private static void persistConfiguredTheme(WorkbenchThemeMode mode) {
        try {
            ExecutionOverlayClientConfig.setWorkbenchTheme(mode);
        } catch (RuntimeException | LinkageError ignored) {
            // Headless unit tests run without a loaded NeoForge ModConfigSpec.
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int viewportWidth = width();
        int viewportHeight = height();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }
        CirrusPalette palette = palette();
        canvas.fill(0, 0, viewportWidth, viewportHeight, palette.backdropScrim());
        canvas.fill(0, 0, viewportWidth, viewportHeight, palette.canvasVoid());
        WorkbenchPaint.dotGrid(canvas, viewportWidth, viewportHeight, 1f, palette);
    }

    //endregion

    private final class DockExpandTabWidget extends Widget implements OreUiTooltipProvider {
        private final boolean leftSide;

        private DockExpandTabWidget(boolean leftSide) {
            this.leftSide = leftSide;
            setInteractive(true);
            setVisible(false);
            onMouseClick((input, clickCount, clickCtx) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                if (leftSide) {
                    setLeftDockCollapsed(false);
                } else {
                    setRightDockCollapsed(false);
                }
                clickCtx.consume();
                return EventDispatch.consumed;
            });
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            return leftSide
                    ? OreUiRichTooltip.builder("Expand Plans Panel").badge("OPEN", OreUiRichTooltip.Tone.accent)
                            .text("Restores the left Pareto plans dock.", OreUiRichTooltip.Tone.secondary).build()
                    : OreUiRichTooltip.builder("Expand Co-Pilot Panel").badge("OPEN", OreUiRichTooltip.Tone.accent)
                            .text("Restores the right decisions & inspector dock.", OreUiRichTooltip.Tone.secondary)
                            .build();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (width() <= 0 || height() <= 0) {
                return;
            }
            CirrusPalette palette = palette();
            boolean hover = mouseX >= 0 && mouseX < width() && mouseY >= 0 && mouseY < height();
            OreUiPaint.raisedButton(canvas, 0, 0, width(), height(), palette, false, hover, false, false);
            int iconX = Math.max(2, (width() - 7) / 2);
            int iconY = Math.max(2, (height() - 2 - 7) / 2);
            int ink = hover ? palette.focusRing() : palette.textSecondary();
            if (leftSide) {
                ChromeButtons.doubleChevronRight(canvas, iconX, iconY, ink);
            } else {
                ChromeButtons.doubleChevronLeft(canvas, iconX, iconY, ink);
            }
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A listener must never break the shell state machine.
            }
        }
    }
}
