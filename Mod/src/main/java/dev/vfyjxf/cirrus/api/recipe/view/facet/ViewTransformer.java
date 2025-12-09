package dev.vfyjxf.cirrus.api.recipe.view.facet;

import dev.vfyjxf.cirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer.DecrementOps;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer.IncrementOps;

public interface ViewTransformer<F extends RecipeFacet> {

    static <F extends RecipeFacet> ViewTransformer<F> of(SelectionFunction selector, TransformFunction<F> transformation) {
        return new ViewTransformer<>() {
            @Override
            public boolean selectView(RecipeContext context, RecipeView view) {
                return selector.select(context, view);
            }

            @Override
            public void transformView(RecipeContext context, RecipeView view, F facet, IncrementOps increment, DecrementOps decrement) {
                transformation.transform(context, view, facet, increment, decrement);
            }
        };
    }

    boolean selectView(RecipeContext context, RecipeView view);

    void transformView(RecipeContext context, RecipeView view, F facet, IncrementOps increment, DecrementOps decrement);

    interface SelectionFunction {
        boolean select(RecipeContext context, RecipeView view);
    }

    interface TransformFunction<F extends RecipeFacet> {
        void transform(RecipeContext context, RecipeView view, F facet, IncrementOps increment, DecrementOps decrement);
    }

}


