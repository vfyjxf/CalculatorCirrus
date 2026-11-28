package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

/**
 * Semantic reason for a {@link ProjectionChange}.
 */
public enum ChangeReason {
    /** A new slot was added. */
    added,
    /** An existing slot was removed. */
    removed,
    /** Same conceptual content had its amount, chance, or components adjusted (e.g. scaled x2). */
    adjusted,
    /** Slot content was replaced with a different representation/resource (e.g. bucket -> fluid). */
    transformed,
    /** Slot moved from one address (e.g. grid cell) to another. */
    moved,
    /** An attribute on the recipe or a slot was set, updated, or removed. */
    attributeChanged,
    /** A tag on the recipe or a slot was added or removed. */
    tagChanged
}
