package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cloudlib.api.util.Namespace;

/**
 * @deprecated Renamed to {@link GuaranteedOutputClause}.
 */
@Deprecated(forRemoval = false)
public final class SkipChancedOutputClause implements RecipeClause {

    public static final Namespace id = GuaranteedOutputClause.id;
    public static final ClauseType type = GuaranteedOutputClause.type;
    public static final SkipChancedOutputClause instance = new SkipChancedOutputClause();
    @SuppressWarnings("unchecked")
    public static final ProjectionTransformer<SkipChancedOutputClause> projectionTransformer =
        (ProjectionTransformer<SkipChancedOutputClause>) (ProjectionTransformer<?>) GuaranteedOutputClause.projectionTransformer;

    private SkipChancedOutputClause() {}

    @Override
    public Namespace id() {
        return id;
    }
}
