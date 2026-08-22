package dev.vfyjxf.cirrus.config;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.InventorySensitivityProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CirrusConstants.modId, value = Dist.CLIENT)
public final class ExecutionOverlayClientConfig {

    public static final int overlayRateMin = 0;
    public static final int overlayRateMax = 40;
    public static final int overlayRateDefault = 4;
    public static final boolean overlayTraceDefault = false;
    public static final ExecutionObservedStockPolicy observedStockPolicyDefault = ExecutionObservedStockPolicy.consumeObservedStock;
    public static final boolean jeiTargetButtonAutoCloseDefault = true;
    public static final boolean calculatorDatabaseAutoRefreshDefault = true;
    public static final boolean calculatorProjectionUsesGlobalDatabaseDefault = false;
    public static final boolean calculatorRulesReadOtherWorldsDefault = false;
    public static final boolean calculatorUseLiveStockDefault = false;
    public static final boolean calculatorRecipeScreeningEnabledDefault = true;
    public static final PlannerMode calculatorPlannerModeDefault = PlannerMode.auto;
    public static final InventorySensitivityProfile calculatorInventorySensitivityDefault = InventorySensitivityProfile.balanced;
    public static final CalculatorRuleSelection calculatorRuleSelectionDefault = CalculatorRuleSelection.preview;
    public static final boolean calculatorAutoDeepenEnabledDefault = true;
    public static final int calculatorDeepenMaxGeneratedDefault = 240_000;
    public static final int calculatorDeepenMaxGeneratedMin = 60_000;
    public static final int calculatorDeepenMaxGeneratedMax = 960_000;
    public static final boolean executionUsesCalculatorPathSettingsDefault = true;
    public static final PlannerMode executionPlannerModeDefault = PlannerMode.standard;

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue overlayRateValue;
    private static final ModConfigSpec.BooleanValue overlayTraceValue;
    private static final ModConfigSpec.EnumValue<ExecutionObservedStockPolicy> observedStockPolicyValue;
    private static final ModConfigSpec.BooleanValue jeiTargetButtonAutoCloseValue;
    private static final ModConfigSpec.BooleanValue calculatorDatabaseAutoRefreshValue;
    private static final ModConfigSpec.BooleanValue calculatorProjectionUsesGlobalDatabaseValue;
    private static final ModConfigSpec.BooleanValue calculatorRulesReadOtherWorldsValue;
    private static final ModConfigSpec.BooleanValue calculatorUseLiveStockValue;
    private static final ModConfigSpec.BooleanValue calculatorRecipeScreeningEnabledValue;
    private static final ModConfigSpec.EnumValue<PlannerMode> calculatorPlannerModeValue;
    private static final ModConfigSpec.EnumValue<InventorySensitivityProfile> calculatorInventorySensitivityValue;
    private static final ModConfigSpec.EnumValue<CalculatorRuleSelection> calculatorRuleSelectionValue;
    private static final ModConfigSpec.BooleanValue calculatorAutoDeepenEnabledValue;
    private static final ModConfigSpec.IntValue calculatorDeepenMaxGeneratedValue;
    private static final ModConfigSpec.BooleanValue executionUsesCalculatorPathSettingsValue;
    private static final ModConfigSpec.EnumValue<PlannerMode> executionPlannerModeValue;
    public static final ModConfigSpec SPEC;

