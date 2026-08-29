package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Comparator;
import java.util.Objects;

/**
 * Built-in default {@link RuleProfile} presets.
 *
 * <p>All presets share the tiered score models from {@link DefaultScoreModels}
 * and differ only in tuning: {@code assuredBounded} uses the depth ×0.8 /
 * amplification ×1.2 weight variant, a larger shortlist and wider rollback.
 * Base feature scoring lives in the model; user clauses attach as
 * {@link HardRule}s (reject) and {@link ScoreRule}s (weighted adjustments).
 *
 * <p>All built-in profiles share a common hard rule that rejects recipes
 * with probabilistic (non-constant) inputs or outputs.
 *
 * @see RuleProfile
 */
public final class DefaultRuleProfiles {

    public static final Namespace standardDefaultId = Namespace.of("cirrus", "routing/profile/standard_default");
    public static final Namespace standardExecutionId = Namespace.of("cirrus", "routing/profile/standard_execution");
    public static final Namespace assuredBoundedDefaultId = Namespace.of("cirrus", "routing/profile/assured_bounded_default");
    public static final Namespace assuredProvenCompatId = Namespace.of("cirrus", "routing/profile/assured_proven_compat");

    private static final ImmutableNoOpExplorePolicy noOpExplore = new ImmutableNoOpExplorePolicy();
    private static final TieBreakPolicy stableTieBreak = Comparator
        .comparing((CandidateContext context) -> context.recipe().recipe().systemId().toString())
        .thenComparing(context -> Objects.toString(context.recipe().recipe().id(), "null"))
        .thenComparingInt(context -> context.recipe().recipe().hashCode())::compare;

    private DefaultRuleProfiles() {
    }

    /**
     * Returns the default profile for the {@code standard} planner mode:
     * lexicographic tier ranking, {@code shortlistK=4}, {@code rollbackWidth=2},
     * and a no-op explore policy for deterministic behavior.
     */
    public static RuleProfile standardDefault() {
        return new RuleProfile(
            standardDefaultId,
            DefaultScoreModels.standard(),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.empty(),
            lexicographicRank(),
            stableTieBreak(),
            noOpExplore,
            strictUnresolvedInputs(),
            new RuleCapabilities(Sets.immutable.of(
                RuleCapabilityFlag.totalOrderComparator,
                RuleCapabilityFlag.pureRulesOnly,
                RuleCapabilityFlag.deterministicTiebreak
            )),
            4,
            2,
            0.03,
            0.60,
            0.85,
            false,
            true
        );
    }

    public static RuleProfile standardExecution() {
        return new RuleProfile(
            standardExecutionId,
            DefaultScoreModels.standard(),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.empty(),
            lexicographicRank(),
            stableTieBreak(),
            noOpExplore,
            directSupplyUnresolvedInputs(),
            new RuleCapabilities(Sets.immutable.of(
                RuleCapabilityFlag.totalOrderComparator,
                RuleCapabilityFlag.pureRulesOnly,
                RuleCapabilityFlag.deterministicTiebreak
            )),
            4,
            2,
            0.03,
            0.60,
            0.85,
            true,
            true
        );
    }

    /**
     * Returns the default profile for the {@code assuredBounded} planner
     * mode: depth ×0.8 / amplification ×1.2 weights, {@code shortlistK=12},
     * {@code rollbackWidth=11}, and tighter amplification thresholds.
     */
    public static RuleProfile assuredBoundedDefault() {
        return new RuleProfile(
            assuredBoundedDefaultId,
            DefaultScoreModels.assuredBounded(),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.empty(),
            lexicographicRank(),
            stableTieBreak(),
            noOpExplore,
            strictUnresolvedInputs(),
            new RuleCapabilities(Sets.immutable.of(
                RuleCapabilityFlag.totalOrderComparator,
                RuleCapabilityFlag.pureRulesOnly,
                RuleCapabilityFlag.deterministicTiebreak
            )),
            12,
            11,
            0.02,
            0.65,
            1.0,
            false,
            true
        );
    }

    /**
     * Returns the default profile for the {@code assuredProven} planner
     * mode. Identical scoring to {@link #standardDefault()} but declares
     * additional capabilities ({@link RuleCapabilityFlag#monotonicScore},
     * {@link RuleCapabilityFlag#admissibleBound}) that allow the planner to
     * use A*-class algorithms with optimality guarantees.
     */
    public static RuleProfile assuredProvenCompat() {
        return new RuleProfile(
            assuredProvenCompatId,
            DefaultScoreModels.standard(),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.empty(),
            lexicographicRank(),
            stableTieBreak(),
            noOpExplore,
            strictUnresolvedInputs(),
            new RuleCapabilities(Sets.immutable.of(
                RuleCapabilityFlag.monotonicScore,
                RuleCapabilityFlag.totalOrderComparator,
                RuleCapabilityFlag.admissibleBound,
                RuleCapabilityFlag.pureRulesOnly,
                RuleCapabilityFlag.deterministicTiebreak
            )),
            12,
            11,
            0.02,
            0.65,
            1.0,
            false,
            true
        );
    }

    static HardRuleResult rejectProbabilistic(CandidateContext context) {
        for (int inputIndex = 0; inputIndex < context.recipe().inputCount(); inputIndex++) {
            InputContent input = context.recipe().inputAt(inputIndex);
            if (!input.constantly()) {
                return HardRuleResult.reject(RoutingFailureCode.noFeasibleSource, "probabilistic_input");
            }
        }
        for (int outputIndex = 0; outputIndex < context.recipe().outputCount(); outputIndex++) {
            OutputContent output = context.recipe().outputAt(outputIndex);
            if (!output.constantly()) {
                return HardRuleResult.reject(RoutingFailureCode.noFeasibleSource, "probabilistic_output");
            }
        }
        return HardRuleResult.accept();
    }

    private static UnresolvedInputRule strictUnresolvedInputs() {
        return context -> UnresolvedInputRule.Resolution.fail;
    }

    private static UnresolvedInputRule directSupplyUnresolvedInputs() {
        return context -> context.phase() == UnresolvedInputContext.Phase.noSource
            ? UnresolvedInputRule.Resolution.pendingInput
            : UnresolvedInputRule.Resolution.fail;
    }

    private static RankPolicy lexicographicRank() {
        return (left, right) -> {
            int max = Math.min(left.length, right.length);
            for (int i = 0; i < max; i++) {
                int cmp = Double.compare(left[i], right[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return Integer.compare(left.length, right.length);
        };
    }

    private static TieBreakPolicy stableTieBreak() {
        return stableTieBreak;
    }

    public static boolean usesStableRecipeTieBreak(RuleProfile profile) {
        return profile.tieBreakPolicy() == stableTieBreak;
    }

    private static final class ImmutableNoOpExplorePolicy implements ExplorePolicy {
        @Override
        public void reorderNearTies(org.eclipse.collections.api.list.MutableList<RankedCandidate> candidates, double epsilon, long seed) {
            // Intentionally no-op to preserve deterministic default behavior.
        }
    }
}
