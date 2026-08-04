package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cloudlib.api.util.Namespace;

public record ExecutionInteraction(
    Namespace interactionId,
    InteractionApi interactionApi
) {

    public ExecutionPlanningSnapshot planningSnapshot() {
        return interactionApi.planningSnapshot();
    }
}
