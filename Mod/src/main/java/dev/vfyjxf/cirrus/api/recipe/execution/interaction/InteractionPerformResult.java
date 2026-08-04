package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;

/**
 * Result of attempting to perform a recipe interaction automatically.
 */
public sealed interface InteractionPerformResult permits
    InteractionPerformResult.Performed,
    InteractionPerformResult.ReTranslationRequired,
    InteractionPerformResult.ManualRequired,
    InteractionPerformResult.Failed {

    //region variants

    record Performed() implements InteractionPerformResult {}

    record ReTranslationRequired(ReTranslationHint hint) implements InteractionPerformResult {}

    record ManualRequired(ExecutionGuidance guidance) implements InteractionPerformResult {}

    record Failed(ExecutionGuidance guidance) implements InteractionPerformResult {}

    //endregion
}
