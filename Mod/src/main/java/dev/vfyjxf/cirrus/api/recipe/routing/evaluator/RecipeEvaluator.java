package dev.vfyjxf.cirrus.api.recipe.routing.evaluator;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.RoutingContext;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public non-sealed interface RecipeEvaluator extends RoutingEvaluator {

    Namespace basic = Namespace.of("cirrus", "evaluator/recipe/basic");

    double getCost(RecipeGraph.Node recipe, int times, RoutingContext context);


}
