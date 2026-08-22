package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Final result produced by {@link RoutingService#route(RoutingRequest)}.
 *
 * <p>An outcome always contains a {@link #status()}, a list of {@link #issues()},
 * and {@link #metrics()}. The {@link #plan()} is present only when routing
 * succeeded or partially succeeded. The {@link #trace()} is present only when
 * diagnostics were enabled in the request.
 *
 * @param status  terminal routing status
 * @param plan    the execution plan, or {@code null} if routing failed completely
 * @param issues  list of issues encountered during routing (may be empty on success)
 * @param metrics observability metrics for this run
 * @param trace   optional diagnostic trace (present only when diagnostics are enabled)
 * @param continuationToken optional session token for continuation runs; non-null when
 *                          this route stopped early (partial plan or exhausted budget)
 *                          and a follow-up wave with a larger budget may resolve more.
 *                          Pass it to {@link RoutingService#routeContinuation(String, RoutingRequest)}
 * @see RoutingService
 */
public record RoutingOutcome(
    RoutingStatus status,
    @Nullable RoutingPlan plan,
    ImmutableList<RoutingIssue> issues,
    RoutingMetrics metrics,
    @Nullable RoutingTrace trace,
    @Nullable String continuationToken
) {
    public RoutingOutcome {
        Checks.checkNotNull(status, "status");
        Checks.checkNotNull(issues, "issues");
        Checks.checkNotNull(metrics, "metrics");
    }

    /** Backwards-compatible constructor without a continuation token. */
    public RoutingOutcome(
        RoutingStatus status,
        @Nullable RoutingPlan plan,
        ImmutableList<RoutingIssue> issues,
        RoutingMetrics metrics,
        @Nullable RoutingTrace trace
    ) {
        this(status, plan, issues, metrics, trace, null);
    }

    /** Returns a copy of this outcome carrying the given continuation token. */
    public RoutingOutcome withContinuationToken(String token) {
        return new RoutingOutcome(status, plan, issues, metrics, trace, token);
    }

    /** Returns {@code true} if routing produced a complete plan. */
    public boolean success() {
        return status == RoutingStatus.success;
    }

    /** Returns {@code true} if routing produced an incomplete (partial) plan. */
    public boolean partial() {
        return status == RoutingStatus.partial;
    }

    /** Returns {@code true} if routing failed to produce any plan. */
    public boolean failed() {
        return status == RoutingStatus.failed;
    }
}

