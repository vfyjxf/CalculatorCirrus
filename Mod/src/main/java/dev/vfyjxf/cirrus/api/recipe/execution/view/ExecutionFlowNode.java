package dev.vfyjxf.cirrus.api.recipe.execution.view;

import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable tree node used to visualize translated execution progress.
 */
public record ExecutionFlowNode(
    String nodeId,
    Component label,
    ExecutionFlowStatus status,
    @Nullable ExecutionGuidance guidance,
    ImmutableList<InteractionStep> steps,
    ImmutableList<ExecutionFlowNode> children
) {

    public ExecutionFlowNode {
        Checks.checkNotNull(nodeId, "nodeId");
        Checks.checkNotNull(label, "label");
        Checks.checkNotNull(status, "status");
        Checks.checkNotNull(steps, "steps");
        Checks.checkNotNull(children, "children");
    }

    //region factories

    public static ExecutionFlowNode pending(
        String nodeId,
        Component label,
        ImmutableList<InteractionStep> steps,
        @Nullable ExecutionGuidance guidance,
        ImmutableList<ExecutionFlowNode> children
    ) {
        return new ExecutionFlowNode(nodeId, label, ExecutionFlowStatus.pending, guidance, steps, children);
    }

    //endregion

    //region copy

    public ExecutionFlowNode withStatus(ExecutionFlowStatus status) {
        return new ExecutionFlowNode(nodeId, label, status, guidance, steps, children);
    }

    public ExecutionFlowNode withChildren(ImmutableList<ExecutionFlowNode> children) {
        return new ExecutionFlowNode(nodeId, label, status, guidance, steps, children);
    }

    //endregion
}
