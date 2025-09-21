package dev.vfyjxf.calculatorcirrus.api.recipe.view;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.OutputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetContainer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetedContent;
import org.eclipse.collections.api.list.ImmutableList;

public class RecipeView {

    //region factory


    //endregion

    //region internal

    private final RecipeEntry<?> recipe;
    private final ImmutableList<FacetedContent<InputContent>> inputs;
    private final ImmutableList<FacetedContent<OutputContent>> outputs;
    private final FacetContainer facets = new FacetContainer();

    public RecipeView(
        RecipeEntry<?> recipe,
        ImmutableList<FacetedContent<InputContent>> inputs,
        ImmutableList<FacetedContent<OutputContent>> outputs
    ) {
        this.recipe = recipe;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    //endregion

    //region recipe and ingredient

    public RecipeEntry<?> recipe() {
        return recipe;
    }

    public ImmutableList<FacetedContent<InputContent>> inputs() {
        return inputs;
    }

    public ImmutableList<FacetedContent<OutputContent>> outputs() {
        return outputs;
    }

    //endregion

    //region facet

    public FacetContainer facets() {
        return facets;
    }


    //endregion
}