    private static volatile int overlayRate = overlayRateDefault;
    private static volatile boolean overlayTraceEnabled = overlayTraceDefault;
    private static volatile ExecutionObservedStockPolicy observedStockPolicy = observedStockPolicyDefault;
    private static volatile boolean jeiTargetButtonAutoClose = jeiTargetButtonAutoCloseDefault;
    private static volatile boolean calculatorDatabaseAutoRefresh = calculatorDatabaseAutoRefreshDefault;
    private static volatile boolean calculatorProjectionUsesGlobalDatabase = calculatorProjectionUsesGlobalDatabaseDefault;
    private static volatile boolean calculatorRulesReadOtherWorlds = calculatorRulesReadOtherWorldsDefault;
    private static volatile boolean calculatorUseLiveStock = calculatorUseLiveStockDefault;
    private static volatile boolean calculatorRecipeScreeningEnabled = calculatorRecipeScreeningEnabledDefault;
    private static volatile PlannerMode calculatorPlannerMode = calculatorPlannerModeDefault;
    private static volatile InventorySensitivityProfile calculatorInventorySensitivity = calculatorInventorySensitivityDefault;
    private static volatile CalculatorRuleSelection calculatorRuleSelection = calculatorRuleSelectionDefault;
    private static volatile boolean calculatorAutoDeepenEnabled = calculatorAutoDeepenEnabledDefault;
    private static volatile int calculatorDeepenMaxGenerated = calculatorDeepenMaxGeneratedDefault;
    private static volatile boolean executionUsesCalculatorPathSettings = executionUsesCalculatorPathSettingsDefault;
    private static volatile PlannerMode executionPlannerMode = executionPlannerModeDefault;

