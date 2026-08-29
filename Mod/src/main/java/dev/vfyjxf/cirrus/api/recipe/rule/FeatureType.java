package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;

/**
 * Named per-candidate features the score model can build components from.
 * Features are computed once per candidate by the planner; a component's
 * {@code feature} reference must be one of these names (validated at model
 * compile time, so typos fail loudly instead of silently contributing zero).
 */
public enum FeatureType {
    /** Base subtree cost (unit-cost oracle without advisory multipliers), impurity included. */
    subtreeCost,
    /** Excess cost added by advisory multipliers ({@code full − base}); keeps the cost tier purely additive. */
    advisoryCostExcess,
    /** Request-scoped contextual penalty total (target family, missing machine). */
    contextualPenalty,
    /** Craft iterations needed for the demanded amount. */
    craftTimes,
    /** Stock-shortage share of the candidate's inputs ({@code 1 − coverage}). */
    inventoryShortage,
    /** Search depth (0 = root demand). */
    depth,
    /** Estimated amplification risk in [0,1]. */
    amplificationRisk,
    /** 1.0 when the candidate closes an indirect active-demand cycle, else 0. */
    activeDemandCycle,
    /** 1.0 when the candidate closes an equal-amount round trip, else 0. */
    roundTrip,
    /** 1.0 when the candidate is an intra-loop no-op conversion, else 0. */
    intraLoopConversion,
    /** 1.0 when the node's routing hint policy is {@code discouraged}, else 0. */
    hintDiscouraged,
    /** 1.0 when the node's routing hint policy is {@code lastResort}, else 0. */
    hintLastResort,
    /** Raw integer routing-hint penalty from heuristic rules. */
    hintPenalty;

    public static final Codec<FeatureType> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(FeatureType.class);
}
