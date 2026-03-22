package dev.vfyjxf.cirrus.ui.calc.target;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.ui.calc.database.DatabaseDefinition;

import java.util.List;

public record RecipeCandidate(
    String systemId,
    String recipeId,
    String displayName,
    DatabaseDefinition database,
    List<DatabaseDefinition> sources,
    RecipeEntry<?> recipeEntry,
    RecipeGraph.Node node,
    List<String> outputNames,
    List<String> inputNames
) {
}
