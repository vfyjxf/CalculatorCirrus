package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class ResultStepsState {
    private RoutingOutcome outcome;
    private RoutingPlan plan;
    private StepTree tree;
    private ResultStepsViewMode viewMode = ResultStepsViewMode.LIST;
    private final Set<String> foldedLayered = new HashSet<>();
    private final Set<String> foldedTree = new HashSet<>();
    private final Set<String> foldedCard = new HashSet<>();

    // ── Redesign fields ──
    private String searchQuery = "";
    private SortMode sortMode = SortMode.EXECUTION_ORDER;
    private GroupMode groupMode = GroupMode.NONE;
    private @Nullable ContentStack<?, ?> highlightedMaterial;
    private @Nullable String selectedNode;
    private boolean navVisible = false;
    private boolean summaryCollapsed = false;

    public RoutingOutcome outcome() {
        return outcome;
    }

    public RoutingPlan plan() {
        return plan;
    }

    public StepTree tree() {
        return tree;
    }

    public ResultStepsViewMode viewMode() {
        return viewMode;
    }

    public void setViewMode(ResultStepsViewMode viewMode) {
        this.viewMode = viewMode == null ? ResultStepsViewMode.LIST : viewMode;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode == null ? SortMode.EXECUTION_ORDER : sortMode;
    }

    public GroupMode groupMode() {
        return groupMode;
    }

    public void setGroupMode(GroupMode groupMode) {
        this.groupMode = groupMode == null ? GroupMode.NONE : groupMode;
    }

    public @Nullable ContentStack<?, ?> highlightedMaterial() {
        return highlightedMaterial;
    }

    public void setHighlightedMaterial(@Nullable ContentStack<?, ?> material) {
        this.highlightedMaterial = material;
    }

    public @Nullable String selectedNode() {
        return selectedNode;
    }

    public void setSelectedNode(@Nullable String nodeId) {
        this.selectedNode = nodeId;
    }

    public boolean navVisible() {
        return navVisible;
    }

    public void setNavVisible(boolean visible) {
        this.navVisible = visible;
    }

    public boolean summaryCollapsed() {
        return summaryCollapsed;
    }

    public void setSummaryCollapsed(boolean collapsed) {
        this.summaryCollapsed = collapsed;
    }

    public boolean isLayerFolded(String nodeId) {
        return foldedLayered.contains(nodeId);
    }

    public boolean isTreeFolded(String nodeId) {
        return foldedTree.contains(nodeId);
    }

    public boolean isCardFolded(String nodeId) {
        return foldedCard.contains(nodeId);
    }

    public void setLayerFolded(String nodeId, boolean folded) {
        toggle(foldedLayered, nodeId, folded);
    }

    public void setTreeFolded(String nodeId, boolean folded) {
        toggle(foldedTree, nodeId, folded);
    }

    public void setCardFolded(String nodeId, boolean folded) {
        toggle(foldedCard, nodeId, folded);
    }

    void setOutcome(RoutingOutcome outcome) {
        this.outcome = outcome;
    }

    void setPlan(RoutingPlan plan) {
        this.plan = plan;
    }

    void setTree(StepTree tree) {
        this.tree = tree;
    }

    private static void toggle(Set<String> set, String nodeId, boolean folded) {
        if (nodeId == null) {
            return;
        }
        if (folded) {
            set.add(nodeId);
        } else {
            set.remove(nodeId);
        }
    }
}
