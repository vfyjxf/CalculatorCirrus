package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

public record ProjectionEffectSource(
    ProjectionPlugin plugin,
    Namespace clauseId,
    ProjectionEffectPhase phase,
    @Nullable Namespace resolveEffectId,
    int resolveEffectOrder,
    int resolveEffectDeclaredIndex
) {
    public ProjectionEffectSource {
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(clauseId, "clauseId");
        Checks.checkNotNull(phase, "phase");
    }

    public static ProjectionEffectSource clause(ProjectionPlugin plugin, Namespace clauseId, ProjectionEffectPhase phase) {
        if (phase == ProjectionEffectPhase.resolveEffect) {
            throw new IllegalArgumentException("Use resolveEffect(...) for resolve effects");
        }
        return new ProjectionEffectSource(plugin, clauseId, phase, null, -1, -1);
    }

    public static ProjectionEffectSource resolveEffect(
        ProjectionPlugin plugin,
        Namespace policyClauseId,
        Namespace effectId,
        int effectOrder,
        int declaredIndex
    ) {
        Checks.checkNotNull(effectId, "effectId");
        return new ProjectionEffectSource(
            plugin,
            policyClauseId,
            ProjectionEffectPhase.resolveEffect,
            effectId,
            effectOrder,
            declaredIndex
        );
    }
}
