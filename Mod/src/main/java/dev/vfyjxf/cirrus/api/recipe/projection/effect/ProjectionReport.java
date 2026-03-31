package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardFinding;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectionReport(
    RecipeProjection projection,
    ImmutableList<ProjectionEffect> effects,
    boolean runnable,
    ImmutableList<ResolveGuardFinding> guardFindings
) {
    public ProjectionReport {
        Checks.checkNotNull(projection, "projection");
        Checks.checkNotNull(effects, "effects");
        Checks.checkNotNull(guardFindings, "guardFindings");
    }

    public ProjectionReport(
        RecipeProjection projection,
        ImmutableList<ProjectionEffect> effects
    ) {
        this(projection, effects, true, Lists.immutable.empty());
    }

    public ProjectedRecipe projectedRecipe() {
        ImmutableList<InputContent> inputs = projection.inputs()
            .collect(content -> content.content());
        ImmutableList<OutputContent> outputs = projection.outputs()
            .collect(content -> content.content());
        return new ProjectedRecipe(projection.recipe(), inputs, outputs);
    }

    public ImmutableList<ProjectionPluginReport> pluginReports(Iterable<ProjectionPlugin> plugins) {
        Checks.checkNotNull(plugins, "plugins");
        LinkedHashMap<ProjectionPlugin, PluginAccumulator> grouped = new LinkedHashMap<>();
        for (ProjectionPlugin plugin : plugins) {
            grouped.put(plugin, new PluginAccumulator(plugin));
        }
        for (ProjectionEffect effect : effects) {
            ProjectionPlugin plugin = effect.source().plugin();
            PluginAccumulator accumulator = grouped.computeIfAbsent(plugin, PluginAccumulator::new);
            accumulator.add(effect);
        }
        return grouped.values().stream()
            .map(PluginAccumulator::report)
            .collect(org.eclipse.collections.impl.collector.Collectors2.toImmutableList());
    }

    public ProjectionPluginReport pluginReport(ProjectionPlugin plugin) {
        Checks.checkNotNull(plugin, "plugin");
        PluginAccumulator accumulator = new PluginAccumulator(plugin);
        for (ProjectionEffect effect : effects) {
            if (effect.source().plugin().id().equals(plugin.id())) {
                accumulator.add(effect);
            }
        }
        return accumulator.report();
    }

    public Map<ProjectionPlugin, ProjectionPluginReport> pluginReportMap(Iterable<ProjectionPlugin> plugins) {
        LinkedHashMap<ProjectionPlugin, ProjectionPluginReport> out = new LinkedHashMap<>();
        for (ProjectionPluginReport report : pluginReports(plugins)) {
            out.put(report.plugin(), report);
        }
        return Map.copyOf(out);
    }

    private static final class PluginAccumulator {
        private final ProjectionPlugin plugin;
        private final org.eclipse.collections.api.list.MutableList<ProjectionEffect> effects = Lists.mutable.empty();
        private int inputChanges;
        private int outputChanges;

        private PluginAccumulator(ProjectionPlugin plugin) {
            this.plugin = plugin;
        }

        private void add(ProjectionEffect effect) {
            effects.add(effect);
            if (effect instanceof InputEffect) {
                inputChanges++;
            } else if (effect instanceof OutputEffect) {
                outputChanges++;
            }
        }

        private ProjectionPluginReport report() {
            return new ProjectionPluginReport(plugin, effects.toImmutable(), inputChanges, outputChanges);
        }
    }
}
