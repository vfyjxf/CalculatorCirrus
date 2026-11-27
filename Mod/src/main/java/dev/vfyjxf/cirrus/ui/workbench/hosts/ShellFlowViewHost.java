package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowViewHost;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Adapter from the ledger and checklist views to the workbench shell.
 */
public final class ShellFlowViewHost implements FlowViewHost {

    private final WorkbenchShellContext context;
    private final BooleanSupplier reducedMotion;

    private final List<String> focusRequests = new ArrayList<>();
    private @Nullable Consumer<String> focusHandler;
    private @Nullable HumanFeedback lastDispatched;

    public ShellFlowViewHost(WorkbenchShellContext context) {
        this(context, () -> false);
    }

    public ShellFlowViewHost(WorkbenchShellContext context, @Nullable BooleanSupplier reducedMotion) {
        this.context = Objects.requireNonNull(context, "context");
        this.reducedMotion = reducedMotion == null ? () -> false : reducedMotion;
    }

    public ShellFlowViewHost focusHandler(@Nullable Consumer<String> focusHandler) {
        this.focusHandler = focusHandler;
        return this;
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
    public void dispatch(HumanFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        lastDispatched = feedback;
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.refine(feedback, null);
        }
    }

    @Override
    public void onFocusStep(String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return;
        }
        focusRequests.add(stepId);
        Consumer<String> handler = focusHandler;
        if (handler != null) {
            handler.accept(stepId);
        } else {
            context.toast(CirrusLangs.Workbench.Action.locateDag);
        }
    }

    @Override
    public void toast(LangEntry entry) {
        context.toast(Objects.requireNonNull(entry, "entry"));
    }

    @Override
    public boolean isReducedMotion() {
        return reducedMotion.getAsBoolean();
    }

    /* region slots awaiting WP-UI-7B wiring */

    public List<String> focusRequests() {
        return List.copyOf(focusRequests);
    }

    public @Nullable String lastFocusedStepId() {
        return focusRequests.isEmpty() ? null : focusRequests.get(focusRequests.size() - 1);
    }

    public void clearFocusRequests() {
        focusRequests.clear();
    }

    public @Nullable HumanFeedback lastDispatched() {
        return lastDispatched;
    }

    /* endregion */
}
