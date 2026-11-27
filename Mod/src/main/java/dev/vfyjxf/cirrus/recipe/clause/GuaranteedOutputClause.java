package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class GuaranteedOutputClause implements RecipeClause {

    public static final Namespace id = CirrusNamespaces.ofMod("output/guaranteed");
    public static final ClauseType type = new ClauseType(
        id,
        ProjectionPlugin.builtin,
        (recipe, content) -> !content.constantly()
    );
    public static final GuaranteedOutputClause instance = new GuaranteedOutputClause();
    public static final ProjectionTransformer<GuaranteedOutputClause> projectionTransformer = ProjectionTransformer.of(
        ProjectionPlugin.builtin,
        (context, projection) -> projection.outputs().anySatisfy(content -> !content.constantly()),
        (context, projection, clause, increment, decrement) -> {
            for (var output : projection.outputs()) {
                if (!output.constantly()) {
                    decrement.rejectOutput(output.content());
                }
            }
        }
    );

    private GuaranteedOutputClause() {}

    @Override
    public Namespace id() {
        return id;
    }

    @Override
    public ProjectionPlugin plugin() {
        return ProjectionPlugin.builtin;
    }
}
