package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer.DecrementOps;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer.IncrementOps;

public interface ProjectionTransformer<F extends RecipeClause> {

    static <F extends RecipeClause> ProjectionTransformer<F> of(
        ProjectionPlugin plugin,
        SelectionFunction selector,
        TransformFunction<F> transformation
    ) {
        return new ProjectionTransformer<>() {
            @Override
            public ProjectionPlugin plugin() {
                return plugin;
            }

            @Override
            public boolean selectProjection(ExecutionContext context, RecipeProjection projection) {
                return selector.select(context, projection);
            }

            @Override
            public void project(
                ExecutionContext context,
                RecipeProjection projection,
                F clause,
                IncrementOps increment,
                DecrementOps decrement
            ) {
                transformation.transform(context, projection, clause, increment, decrement);
            }
        };
    }

    ProjectionPlugin plugin();

    boolean selectProjection(ExecutionContext context, RecipeProjection projection);

    void project(
        ExecutionContext context,
        RecipeProjection projection,
        F clause,
        IncrementOps increment,
        DecrementOps decrement
    );

    interface SelectionFunction {
        boolean select(ExecutionContext context, RecipeProjection projection);
    }

    interface TransformFunction<F extends RecipeClause> {
        void transform(
            ExecutionContext context,
            RecipeProjection projection,
            F clause,
            IncrementOps increment,
            DecrementOps decrement
        );
    }

}
