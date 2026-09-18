package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight observability metrics for one routing run.
 *
 * <p>Metrics are always present in a {@link RoutingOutcome} regardless of
 * success or failure, providing telemetry data for monitoring, debugging,
 * and performance analysis.
 *
 * @param elapsedMs                             wall-clock time spent planning (milliseconds)
 * @param expandedNodes                         number of recipe nodes visited
 * @param generatedCandidates                   total candidates that entered evaluation (static gates passed)
 * @param scannedRefs                           raw producer refs visited during full producer scans
 * @param acceptedCandidates                    candidates that passed hard rules
 * @param effectivePlannerMode                  the planner mode actually used
 * @param effectiveInventorySensitivityProfile   the inventory sensitivity that was applied
 * @param effectiveTagResolvePolicy             the tag resolve policy that was applied
 * @param effectiveRuleProfileId                the rule profile ID that was used
 * @param effectiveSourcePolicyId               the source policy ID that was used
 * @param tagSnapshotVersion                    version of the tag snapshot used
 * @param adapterAttemptCount                   number of output-target adapter attempts
 * @param adapterTriedProducerIds               stable IDs of producers tried by the adapter
 * @param adapterBestIntermediateStatus          best routing status seen across adapter attempts
 * @param budgetLimit                           the configured budget limit
 * @param budgetUsed                            how much budget was consumed
 * @param budgetExhausted                       whether the budget was fully exhausted
 * @param capabilityGatePassed                  whether the capability gate check passed
 * @param missingCapabilities                   names of missing capability flags (if any)
 * @param budgetStopReason                      reason the budget stopped (or {@code "none"})
 * @param budgetCulpritRecipe                   recipe associated with the first budget exhaustion
 * @param budgetCulpritInput                    input associated with the first budget exhaustion
 * @param budgetCulpritDepth                    search depth of the first budget exhaustion
 * @param budgetCulpritRemaining                remaining input amount at the first budget exhaustion
 * @param budgetCulpritActiveCycle              whether the budget culprit was on an active-path cycle
 * @param explorationBranchesExpanded           branches expanded during exploration
 * @param explorationBudgetUsed                 exploration-specific budget consumed
 * @param effectiveExplorationSeed              the effective exploration seed used
 * @param effectiveLoopPolicy                   the loop policy that was applied
 * @param candidateRollbacks                    candidate probes rolled back after a failed/soft child outcome
 */
public record RoutingMetrics(
    long elapsedMs,
    int expandedNodes,
    int generatedCandidates,
    int scannedRefs,
    int acceptedCandidates,
    PlannerMode effectivePlannerMode,
    InventorySensitivityProfile effectiveInventorySensitivityProfile,
    TagResolvePolicy effectiveTagResolvePolicy,
    Namespace effectiveRuleProfileId,
    Namespace effectiveSourcePolicyId,
    long tagSnapshotVersion,
    int adapterAttemptCount,
    ImmutableList<String> adapterTriedProducerIds,
    @Nullable RoutingStatus adapterBestIntermediateStatus,
    int budgetLimit,
    int budgetUsed,
    boolean budgetExhausted,
    boolean capabilityGatePassed,
    ImmutableList<String> missingCapabilities,
    String budgetStopReason,
    String budgetCulpritRecipe,
    String budgetCulpritInput,
    int budgetCulpritDepth,
    long budgetCulpritRemaining,
    boolean budgetCulpritActiveCycle,
    int explorationBranchesExpanded,
    int explorationBudgetUsed,
    long effectiveExplorationSeed,
    LoopPolicy effectiveLoopPolicy,
    int candidateRollbacks
) {
    public RoutingMetrics {
        Checks.checkArgument(elapsedMs >= 0, "elapsedMs must be non-negative");
        Checks.checkArgument(expandedNodes >= 0, "expandedNodes must be non-negative");
        Checks.checkArgument(generatedCandidates >= 0, "generatedCandidates must be non-negative");
        Checks.checkArgument(scannedRefs >= 0, "scannedRefs must be non-negative");
        Checks.checkArgument(acceptedCandidates >= 0, "acceptedCandidates must be non-negative");
        Checks.checkNotNull(effectivePlannerMode, "effectivePlannerMode");
        Checks.checkNotNull(effectiveInventorySensitivityProfile, "effectiveInventorySensitivityProfile");
        Checks.checkNotNull(effectiveTagResolvePolicy, "effectiveTagResolvePolicy");
        Checks.checkNotNull(effectiveRuleProfileId, "effectiveRuleProfileId");
        Checks.checkNotNull(effectiveSourcePolicyId, "effectiveSourcePolicyId");
        Checks.checkArgument(tagSnapshotVersion >= 0, "tagSnapshotVersion must be non-negative");
        Checks.checkArgument(adapterAttemptCount >= 0, "adapterAttemptCount must be non-negative");
        Checks.checkNotNull(adapterTriedProducerIds, "adapterTriedProducerIds");
        Checks.checkArgument(budgetLimit >= 0, "budgetLimit must be non-negative");
        Checks.checkArgument(budgetUsed >= 0, "budgetUsed must be non-negative");
        Checks.checkNotNull(missingCapabilities, "missingCapabilities");
        Checks.checkNotNull(budgetStopReason, "budgetStopReason");
        Checks.checkNotNull(budgetCulpritRecipe, "budgetCulpritRecipe");
        Checks.checkNotNull(budgetCulpritInput, "budgetCulpritInput");
        Checks.checkArgument(budgetCulpritDepth >= -1, "budgetCulpritDepth must be -1 or non-negative");
        Checks.checkArgument(budgetCulpritRemaining >= 0, "budgetCulpritRemaining must be non-negative");
        Checks.checkArgument(explorationBranchesExpanded >= 0, "explorationBranchesExpanded must be non-negative");
        Checks.checkArgument(explorationBudgetUsed >= 0, "explorationBudgetUsed must be non-negative");
        Checks.checkNotNull(effectiveLoopPolicy, "effectiveLoopPolicy");
        Checks.checkArgument(candidateRollbacks >= 0, "candidateRollbacks must be non-negative");
    }

    public static RoutingMetrics empty(
        PlannerMode mode,
        Namespace profileId,
        Namespace sourcePolicyId,
        long snapshotVersion,
        LoopPolicy loopPolicy
    ) {
        return new RoutingMetrics(
            0,
            0,
            0,
            0,
            0,
            mode,
            InventorySensitivityProfile.balanced,
            TagResolvePolicy.balanced,
            profileId,
            sourcePolicyId,
            snapshotVersion,
            0,
            org.eclipse.collections.api.factory.Lists.immutable.empty(),
            null,
            0,
            0,
            false,
            true,
            org.eclipse.collections.api.factory.Lists.immutable.empty(),
            "none",
            "none",
            "none",
            -1,
            0,
            false,
            0,
            0,
            0,
            loopPolicy,
            0
        );
    }
}
