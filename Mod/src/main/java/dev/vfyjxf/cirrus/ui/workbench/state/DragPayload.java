package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

public sealed interface DragPayload
        permits DragPayload.ResourceDrag, DragPayload.MachineDrag, DragPayload.RecipeDrag, DragPayload.PlanCompareDrag {

    record ResourceDrag(ComponentStackKey resource, long suggestedAmount, String sourceTag) implements DragPayload {}

    record MachineDrag(MachineEntry machine, int suggestedCount) implements DragPayload {}

    record RecipeDrag(ComponentStackKey outputResource, CompiledRecipeKey recipeKey) implements DragPayload {}

    record PlanCompareDrag(String planId, String strategyLabelKey) implements DragPayload {}
}
