package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * User-facing guidance for reaching an interaction context.
 */
public record InteractionGuideResult(ExecutionGuidance guidance) {

    public InteractionGuideResult {
        Checks.checkNotNull(guidance, "guidance");
    }
}
