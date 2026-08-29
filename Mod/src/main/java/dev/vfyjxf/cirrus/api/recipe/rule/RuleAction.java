package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * One action executed when a rule's condition matches. Actions split into
 * catalog-stage ({@link Tag}, {@link Exclude}, {@link Include}, {@link Hint})
 * and candidate-stage ({@link ScoreAdjust}, {@link Reject}) kinds; a rule may
 * only mix actions of its own stage (validated by {@link RuleEngine#compile}).
 *
 * <p>Serialized as a {@code "do"} dispatch key:
 * <pre>{@code
 * { "do": "tag",     "tag": "cirrus:quality/junk" }
 * { "do": "exclude" }
 * { "do": "include" }
 * { "do": "hint",    "policy": "discouraged" }
 * { "do": "penalize","component": "contextual", "amount": 5000.0, "scale": "coverage_shortage" }
 * { "do": "boost",   "component": "craft", "amount": 2.0 }
 * { "do": "reject",  "reason": "probabilistic output" }
 * }</pre>
 */
public sealed interface RuleAction permits
    RuleAction.Tag,
    RuleAction.Exclude,
    RuleAction.Include,
    RuleAction.Hint,
    RuleAction.ScoreAdjust,
    RuleAction.Reject {

    Codec<RuleAction> CODEC = Codec.STRING.dispatch(
        "do",
        RuleAction::codecKind,
        RuleAction::codecForType
    );

    /** The {@code "do"} dispatch key. */
    default String codecKind() {
        return switch (this) {
            case Tag ignored -> "tag";
            case Exclude ignored -> "exclude";
            case Include ignored -> "include";
            case Hint ignored -> "hint";
            case ScoreAdjust adjust -> adjust.boost() ? "boost" : "penalize";
            case Reject ignored -> "reject";
        };
    }

    /** Whether this action is meaningful at the catalog stage. */
    default boolean catalogStage() {
        return false;
    }

    /** Whether this action is meaningful at the candidate stage. */
    default boolean candidateStage() {
        return false;
    }

    private static MapCodec<? extends RuleAction> codecForType(String kind) {
        return switch (kind) {
            case "tag" -> Codec.STRING.fieldOf("tag").xmap(Tag::new, Tag::tag);
            case "exclude" -> Codec.BOOL.optionalFieldOf("force", false).xmap(ignored -> new Exclude(), ignored -> false);
            case "include" -> Codec.BOOL.optionalFieldOf("override", true).xmap(ignored -> new Include(), ignored -> true);
            case "hint" -> dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RecipeUsePolicy.class)
                .fieldOf("policy").xmap(Hint::new, Hint::policy);
            case "penalize" -> ScoreAdjust.codec(false);
            case "boost" -> ScoreAdjust.codec(true);
            case "reject" -> Codec.STRING.optionalFieldOf("reason", "").xmap(Reject::new, Reject::reason);
            default -> throw new IllegalArgumentException("Unknown rule action kind: " + kind);
        };
    }

    /** Attaches a classification tag (catalog stage). */
    record Tag(String tag) implements RuleAction {
        public Tag {
            Checks.checkNotNull(tag, "tag");
            tag = tag.trim();
            Checks.checkArgument(!tag.isEmpty(), "tag must not be blank");
        }

        @Override
        public boolean catalogStage() {
            return true;
        }
    }

    /**
     * Excludes the recipe from the routing catalog (catalog stage). Later
     * {@link Include} actions in document order can still override this.
     */
    record Exclude() implements RuleAction {
        @Override
        public boolean catalogStage() {
            return true;
        }
    }

    /** Re-includes a previously excluded recipe (catalog stage, last-wins). */
    record Include() implements RuleAction {
        @Override
        public boolean catalogStage() {
            return true;
        }
    }

    /** Sets a routing hint policy on the recipe (catalog stage). */
    record Hint(RecipeUsePolicy policy) implements RuleAction {
        public Hint {
            Checks.checkNotNull(policy, "policy");
        }

        @Override
        public boolean catalogStage() {
            return true;
        }
    }

    /**
     * Score adjustment against a named score-model component (candidate
     * stage). {@code penalize} worsens the score by {@code amount} (lower is
     * better), {@code boost} improves it; optional {@code scale} multiplies
     * the amount by a runtime factor.
     *
     * @param component declared component name in the active {@code ScoreModel}
     * @param amount    magnitude in component units (non-negative)
     * @param scale     optional scaling of the amount
     * @param boost     {@code true} for boost, {@code false} for penalize
     */
    record ScoreAdjust(
        String component,
        double amount,
        ScoreScale scale,
        boolean boost
    ) implements RuleAction {

        public enum ScoreScale {
            none,
            /** Multiply the amount by {@code 1 - coverage} (stock shortage). */
            coverageShortage;

            public static final Codec<ScoreScale> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(ScoreScale.class);
        }

        static MapCodec<ScoreAdjust> codec(boolean boost) {
            return com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("component").forGetter(ScoreAdjust::component),
                Codec.DOUBLE.fieldOf("amount").forGetter(ScoreAdjust::amount),
                ScoreScale.CODEC.optionalFieldOf("scale", ScoreScale.none).forGetter(ScoreAdjust::scale)
            ).apply(instance, (component, amount, scale) -> new ScoreAdjust(component, amount, scale, boost)));
        }

        public ScoreAdjust {
            Checks.checkNotNull(component, "component");
            component = component.trim();
            Checks.checkArgument(!component.isEmpty(), "component must not be blank");
            Checks.checkNotNull(scale, "scale");
            Checks.checkArgument(amount >= 0, "amount must be non-negative");
        }

        public static ScoreAdjust penalize(String component, double amount) {
            return new ScoreAdjust(component, amount, ScoreScale.none, false);
        }

        public static ScoreAdjust boost(String component, double amount) {
            return new ScoreAdjust(component, amount, ScoreScale.none, true);
        }

        /** Signed contribution to the component (lower score is better). */
        public double signedAmount(double coverage) {
            double scaled = scale == ScoreScale.coverageShortage
                ? amount * (1.0d - Math.min(1.0d, Math.max(0.0d, coverage)))
                : amount;
            return boost ? -scaled : scaled;
        }

        @Override
        public boolean candidateStage() {
            return true;
        }
    }

    /** Hard candidate rejection (candidate stage). */
    record Reject(String reason) implements RuleAction {
        public Reject {
            reason = reason == null ? "" : reason;
        }

        @Override
        public boolean candidateStage() {
            return true;
        }

        public @Nullable String reasonOrNull() {
            return reason.isEmpty() ? null : reason;
        }
    }
}
