package dev.vfyjxf.cirrus.planning.core.verify;

public final class ErrorCodes {

    public static final String errDuplicateStepId = "ERR_DUPLICATE_STEP_ID";
    public static final String errMissingDependency = "ERR_MISSING_DEPENDENCY";
    public static final String errTopologicalOrderViolation = "ERR_TOPOLOGICAL_ORDER_VIOLATION";
    public static final String errNonPositiveAcquire = "ERR_NON_POSITIVE_ACQUIRE";
    public static final String errBannedResource = "ERR_BANNED_RESOURCE";
    public static final String errUnacquirableResource = "ERR_UNACQUIRABLE_RESOURCE";
    public static final String errNonPositivePull = "ERR_NON_POSITIVE_PULL";
    public static final String errUnknownLine = "ERR_UNKNOWN_LINE";
    public static final String errNonPositiveMachineCount = "ERR_NON_POSITIVE_MACHINE_COUNT";
    public static final String errBannedMachineBuild = "ERR_BANNED_MACHINE_BUILD";
    public static final String errInsufficientMachineItem = "ERR_INSUFFICIENT_MACHINE_ITEM";
    public static final String errNonPositiveBatch = "ERR_NON_POSITIVE_BATCH";
    public static final String errUnknownRecipe = "ERR_UNKNOWN_RECIPE";
    public static final String errBannedRecipe = "ERR_BANNED_RECIPE";
    public static final String errBannedRecipeForTarget = "ERR_BANNED_RECIPE_FOR_TARGET";
    public static final String errBannedSystem = "ERR_BANNED_SYSTEM";
    public static final String errMachineNotAvailable = "ERR_MACHINE_NOT_AVAILABLE";
    public static final String errAutomationUnavailable = "ERR_AUTOMATION_UNAVAILABLE";
    public static final String errMissingCatalyst = "ERR_MISSING_CATALYST";
    public static final String errInvalidSlotBinding = "ERR_INVALID_SLOT_BINDING";
    public static final String errNegativeStock = "ERR_NEGATIVE_STOCK";
    public static final String errNotReversibleConversion = "ERR_NOT_REVERSIBLE_CONVERSION";
    public static final String errInvalidCycleIter = "ERR_INVALID_CYCLE_ITER";
    public static final String errMissingCycleSeed = "ERR_MISSING_CYCLE_SEED";
    public static final String errUnknownCycleMacro = "ERR_UNKNOWN_CYCLE_MACRO";
    public static final String errCycleDeltaMismatch = "ERR_CYCLE_DELTA_MISMATCH";
    public static final String errCycleInsufficientReagent = "ERR_CYCLE_INSUFFICIENT_REAGENT";
    public static final String errGoalNotMet = "ERR_GOAL_NOT_MET";
    public static final String errSurplusMismatch = "ERR_SURPLUS_MISMATCH";
    public static final String errPreparationMismatch = "ERR_PREPARATION_MISMATCH";
    public static final String errCostMismatch = "ERR_COST_MISMATCH";
    public static final String errRoundTrip = "ERR_ROUND_TRIP";
    public static final String errStockOverflow = "ERR_STOCK_OVERFLOW";
    public static final String errMalformedPlan = "ERR_MALFORMED_PLAN";

    private ErrorCodes() {}
}
