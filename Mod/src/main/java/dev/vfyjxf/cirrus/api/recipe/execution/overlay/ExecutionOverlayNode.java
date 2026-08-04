package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Flattened tree row projected for the execution overlay body.
 */
public record ExecutionOverlayNode(
    String nodeId,
    Component label,
    @Nullable Component subtitle,
    @Nullable RecipeContent content,
    @Nullable ExecutionOverlayRecipePreview recipePreview,
    ExecutionFlowStatus status,
    ExecutionOverlayNodeKind kind,
    int depth,
    boolean activeBranch,
    boolean expanded,
    ImmutableList<ExecutionOverlayLaneSegment> laneSegments,
    ImmutableList<ExecutionOverlayNode> children
) {

    public ExecutionOverlayNode {
        Checks.checkArgument(depth >= 0, "depth must be non-negative");
        if (content != null) {
            content = content.copy();
        }
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
