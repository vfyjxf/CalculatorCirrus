package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.display.ProjectionDisplayModel;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of running a {@link ProjectionPipeline} in any {@link TraceMode}.
 *
 * <p>On the fast path ({@link TraceMode#none}), {@link #steps()} is empty and {@link #diff()}
 * computes a structural {@link ProjectionDiff} lazily on demand. On {@link TraceMode#full},
 * both per-step provenance and the collapsed net diff are immediately available.
 */
public final class ProjectionResult {

    private final RecipeProjection original;
    private final RecipeProjection projected;
    private final boolean runnable;
    private final ImmutableList<ProjectionGuard.Finding> guardFindings;
    private final ImmutableList<PipelineStepTrace> steps;
    private volatile @Nullable ProjectionDiff cachedDiff;

    public ProjectionResult(
        RecipeProjection original,
        RecipeProjection projected,
        boolean runnable,
        ImmutableList<ProjectionGuard.Finding> guardFindings,
        ImmutableList<PipelineStepTrace> steps,
        @Nullable ProjectionDiff diff
    ) {
        this.original = Checks.checkNotNull(original, "original");
        this.projected = Checks.checkNotNull(projected, "projected");
        this.runnable = runnable;
        this.guardFindings = Checks.checkNotNull(guardFindings, "guardFindings");
        this.steps = Checks.checkNotNull(steps, "steps");
        this.cachedDiff = diff;
    }

    public RecipeProjection original() {
        return original;
    }

    public RecipeProjection projected() {
        return projected;
    }

    public ProjectedRecipe projectedRecipe() {
        return projected.toProjectedRecipe();
    }

    public boolean runnable() {
        return runnable;
    }

    public ImmutableList<ProjectionGuard.Finding> guardFindings() {
        return guardFindings;
    }

    public ImmutableList<PipelineStepTrace> steps() {
        return steps;
    }

    /**
     * Returns the net {@link ProjectionDiff}, computing it lazily on demand if the pipeline ran
     * with {@link TraceMode#none}.
     */
    public ProjectionDiff diff() {
        ProjectionDiff current = cachedDiff;
        if (current != null) {
            return current;
        }
        ProjectionDiff computed = steps.isEmpty()
                ? ProjectionDiff.between(original, projected)
                : ProjectionDiff.fromTrace(original, projected, steps);
        cachedDiff = computed;
        return computed;
    }

    public List<ProjectionChange> allChanges() {
        List<ProjectionChange> out = new ArrayList<>();
        for (PipelineStepTrace step : steps) {
            for (ProjectionChange change : step.changes()) {
                out.add(change);
            }
        }
        return List.copyOf(out);
    }

    public List<PipelineStepTrace> stepsInStage(ProjectionStage stage) {
        List<PipelineStepTrace> out = new ArrayList<>();
        for (PipelineStepTrace step : steps) {
            if (step.stage() == stage) {
                out.add(step);
            }
        }
        return List.copyOf(out);
    }

    public PluginSummary pluginSummary(ProjectionPlugin plugin) {
        Checks.checkNotNull(plugin, "plugin");
        List<PipelineStepTrace> matchedSteps = new ArrayList<>();
        List<ProjectionChange> changes = new ArrayList<>();
        int inputChanges = 0;
        int outputChanges = 0;
        int metaChanges = 0;
        for (PipelineStepTrace step : steps) {
            if (!step.plugin().id().equals(plugin.id())) {
                continue;
            }
            matchedSteps.add(step);
            for (ProjectionChange change : step.changes()) {
                changes.add(change);
                if (change instanceof ProjectionChange.SlotChange sc) {
                    if (sc.channel() == SlotChannel.input) {
                        inputChanges++;
                    } else {
                        outputChanges++;
                    }
                } else {
                    metaChanges++;
                }
            }
        }
        return new PluginSummary(
            plugin,
            Lists.immutable.withAll(matchedSteps),
            Lists.immutable.withAll(changes),
            inputChanges,
            outputChanges,
            metaChanges
        );
    }

    public Map<ProjectionPlugin, PluginSummary> pluginSummaries(Iterable<ProjectionPlugin> plugins) {
        LinkedHashMap<ProjectionPlugin, PluginSummary> out = new LinkedHashMap<>();
        for (ProjectionPlugin plugin : plugins) {
            out.put(plugin, pluginSummary(plugin));
        }
        return Map.copyOf(out);
    }

    public ProjectionDisplayModel toDisplayModel() {
        return ProjectionDisplayModel.fromResult(this);
    }

    public ProjectionDisplayModel toDisplayModel(ProjectionDisplayModel.Options options) {
        return ProjectionDisplayModel.fromResult(this, options);
    }

    public record PluginSummary(
        ProjectionPlugin plugin,
        ImmutableList<PipelineStepTrace> steps,
        ImmutableList<ProjectionChange> changes,
        int inputChangeCount,
        int outputChangeCount,
        int metaChangeCount
    ) {
        public static PluginSummary empty(ProjectionPlugin plugin) {
            return new PluginSummary(
                plugin,
                Lists.immutable.empty(),
                Lists.immutable.empty(),
                0,
                0,
                0
            );
        }

        public int totalChangeCount() {
            return changes.size();
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }

        public boolean changesInputs() {
            return inputChangeCount > 0;
        }

        public boolean changesOutputs() {
            return outputChangeCount > 0;
        }
    }
}
