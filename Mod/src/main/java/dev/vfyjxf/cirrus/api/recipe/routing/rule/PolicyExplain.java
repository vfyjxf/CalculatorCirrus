package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Diagnostic payload recording the effect of source-policy evaluation.
 *
 * <p>Attached to each {@link SourcePolicyDecision} to explain how the
 * allowed-recipe set was derived. Contains a per-rule trace showing the
 * number of matched recipes, the delta, and the running total.
 *
 * @param snapshotVersion    the tag snapshot version used during evaluation
 * @param initialRecipeCount the total number of recipes before filtering
 * @param finalRecipeCount   the number of allowed recipes after all rules
 * @param ruleTraces         ordered list of per-rule diagnostic traces
 */
public record PolicyExplain(
    long snapshotVersion,
    int initialRecipeCount,
    int finalRecipeCount,
    ImmutableList<RuleTrace> ruleTraces
) {
    public PolicyExplain {
        Checks.checkArgument(snapshotVersion >= 0, "snapshotVersion must be non-negative");
        Checks.checkArgument(initialRecipeCount >= 0, "initialRecipeCount must be non-negative");
        Checks.checkArgument(finalRecipeCount >= 0, "finalRecipeCount must be non-negative");
        Checks.checkNotNull(ruleTraces, "ruleTraces");
    }

    /**
     * Diagnostic trace for a single source-policy rule evaluation.
     *
     * @param ruleId              the rule's unique identifier
     * @param action              the action the rule performed
     * @param matchedCount        how many recipes matched the rule's tag expression
     * @param deltaCount          change in allowed-set size (may be negative for
     *                            excludes)
     * @param runningAllowedCount the allowed count after applying this rule
     */
    public record RuleTrace(
        Namespace ruleId,
        SourcePolicyAction action,
        int matchedCount,
        int deltaCount,
        int runningAllowedCount
    ) {
        public RuleTrace {
            Checks.checkNotNull(ruleId, "ruleId");
            Checks.checkNotNull(action, "action");
            Checks.checkArgument(matchedCount >= 0, "matchedCount must be non-negative");
            Checks.checkArgument(runningAllowedCount >= 0, "runningAllowedCount must be non-negative");
        }
    }
}
