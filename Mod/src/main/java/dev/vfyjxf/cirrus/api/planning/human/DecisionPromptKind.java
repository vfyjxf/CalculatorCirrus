package dev.vfyjxf.cirrus.api.planning.human;

/**
 * Seven high-leverage human-in-the-loop decision classes strictly grounded in unobservable
 * real-world context or NP-Hard ambiguities (H1–H7, Planning v3 §7 / M9.1).
 */
public enum DecisionPromptKind {
    /** H1: Machine & facility availability / ownership confirmation. */
    machineAvailability,
    /** H2: Base stop-here boundary or automated supply line confirmation. */
    baseOrSupplyBoundary,
    /** H3: Non-consumable mold, template, tool, or catalytic bootstrap seed check. */
    reusableCatalystCheck,
    /** H4: Dominant material family / high-impact Tag anchor selection. */
    materialFamilyAnchor,
    /** H5: Suspicious JEI recipe executability verification (+ bulk similar-recipe filter). */
    suspiciousJeiVerify,
    /** H6: High-cost field-trip manual gathering confirmation. */
    fieldTripConfirm,
    /** H7: Minimal unsatisfiable human-rule conflict resolution. */
    ruleConflictResolution
}
