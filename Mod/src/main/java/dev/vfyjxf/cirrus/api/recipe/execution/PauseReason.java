package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.util.PauseReasonKey;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * Normal pause state emitted by the runtime when execution cannot advance automatically.
 */
public record PauseReason(PauseReasonKey key, @Nullable ExecutionGuidance guidance) {

    public PauseReason {
        Checks.checkNotNull(key, "key");
    }
}
