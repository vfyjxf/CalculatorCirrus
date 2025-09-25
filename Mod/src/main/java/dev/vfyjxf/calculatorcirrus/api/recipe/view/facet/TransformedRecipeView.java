package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.OutputContent;
import org.eclipse.collections.api.list.ImmutableList;

public record TransformedRecipeView(
    RecipeEntry<?> recipe,
    ImmutableList<InputContent> inputs,
    ImmutableList<OutputContent> outputs
) {}
