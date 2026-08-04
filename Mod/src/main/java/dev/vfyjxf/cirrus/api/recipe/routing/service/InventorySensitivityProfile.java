package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Request-level profile describing how strongly the planner should bias
 * toward consuming resources from immediate inventory stock versus
 * crafting them from scratch.
 *
 * <p>This profile indirectly controls several derived defaults:
 * <ul>
 *     <li>{@link TagResolvePolicy} — stock-biased vs. path-biased variant ordering.</li>
 *     <li>{@code maxProducerCandidates} — how many output-target adapter
 *         candidates to try.</li>
 *     <li>Exploration parameters — whether non-deterministic search diversity
 *         is enabled and its budget share.</li>
 * </ul>
 */
public enum InventorySensitivityProfile {
    /** Strongly favors consuming existing stock; fewer producer candidates explored. */
    high,
    /** Balanced trade-off between stock consumption and crafting. */
    balanced,
    /** Weakly favors stock; more willing to craft and explore alternative paths. */
    low
}

