package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.ImmutableMap;

/**
 * Optional diagnostic payload collected when {@link DiagnosticLevel} is not
 * {@link DiagnosticLevel#none}.
 *
 * <p>A trace records a sequence of {@link TraceStage} entries corresponding to
 * major pipeline stages (source-policy filtering, tag resolution, candidate
 * generation, etc.). Each stage carries a key-value map of stage-specific data.
 *
 * @param traceSchemaVersion    version of the trace schema for forward compatibility
 * @param requestFingerprint    deterministic fingerprint identifying the request configuration
 * @param stages                ordered list of trace stages recorded during the run
 */
public record RoutingTrace(
    int traceSchemaVersion,
    String requestFingerprint,
    ImmutableList<TraceStage> stages
) {
    public RoutingTrace {
        Checks.checkArgument(traceSchemaVersion > 0, "traceSchemaVersion must be positive");
        Checks.checkNotNull(requestFingerprint, "requestFingerprint");
        Checks.checkNotNull(stages, "stages");
    }

    /**
     * Classification of trace pipeline stages.
     */
    public enum TraceStageKind {
        /** Source-policy evaluation stage. */
        sourcePolicy,
        /** Tag-based variant resolution stage. */
        tagResolve,
        /** Candidate recipe generation stage. */
        candidateGeneration,
        /** Hard-rule filtering stage. */
        hardRuleFilter,
        /** Score-rule ranking stage. */
        scoringAndRanking,
        /** Final plan assembly stage. */
        planAssembly,
        /** Output-target adapter producer selection stage. */
        adapterSelection
    }

    /**
     * One recorded stage in the trace, containing its kind and
     * a key-value map of stage-specific data.
     *
     * @param kind   which pipeline stage this entry represents
     * @param values stage-specific named values
     */
    public record TraceStage(
        TraceStageKind kind,
        ImmutableMap<String, String> values
    ) {
        public TraceStage {
            Checks.checkNotNull(kind, "kind");
            Checks.checkNotNull(values, "values");
        }
    }
}

