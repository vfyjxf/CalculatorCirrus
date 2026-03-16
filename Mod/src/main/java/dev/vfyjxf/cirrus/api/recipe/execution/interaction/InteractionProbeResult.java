package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * Describes whether a recipe can be handled by a specific interaction adapter.
 */
public sealed interface InteractionProbeResult permits
    InteractionProbeResult.Supported,
    InteractionProbeResult.Unsupported {

    //region variants

    record Supported(String interactionId, boolean performerAvailable, boolean contextOpen) implements InteractionProbeResult {
        public Supported {
            Checks.checkNotNull(interactionId, "interactionId");
        }
    }

    record Unsupported(@Nullable ExecutionGuidance guidance) implements InteractionProbeResult {}

    //endregion
}
