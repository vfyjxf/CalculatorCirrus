package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotHost;
import dev.vfyjxf.cirrus.ui.workbench.state.HistoryEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Full mapping from the Co-Pilot dock islands to the active planning controller; drawer and canvas focus requests land in pending slots.
 */
public final class ShellCoPilotHost implements CoPilotHost {

    private final WorkbenchShellContext context;
    private final BooleanSupplier reducedMotion;

    private final List<String> focusRequests = new ArrayList<>();
    private @Nullable ResourceKey pendingAlternatives;
    private @Nullable HumanFeedback lastRefined;

    public ShellCoPilotHost(WorkbenchShellContext context) {
        this(context, () -> false);
    }

    public ShellCoPilotHost(WorkbenchShellContext context, @Nullable BooleanSupplier reducedMotion) {
        this.context = Objects.requireNonNull(context, "context");
        this.reducedMotion = reducedMotion == null ? () -> false : reducedMotion;
    }

    @Override
    public WorkbenchUiSnapshot snapshot() {
        return context.activeSnapshot();
    }

    @Override
    public CirrusPalette palette() {
        return context.palette();
    }

    @Override
    public void refine(HumanFeedback feedback, @Nullable HistoryEntry entry) {
        Objects.requireNonNull(feedback, "feedback");
        lastRefined = feedback;
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.refine(feedback, entry);
        }
    }

    @Override
    public void jumpToHistory(int index) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.jumpToHistoryIndex(index);
        }
    }

    @Override
    public void relaxConflictRules(List<String> ruleIds) {
        if (ruleIds == null) {
            return;
        }
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.relaxConflictRules(ruleIds);
        }
    }

    @Override
    public void promoteSessionRulesToWorld(@Nullable String singleRuleId) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.promoteSessionRulesToWorld(singleRuleId);
        }
    }

    @Override
    public void selectPlan(@Nullable String planId) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.selectPlan(planId);
        }
    }

    @Override
    public void setComparePlan(@Nullable String planId) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setComparePlan(planId);
        }
    }

    @Override
    public void setSelectedSteps(Set<String> stepIds) {
        if (stepIds == null) {
            return;
        }
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setSelectedSteps(stepIds);
        }
    }

    @Override
    public void focusStepInDag(String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return;
        }
        focusRequests.add(stepId);
        context.toast(CirrusLangs.Workbench.Action.locateDag);
    }

    @Override
    public void openAlternativesFor(ResourceKey resource) {
        if (resource == null) {
            return;
        }
        pendingAlternatives = resource;
    }

    @Override
    public void toast(LangEntry message) {
        context.toast(Objects.requireNonNull(message, "message"));
    }

    @Override
    public boolean isReducedMotion() {
        return reducedMotion.getAsBoolean();
    }

    /* region slots awaiting WP-UI-7B wiring */

    public List<String> focusRequests() {
        return List.copyOf(focusRequests);
    }

    public void clearFocusRequests() {
        focusRequests.clear();
    }

    public @Nullable ResourceKey pendingAlternatives() {
        return pendingAlternatives;
    }

    public @Nullable ResourceKey consumePendingAlternatives() {
        ResourceKey consumed = pendingAlternatives;
        pendingAlternatives = null;
        return consumed;
    }

    public @Nullable HumanFeedback lastRefined() {
        return lastRefined;
    }

    /* endregion */
}
