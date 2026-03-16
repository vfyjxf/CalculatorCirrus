package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * A typed key identifying an execution status.
 *
 * @param id the namespace identifier for this status
 */
public record ExecutionStatusKey(Namespace id) {

    public ExecutionStatusKey {
        Checks.checkNotNull(id, "id");
    }
}
