package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class SessionTabState {

    private final String tabId;
    private String tabTitle = "";
    private List<TargetDemand> targets = List.of();
    private @Nullable RecipeEntry<?> primaryRecipe;
    private PlanningProfile profile = PlanningProfile.defaultBalanced();
    private PlanningBudget budget = PlanningBudget.defaultInteractive();
    private boolean useLiveStock = true;
    private StockLedger reservedLedger = StockLedger.empty;
    private ProgressionState progression = ProgressionState.overworldDefault;
    private FlowViewMode flowViewMode = FlowViewMode.spatialDag;
    private DagLayoutMode dagLayoutMode = DagLayoutMode.horizontalLr;
    private DagEdgeStyle dagEdgeStyle = defaultEdgeStyle();
    private @Nullable String selectedPlanId;
    private @Nullable String comparePlanId;
    private Set<String> selectedStepIds = Set.of();
    private @Nullable String hoveredStepId;
    private @Nullable ResourceKey hoveredResource;
    private Set<String> expandedLoopStepIds = Set.of();
    private Map<String, FloatPos> manualNodeOffsets = Map.of();
    private List<GuidanceRule> sessionRules = List.of();
    private boolean defaultPersistToWorld;
    private boolean staleGraph;
    private long runtimeGraphVersion;
    private boolean cameraInitialized;
    private float cameraX;
    private float cameraY;
    private float cameraZoom = 1.0f;
    private @Nullable String anchorStepId;
    private @Nullable ResourceKey anchorResource;
    private boolean leftDockCollapsed;
    private boolean rightDockCollapsed;
    private String leftDockMode = "cards";
    private String rightDockTab = "prompts";
    private boolean minimapVisible = true;
    private boolean locatorRulerVisible = true;
    private boolean guidedPauseEnabled = false;
    private boolean guidedPauseDismissed;
    private float locatorScrollOffset;
    private Set<String> checkedChecklistSteps = Set.of();

    public SessionTabState(String tabId) {
        this.tabId = Objects.requireNonNull(tabId, "tabId");
    }

    private SessionTabState(SessionTabState source, String newTabId) {
        this.tabId = Objects.requireNonNull(newTabId, "newTabId");
        this.tabTitle = source.tabTitle;
        this.targets = source.targets;
        this.primaryRecipe = source.primaryRecipe;
        this.profile = source.profile;
        this.budget = source.budget;
        this.useLiveStock = source.useLiveStock;
        this.reservedLedger = source.reservedLedger;
        this.progression = source.progression;
        this.flowViewMode = source.flowViewMode;
        this.dagLayoutMode = source.dagLayoutMode;
        this.dagEdgeStyle = source.dagEdgeStyle;
        this.selectedPlanId = null;
        this.comparePlanId = null;
        this.selectedStepIds = Set.of();
        this.expandedLoopStepIds = source.expandedLoopStepIds;
        this.manualNodeOffsets = source.manualNodeOffsets;
        this.sessionRules = source.sessionRules;
        this.defaultPersistToWorld = source.defaultPersistToWorld;
        this.staleGraph = false;
        this.runtimeGraphVersion = source.runtimeGraphVersion;
        this.cameraInitialized = source.cameraInitialized;
        this.cameraX = source.cameraX;
        this.cameraY = source.cameraY;
        this.cameraZoom = source.cameraZoom;
        this.anchorStepId = source.anchorStepId;
        this.anchorResource = source.anchorResource;
        this.leftDockCollapsed = source.leftDockCollapsed;
        this.rightDockCollapsed = source.rightDockCollapsed;
        this.leftDockMode = source.leftDockMode;
        this.rightDockTab = source.rightDockTab;
        this.minimapVisible = source.minimapVisible;
        this.locatorRulerVisible = source.locatorRulerVisible;
        this.guidedPauseEnabled = source.guidedPauseEnabled;
        this.guidedPauseDismissed = false;
        this.locatorScrollOffset = source.locatorScrollOffset;
        this.checkedChecklistSteps = source.checkedChecklistSteps;
    }

    private static DagEdgeStyle defaultEdgeStyle() {
        try {
            DagEdgeStyle configured = ExecutionOverlayClientConfig.workbenchDefaultDagEdgeStyle();
            return configured == null ? DagEdgeStyle.orthogonal : configured;
        } catch (RuntimeException | LinkageError ignored) {
            return DagEdgeStyle.orthogonal;
        }
    }

    public SessionTabState copyAs(String newTabId) {
        return new SessionTabState(this, newTabId);
    }

    public String tabId() {
        return tabId;
    }

    public String tabTitle() {
        return tabTitle;
    }

    public void setTabTitle(String tabTitle) {
        this.tabTitle = tabTitle == null ? "" : tabTitle;
    }

    public List<TargetDemand> targets() {
        return targets;
    }

    public @Nullable RecipeEntry<?> primaryRecipe() {
        return primaryRecipe;
    }

    public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.primaryRecipe = primaryRecipe;
    }

    public PlanningProfile profile() {
        return profile;
    }

    public void setProfile(PlanningProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public PlanningBudget budget() {
        return budget;
    }

    public void setBudget(PlanningBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public boolean useLiveStock() {
        return useLiveStock;
    }

    public void setUseLiveStock(boolean useLiveStock) {
        this.useLiveStock = useLiveStock;
    }

    public StockLedger reservedLedger() {
        return reservedLedger;
    }

    public void setReservedLedger(StockLedger reservedLedger) {
        this.reservedLedger = Objects.requireNonNull(reservedLedger, "reservedLedger");
    }

    public ProgressionState progression() {
        return progression;
    }

    public void setProgression(ProgressionState progression) {
        this.progression = Objects.requireNonNull(progression, "progression");
    }

    public FlowViewMode flowViewMode() {
        return flowViewMode;
    }

    public void setFlowViewMode(FlowViewMode flowViewMode) {
        this.flowViewMode = Objects.requireNonNull(flowViewMode, "flowViewMode");
    }

    public DagLayoutMode dagLayoutMode() {
        return dagLayoutMode;
    }

    public void setDagLayoutMode(DagLayoutMode dagLayoutMode) {
        this.dagLayoutMode = Objects.requireNonNull(dagLayoutMode, "dagLayoutMode");
    }

    public @Nullable String selectedPlanId() {
        return selectedPlanId;
    }

    public void setSelectedPlanId(@Nullable String selectedPlanId) {
        this.selectedPlanId = selectedPlanId;
    }

    public @Nullable String comparePlanId() {
        return comparePlanId;
    }

    public void setComparePlanId(@Nullable String comparePlanId) {
        this.comparePlanId = comparePlanId;
    }

    public Set<String> selectedStepIds() {
        return selectedStepIds;
    }

    public void setSelectedStepIds(Set<String> selectedStepIds) {
        this.selectedStepIds = selectedStepIds == null ? Set.of() : Set.copyOf(selectedStepIds);
    }

    public @Nullable String hoveredStepId() {
        return hoveredStepId;
    }

    public void setHoveredStep(@Nullable String hoveredStepId) {
        this.hoveredStepId = hoveredStepId;
    }

    public @Nullable ResourceKey hoveredResource() {
        return hoveredResource;
    }

    public void setHoveredResource(@Nullable ResourceKey hoveredResource) {
        this.hoveredResource = hoveredResource;
    }

    public Set<String> expandedLoopStepIds() {
        return expandedLoopStepIds;
    }

    public void setExpandedLoopStepIds(Set<String> expandedLoopStepIds) {
        this.expandedLoopStepIds = expandedLoopStepIds == null ? Set.of() : Set.copyOf(expandedLoopStepIds);
    }

    public Map<String, FloatPos> manualNodeOffsets() {
        return manualNodeOffsets;
    }

    public void setManualNodeOffsets(Map<String, FloatPos> manualNodeOffsets) {
        this.manualNodeOffsets = copyOffsets(manualNodeOffsets);
    }

    public void setManualOffset(String stepId, FloatPos offset) {
        Objects.requireNonNull(stepId, "stepId");
        if (offset == null) {
            removeManualOffset(stepId);
            return;
        }
        TreeMap<String, FloatPos> next = new TreeMap<>(manualNodeOffsets);
        next.put(stepId, offset.copy());
        this.manualNodeOffsets = Map.copyOf(next);
    }

    public void removeManualOffset(String stepId) {
        if (stepId == null || !manualNodeOffsets.containsKey(stepId)) {
            return;
        }
        TreeMap<String, FloatPos> next = new TreeMap<>(manualNodeOffsets);
        next.remove(stepId);
        this.manualNodeOffsets = next.isEmpty() ? Map.of() : Map.copyOf(next);
    }

    public void clearManualOffsets() {
        this.manualNodeOffsets = Map.of();
    }

    public List<GuidanceRule> sessionRules() {
        return sessionRules;
    }

    public void setSessionRules(List<GuidanceRule> sessionRules) {
        this.sessionRules = sessionRules == null ? List.of() : List.copyOf(sessionRules);
    }

    public boolean defaultPersistToWorld() {
        return defaultPersistToWorld;
    }

    public void setDefaultPersistToWorld(boolean defaultPersistToWorld) {
        this.defaultPersistToWorld = defaultPersistToWorld;
    }

    public boolean staleGraph() {
        return staleGraph;
    }

    public void setStaleGraph(boolean staleGraph) {
        this.staleGraph = staleGraph;
    }

    public long runtimeGraphVersion() {
        return runtimeGraphVersion;
    }

    public void setRuntimeGraphVersion(long runtimeGraphVersion) {
        this.runtimeGraphVersion = runtimeGraphVersion;
    }

    public DagEdgeStyle dagEdgeStyle() {
        return dagEdgeStyle;
    }

    public void setDagEdgeStyle(DagEdgeStyle dagEdgeStyle) {
        this.dagEdgeStyle = dagEdgeStyle == null ? DagEdgeStyle.orthogonal : dagEdgeStyle;
    }

    public boolean cameraInitialized() {
        return cameraInitialized;
    }

    public float cameraX() {
        return cameraX;
    }

    public float cameraY() {
        return cameraY;
    }

    public float cameraZoom() {
        return cameraZoom;
    }

    public void setCameraState(float x, float y, float zoom) {
        this.cameraInitialized = true;
        this.cameraX = Float.isFinite(x) ? x : 0f;
        this.cameraY = Float.isFinite(y) ? y : 0f;
        this.cameraZoom = Float.isFinite(zoom) && zoom > 0f ? zoom : 1.0f;
    }

    public void clearCameraState() {
        this.cameraInitialized = false;
        this.cameraX = 0f;
        this.cameraY = 0f;
        this.cameraZoom = 1.0f;
    }

    public @Nullable String anchorStepId() {
        return anchorStepId;
    }

    public @Nullable ResourceKey anchorResource() {
        return anchorResource;
    }

    public void setAnchor(@Nullable String stepId, @Nullable ResourceKey resource) {
        this.anchorStepId = stepId;
        this.anchorResource = resource;
    }

    public boolean leftDockCollapsed() {
        return leftDockCollapsed;
    }

    public void setLeftDockCollapsed(boolean leftDockCollapsed) {
        this.leftDockCollapsed = leftDockCollapsed;
    }

    public boolean rightDockCollapsed() {
        return rightDockCollapsed;
    }

    public void setRightDockCollapsed(boolean rightDockCollapsed) {
        this.rightDockCollapsed = rightDockCollapsed;
    }

    public String leftDockMode() {
        return leftDockMode;
    }

    public void setLeftDockMode(String leftDockMode) {
        this.leftDockMode = leftDockMode == null || leftDockMode.isBlank() ? "cards" : leftDockMode;
    }

    public String rightDockTab() {
        return rightDockTab;
    }

    public void setRightDockTab(String rightDockTab) {
        this.rightDockTab = rightDockTab == null || rightDockTab.isBlank() ? "prompts" : rightDockTab;
    }

    public boolean minimapVisible() {
        return minimapVisible;
    }

    public void setMinimapVisible(boolean minimapVisible) {
        this.minimapVisible = minimapVisible;
    }

    public boolean locatorRulerVisible() {
        return locatorRulerVisible;
    }

    public void setLocatorRulerVisible(boolean locatorRulerVisible) {
        this.locatorRulerVisible = locatorRulerVisible;
    }

    public boolean guidedPauseEnabled() {
        return guidedPauseEnabled;
    }

    public void setGuidedPauseEnabled(boolean guidedPauseEnabled) {
        this.guidedPauseEnabled = guidedPauseEnabled;
    }

    public boolean guidedPauseDismissed() {
        return guidedPauseDismissed;
    }

    public void setGuidedPauseDismissed(boolean guidedPauseDismissed) {
        this.guidedPauseDismissed = guidedPauseDismissed;
    }

    public float locatorScrollOffset() {
        return locatorScrollOffset;
    }

    public void setLocatorScrollOffset(float locatorScrollOffset) {
        this.locatorScrollOffset = Float.isFinite(locatorScrollOffset) && locatorScrollOffset > 0f
                ? locatorScrollOffset
                : 0f;
    }

    public Set<String> checkedChecklistSteps() {
        return checkedChecklistSteps;
    }

    public void setCheckedChecklistSteps(Set<String> checkedChecklistSteps) {
        this.checkedChecklistSteps = checkedChecklistSteps == null ? Set.of() : Set.copyOf(checkedChecklistSteps);
    }

    public void resetViewState() {
        clearCameraState();
        this.anchorStepId = null;
        this.anchorResource = null;
        this.manualNodeOffsets = Map.of();
        this.locatorScrollOffset = 0f;
        this.checkedChecklistSteps = Set.of();
    }

    private static Map<String, FloatPos> copyOffsets(Map<String, FloatPos> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        TreeMap<String, FloatPos> next = new TreeMap<>();
        for (var e : source.entrySet()) {
            next.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()).copy());
        }
        return Map.copyOf(next);
    }
}
