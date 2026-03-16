package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Result of attempting to perform a recipe interaction automatically.
 */
public sealed interface InteractionPerformResult permits
    InteractionPerformResult.Performed,
    InteractionPerformResult.ManualRequired,
    InteractionPerformResult.Failed {

    //region variants

    record Performed() implements InteractionPerformResult {}

    record ManualRequired(ExecutionGuidance guidance) implements InteractionPerformResult {
        public ManualRequired {
            Checks.checkNotNull(guidance, "guidance");
        }
    }

    record Failed(ExecutionGuidance guidance) implements InteractionPerformResult {
        public Failed {
            Checks.checkNotNull(guidance, "guidance");
        }
    }

    //endregion
}
