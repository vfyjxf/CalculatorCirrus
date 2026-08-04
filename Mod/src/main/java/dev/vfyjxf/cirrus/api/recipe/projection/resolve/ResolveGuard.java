package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Guard used by {@link ResolvePolicy} to decide whether resolve effects are runnable.
 */
public interface ResolveGuard {

    //region factory

    static ResolveGuard of(Namespace id, EvaluateFunction evaluator) {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(evaluator, "evaluator");
        return new ResolveGuard() {
            @Override
            public Namespace id() {
                return id;
            }

            @Override
            public ResolveGuardResult evaluate(ExecutionContext context, ProjectedRecipe recipe) {
                return evaluator.evaluate(context, recipe);
            }
        };
    }

    //endregion

    Namespace id();

    default ResolveScope scope() {
        return ResolveScope.both;
    }

    ResolveGuardResult evaluate(ExecutionContext context, ProjectedRecipe recipe);

    interface EvaluateFunction {
        ResolveGuardResult evaluate(ExecutionContext context, ProjectedRecipe recipe);
    }
}
