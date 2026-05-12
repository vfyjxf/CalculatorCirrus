package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Complete, aggregated configuration for the candidate evaluation pipeline.
 *
 * <p>A {@code RuleProfile} bundles all the components that control how
 * candidates are filtered, scored, ranked, and explored:
 * <ol>
 *     <li><b>Hard rules</b> — binary accept/reject filters
 *         ({@link #hardRules()}).</li>
 *     <li><b>Score rules</b> — dimension contributors
 *         ({@link #scoreRules()}).</li>
 *     <li><b>Rank policy</b> — score-vector comparator
 *         ({@link #rankPolicy()}).</li>
 *     <li><b>Tiebreak policy</b> — deterministic secondary ordering
 *         ({@link #tieBreakPolicy()}).</li>
 *     <li><b>Explore policy</b> — optional near-tie perturbation
 *         ({@link #explorePolicy()}).</li>
 *     <li><b>Unresolved-input rule</b> — terminal handling for inputs that
 *         still cannot be satisfied after recursive expansion
 *         ({@link #unresolvedInputRule()}).</li>
 * </ol>
 *
 * <p>Additionally, tuning parameters control planner behavior:
 * <ul>
 *     <li>{@link #shortlistK()} — maximum candidates to evaluate per demand.</li>
 *     <li>{@link #rollbackWidth()} — how many alternatives to try on failure.</li>
 *     <li>{@link #nearTieEpsilon()} — epsilon for near-tie detection.</li>
 *     <li>{@link #amplificationSoftThreshold()} and
 *         {@link #amplificationHardThreshold()} — cycle-amplification
 *         thresholds.</li>
 * </ul>
 *
 * <p>The {@link #capabilities()} declare which mathematical properties the
 * profile satisfies, enabling planners to select appropriate algorithms.
 *
 * @see DefaultRuleProfiles
 * @see RuleCapabilities
 */
public final class RuleProfile {

    private final Namespace profileId;
    private final ImmutableList<String> scoreKeys;
    private final Map<String, Integer> scoreKeyToIndex;
    private final ImmutableList<HardRule> hardRules;
    private final ImmutableList<ScoreRule> scoreRules;
    private final RankPolicy rankPolicy;
    private final TieBreakPolicy tieBreakPolicy;
    private final ExplorePolicy explorePolicy;
    private final UnresolvedInputRule unresolvedInputRule;
    private final RuleCapabilities capabilities;
    private final int shortlistK;
    private final int rollbackWidth;
    private final double nearTieEpsilon;
    private final double amplificationSoftThreshold;
    private final double amplificationHardThreshold;
    private final boolean liftMultiSemanticChildShortages;
    private final @Nullable FastScoringPreset fastScoringPreset;

    /**
     * Creates a new rule profile with all components and tuning parameters.
     *
     * @param profileId                   unique identifier for this profile
     * @param scoreKeys                   ordered list of score dimension names
     * @param hardRules                   binary accept/reject filters
     * @param scoreRules                  score dimension contributors
     * @param rankPolicy                  score-vector comparator
     * @param tieBreakPolicy              deterministic secondary ordering
     * @param explorePolicy               optional near-tie perturbation
     * @param unresolvedInputRule         terminal handling for unresolved inputs
     * @param capabilities                declared mathematical properties
     * @param shortlistK                  maximum candidates per demand (must be positive)
     * @param rollbackWidth               alternatives to try on failure (non-negative)
     * @param nearTieEpsilon              epsilon for near-tie detection (non-negative)
     * @param amplificationSoftThreshold  soft threshold for cycle amplification risk
     * @param amplificationHardThreshold  hard threshold for cycle amplification risk
     * @param liftMultiSemanticChildShortages whether child unresolved-stock warnings should be lifted to tag/list parents
     */
    public RuleProfile(
        Namespace profileId,
        ImmutableList<String> scoreKeys,
        ImmutableList<HardRule> hardRules,
        ImmutableList<ScoreRule> scoreRules,
        RankPolicy rankPolicy,
        TieBreakPolicy tieBreakPolicy,
        ExplorePolicy explorePolicy,
        UnresolvedInputRule unresolvedInputRule,
        RuleCapabilities capabilities,
        int shortlistK,
        int rollbackWidth,
        double nearTieEpsilon,
        double amplificationSoftThreshold,
        double amplificationHardThreshold,
        boolean liftMultiSemanticChildShortages
    ) {
        this(
            profileId,
            scoreKeys,
            hardRules,
            scoreRules,
            rankPolicy,
            tieBreakPolicy,
            explorePolicy,
            unresolvedInputRule,
            capabilities,
            shortlistK,
            rollbackWidth,
            nearTieEpsilon,
            amplificationSoftThreshold,
            amplificationHardThreshold,
            liftMultiSemanticChildShortages,
            null
        );
    }

    public RuleProfile(
        Namespace profileId,
        ImmutableList<String> scoreKeys,
        ImmutableList<HardRule> hardRules,
        ImmutableList<ScoreRule> scoreRules,
        RankPolicy rankPolicy,
        TieBreakPolicy tieBreakPolicy,
        ExplorePolicy explorePolicy,
        UnresolvedInputRule unresolvedInputRule,
        RuleCapabilities capabilities,
        int shortlistK,
        int rollbackWidth,
        double nearTieEpsilon,
        double amplificationSoftThreshold,
        double amplificationHardThreshold,
        boolean liftMultiSemanticChildShortages,
        @Nullable FastScoringPreset fastScoringPreset
    ) {
        this.profileId = Checks.checkNotNull(profileId, "profileId");
        this.scoreKeys = Checks.checkNotNull(scoreKeys, "scoreKeys");
        this.hardRules = Checks.checkNotNull(hardRules, "hardRules");
        this.scoreRules = Checks.checkNotNull(scoreRules, "scoreRules");
        this.rankPolicy = Checks.checkNotNull(rankPolicy, "rankPolicy");
        this.tieBreakPolicy = Checks.checkNotNull(tieBreakPolicy, "tieBreakPolicy");
        this.explorePolicy = Checks.checkNotNull(explorePolicy, "explorePolicy");
        this.unresolvedInputRule = Checks.checkNotNull(unresolvedInputRule, "unresolvedInputRule");
        this.capabilities = Checks.checkNotNull(capabilities, "capabilities");
        Checks.checkArgument(shortlistK > 0, "shortlistK must be positive");
        Checks.checkArgument(rollbackWidth >= 0, "rollbackWidth must be non-negative");
        Checks.checkArgument(nearTieEpsilon >= 0, "nearTieEpsilon must be non-negative");
        Checks.checkArgument(amplificationSoftThreshold >= 0, "amplificationSoftThreshold must be non-negative");
        Checks.checkArgument(amplificationHardThreshold >= 0, "amplificationHardThreshold must be non-negative");
        this.shortlistK = shortlistK;
        this.rollbackWidth = rollbackWidth;
        this.nearTieEpsilon = nearTieEpsilon;
        this.amplificationSoftThreshold = amplificationSoftThreshold;
        this.amplificationHardThreshold = amplificationHardThreshold;
        this.liftMultiSemanticChildShortages = liftMultiSemanticChildShortages;
        this.fastScoringPreset = fastScoringPreset;

        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < scoreKeys.size(); i++) {
            map.put(scoreKeys.get(i), i);
        }
        this.scoreKeyToIndex = map;
    }

    /** Returns the unique identifier of this profile. */
    public Namespace profileId() {
        return profileId;
    }

    /** Returns the ordered list of score dimension names. */
    public ImmutableList<String> scoreKeys() {
        return scoreKeys;
    }

    /** Returns the list of hard (accept/reject) rules. */
    public ImmutableList<HardRule> hardRules() {
        return hardRules;
    }

    /** Returns the list of score contribution rules. */
    public ImmutableList<ScoreRule> scoreRules() {
        return scoreRules;
    }

    /** Returns the score-vector comparator. */
    public RankPolicy rankPolicy() {
        return rankPolicy;
    }

    /** Returns the deterministic tiebreak comparator. */
    public TieBreakPolicy tieBreakPolicy() {
        return tieBreakPolicy;
    }

    /** Returns the optional near-tie exploration hook. */
    public ExplorePolicy explorePolicy() {
        return explorePolicy;
    }

    /** Returns the terminal rule for unresolved inputs. */
    public UnresolvedInputRule unresolvedInputRule() {
        return unresolvedInputRule;
    }

    /** Returns the declared mathematical capability flags. */
    public RuleCapabilities capabilities() {
        return capabilities;
    }

    /** Returns the maximum number of candidates to evaluate per demand. */
    public int shortlistK() {
        return shortlistK;
    }

    /** Returns how many alternatives the planner tries on failure (rollback). */
    public int rollbackWidth() {
        return rollbackWidth;
    }

    /** Returns the epsilon for near-tie detection. */
    public double nearTieEpsilon() {
        return nearTieEpsilon;
    }

    /** Returns the soft threshold for cycle-amplification risk warnings. */
    public double amplificationSoftThreshold() {
        return amplificationSoftThreshold;
    }

    /** Returns the hard threshold for cycle-amplification risk rejection. */
    public double amplificationHardThreshold() {
        return amplificationHardThreshold;
    }

    public boolean liftMultiSemanticChildShortages() {
        return liftMultiSemanticChildShortages;
    }

    public @Nullable FastScoringPreset fastScoringPreset() {
        return fastScoringPreset;
    }

    /**
     * Allocates a fresh zero-initialized score vector with the correct dimension count.
     *
     * @return a new {@code double[]} sized to match {@link #scoreKeys()}
     */
    public double[] newScoreVector() {
        return new double[scoreKeys.size()];
    }

    /**
     * Creates a {@link ScoreAccumulator} backed by the given score vector.
     *
     * @param scoreVector the vector to accumulate scores into
     * @return a new accumulator for this profile's score dimensions
     */
    public ScoreAccumulator newAccumulator(double[] scoreVector) {
        return new ScoreAccumulator(scoreVector, scoreKeyToIndex);
    }

    public enum FastScoringPreset {
        standard,
        assuredBounded
    }
}
