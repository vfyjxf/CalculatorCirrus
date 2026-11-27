package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * Boolean candidate-stage facts a condition can test. Available only when
 * {@link RuleInput#hasCandidateFields()} is true; otherwise the condition
 * evaluates to {@code false}.
 */
public enum RuleFlag {
    /** The candidate would create or extend a dependency cycle. */
    cycleCandidate,
    /** The candidate closes an indirect active-demand cycle (planner guard). */
    activeDemandCycle,
    /** The candidate is an intra-loop no-op conversion. */
    intraLoopConversion,
    /** The candidate closes an equal-amount round trip with the active path. */
    roundTrip;

    public static final Codec<RuleFlag> codec = Codecs.lowerCaseEnum(RuleFlag.class);
}
