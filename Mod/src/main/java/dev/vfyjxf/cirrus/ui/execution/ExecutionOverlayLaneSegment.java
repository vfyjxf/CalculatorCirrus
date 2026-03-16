package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;

/**
 * Compact read-only lane segment projected from a single interaction step.
 */
public record ExecutionOverlayLaneSegment(
    String segmentId,
    Component label,
    ExecutionFlowStatus status,
    boolean current
) {

    public ExecutionOverlayLaneSegment {
        Checks.checkNotNull(segmentId, "segmentId");
        Checks.checkNotNull(label, "label");
        Checks.checkNotNull(status, "status");
    }

    //region derived state

    public String statusText() {
        return switch (status) {
            case ExecutionFlowStatus.Pending pending -> "pending";
            case ExecutionFlowStatus.Running running -> "running";
            case ExecutionFlowStatus.Paused paused -> "paused";
            case ExecutionFlowStatus.Completed completed -> "completed";
            case ExecutionFlowStatus.Failed failed -> "failed";
        };
    }

    //endregion
}
