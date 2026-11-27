package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverApiImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Query API for resolving execution driver factories by runtime context.
 */
@ApiStatus.NonExtendable
public interface ExecutionDriverApi {

    static ExecutionDriverApi instance() {
        ExecutionDriverApi instance = ExecutionDriverApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("ExecutionDriverApi is not initialized");
        }
        return instance;
    }

    /**
     * Resolves a matching client-side driver and normalizes its lookup context.
     */
    @Nullable
    ExecutionDriverHandle resolveClient(DriverLookup lookup);

    /**
     * Resolves a matching server-side driver and normalizes its lookup context.
     */
    @Nullable
    ExecutionDriverHandle resolveServer(DriverLookup lookup);

    /**
     * Finds a matching client-side driver factory.
     */
    default @Nullable ExecutionDriverFactory findClient(DriverLookup lookup) {
        ExecutionDriverHandle handle = resolveClient(lookup);
        return handle == null ? null : handle.factory();
    }

    /**
     * Finds a matching server-side driver factory.
     */
    default @Nullable ExecutionDriverFactory findServer(DriverLookup lookup) {
        ExecutionDriverHandle handle = resolveServer(lookup);
        return handle == null ? null : handle.factory();
    }
}
