package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

public record ProjectionPluginReport(
    ProjectionPlugin plugin,
    ImmutableList<ProjectionEffect> effects,
    int inputChangeCount,
    int outputChangeCount
) {
    public ProjectionPluginReport {
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(effects, "effects");
        Checks.checkArgument(inputChangeCount >= 0, "inputChangeCount must be non-negative");
        Checks.checkArgument(outputChangeCount >= 0, "outputChangeCount must be non-negative");
    }

    public static ProjectionPluginReport empty(ProjectionPlugin plugin) {
        return new ProjectionPluginReport(plugin, Lists.immutable.empty(), 0, 0);
    }

    public int effectCount() {
        return effects.size();
    }

    public boolean hasEffects() {
        return !effects.isEmpty();
    }

    public boolean changesInputs() {
        return inputChangeCount > 0;
    }

    public boolean changesOutputs() {
        return outputChangeCount > 0;
    }
}
