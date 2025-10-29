package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.OutputContent;
import org.eclipse.collections.api.list.ImmutableList;

public record TransformedRecipeView(
    RecipeEntry<?> recipe,
    ImmutableList<InputContent> inputs,
    ImmutableList<OutputContent> outputs
) {

    public boolean isConstantInputs() {
        return inputs.allSatisfy(InputContent::constantly);
    }

    public boolean isConstantOutputs() {
        return outputs.allSatisfy(OutputContent::constantly);
    }

}