    static {
        builder.push("execution");
        builder.push("overlay");
        overlayRateValue = builder
            .comment("Default execution overlay rate used for the recipe auto-step slider.")
            .defineInRange("rate", overlayRateDefault, overlayRateMin, overlayRateMax);
        overlayTraceValue = builder
            .comment("Whether execution overlay visual trace is enabled by default.")
            .define("trace_enabled", overlayTraceDefault);
        observedStockPolicyValue = builder
            .comment("How rebuild planning treats newly observed stock. consumeObservedStock uses live inventory by default; preservePlannedStock ignores unexpected additions unless planned.")
            .defineEnum("observed_stock_policy", observedStockPolicyDefault);
        builder.pop();
        builder.push("routing");
        executionUsesCalculatorPathSettingsValue = builder
            .comment("Whether execution routing uses the calculator path settings, including active recipe database, rule profile, planner mode, and inventory sensitivity.")
            .define("use_calculator_path_settings", executionUsesCalculatorPathSettingsDefault);
        executionPlannerModeValue = builder
            .comment("Planner mode used by execution routing when use_calculator_path_settings is false.")
            .defineEnum("planner_mode", executionPlannerModeDefault);
        builder.pop();
        builder.push("jei");
        jeiTargetButtonAutoCloseValue = builder
            .comment("Whether clicking the JEI recipe target button closes the JEI recipes screen after selecting a target.")
            .define("target_button_auto_close", jeiTargetButtonAutoCloseDefault);
        builder.pop();
        builder.push("calculator");
        builder.push("database");
        calculatorDatabaseAutoRefreshValue = builder
            .comment("Whether the calculator database page rebuilds its index from registered systems when opened.")
            .define("auto_refresh", calculatorDatabaseAutoRefreshDefault);
        calculatorProjectionUsesGlobalDatabaseValue = builder
            .comment("Whether projection details opened from the calculator use the saved global recipe database context. Disabled keeps projection details local to the current calculator recipe.")
            .define("projection_uses_global_database", calculatorProjectionUsesGlobalDatabaseDefault);
        builder.pop();
        builder.push("rules");
        calculatorRulesReadOtherWorldsValue = builder
            .comment("Whether the calculator rules page may list rule files from other saves.")
            .define("read_other_worlds", calculatorRulesReadOtherWorldsDefault);
        calculatorRuleSelectionValue = builder
            .comment("Routing rule used by the calculator process graph.")
            .defineEnum("calculator_rule", calculatorRuleSelectionDefault);
        builder.pop();
        builder.push("planning");
        calculatorUseLiveStockValue = builder
            .comment("Whether calculator previews read the player's current inventory as routing stock.")
            .define("use_live_stock", calculatorUseLiveStockDefault);
        calculatorRecipeScreeningEnabledValue = builder
            .comment("Whether recipe screening filters junk/duplicate recipes out of planning and marks them in the database browser.")
            .define("recipe_screening_enabled", calculatorRecipeScreeningEnabledDefault);
        calculatorPlannerModeValue = builder
            .comment("Planner mode used by the calculator process graph.")
            .defineEnum("planner_mode", calculatorPlannerModeDefault);
        calculatorInventorySensitivityValue = builder
            .comment("How strongly calculator previews should prefer current stock when live stock is enabled.")
            .defineEnum("inventory_sensitivity", calculatorInventorySensitivityDefault);
        calculatorAutoDeepenEnabledValue = builder
            .comment("Whether the calculator automatically continues planning with a larger budget when the first wave stops on budget limits with pending inputs.")
            .define("auto_deepen", calculatorAutoDeepenEnabledDefault);
        calculatorDeepenMaxGeneratedValue = builder
            .comment("Upper bound on generated candidates across all auto-deepen waves of one calculator request.")
            .defineInRange("deepen_max_generated", calculatorDeepenMaxGeneratedDefault, calculatorDeepenMaxGeneratedMin, calculatorDeepenMaxGeneratedMax);
        builder.pop();
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private ExecutionOverlayClientConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshCachedValues();
        }
    }

    public static int overlayRate() {
        return overlayRate;
    }

    public static void setOverlayRate(long value) {
        int clamped = clamp((int) value);
        overlayRate = clamped;
        overlayRateValue.set(clamped);
        overlayRateValue.save();
    }

    public static boolean overlayTraceEnabled() {
        return overlayTraceEnabled;
    }

    public static ExecutionObservedStockPolicy observedStockPolicy() {
        return observedStockPolicy;
    }

    public static boolean jeiTargetButtonAutoClose() {
        return jeiTargetButtonAutoClose;
    }

    public static void setJeiTargetButtonAutoClose(boolean enabled) {
        jeiTargetButtonAutoClose = enabled;
        jeiTargetButtonAutoCloseValue.set(enabled);
        jeiTargetButtonAutoCloseValue.save();
    }


    public static boolean calculatorDatabaseAutoRefresh() {
        return calculatorDatabaseAutoRefresh;
    }

    public static void setCalculatorDatabaseAutoRefresh(boolean enabled) {
        calculatorDatabaseAutoRefresh = enabled;
        calculatorDatabaseAutoRefreshValue.set(enabled);
        calculatorDatabaseAutoRefreshValue.save();
    }

    public static boolean calculatorProjectionUsesGlobalDatabase() {
        return calculatorProjectionUsesGlobalDatabase;
    }

    public static void setCalculatorProjectionUsesGlobalDatabase(boolean enabled) {
        calculatorProjectionUsesGlobalDatabase = enabled;
        calculatorProjectionUsesGlobalDatabaseValue.set(enabled);
        calculatorProjectionUsesGlobalDatabaseValue.save();
    }

    public static boolean calculatorRulesReadOtherWorlds() {
        return calculatorRulesReadOtherWorlds;
    }

    public static void setCalculatorRulesReadOtherWorlds(boolean enabled) {
        calculatorRulesReadOtherWorlds = enabled;
        calculatorRulesReadOtherWorldsValue.set(enabled);
        calculatorRulesReadOtherWorldsValue.save();
    }

    public static boolean calculatorUseLiveStock() {
        return calculatorUseLiveStock;
    }

    public static void setCalculatorUseLiveStock(boolean enabled) {
        calculatorUseLiveStock = enabled;
        calculatorUseLiveStockValue.set(enabled);
        calculatorUseLiveStockValue.save();
    }

    public static boolean calculatorRecipeScreeningEnabled() {
        return calculatorRecipeScreeningEnabled;
    }

    public static void setCalculatorRecipeScreeningEnabled(boolean enabled) {
        calculatorRecipeScreeningEnabled = enabled;
        calculatorRecipeScreeningEnabledValue.set(enabled);
        calculatorRecipeScreeningEnabledValue.save();
    }

    public static PlannerMode calculatorPlannerMode() {
        return calculatorPlannerMode;
    }

    public static void setCalculatorPlannerMode(PlannerMode mode) {
        calculatorPlannerMode = mode == null ? calculatorPlannerModeDefault : mode;
        calculatorPlannerModeValue.set(calculatorPlannerMode);
        calculatorPlannerModeValue.save();
    }

    public static InventorySensitivityProfile calculatorInventorySensitivity() {
        return calculatorInventorySensitivity;
    }

    public static void setCalculatorInventorySensitivity(InventorySensitivityProfile profile) {
        calculatorInventorySensitivity = profile == null ? calculatorInventorySensitivityDefault : profile;
        calculatorInventorySensitivityValue.set(calculatorInventorySensitivity);
        calculatorInventorySensitivityValue.save();
    }

    public static CalculatorRuleSelection calculatorRuleSelection() {
        return calculatorRuleSelection;
    }

    public static boolean calculatorAutoDeepenEnabled() {
        return calculatorAutoDeepenEnabled;
    }

    public static void setCalculatorAutoDeepenEnabled(boolean enabled) {
        calculatorAutoDeepenEnabled = enabled;
        calculatorAutoDeepenEnabledValue.set(enabled);
        calculatorAutoDeepenEnabledValue.save();
    }

    public static int calculatorDeepenMaxGenerated() {
        return calculatorDeepenMaxGenerated;
    }

    public static void setCalculatorDeepenMaxGenerated(int value) {
        int clamped = Math.max(calculatorDeepenMaxGeneratedMin, Math.min(calculatorDeepenMaxGeneratedMax, value));
        calculatorDeepenMaxGenerated = clamped;
        calculatorDeepenMaxGeneratedValue.set(clamped);
        calculatorDeepenMaxGeneratedValue.save();
    }

    public static void setCalculatorRuleSelection(CalculatorRuleSelection selection) {
        calculatorRuleSelection = selection == null ? calculatorRuleSelectionDefault : selection;
        calculatorRuleSelectionValue.set(calculatorRuleSelection);
        calculatorRuleSelectionValue.save();
    }

    public static boolean executionUsesCalculatorPathSettings() {
        return executionUsesCalculatorPathSettings;
    }

    public static void setExecutionUsesCalculatorPathSettings(boolean enabled) {
        executionUsesCalculatorPathSettings = enabled;
        executionUsesCalculatorPathSettingsValue.set(enabled);
        executionUsesCalculatorPathSettingsValue.save();
    }

    public static PlannerMode executionPlannerMode() {
        return executionPlannerMode;
    }

    public static void setExecutionPlannerMode(PlannerMode mode) {
        executionPlannerMode = mode == null ? executionPlannerModeDefault : mode;
        executionPlannerModeValue.set(executionPlannerMode);
        executionPlannerModeValue.save();
    }

    public static void setObservedStockPolicy(ExecutionObservedStockPolicy policy) {
        observedStockPolicy = policy == null ? observedStockPolicyDefault : policy;
        observedStockPolicyValue.set(observedStockPolicy);
        observedStockPolicyValue.save();
    }

    public static void setOverlayTraceEnabled(boolean enabled) {
        overlayTraceEnabled = enabled;
        overlayTraceValue.set(enabled);
        overlayTraceValue.save();
    }

    private static void refreshCachedValues() {
        overlayRate = clamp(overlayRateValue.get());
        overlayTraceEnabled = overlayTraceValue.get();
        observedStockPolicy = observedStockPolicyValue.get();
        jeiTargetButtonAutoClose = jeiTargetButtonAutoCloseValue.get();
        calculatorDatabaseAutoRefresh = calculatorDatabaseAutoRefreshValue.get();
        calculatorProjectionUsesGlobalDatabase = calculatorProjectionUsesGlobalDatabaseValue.get();
        calculatorRulesReadOtherWorlds = calculatorRulesReadOtherWorldsValue.get();
        calculatorUseLiveStock = calculatorUseLiveStockValue.get();
        calculatorRecipeScreeningEnabled = calculatorRecipeScreeningEnabledValue.get();
        calculatorPlannerMode = calculatorPlannerModeValue.get();
        calculatorInventorySensitivity = calculatorInventorySensitivityValue.get();
        calculatorRuleSelection = calculatorRuleSelectionValue.get();
        calculatorAutoDeepenEnabled = calculatorAutoDeepenEnabledValue.get();
        calculatorDeepenMaxGenerated = calculatorDeepenMaxGeneratedValue.get();
        executionUsesCalculatorPathSettings = executionUsesCalculatorPathSettingsValue.get();
        executionPlannerMode = executionPlannerModeValue.get();
    }

    private static int clamp(int value) {
        return Math.max(overlayRateMin, Math.min(overlayRateMax, value));
    }

}
