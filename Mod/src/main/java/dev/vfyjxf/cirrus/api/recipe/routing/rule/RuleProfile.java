package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.rule.FeatureType;
import dev.vfyjxf.cirrus.api.recipe.rule.FeatureVector;
import dev.vfyjxf.cirrus.api.recipe.rule.ScoreModel;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Complete, aggregated configuration for the candidate evaluation pipeline.
 *
 * <p>A {@code RuleProfile} bundles all the components that control how
 * candidates are filtered, scored, ranked, and explored:
 * <ol>
 *     <li><b>Hard rules</b> — binary accept/reject filters
 *         ({@link #hardRules()}).</li>
 *     <li><b>Score model</b> — the tiered weighted scoring function
 *         ({@link #scoreModel()}); base values come from per-candidate
 *         {@link FeatureType features}, rule adjustments from
 *         {@link ScoreRule}s.</li>
 *     <li><b>Rank policy</b> — tier-vector comparator, lexicographic over
 *         tiers by default ({@link #rankPolicy()}).</li>
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
    private final ScoreModel.Compiled scoreModel;
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
    private final boolean fastPathEligible;
    private final int costTierIndex;

    /**
     * Creates a new rule profile with all components and tuning parameters.
     *
     * @param profileId                   unique identifier for this profile
     * @param scoreModel                  the tiered weighted score model
     * @param hardRules                   binary accept/reject filters
     * @param scoreRules                  rule-fed score adjustments; base
     *                                    feature values come from the model
     * @param rankPolicy                  tier-vector comparator
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
     * @param fastPathEligible            whether the planner may skip per-candidate
     *                                    rule evaluation (true only when the
     *                                    profile carries no user clauses)
     */
    public RuleProfile(
        Namespace profileId,
        ScoreModel scoreModel,
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
        boolean fastPathEligible
    ) {
        this.profileId = Checks.checkNotNull(profileId, "profileId");
        this.scoreModel = Checks.checkNotNull(scoreModel, "scoreModel").compile();
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
        this.fastPathEligible = fastPathEligible;
        this.costTierIndex = resolveCostTier(this.scoreModel);
    }

    /** The tier carrying the {@code subtreeCost} feature (fallback: last tier). */
    private static int resolveCostTier(ScoreModel.Compiled model) {
        List<ScoreModel.Tier> tiers = model.model().tiers();
        for (int tierIndex = 0; tierIndex < tiers.size(); tierIndex++) {
            for (ScoreModel.Component component : tiers.get(tierIndex).components()) {
                if (component.feature().map(FeatureType.subtreeCost.name()::equals).orElse(false)) {
                    return tierIndex;
                }
            }
        }
        return tiers.size() - 1;
    }

    /** Returns the unique identifier of this profile. */
    public Namespace profileId() {
        return profileId;
    }

    /** Returns the compiled tiered score model. */
    public ScoreModel.Compiled scoreModel() {
        return scoreModel;
    }

    /** Returns the serialized score model. */
    public ScoreModel model() {
        return scoreModel.model();
    }

    /** Returns the ordered tier names of the score model. */
    public List<String> tierNames() {
        return scoreModel.model().tiers().stream().map(ScoreModel.Tier::name).toList();
    }

    /** Returns the list of hard (accept/reject) rules. */
    public ImmutableList<HardRule> hardRules() {
        return hardRules;
    }

    /** Returns the list of rule-fed score adjustment rules. */
    public ImmutableList<ScoreRule> scoreRules() {
        return scoreRules;
    }

    /** Returns the tier-vector comparator. */
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

    /**
     * Whether the planner may evaluate this profile through the fast path
     * (feature evaluation only, no per-candidate rule objects). True only
     * when the profile has no user clauses attached.
     */
    public boolean fastPathEligible() {
        return fastPathEligible;
    }

    /**
     * The index of the tier carrying the subtree-cost feature — the "leading
     * cost axis" for near-tie/exploration heuristics. Tiers before it
     * (safety flags) must tie exactly for two candidates to be comparable.
     */
    public int costTierIndex() {
        return costTierIndex;
    }

    /**
     * Allocates a fresh zero-initialized tier-sum vector.
     *
     * @return a new {@code double[]} sized to the model's tier count
     */
    public double[] newScoreVector() {
        return new double[scoreModel.tierCount()];
    }

    /**
     * Creates a {@link ScoreAccumulator} backed by the given tier-sum vector.
     *
     * @param scoreVector the vector to accumulate score adjustments into
     * @return a new accumulator for this profile's score components
     */
    public ScoreAccumulator newAccumulator(double[] scoreVector) {
        return new ScoreAccumulator(scoreVector, scoreModel);
    }

    /**
     * Evaluates the feature-driven baseline into the given tier-sum vector.
     * Equivalent to {@code scoreModel().evaluate(features, tierSums)}.
     */
    public void evaluateFeatures(FeatureVector features, double[] tierSums) {
        scoreModel.evaluate(features, tierSums);
    }

    /**
     * Validates that every component name used by rule-fed adjustments is
     * declared in the score model; returns an error list (empty = ok).
     */
    public List<String> validateComponents(Map<String, ? extends List<String>> ruleIdToComponents) {
        java.util.ArrayList<String> problems = new java.util.ArrayList<>();
        ruleIdToComponents.forEach((ruleId, components) -> {
            for (String component : components) {
                if (!scoreModel.hasComponent(component)) {
                    problems.add("rule " + ruleId + " references undeclared score component '" + component + "'");
                }
            }
        });
        return problems;
    }
}
