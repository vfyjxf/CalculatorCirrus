package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tiered weighted score model. Candidates are ranked by comparing tiers
 * in order (lexicographic, lower is better): a lower tier <b>absolutely</b>
 * dominates every higher tier, and within a tier the score is the weighted
 * sum of its components:
 *
 * <pre>{@code
 * tierScore(t)  = Σ component.weight × value(component)
 * rank(c1, c2)  = lexicographic over [tierScore(0), tierScore(1), ...]
 * }</pre>
 *
 * <p>Component values come from two sources:
 * <ul>
 *     <li><b>feature components</b> — {@code feature != null}: the value is
 *         the named {@link FeatureType} of the candidate (see
 *         {@link Compiled#evaluate(FeatureVector, double[])});</li>
 *     <li><b>rule-fed components</b> — {@code feature == null}: the baseline
 *         is 0 and candidate-stage {@code penalize}/{@code boost} actions
 *         write into them (see {@link Compiled#applyAdjustment}).</li>
 * </ul>
 *
 * <p>This replaces both the old five-axis lexicographic vector and the
 * magnitude-tuned magic penalties: "cycles dominate cost" is now declared by
 * putting cycle components in a lower tier, not by adding {@code 1_000_000}
 * to a shared axis.
 *
 * <pre>{@code
 * "score_model": {
 *   "tiers": [
 *     { "name": "safety", "components": [
 *         { "name": "cycle", "feature": "activeDemandCycle" } ] },
 *     { "name": "cost", "components": [
 *         { "name": "subtreeCost", "feature": "subtreeCost" },
 *         { "name": "advisoryExtra", "feature": "advisoryCostExcess" } ] },
 *     { "name": "quality", "components": [
 *         { "name": "craft", "feature": "craftTimes", "weight": 1.0 },
 *         { "name": "depth", "feature": "depth", "weight": 0.8 } ] }
 *   ],
 *   "generation_cost_multiplier": 1000.0,
 *   "soft_advisory_multiplier": 4.0
 * }
 * }</pre>
 *
 * <p>The two advisory multipliers configure the cost oracle's treatment of
 * generation/soft-advisory recipes: the planner evaluates the oracle once
 * without them (base cost) and once with them (full cost), exposing the
 * difference as the {@code advisoryCostExcess} feature so the cost tier stays
 * purely additive while the historical {@code ×1000}/{@code ×4} semantics are
 * preserved exactly.
 */
public record ScoreModel(List<Tier> tiers, double generationCostMultiplier, double softAdvisoryMultiplier) {

    public static final double defaultGenerationCostMultiplier = 1_000.0d;
    public static final double defaultSoftAdvisoryMultiplier = 4.0d;

    /** Convenience constructor with the default advisory multipliers. */
    public ScoreModel(List<Tier> tiers) {
        this(tiers, defaultGenerationCostMultiplier, defaultSoftAdvisoryMultiplier);
    }

    public static final Codec<ScoreModel> codec = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.list(Tier.codec).fieldOf("tiers").forGetter(ScoreModel::tiers),
            Codec.DOUBLE.optionalFieldOf("generation_cost_multiplier", defaultGenerationCostMultiplier)
                    .forGetter(ScoreModel::generationCostMultiplier),
            Codec.DOUBLE.optionalFieldOf("soft_advisory_multiplier", defaultSoftAdvisoryMultiplier)
                    .forGetter(ScoreModel::softAdvisoryMultiplier)
        ).apply(instance, ScoreModel::new)
    );

    /**
     * One tier: a named, ordered comparison slot.
     *
     * @param name       tier name (diagnostics only)
     * @param components weighted components summed into this tier
     */
    public record Tier(String name, List<Component> components) {

        public static final Codec<Tier> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Tier::name),
                Codec.list(Component.codec).fieldOf("components").forGetter(Tier::components)
            ).apply(instance, Tier::new)
        );

        public Tier {
            name = name == null ? "" : name;
            components = List.copyOf(components == null ? List.of() : components);
        }
    }

    /**
     * One weighted component inside a tier.
     *
     * @param name    unique component name; candidate-stage score actions
     *                reference it
     * @param feature feature feeding the baseline value; empty for a
     *                rule-fed-only component
     * @param weight  multiplier applied to every contribution (default 1.0)
     */
    public record Component(String name, Optional<String> feature, double weight) {

        public static final Codec<Component> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Component::name),
                Codec.STRING.optionalFieldOf("feature").forGetter(Component::feature),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0d).forGetter(Component::weight)
            ).apply(instance, Component::new)
        );

        public Component {
            Checks.checkNotNull(name, "name");
            name = name.trim();
            Checks.checkArgument(!name.isEmpty(), "component name must not be blank");
            feature = feature == null ? Optional.empty() : feature.filter(value -> !value.isBlank());
            Checks.checkArgument(Double.isFinite(weight), "weight must be finite");
        }
    }

    public ScoreModel {
        tiers = List.copyOf(tiers == null ? List.of() : tiers);
        Checks.checkArgument(generationCostMultiplier >= 1.0d, "generationCostMultiplier must be >= 1");
        Checks.checkArgument(softAdvisoryMultiplier >= 1.0d, "softAdvisoryMultiplier must be >= 1");
    }

    /** Validation problems (empty list = valid). */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (tiers.isEmpty()) {
            problems.add("score model has no tiers");
            return problems;
        }
        Set<String> componentNames = new HashSet<>();
        for (int tierIndex = 0; tierIndex < tiers.size(); tierIndex++) {
            Tier tier = tiers.get(tierIndex);
            if (tier.components().isEmpty()) {
                problems.add("tier '" + tier.name() + "' has no components");
            }
            for (Component component : tier.components()) {
                if (!componentNames.add(component.name())) {
                    problems.add("duplicate component name '" + component.name() + "'");
                }
                if (component.feature().isPresent()) {
                    try {
                        FeatureType.valueOf(component.feature().get());
                    } catch (IllegalArgumentException ignored) {
                        problems.add(
                            "component '" + component.name() + "' references unknown feature '"
                                    + component.feature().get() + "'"
                        );
                    }
                }
            }
        }
        return problems;
    }

    public Set<String> componentNames() {
        Set<String> names = new HashSet<>();
        for (Tier tier : tiers) {
            for (Component component : tier.components()) {
                names.add(component.name());
            }
        }
        return names;
    }

    /** Compiles the model into its flat evaluation form. */
    public Compiled compile() {
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid score model: " + String.join("; ", problems));
        }
        return new Compiled(this);
    }

    /** Flat, allocation-free evaluation form of a {@link ScoreModel}. */
    public static final class Compiled {

        private final ScoreModel model;
        private final int tierCount;
        /** Flat per-component slots: [tier, weight, featureOrdinal or -1]. */
        private final int[] componentTier;
        private final double[] componentWeight;
        private final int[] componentFeature;
        private final Map<String, Integer> componentIndex;
        /** Per tier: [start, end) range over component slots. */
        private final int[] tierStart;
        private final int[] tierEnd;

        private Compiled(ScoreModel model) {
            this.model = model;
            this.tierCount = model.tiers().size();
            int total = 0;
            for (Tier tier : model.tiers()) {
                total += tier.components().size();
            }
            this.componentTier = new int[total];
            this.componentWeight = new double[total];
            this.componentFeature = new int[total];
            this.componentIndex = new HashMap<>();
            this.tierStart = new int[tierCount];
            this.tierEnd = new int[tierCount];
            int slot = 0;
            for (int tierIndex = 0; tierIndex < tierCount; tierIndex++) {
                tierStart[tierIndex] = slot;
                for (Component component : model.tiers().get(tierIndex).components()) {
                    componentTier[slot] = tierIndex;
                    componentWeight[slot] = component.weight();
                    componentFeature[slot] = component.feature().map(feature -> FeatureType.valueOf(feature).ordinal())
                            .orElse(-1);
                    componentIndex.put(component.name(), slot);
                    slot++;
                }
                tierEnd[tierIndex] = slot;
            }
        }

        public ScoreModel model() {
            return model;
        }

        public int tierCount() {
            return tierCount;
        }

        public boolean hasComponent(String name) {
            return componentIndex.containsKey(name);
        }

        /**
         * Writes the feature-driven baseline into {@code tierSums} (length ≥
         * tier count). Rule-fed components stay at their zero baseline.
         */
        public void evaluate(FeatureVector features, double[] tierSums) {
            for (int slot = 0; slot < componentTier.length; slot++) {
                int featureOrdinal = componentFeature[slot];
                if (featureOrdinal >= 0) {
                    tierSums[componentTier[slot]] += componentWeight[slot] * features.valueAt(featureOrdinal);
                }
            }
        }

        /**
         * Applies one rule adjustment ({@code penalize}/{@code boost}) into
         * the tier sums. Unknown component names throw — the compile step
         * validates rule actions, so this only fires on internal wiring bugs.
         */
        public void applyAdjustment(double[] tierSums, String component, double signedAmount) {
            Integer slot = componentIndex.get(component);
            if (slot == null) {
                throw new IllegalArgumentException(
                    "score component '" + component + "' is not declared in the score model"
                );
            }
            tierSums[componentTier[slot]] += componentWeight[slot] * signedAmount;
        }

        /** Human-readable breakdown of the tier sums (diagnostics/traces). */
        public String describe(double[] tierSums, FeatureVector features) {
            StringBuilder out = new StringBuilder();
            for (int tierIndex = 0; tierIndex < tierCount; tierIndex++) {
                if (tierIndex > 0) {
                    out.append(" | ");
                }
                out.append(model.tiers().get(tierIndex).name()).append('=').append(tierSums[tierIndex]);
                for (int slot = tierStart[tierIndex]; slot < tierEnd[tierIndex]; slot++) {
                    int featureOrdinal = componentFeature[slot];
                    double value = featureOrdinal >= 0 ? features.valueAt(featureOrdinal) : Double.NaN;
                    out.append(' ')
                            .append(model.tiers().get(tierIndex).components().get(slot - tierStart[tierIndex]).name())
                            .append('=').append(Double.isNaN(value) ? "rule" : value).append("×")
                            .append(componentWeight[slot]);
                }
            }
            return out.toString();
        }
    }
}
