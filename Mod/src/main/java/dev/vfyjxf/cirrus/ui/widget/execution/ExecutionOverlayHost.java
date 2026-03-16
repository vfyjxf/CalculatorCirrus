package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the latest execution session snapshot to a standalone overlay widget.
 */
@FunctionalInterface
public interface ExecutionOverlayHost {

    @Nullable ExecutionSession executionOverlaySession();
}
