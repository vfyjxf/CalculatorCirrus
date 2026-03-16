package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * A typed key identifying an execution mode.
 *
 * @param id the namespace identifier for this mode
 */
public record ExecutionModeKey(Namespace id) {

    public ExecutionModeKey {
        Checks.checkNotNull(id, "id");
    }
}
