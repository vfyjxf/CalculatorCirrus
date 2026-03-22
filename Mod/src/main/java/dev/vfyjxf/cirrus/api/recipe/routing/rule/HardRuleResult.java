package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * Result of a {@link HardRule} evaluation.
 *
 * <p>Use the factory methods {@link #accept()} and
 * {@link #reject(RoutingFailureCode, String)} to construct instances.
 *
 * @param accepted    {@code true} if the candidate passed
 * @param failureCode the failure classification if rejected; {@code null}
 *                    if accepted
 * @param reason      human-readable rejection reason; {@code null} if
 *                    accepted
 */
public record HardRuleResult(
    boolean accepted,
    @Nullable RoutingFailureCode failureCode,
    @Nullable String reason
) {
    public HardRuleResult {
        if (!accepted) {
            Checks.checkNotNull(failureCode, "failureCode");
        }
    }

    /** Returns an accepting result. */
    public static HardRuleResult accept() {
        return new HardRuleResult(true, null, null);
    }

    /**
     * Returns a rejecting result with the given failure code and reason.
     *
     * @param code   the failure classification
     * @param reason human-readable reason for rejection
     * @return a rejecting result
     */
    public static HardRuleResult reject(RoutingFailureCode code, String reason) {
        return new HardRuleResult(false, code, reason);
    }
}

