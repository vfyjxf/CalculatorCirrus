package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;

/**
 * Resolved driver handle carrying normalized runtime lookup for recreation.
 */
public record ExecutionDriverHandle(ExecutionDriverFactory factory, DriverLookup lookup) {

    public ExecutionDriver create() {
        return factory.create(lookup);
    }

    public ExecutionSideRequirement side() {
        return lookup.side();
    }
}
