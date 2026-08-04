package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Controls the verbosity of diagnostic information collected during routing.
 *
 * <p>Higher levels produce more detailed {@link RoutingTrace} payloads at the
 * cost of additional runtime overhead.
 */
public enum DiagnosticLevel {
    /** No diagnostic data collected. Best performance. */
    none,
    /** Lightweight stage-level summaries. */
    basic,
    /** Full per-step trace data including trace stages and policy details. */
    detailed
}

