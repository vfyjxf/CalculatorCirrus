package dev.vfyjxf.cirrus.api.recipe.execution.core;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionModeKey;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;

/**
 * Builtin execution modes describing how much automation an executor can provide.
 */
public final class ExecutionModes {

    //region modes

    public static final ExecutionModeKey automatic = mode("automatic");
    public static final ExecutionModeKey guided = mode("guided");

    //endregion

    //region helpers

    private static ExecutionModeKey mode(String path) {
        return new ExecutionModeKey(CirrusNamespaces.ofMod(path));
    }

    //endregion

    private ExecutionModes() {
        throw new AssertionError("No " + ExecutionModes.class.getSimpleName() + " instances for you!");
    }
}
