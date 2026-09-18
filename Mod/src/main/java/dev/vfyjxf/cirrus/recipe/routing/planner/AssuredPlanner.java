package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCapabilityFlag;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;

/**
 * Quality-oriented planner that applies capability gates before planning.
 *
 * <p>In {@link PlannerMode#assuredProven} mode, this planner verifies that the
 * active {@link RuleProfile} declares all required {@link RuleCapabilityFlag}
 * capabilities (e.g. monotonic score, admissible bound). If any capability is
 * missing and {@link RoutingRequest#allowModeFallback()} is {@code false}, the
 * planner immediately rejects the request with a
 * {@link RoutingFailureCode#capabilityGateRejected} failure.
 *
 * <p>If fallback is allowed, the planner continues in
 * {@link PlannerMode#assuredBounded} semantics instead.
 *
 * <p>The actual graph walk is shared with {@link StandardPlanner}, but the
 * request mode remains visible to that search core. Assured modes therefore use
 * the wider bounded candidate window instead of merely relabeling a standard
 * greedy run.
 *
 * @see StandardPlanner
 * @see RuleCapabilityFlag
 */
public final class AssuredPlanner implements RoutePlanner {

    private static final ImmutableSet<RuleCapabilityFlag> provenRequired = Sets.immutable.of(
        RuleCapabilityFlag.monotonicScore,
        RuleCapabilityFlag.totalOrderComparator,
        RuleCapabilityFlag.admissibleBound,
        RuleCapabilityFlag.pureRulesOnly,
        RuleCapabilityFlag.deterministicTiebreak
    );

    private final StandardPlanner delegate = new StandardPlanner();

    @Override
    public RoutingOutcome plan(PlannerContext context) {
        PlannerMode requestedMode = context.request().plannerMode();
        if (requestedMode == PlannerMode.assuredProven) {
            var missing = provenRequired.reject(context.ruleProfile().capabilities().flags()::contains).toImmutable();
            if (missing.notEmpty()) {
                if (!context.request().allowModeFallback()) {
                    return gateRejectedOutcome(context, missing);
                }
                RoutingOutcome bounded = delegate.plan(context.withRequest(copyRequestWithMode(context.request(), PlannerMode.assuredBounded)));
                return rewriteMode(bounded, PlannerMode.assuredBounded, false, Lists.immutable.ofAll(missing.collect(Enum::name)));
            }
        }

        RoutingOutcome outcome = delegate.plan(context);
        return rewriteMode(
            outcome,
            requestedMode == PlannerMode.assuredProven ? PlannerMode.assuredProven : PlannerMode.assuredBounded,
            true,
            Lists.immutable.empty()
        );
    }

    private RoutingOutcome gateRejectedOutcome(PlannerContext context, ImmutableSet<RuleCapabilityFlag> missing) {
        RoutingIssue issue = new RoutingIssue(
            "issue-gate",
            RoutingFailureCode.capabilityGateRejected,
            RoutingIssue.Severity.error,
            true,
            "cirrus.routing.capability_gate_rejected",
            Maps.immutable.of("missing", missing.collect(Enum::name).makeString(",")),
            new RoutingIssuePayload.CapabilityGate(missing.collect(Enum::name).makeString(",")),
            null,
            null,
            Maps.immutable.empty()
        );
        RoutingMetrics metrics = new RoutingMetrics(
            0,
            0,
            0,
            0,
            0,
            PlannerMode.assuredProven,
            context.request().inventorySensitivityProfile(),
            context.request().effectiveTagResolvePolicy(),
            context.request().ruleProfileId(),
            context.request().sourcePolicyId(),
            context.tagSnapshot().version(),
            0,
            Lists.immutable.empty(),
            null,
            context.request().budget().maxExpandedNodes(),
            0,
            false,
            false,
            Lists.immutable.ofAll(missing.collect(Enum::name)),
            "capabilityGate",
            "none",
            "none",
            -1,
            0,
            false,
            0,
            0,
            context.request().explorationSeed() == null ? 0 : context.request().explorationSeed(),
            context.request().loopPolicy(),
            0
        );
        return new RoutingOutcome(RoutingStatus.failed, null, Lists.immutable.of(issue), metrics, null);
    }

    private RoutingRequest copyRequestWithMode(RoutingRequest request, PlannerMode mode) {
        return new RoutingRequest(
            mode,
            request.ruleProfileId(),
            request.sourcePolicyId(),
            request.diagnosticLevel(),
            request.deterministic(),
            request.allowModeFallback(),
            request.budget(),
            request.stock(),
            request.tagSnapshot(),
            request.target(),
            request.inventorySensitivityProfile(),
            request.tagResolvePolicy(),
            request.effectiveTagResolvePolicy(),
            request.preferredVariants(),
            request.adapterSelectionPolicy(),
            request.maxProducerCandidates(),
            request.loopPolicy(),
            request.loopBudget(),
            request.explorationEnabled(),
            request.explorationPolicyId(),
            request.explorationSeed(),
            request.explorationBudgetShare(),
            request.heuristicPolicy()
        );
    }

    private RoutingOutcome rewriteMode(
        RoutingOutcome origin,
        PlannerMode mode,
        boolean capabilityGatePassed,
        org.eclipse.collections.api.list.ImmutableList<String> missingCapabilities
    ) {
        RoutingMetrics m = origin.metrics();
        RoutingMetrics rewritten = new RoutingMetrics(
            m.elapsedMs(),
            m.expandedNodes(),
            m.generatedCandidates(),
            m.scannedRefs(),
            m.acceptedCandidates(),
            mode,
            m.effectiveInventorySensitivityProfile(),
            m.effectiveTagResolvePolicy(),
            m.effectiveRuleProfileId(),
            m.effectiveSourcePolicyId(),
            m.tagSnapshotVersion(),
            m.adapterAttemptCount(),
            m.adapterTriedProducerIds(),
            m.adapterBestIntermediateStatus(),
            m.budgetLimit(),
            m.budgetUsed(),
            m.budgetExhausted(),
            capabilityGatePassed,
            missingCapabilities,
            m.budgetStopReason(),
            m.budgetCulpritRecipe(),
            m.budgetCulpritInput(),
            m.budgetCulpritDepth(),
            m.budgetCulpritRemaining(),
            m.budgetCulpritActiveCycle(),
            m.explorationBranchesExpanded(),
            m.explorationBudgetUsed(),
            m.effectiveExplorationSeed(),
            m.effectiveLoopPolicy(),
            m.candidateRollbacks()
        );
        return new RoutingOutcome(origin.status(), origin.plan(), origin.issues(), rewritten, origin.trace());
    }
}
