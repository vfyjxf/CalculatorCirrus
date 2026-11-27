package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

public sealed interface DragPayload
        permits DragPayload.ResourceDrag, DragPayload.MachineDrag, DragPayload.RecipeDrag, DragPayload.PlanCompareDrag {

    record ResourceDrag(ResourceKey resource, long suggestedAmount, String sourceTag) implements DragPayload {}

    record MachineDrag(String machineId, int suggestedCount) implements DragPayload {}

    record RecipeDrag(ResourceKey outputResource, CompiledRecipeKey recipeKey) implements DragPayload {}

    record PlanCompareDrag(String planId, String strategyLabelKey) implements DragPayload {}
}
