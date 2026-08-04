package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Controls how the output-target adapter selects among multiple producer
 * candidates when routing by output resource rather than by recipe.
 *
 * @see RoutingRequest.OutputTarget
 */
public enum OutputSelectionPolicy {
    /**
     * Try multiple producer candidates in cost order; accept the first
     * fully successful result, or fall back to the best partial.
     */
    bestEffortSingleRecipe,
    /**
     * Try only the single best producer candidate and return whatever
     * result it produces (success, partial, or failure).
     */
    strictSingleRecipe
}

