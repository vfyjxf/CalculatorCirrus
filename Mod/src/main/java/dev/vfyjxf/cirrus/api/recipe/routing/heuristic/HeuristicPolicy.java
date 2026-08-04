package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

/**
 * Request-level control for recipe routing hints.
 */
public enum HeuristicPolicy {
    defaultSafe,
    allowLastResort,
    includeAll,
    ignoreHints
}
