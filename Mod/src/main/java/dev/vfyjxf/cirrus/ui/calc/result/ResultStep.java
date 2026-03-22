package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import org.eclipse.collections.api.list.ImmutableList;

public record ResultStep(
    int actionId,
    int actionIndex,
    RecipeEntry<?> recipe,
    RecipeGraph.Node node,
    int times,
    ImmutableList<PlanAction.InputBinding> inputs,
    ImmutableList<PlanAction.OutputBinding> outputs
) {
}
