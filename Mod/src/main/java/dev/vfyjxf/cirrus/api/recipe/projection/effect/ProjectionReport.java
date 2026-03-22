package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolveGuardFinding;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

public record ProjectionReport(
    ProjectedRecipe recipe,
    ImmutableList<ProjectionEffect> effects,
    boolean runnable,
    ImmutableList<ResolveGuardFinding> guardFindings
) {
    public ProjectionReport {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(effects, "effects");
        Checks.checkNotNull(guardFindings, "guardFindings");
    }

    public ProjectionReport(
        ProjectedRecipe recipe,
        ImmutableList<ProjectionEffect> effects
    ) {
        this(recipe, effects, true, Lists.immutable.empty());
    }
}
