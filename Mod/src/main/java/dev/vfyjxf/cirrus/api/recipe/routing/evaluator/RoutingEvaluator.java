package dev.vfyjxf.cirrus.api.recipe.routing.evaluator;

public sealed interface RoutingEvaluator permits ContentEvaluator, RecipeEvaluator {
    double impossible = Double.NEGATIVE_INFINITY;
    double noContribution = 0;
}
