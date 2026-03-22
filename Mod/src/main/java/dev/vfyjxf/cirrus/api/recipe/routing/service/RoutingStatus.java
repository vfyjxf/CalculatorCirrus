package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Terminal status of a routing attempt.
 */
public enum RoutingStatus {
    /** All inputs were fully resolved and a complete plan was produced. */
    success,
    /** Some inputs were resolved but the plan is incomplete. */
    partial,
    /** No viable plan could be produced. */
    failed
}

