package dev.vfyjxf.cirrus.api.recipe.routing.service;

/**
 * Strategy for ordering multiple variants when a single input can be satisfied
 * by more than one concrete resource (e.g. tag-based inputs with multiple
 * matching items).
 *
 * <p>The policy controls the variant iteration order during demand consumption
 * and candidate reordering, affecting which specific items are consumed first
 * from stock and which producer paths are preferred.
 *
 * @see InventorySensitivityProfile
 */
public enum TagResolvePolicy {
    /**
     * Prioritize variants with the most available stock, consuming
     * from inventory before considering crafting paths.
     */
    stockBiased,
    /**
     * Balanced ordering considering both stock coverage and
     * candidate ranking equally.
     */
    balanced,
    /**
     * Prioritize variants whose best candidate has the highest ranking,
     * preferring better crafting paths over immediate stock availability.
     */
    pathBiased;

    public static TagResolvePolicy fromInventorySensitivity(InventorySensitivityProfile profile) {
        return switch (profile) {
            case high -> stockBiased;
            case balanced -> balanced;
            case low -> pathBiased;
        };
    }
}

