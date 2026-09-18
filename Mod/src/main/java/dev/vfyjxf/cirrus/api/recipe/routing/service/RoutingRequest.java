package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.HeuristicPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Immutable request describing all parameters for one routing run.
 *
 * <p>The constructor receives fully resolved defaults so planners operate on a
 * frozen, self-contained request view without needing to look up configuration
 * at planning time.
 *
 * <p>Use {@link Builder} (via {@link #builder()}) for ergonomic construction.
 * The builder applies sensible defaults derived from the selected
 * {@link PlannerMode} and {@link InventorySensitivityProfile}.
 *
 * @param plannerMode                the planning algorithm to use
 * @param ruleProfileId              ID of the rule profile governing candidate evaluation
 * @param sourcePolicyId             ID of the source policy for pre-filtering recipes
 * @param diagnosticLevel            verbosity of diagnostic data collection
 * @param deterministic              whether the planner should produce reproducible results
 * @param allowModeFallback          whether to fall back from assuredProven to assuredBounded
 *                                   if capability gates fail
 * @param budget                     runtime budget limits
 * @param stock                      available inventory resources
 * @param tagSnapshot                optional pre-frozen tag snapshot; if {@code null}, the
 *                                   service takes a fresh snapshot
 * @param target                     what to route — either a specific recipe or an output resource
 * @param inventorySensitivityProfile how strongly to bias toward stock usage
 * @param tagResolvePolicy           explicit tag resolve policy; {@code null} to derive from
 *                                   inventory sensitivity
 * @param effectiveTagResolvePolicy  the resolved tag resolve policy actually used
 * @param adapterSelectionPolicy     output-target adapter selection strategy
 * @param maxProducerCandidates      maximum producer candidates for output-target adapter
 * @param loopPolicy                 how to handle cyclic dependencies
 * @param loopBudget                 budget limits for cyclic expansion
 * @param explorationEnabled         whether non-deterministic search diversity is enabled
 * @param explorationPolicyId        optional exploration policy ID
 * @param explorationSeed            optional seed for exploration randomization
 * @param explorationBudgetShare     fraction of budget reserved for exploration
 * @see RoutingService#route(RoutingRequest)
 */
public record RoutingRequest(
    PlannerMode plannerMode,
    Namespace ruleProfileId,
    Namespace sourcePolicyId,
    DiagnosticLevel diagnosticLevel,
    boolean deterministic,
    boolean allowModeFallback,
    RoutingBudget budget,
    Stock stock,
    @Nullable TagSnapshot tagSnapshot,
    Target target,
    InventorySensitivityProfile inventorySensitivityProfile,
    @Nullable TagResolvePolicy tagResolvePolicy,
    TagResolvePolicy effectiveTagResolvePolicy,
    Map<String, String> preferredVariants,
    OutputSelectionPolicy adapterSelectionPolicy,
    int maxProducerCandidates,
    LoopPolicy loopPolicy,
    LoopBudget loopBudget,
    boolean explorationEnabled,
    @Nullable Namespace explorationPolicyId,
    @Nullable Long explorationSeed,
    double explorationBudgetShare,
    HeuristicPolicy heuristicPolicy
) {
    public RoutingRequest {
        Checks.checkNotNull(plannerMode, "plannerMode");
        Checks.checkNotNull(ruleProfileId, "ruleProfileId");
        Checks.checkNotNull(sourcePolicyId, "sourcePolicyId");
        Checks.checkNotNull(diagnosticLevel, "diagnosticLevel");
        Checks.checkNotNull(budget, "budget");
        Checks.checkNotNull(stock, "stock");
        Checks.checkNotNull(target, "target");
        Checks.checkNotNull(inventorySensitivityProfile, "inventorySensitivityProfile");
        Checks.checkNotNull(effectiveTagResolvePolicy, "effectiveTagResolvePolicy");
        Checks.checkNotNull(preferredVariants, "preferredVariants");
        preferredVariants = Map.copyOf(preferredVariants);
        Checks.checkNotNull(adapterSelectionPolicy, "adapterSelectionPolicy");
        Checks.checkNotNull(loopPolicy, "loopPolicy");
        Checks.checkNotNull(loopBudget, "loopBudget");
        Checks.checkArgument(maxProducerCandidates > 0, "maxProducerCandidates must be positive");
        Checks.checkArgument(explorationBudgetShare >= 0 && explorationBudgetShare <= 1, "explorationBudgetShare must be in [0,1]");
        Checks.checkNotNull(heuristicPolicy, "heuristicPolicy");
    }

    /**
     * Sealed target type — either a specific recipe or an output resource.
     */
    public sealed interface Target permits RecipeTarget, OutputTarget {
    }

    /**
     * Route by specifying a target recipe and the number of times to craft it.
     *
     * @param recipe the recipe to route for
     * @param times  how many times to execute the recipe
     */
    public record RecipeTarget(RecipeEntry<?> recipe, int times) implements Target {
        public RecipeTarget {
            Checks.checkNotNull(recipe, "recipe");
            Checks.checkArgument(times > 0, "times must be positive");
        }
    }

    /**
     * Route by specifying a desired output resource and amount.
     *
     * <p>The routing service will automatically search for producer recipes
     * and select the best one via the output-target adapter.
     *
     * @param output the desired output resource
     * @param amount the desired amount
     */
    public record OutputTarget(ContentResource output, long amount) implements Target {
        public OutputTarget {
            Checks.checkNotNull(output, "output");
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }

    /** Creates a new request builder with sensible defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing {@link RoutingRequest} instances.
     *
     * <p>Unset fields are resolved to sensible defaults derived from the
     * selected {@link PlannerMode} and {@link InventorySensitivityProfile}
     * when {@link #build()} is called.
     */
    public static final class Builder {
        private PlannerMode plannerMode = PlannerMode.standard;
        private Namespace ruleProfileId;
        private Namespace sourcePolicyId = SourcePolicy.defaultPolicyId;
        private DiagnosticLevel diagnosticLevel = DiagnosticLevel.none;
        private boolean deterministic = true;
        private boolean allowModeFallback = false;
        private RoutingBudget budget;
        private Stock stock = Stock.empty();
        private TagSnapshot tagSnapshot;
        private Target target;
        private InventorySensitivityProfile inventorySensitivityProfile = InventorySensitivityProfile.balanced;
        private TagResolvePolicy tagResolvePolicy;
        private Map<String, String> preferredVariants = Map.of();
        private OutputSelectionPolicy adapterSelectionPolicy = OutputSelectionPolicy.bestEffortSingleRecipe;
        private Integer maxProducerCandidates;
        private LoopPolicy loopPolicy = LoopPolicy.forbidAmplification;
        private LoopBudget loopBudget;
        private Boolean explorationEnabled;
        private Namespace explorationPolicyId;
        private Long explorationSeed;
        private Double explorationBudgetShare;
        private HeuristicPolicy heuristicPolicy = HeuristicPolicy.defaultSafe;

        private Builder() {
        }

        public Builder plannerMode(PlannerMode plannerMode) {
            this.plannerMode = plannerMode;
            return this;
        }

        public Builder ruleProfileId(Namespace ruleProfileId) {
            this.ruleProfileId = ruleProfileId;
            return this;
        }

        public Builder sourcePolicyId(Namespace sourcePolicyId) {
            this.sourcePolicyId = sourcePolicyId;
            return this;
        }

        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;
            return this;
        }

        public Builder deterministic(boolean deterministic) {
            this.deterministic = deterministic;
            return this;
        }

        public Builder allowModeFallback(boolean allowModeFallback) {
            this.allowModeFallback = allowModeFallback;
            return this;
        }

        public Builder budget(RoutingBudget budget) {
            this.budget = budget;
            return this;
        }

        public Builder stock(Stock stock) {
            this.stock = stock;
            return this;
        }

        public Builder tagSnapshot(TagSnapshot tagSnapshot) {
            this.tagSnapshot = tagSnapshot;
            return this;
        }

        public Builder target(Target target) {
            this.target = target;
            return this;
        }

        public Builder inventorySensitivityProfile(InventorySensitivityProfile inventorySensitivityProfile) {
            this.inventorySensitivityProfile = inventorySensitivityProfile;
            return this;
        }

        public Builder tagResolvePolicy(TagResolvePolicy tagResolvePolicy) {
            this.tagResolvePolicy = tagResolvePolicy;
            return this;
        }

        public Builder preferredVariants(Map<String, String> preferredVariants) {
            this.preferredVariants = Map.copyOf(preferredVariants);
            return this;
        }

        public Builder preferVariant(InputContent input, String variantKey) {
            Checks.checkNotNull(input, "input");
            Checks.checkNotNull(variantKey, "variantKey");
            java.util.LinkedHashMap<String, String> copy = new java.util.LinkedHashMap<>(preferredVariants);
            copy.put(RoutingVariantKeys.inputKey(input), variantKey);
            this.preferredVariants = Map.copyOf(copy);
            return this;
        }

        public Builder targetRecipe(RecipeEntry<?> recipe, int times) {
            this.target = new RecipeTarget(recipe, times);
            return this;
        }

        public Builder targetOutput(ContentResource output, long amount) {
            this.target = new OutputTarget(output, amount);
            return this;
        }

        public Builder adapterSelectionPolicy(OutputSelectionPolicy adapterSelectionPolicy) {
            this.adapterSelectionPolicy = adapterSelectionPolicy;
            return this;
        }

        public Builder maxProducerCandidates(int maxProducerCandidates) {
            this.maxProducerCandidates = maxProducerCandidates;
            return this;
        }

        public Builder loopPolicy(LoopPolicy loopPolicy) {
            this.loopPolicy = loopPolicy;
            return this;
        }

        public Builder loopBudget(LoopBudget loopBudget) {
            this.loopBudget = loopBudget;
            return this;
        }

        public Builder explorationEnabled(boolean explorationEnabled) {
            this.explorationEnabled = explorationEnabled;
            return this;
        }

        public Builder explorationPolicyId(Namespace explorationPolicyId) {
            this.explorationPolicyId = explorationPolicyId;
            return this;
        }

        public Builder explorationSeed(Long explorationSeed) {
            this.explorationSeed = explorationSeed;
            return this;
        }

        public Builder explorationBudgetShare(double explorationBudgetShare) {
            this.explorationBudgetShare = explorationBudgetShare;
            return this;
        }

        public Builder heuristicPolicy(HeuristicPolicy heuristicPolicy) {
            this.heuristicPolicy = heuristicPolicy;
            return this;
        }

        public RoutingRequest build() {
            Checks.checkNotNull(target, "target");
            Namespace resolvedRuleProfile = ruleProfileId != null ? ruleProfileId : switch (plannerMode) {
                case standard -> DefaultRuleProfiles.standardDefaultId;
                case auto, assuredBounded -> DefaultRuleProfiles.assuredBoundedDefaultId;
                case assuredProven -> DefaultRuleProfiles.assuredProvenCompatId;
                case costAware -> DefaultRuleProfiles.standardDefaultId;
            };
            RoutingBudget resolvedBudget = budget != null ? budget : switch (plannerMode) {
                case standard -> RoutingBudget.standardDefault();
                case auto, assuredBounded, assuredProven -> RoutingBudget.assuredDefault();
                case costAware -> RoutingBudget.assuredDefault();
            };
            LoopBudget resolvedLoopBudget = loopBudget != null ? loopBudget : resolvedBudget.loopBudget();
            InventorySensitivityProfile resolvedInventoryProfile = inventorySensitivityProfile != null
                ? inventorySensitivityProfile
                : InventorySensitivityProfile.balanced;
            TagResolvePolicy resolvedTagResolvePolicy = tagResolvePolicy != null
                ? tagResolvePolicy
                : TagResolvePolicy.fromInventorySensitivity(resolvedInventoryProfile);
            int resolvedMaxProducerCandidates = maxProducerCandidates != null
                ? maxProducerCandidates
                : defaultMaxProducerCandidates(resolvedInventoryProfile);
            boolean resolvedExplorationEnabled = explorationEnabled != null
                ? explorationEnabled
                : defaultExplorationEnabled(resolvedInventoryProfile);
            double resolvedExplorationShare = explorationBudgetShare != null
                ? explorationBudgetShare
                : defaultExplorationBudgetShare(resolvedInventoryProfile);
            return new RoutingRequest(
                plannerMode,
                resolvedRuleProfile,
                sourcePolicyId,
                diagnosticLevel,
                deterministic,
                allowModeFallback,
                resolvedBudget,
                stock,
                tagSnapshot,
                target,
                resolvedInventoryProfile,
                tagResolvePolicy,
                resolvedTagResolvePolicy,
                preferredVariants,
                adapterSelectionPolicy,
                resolvedMaxProducerCandidates,
                loopPolicy,
                resolvedLoopBudget,
                resolvedExplorationEnabled,
                explorationPolicyId,
                explorationSeed,
                resolvedExplorationShare,
                heuristicPolicy
            );
        }

        private static int defaultMaxProducerCandidates(InventorySensitivityProfile profile) {
            return switch (profile) {
                case high -> 6;
                case balanced -> 8;
                case low -> 12;
            };
        }

        private static boolean defaultExplorationEnabled(InventorySensitivityProfile profile) {
            return switch (profile) {
                case high, balanced -> false;
                case low -> true;
            };
        }

        private static double defaultExplorationBudgetShare(InventorySensitivityProfile profile) {
            return switch (profile) {
                case high -> 0.03;
                case balanced -> 0.08;
                case low -> 0.15;
            };
        }
    }
}
