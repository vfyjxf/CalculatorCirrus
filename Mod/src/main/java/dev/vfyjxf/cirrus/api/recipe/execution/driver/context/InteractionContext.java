package dev.vfyjxf.cirrus.api.recipe.execution.driver.context;

import dev.vfyjxf.cloudlib.api.util.Namespace;

/**
 * Carries the interaction id for driver matching.
 */
public record InteractionContext(Namespace interactionId) implements DriverContext {}
