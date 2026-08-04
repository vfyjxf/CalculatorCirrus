package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;

/**
 * Generic runtime boundary for opening and executing machine interactions.
 */
public interface InteractionApi {

    InteractionProbeResult probe(RecipeEntry<?> recipe);

    InteractionGuideResult guide(RecipeEntry<?> recipe);

    InteractionPerformResult perform(InteractionContext context);

    /**
     * Reports how many recipe executions this interaction can safely perform in one translated batch.
     *
     * <p>The default implementation imposes no additional limit beyond the requested amount.
     */
    default int maxExecuteBatchSize(InteractionContext context) {
        return Math.max(1, context.times());
    }

    default ExecutionPlanningSnapshot planningSnapshot() {
        return ExecutionPlanningSnapshot.empty();
    }
}
