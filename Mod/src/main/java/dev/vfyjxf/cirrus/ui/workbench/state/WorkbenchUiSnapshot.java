package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record WorkbenchUiSnapshot(
    String tabId,
    String tabTitle,
    WorkbenchPhase phase,
    boolean planningInFlight,
    long runtimeGraphVersion,
    List<TargetDemand> targets,
    PlanningProfile profile,
    PlanningBudget budget,
    boolean useLiveStock,
    StockLedger reservedLedger,
    ProgressionState progression,
    @Nullable PlanningResult result,
    @Nullable PlanningResult lastFeasibleResult,
    @Nullable String selectedPlanId,
    @Nullable String comparePlanId,
    Set<String> selectedStepIds,
    @Nullable String hoveredStepId,
    @Nullable ResourceKey hoveredResource,
    FlowViewMode flowViewMode,
    DagLayoutMode dagLayoutMode,
    Set<String> expandedLoopStepIds,
    Map<String, FloatPos> manualNodeOffsets,
    List<HistoryEntry> undoHistory,
    List<HistoryEntry> redoHistory,
    List<GuidanceRule> activeSessionRules,
    @Nullable String lastErrorMessage
) {

    public WorkbenchUiSnapshot {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(tabTitle, "tabTitle");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(reservedLedger, "reservedLedger");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(selectedStepIds, "selectedStepIds");
        Objects.requireNonNull(flowViewMode, "flowViewMode");
        Objects.requireNonNull(dagLayoutMode, "dagLayoutMode");
        Objects.requireNonNull(expandedLoopStepIds, "expandedLoopStepIds");
        Objects.requireNonNull(manualNodeOffsets, "manualNodeOffsets");
        Objects.requireNonNull(undoHistory, "undoHistory");
        Objects.requireNonNull(redoHistory, "redoHistory");
        Objects.requireNonNull(activeSessionRules, "activeSessionRules");
        targets = List.copyOf(targets);
        selectedStepIds = Set.copyOf(selectedStepIds);
        expandedLoopStepIds = Set.copyOf(expandedLoopStepIds);
        manualNodeOffsets = Map.copyOf(manualNodeOffsets);
        undoHistory = List.copyOf(undoHistory);
        redoHistory = List.copyOf(redoHistory);
        activeSessionRules = List.copyOf(activeSessionRules);
    }

    public Optional<ProductionPlan> selectedPlan() {
        if (result == null || result.paretoPlans().isEmpty()) {
            return Optional.empty();
        }
        if (selectedPlanId != null) {
            for (ProductionPlan plan : result.paretoPlans()) {
                if (plan.planId().equals(selectedPlanId)) {
                    return Optional.of(plan);
                }
            }
        }
        return Optional.of(result.paretoPlans().get(0));
    }

    public Optional<ProductionPlan> comparePlan() {
        if (result == null || comparePlanId == null) {
            return Optional.empty();
        }
        for (ProductionPlan plan : result.paretoPlans()) {
            if (plan.planId().equals(comparePlanId)) {
                return Optional.of(plan);
            }
        }
        return Optional.empty();
    }

    public boolean canUndo() {
        return !undoHistory.isEmpty();
    }

    public boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    public static WorkbenchUiSnapshot empty() {
        return new WorkbenchUiSnapshot(
            "",
            "",
            WorkbenchPhase.noGraph,
            false,
            0L,
            List.of(),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            false,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            null,
            null,
            null,
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.spatialDag,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }
}
