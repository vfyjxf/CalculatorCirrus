package dev.vfyjxf.cirrus.api.planning.tag;

/**
 * M8 evidence trust tier for contamination-proof sibling baseline anchoring.
 * Layer A weight = 1.0, Layer B weight <= 0.25, Layer C weight = 0.0.
 */
public enum RecipeEvidenceTier {
    /** Verified native semantics or independently observed execution evidence. */
    tierA,
    /** Audited/patched mapping with verified I/O and requirements. */
    tierB,
    /** Unverified JEI/display fallback. */
    tierC
}
