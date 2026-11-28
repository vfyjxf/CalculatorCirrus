package dev.vfyjxf.cirrus.api.planning.human;

/**
 * High-leverage human-in-the-loop decision classes strictly grounded in unobservable
 * real-world context or planning ambiguities.
 */
public enum DecisionPromptKind {
    /** Machine and facility availability / ownership confirmation. */
    machineAvailability,
    /** Base stop-here boundary or automated supply line confirmation. */
    baseOrSupplyBoundary,
    /** Non-consumable mold, template, tool, or catalytic bootstrap seed check. */
    reusableCatalystCheck,
    /** Dominant material family / high-impact tag anchor selection. */
    materialFamilyAnchor,
    /** Unverified JEI recipe executability confirmation (+ bulk similar-recipe filter). */
    suspiciousJeiVerify,
    /** High-cost field-trip manual gathering confirmation. */
    fieldTripConfirm,
    /** Minimal unsatisfiable human-rule conflict resolution. */
    ruleConflictResolution
}
