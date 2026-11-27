package dev.vfyjxf.cirrus.api.recipe.execution.core;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionStatusKey;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;

/**
 * Builtin execution statuses shared by translated interaction plans.
 */
public final class ExecutionStatuses {

    //region statuses

    public static final ExecutionStatusKey running = status("running");
    public static final ExecutionStatusKey completed = status("completed");
    public static final ExecutionStatusKey paused = status("paused");
    public static final ExecutionStatusKey failed = status("failed");

    //endregion

    //region helpers

    private static ExecutionStatusKey status(String path) {
        return new ExecutionStatusKey(CirrusNamespaces.ofMod(path));
    }

    //endregion

    private ExecutionStatuses() {
        throw new AssertionError("No " + ExecutionStatuses.class.getSimpleName() + " instances for you!");
    }
}
