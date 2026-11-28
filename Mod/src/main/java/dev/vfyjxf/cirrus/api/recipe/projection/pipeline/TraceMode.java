package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

/**
 * Controls how much diff and provenance data a {@link ProjectionPipeline} captures on demand.
 */
public enum TraceMode {
    /** Fast evaluation path: zero trace/diff allocations; only returns final projected state and guard status. */
    none,
    /** Computes the collapsed net {@link ProjectionDiff} between input and output states without per-step traces. */
    netDiff,
    /** Captures full ordered {@link PipelineStepTrace}s plus collapsed net {@link ProjectionDiff} and {@link GridDiff}. */
    full
}
