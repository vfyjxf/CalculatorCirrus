package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory runtime session tracking current execution progress, pauses, and retranslation requests.
 */
public record ExecutionSession(
    InteractionExecutionPlan plan,
    ExecutionFlowNode flowRoot,
    int nextStepIndex,
    ImmutableSet<InteractionStep> completedSteps,
    ImmutableSet<InteractionStep> pausedSteps,
    @Nullable InteractionStep runningStep,
    @Nullable PauseReason pauseReason,
    @Nullable ReTranslationHint reTranslationHint,
    ExecutionFlowViewModel flowViewModel
) {

    public ExecutionSession {
        Checks.checkNotNull(plan, "plan");
        Checks.checkNotNull(flowRoot, "flowRoot");
        Checks.checkArgument(nextStepIndex >= 0, "nextStepIndex must be non-negative");
        Checks.checkNotNull(completedSteps, "completedSteps");
        Checks.checkNotNull(pausedSteps, "pausedSteps");
        Checks.checkNotNull(flowViewModel, "flowViewModel");
    }

    //region factories

    public static ExecutionSession start(InteractionExecutionPlan plan, ExecutionFlowNode flowRoot) {
        Checks.checkNotNull(plan, "plan");
        Checks.checkNotNull(flowRoot, "flowRoot");
        var runtimeState = ExecutionFlowViewModel.RuntimeState.empty();
        return new ExecutionSession(
            plan,
            flowRoot,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(flowRoot, runtimeState)
        );
    }

    //endregion

    //region derived state

    public @Nullable InteractionStep currentStep() {
        if (nextStepIndex >= plan.steps().size()) {
            return null;
        }
        return plan.steps().get(nextStepIndex);
    }

    public boolean paused() {
        return pauseReason != null;
    }

    public boolean completed() {
        return nextStepIndex >= plan.steps().size() && !paused();
    }

    //endregion
}
