package dev.vfyjxf.cirrus.api.recipe.projection.effect;

/**
 * Structured description of a projection-time recipe mutation.
 */
public sealed interface ProjectionEffect permits InputEffect, OutputEffect, SchemaEffect {

    ProjectionEffectSource source();
}
