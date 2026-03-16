package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;

/**
 * Generic runtime boundary for opening and executing machine interactions.
 */
public interface InteractionApi {

    InteractionProbeResult probe(RecipeEntry<?> recipe);

    InteractionGuideResult guide(RecipeEntry<?> recipe);

    InteractionPerformResult perform(InteractionContext context);
}
