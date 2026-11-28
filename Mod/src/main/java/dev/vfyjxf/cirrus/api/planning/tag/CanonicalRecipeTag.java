package dev.vfyjxf.cirrus.api.planning.tag;

import org.jspecify.annotations.Nullable;

/**
 * The 10 canonical, human-intuitive recipe tags used across tagging, JEI normalization,
 * batch filtering, analytics, and planning heuristics.
 */
public enum CanonicalRecipeTag {
    normalProduction("cirrus:recipe/normal_production"),
    packingUnpacking("cirrus:recipe/packing_unpacking"),
    recolor("cirrus:recipe/recolor"),
    variantSwap("cirrus:recipe/variant_swap"),
    containerTransfer("cirrus:recipe/container_transfer"),
    recycling("cirrus:recipe/recycling"),
    resourceGeneration("cirrus:recipe/resource_generation"),
    catalyticLoop("cirrus:recipe/catalytic_loop"),
    suspiciousJei("cirrus:recipe/suspicious_jei"),
    junkOrDuplicate("cirrus:recipe/junk_or_duplicate");

    private final String id;

    CanonicalRecipeTag(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isExcludedByDefault() {
        return this == junkOrDuplicate;
    }

    public boolean isDiscouragedByDefault() {
        return this == recolor || this == variantSwap || this == suspiciousJei;
    }

    public boolean isLastResortByDefault() {
        return this == recycling;
    }

    public static @Nullable CanonicalRecipeTag fromIdOrNull(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (CanonicalRecipeTag tag : values()) {
            if (tag.id.equals(id)) {
                return tag;
            }
        }
        return null;
    }
}
