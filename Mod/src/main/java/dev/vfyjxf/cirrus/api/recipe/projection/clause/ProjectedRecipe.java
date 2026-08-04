package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import org.eclipse.collections.api.list.ImmutableList;

public record ProjectedRecipe(
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
