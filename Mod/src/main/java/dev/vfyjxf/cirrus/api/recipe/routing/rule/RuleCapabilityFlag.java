package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

/**
 * Individual capability flags that a {@link RuleProfile} may declare.
 *
 * <p>These flags inform the internal assured routing planner
 * whether a profile meets the mathematical preconditions for certain
 * planning algorithms (e.g. A* search requires {@link #admissibleBound}
 * and {@link #monotonicScore}).
 */
public enum RuleCapabilityFlag {
    /** Score rules produce monotonically non-decreasing values along any path. */
    monotonicScore,
    /** The rank policy defines a total order (no incomparable pairs). */
    totalOrderComparator,
    /** The score heuristic is admissible (never overestimates true cost). */
    admissibleBound,
    /** All rules are pure functions with no side effects. */
    pureRulesOnly,
    /** The tiebreak policy is fully deterministic. */
    deterministicTiebreak;

    public static final Codec<RuleCapabilityFlag> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleCapabilityFlag.class);
}

