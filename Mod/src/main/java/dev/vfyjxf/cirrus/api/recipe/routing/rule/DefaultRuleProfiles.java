package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;

import java.util.Comparator;
import java.util.Objects;

/**
 * Built-in default {@link RuleProfile} presets.
 *
 * <p>Provides built-in profiles with increasing quality guarantees, plus an
 * execution-oriented standard variant:
 * <ul>
 *     <li>{@link #standardDefault()} — fast greedy profile with
 *         lexicographic ranking, small rollback width, and deterministic
 *         tiebreaking. Suitable for the {@code standard} planner mode.</li>
 *     <li>{@link #standardExecution()} — standard scoring with a leaf-only
 *         direct-supply unresolved-input policy for execution planning.</li>
 *     <li>{@link #assuredBoundedDefault()} — tighter amplification
 *         control, larger shortlist, and wider rollback. Designed for
 *         {@code assuredBounded} mode.</li>
 *     <li>{@link #assuredProvenCompat()} — additionally declares
 *         {@link RuleCapabilityFlag#monotonicScore} and
 *         {@link RuleCapabilityFlag#admissibleBound}, enabling A*-class
 *         algorithms in the {@code assuredProven} mode.</li>
 * </ul>
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

    private DefaultRuleProfiles() {
    }

    /**
     * Returns the default profile for the {@code standard} planner mode.
     *
     * <p>Uses lexicographic ranking over (craft, depth, amplification),
     * {@code shortlistK=4}, {@code rollbackWidth=2}, and a no-op explore
     * policy for deterministic behavior.
     *
     * @return a fresh {@code standard} profile instance
     */
    public static RuleProfile standardDefault() {
        return new RuleProfile(
            standardDefaultId,
            Lists.immutable.of("craft", "inventoryShortage", "depth", "amplification"),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.of(
                (context, acc) -> acc.add("craft", context.craftTimes()),
                (context, acc) -> acc.add("inventoryShortage", 1.0d - context.availableCoverage()),
                (context, acc) -> acc.add("depth", context.depth()),
                (context, acc) -> acc.add("amplification", context.amplificationRisk())
            ),
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
            false
        );
    }

    public static RuleProfile standardExecution() {
        return new RuleProfile(
            standardExecutionId,
            Lists.immutable.of("craft", "inventoryShortage", "depth", "amplification"),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.of(
                (context, acc) -> acc.add("craft", context.craftTimes()),
                (context, acc) -> acc.add("inventoryShortage", 1.0d - context.availableCoverage()),
                (context, acc) -> acc.add("depth", context.depth()),
                (context, acc) -> acc.add("amplification", context.amplificationRisk())
            ),
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
            true
        );
    }

    /**
     * Returns the default profile for the {@code assuredBounded} planner mode.
     *
     * <p>Compared to {@link #standardDefault()}, this profile uses weighted
     * depth/amplification scores (0.8× depth, 1.2× amplification),
     * {@code shortlistK=12}, {@code rollbackWidth=11}, and tighter
     * amplification thresholds.
     *
     * @return a fresh {@code assuredBounded} profile instance
     */
    public static RuleProfile assuredBoundedDefault() {
        return new RuleProfile(
            assuredBoundedDefaultId,
            Lists.immutable.of("craft", "inventoryShortage", "depth", "amplification"),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.of(
                (context, acc) -> acc.add("craft", context.craftTimes()),
                (context, acc) -> acc.add("inventoryShortage", 1.0d - context.availableCoverage()),
                (context, acc) -> acc.add("depth", context.depth() * 0.8),
                (context, acc) -> acc.add("amplification", context.amplificationRisk() * 1.2)
            ),
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
            false
        );
    }

    /**
     * Returns the default profile for the {@code assuredProven} planner mode.
     *
     * <p>Identical scoring to {@link #standardDefault()} but declares
     * additional capabilities ({@link RuleCapabilityFlag#monotonicScore},
     * {@link RuleCapabilityFlag#admissibleBound}) that allow the planner
     * to use A*-class algorithms with optimality guarantees.
     *
     * @return a fresh {@code assuredProven} profile instance
     */
    public static RuleProfile assuredProvenCompat() {
        return new RuleProfile(
            assuredProvenCompatId,
            Lists.immutable.of("craft", "inventoryShortage", "depth", "amplification"),
            Lists.immutable.of(DefaultRuleProfiles::rejectProbabilistic),
            Lists.immutable.of(
                (context, acc) -> acc.add("craft", context.craftTimes()),
                (context, acc) -> acc.add("inventoryShortage", 1.0d - context.availableCoverage()),
                (context, acc) -> acc.add("depth", context.depth()),
                (context, acc) -> acc.add("amplification", context.amplificationRisk())
            ),
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
            false
        );
    }

    private static HardRuleResult rejectProbabilistic(CandidateContext context) {
        for (InputContent input : context.recipe().current().inputs()) {
            if (!input.constantly()) {
                return HardRuleResult.reject(RoutingFailureCode.noFeasibleSource, "probabilistic_input");
            }
        }
        for (OutputContent output : context.recipe().current().outputs()) {
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
        return Comparator
            .comparing((CandidateContext context) -> context.recipe().recipe().systemId().toString())
            .thenComparing(context -> Objects.toString(context.recipe().recipe().id(), "null"))
            .thenComparingInt(context -> context.recipe().recipe().hashCode())::compare;
    }

    private static final class ImmutableNoOpExplorePolicy implements ExplorePolicy {
        @Override
        public void reorderNearTies(org.eclipse.collections.api.list.MutableList<RankedCandidate> candidates, double epsilon, long seed) {
            // Intentionally no-op to preserve deterministic default behavior.
        }
    }
}
