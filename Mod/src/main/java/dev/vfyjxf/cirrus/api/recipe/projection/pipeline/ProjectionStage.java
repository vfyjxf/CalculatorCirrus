package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

/**
 * Ordered execution stage of a {@link ProjectionEffect} in a {@link ProjectionPipeline}.
 */
public enum ProjectionStage {
    /** Preprocess and unpack raw recipe inputs/outputs (e.g., fluid container unpacking). */
    prepare,
    /** Main semantic recipe and slot transformations (e.g., scaling, overclocking, durability). */
    transform,
    /** Post-transform filtering and normalization (e.g., guaranteed-output filtering, clamping). */
    finalize,
    /** Final validation and guard checks. */
    validate
}
