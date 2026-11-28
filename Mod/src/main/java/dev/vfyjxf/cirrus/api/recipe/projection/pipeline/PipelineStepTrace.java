package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Trace record for a single {@link ProjectionEffect} step evaluated in a {@link ProjectionPipeline}.
 */
public record PipelineStepTrace(
    Namespace effectId,
    ProjectionPlugin plugin,
    ProjectionStage stage,
    int priority,
    boolean applied,
    boolean blocked,
    ImmutableList<ProjectionGuard.Finding> guardFindings,
    ImmutableList<ProjectionChange> changes
) {
    public PipelineStepTrace {
        Checks.checkNotNull(effectId, "effectId");
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(stage, "stage");
        Checks.checkNotNull(guardFindings, "guardFindings");
        Checks.checkNotNull(changes, "changes");
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
