package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * Named candidate-stage scalars a condition can compare against. Available
 * only when {@link RuleInput#hasCandidateFields()} is true; otherwise the
 * condition evaluates to {@code false}.
 */
public enum RuleScalar {
    /** Current search depth (0 = root demand). */
    depth,
    /** Craft iterations needed for the demanded amount. */
    craftTimes,
    /** Estimated subtree cost from the unit-cost oracle. */
    subtreeCost,
    /** Estimated amplification risk in [0,1]. */
    amplificationRisk,
    /** Request-scoped contextual penalty total (target family, missing machine). */
    contextualPenalty;

    public static final Codec<RuleScalar> codec = Codecs.lowerCaseEnum(RuleScalar.class);
}
