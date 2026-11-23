package dev.vfyjxf.cirrus.planning.core.verify;

public final class ErrorCodes {

    public static final String ERR_DUPLICATE_STEP_ID = "ERR_DUPLICATE_STEP_ID";
    public static final String ERR_MISSING_DEPENDENCY = "ERR_MISSING_DEPENDENCY";
    public static final String ERR_TOPOLOGICAL_ORDER_VIOLATION = "ERR_TOPOLOGICAL_ORDER_VIOLATION";
    public static final String ERR_NON_POSITIVE_ACQUIRE = "ERR_NON_POSITIVE_ACQUIRE";
    public static final String ERR_BANNED_RESOURCE = "ERR_BANNED_RESOURCE";
    public static final String ERR_UNACQUIRABLE_RESOURCE = "ERR_UNACQUIRABLE_RESOURCE";
    public static final String ERR_NON_POSITIVE_PULL = "ERR_NON_POSITIVE_PULL";
    public static final String ERR_UNKNOWN_LINE = "ERR_UNKNOWN_LINE";
    public static final String ERR_NON_POSITIVE_MACHINE_COUNT = "ERR_NON_POSITIVE_MACHINE_COUNT";
    public static final String ERR_BANNED_MACHINE_BUILD = "ERR_BANNED_MACHINE_BUILD";
    public static final String ERR_INSUFFICIENT_MACHINE_ITEM = "ERR_INSUFFICIENT_MACHINE_ITEM";
    public static final String ERR_NON_POSITIVE_BATCH = "ERR_NON_POSITIVE_BATCH";
    public static final String ERR_UNKNOWN_RECIPE = "ERR_UNKNOWN_RECIPE";
    public static final String ERR_BANNED_RECIPE = "ERR_BANNED_RECIPE";
    public static final String ERR_BANNED_RECIPE_FOR_TARGET = "ERR_BANNED_RECIPE_FOR_TARGET";
    public static final String ERR_BANNED_SYSTEM = "ERR_BANNED_SYSTEM";
    public static final String ERR_MACHINE_NOT_AVAILABLE = "ERR_MACHINE_NOT_AVAILABLE";
    public static final String ERR_AUTOMATION_UNAVAILABLE = "ERR_AUTOMATION_UNAVAILABLE";
    public static final String ERR_MISSING_CATALYST = "ERR_MISSING_CATALYST";
    public static final String ERR_INVALID_SLOT_BINDING = "ERR_INVALID_SLOT_BINDING";
    public static final String ERR_NEGATIVE_STOCK = "ERR_NEGATIVE_STOCK";
    public static final String ERR_NOT_REVERSIBLE_CONVERSION = "ERR_NOT_REVERSIBLE_CONVERSION";
    public static final String ERR_INVALID_CYCLE_ITER = "ERR_INVALID_CYCLE_ITER";
    public static final String ERR_MISSING_CYCLE_SEED = "ERR_MISSING_CYCLE_SEED";
    public static final String ERR_UNKNOWN_CYCLE_MACRO = "ERR_UNKNOWN_CYCLE_MACRO";
    public static final String ERR_CYCLE_DELTA_MISMATCH = "ERR_CYCLE_DELTA_MISMATCH";
    public static final String ERR_CYCLE_INSUFFICIENT_REAGENT = "ERR_CYCLE_INSUFFICIENT_REAGENT";
    public static final String ERR_GOAL_NOT_MET = "ERR_GOAL_NOT_MET";
    public static final String ERR_SURPLUS_MISMATCH = "ERR_SURPLUS_MISMATCH";
    public static final String ERR_PREPARATION_MISMATCH = "ERR_PREPARATION_MISMATCH";
    public static final String ERR_COST_MISMATCH = "ERR_COST_MISMATCH";
    public static final String ERR_ROUND_TRIP = "ERR_ROUND_TRIP";
    public static final String ERR_STOCK_OVERFLOW = "ERR_STOCK_OVERFLOW";
    public static final String ERR_MALFORMED_PLAN = "ERR_MALFORMED_PLAN";

    private ErrorCodes() {
    }
}
