package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Compact read-only lane segment projected from a single interaction step.
 */
public record ExecutionOverlayLaneSegment(
    String segmentId,
    String stepKey,
    Component label,
    Component description,
    @Nullable ExecutionOverlayRecipePreview recipePreview,
    ExecutionFlowStatus status,
    boolean current
) {

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
