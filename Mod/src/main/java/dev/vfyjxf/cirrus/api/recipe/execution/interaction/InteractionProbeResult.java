package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

/**
 * Describes whether a recipe can be handled by a specific interaction adapter.
 */
public sealed interface InteractionProbeResult permits
    InteractionProbeResult.Supported,
    InteractionProbeResult.Unsupported {

    //region variants

    record Supported(Namespace interactionId, boolean performerAvailable, boolean contextOpen) implements InteractionProbeResult {}

    record Unsupported(@Nullable ExecutionGuidance guidance) implements InteractionProbeResult {}

    //endregion
}
