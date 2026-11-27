package dev.vfyjxf.cirrus.api.recipe.execution.view;

import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.jspecify.annotations.Nullable;

/**
 * UI-facing execution tree with statuses derived from current runtime step state.
 */
public record ExecutionFlowViewModel(ExecutionFlowNode root) {

    //region projection

    public static ExecutionFlowViewModel derive(ExecutionFlowNode root, RuntimeState runtimeState) {
        return new ExecutionFlowViewModel(project(root, runtimeState));
    }

    private static ExecutionFlowNode project(ExecutionFlowNode node, RuntimeState runtimeState) {
        var projectedChildren = node.children().collect(child -> project(child, runtimeState));
        var status = deriveStatus(node, projectedChildren, runtimeState);
        return new ExecutionFlowNode(
            node.nodeId(),
            node.label(),
            status,
            node.guidance(),
            node.steps(),
            projectedChildren
        );
    }

    private static ExecutionFlowStatus deriveStatus(
        ExecutionFlowNode node,
        ImmutableList<ExecutionFlowNode> projectedChildren,
        RuntimeState runtimeState
    ) {
        if (containsFailedStep(node, projectedChildren, runtimeState)) {
            return ExecutionFlowStatus.failed;
        }
        if (containsPausedStep(node, projectedChildren, runtimeState)) {
            return ExecutionFlowStatus.paused;
        }
        if (containsRunningStep(node, projectedChildren, runtimeState)) {
            return ExecutionFlowStatus.running;
        }
        if (isCompleted(node, projectedChildren, runtimeState)) {
            return ExecutionFlowStatus.completed;
        }
        return ExecutionFlowStatus.pending;
    }

    private static boolean containsFailedStep(
        ExecutionFlowNode node,
        ImmutableList<ExecutionFlowNode> projectedChildren,
        RuntimeState runtimeState
    ) {
        return runtimeState.failedStep() != null
                && (node.steps().contains(runtimeState.failedStep())
                        || projectedChildren.anySatisfy(child -> child.status() == ExecutionFlowStatus.failed));
    }

    private static boolean containsPausedStep(
        ExecutionFlowNode node,
        ImmutableList<ExecutionFlowNode> projectedChildren,
        RuntimeState runtimeState
    ) {
        return node.steps().anySatisfy(runtimeState.pausedSteps()::contains)
                || projectedChildren.anySatisfy(child -> child.status() == ExecutionFlowStatus.paused);
    }

    private static boolean containsRunningStep(
        ExecutionFlowNode node,
        ImmutableList<ExecutionFlowNode> projectedChildren,
        RuntimeState runtimeState
    ) {
        return runtimeState.runningStep() != null
                && (node.steps().contains(runtimeState.runningStep())
                        || projectedChildren.anySatisfy(child -> child.status() == ExecutionFlowStatus.running));
    }

    private static boolean isCompleted(
        ExecutionFlowNode node,
        ImmutableList<ExecutionFlowNode> projectedChildren,
        RuntimeState runtimeState
    ) {
        var ownStepsCompleted = node.steps().allSatisfy(runtimeState.completedSteps()::contains);
        var childNodesCompleted = projectedChildren
                .allSatisfy(child -> child.status() == ExecutionFlowStatus.completed);
        return ownStepsCompleted && childNodesCompleted && (node.steps().notEmpty() || projectedChildren.notEmpty());
    }

    //endregion

    //region runtime state

    public record RuntimeState(
        ImmutableSet<InteractionStep> completedSteps,
        ImmutableSet<InteractionStep> pausedSteps,
        @Nullable InteractionStep runningStep,
        @Nullable InteractionStep failedStep
    ) {

        public static RuntimeState empty() {
            return new RuntimeState(Sets.immutable.empty(), Sets.immutable.empty(), null, null);
        }
    }

    //endregion
}
