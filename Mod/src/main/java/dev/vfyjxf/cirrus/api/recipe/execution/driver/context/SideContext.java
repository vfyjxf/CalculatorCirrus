package dev.vfyjxf.cirrus.api.recipe.execution.driver.context;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;

/**
 * Carries the required execution side for driver matching.
 */
public record SideContext(ExecutionSideRequirement side) implements DriverContext {}
