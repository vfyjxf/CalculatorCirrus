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
 * @see RoutingService
 */
public record RoutingOutcome(
    RoutingStatus status,
    @Nullable RoutingPlan plan,
    ImmutableList<RoutingIssue> issues,
    RoutingMetrics metrics,
    @Nullable RoutingTrace trace
) {
    public RoutingOutcome {
        Checks.checkNotNull(status, "status");
        Checks.checkNotNull(issues, "issues");
        Checks.checkNotNull(metrics, "metrics");
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

