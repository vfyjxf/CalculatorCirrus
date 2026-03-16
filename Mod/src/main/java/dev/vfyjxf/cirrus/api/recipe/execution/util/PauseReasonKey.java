package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * A typed key identifying a pause reason.
 *
 * @param id the namespace identifier for this pause reason
 */
public record PauseReasonKey(Namespace id) {

    public PauseReasonKey {
        Checks.checkNotNull(id, "id");
    }
}
