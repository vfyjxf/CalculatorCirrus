package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * Driver registration entrypoint for plugins.
 */
@ApiStatus.NonExtendable
public interface ExecutionDriverRegister {

    /**
     * Registers a client-side driver mapping.
     */
    void registerClient(DriverSelector selector, ExecutionDriverFactory factory);

    /**
     * Registers a server-side driver mapping.
     */
    void registerServer(DriverSelector selector, ExecutionDriverFactory factory);
}
