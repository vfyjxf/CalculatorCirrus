package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer.DecrementOps;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer.IncrementOps;

public interface ViewTransformer<F extends RecipeFacet> {

    boolean selectView(RecipeContext context, RecipeView view);

    void transformView(RecipeContext context, RecipeView view, F facet, IncrementOps increment, DecrementOps decrement);

}


