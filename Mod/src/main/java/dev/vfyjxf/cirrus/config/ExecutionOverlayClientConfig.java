package dev.vfyjxf.cirrus.config;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.settings.ProgressionPreset;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
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
    public static final CalculatorRuleSelection calculatorRuleSelectionDefault = CalculatorRuleSelection.preview;
    public static final boolean calculatorAutoDeepenEnabledDefault = true;
    public static final int calculatorDeepenMaxGeneratedDefault = 240_000;
    public static final int calculatorDeepenMaxGeneratedMin = 60_000;
    public static final int calculatorDeepenMaxGeneratedMax = 960_000;
    public static final boolean executionUsesCalculatorPathSettingsDefault = true;
    public static final WorkbenchThemeMode workbenchThemeDefault = WorkbenchThemeMode.obsidianBlueprint;
    public static final boolean workbenchReducedMotionDefault = false;
    public static final DagLayoutMode workbenchDefaultDagLayoutDefault = DagLayoutMode.horizontalLr;
    public static final DagEdgeStyle workbenchDefaultDagEdgeStyleDefault = DagEdgeStyle.orthogonal;
    public static final ObjectivePreset workbenchDefaultPresetDefault = ObjectivePreset.autoSmart;
    public static final int workbenchSoftBudgetMsDefault = 80;
    public static final int workbenchSoftBudgetMsMin = 20;
    public static final int workbenchSoftBudgetMsMax = 2000;
    public static final int workbenchHardBudgetMsDefault = 250;
    public static final int workbenchHardBudgetMsMin = 80;
    public static final int workbenchHardBudgetMsMax = 5000;
    public static final boolean workbenchDefaultPersistWorldDefault = false;
    public static final ProgressionPreset workbenchProgressionPresetDefault = ProgressionPreset.overworld;
    public static final boolean workbenchGridSnapDefault = true;

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
    private static final ModConfigSpec.EnumValue<CalculatorRuleSelection> calculatorRuleSelectionValue;
    private static final ModConfigSpec.BooleanValue calculatorAutoDeepenEnabledValue;
    private static final ModConfigSpec.IntValue calculatorDeepenMaxGeneratedValue;
    private static final ModConfigSpec.BooleanValue executionUsesCalculatorPathSettingsValue;
    private static final ModConfigSpec.EnumValue<WorkbenchThemeMode> workbenchThemeValue;
    private static final ModConfigSpec.BooleanValue workbenchReducedMotionValue;
    private static final ModConfigSpec.EnumValue<DagLayoutMode> workbenchDefaultDagLayoutValue;
    private static final ModConfigSpec.EnumValue<DagEdgeStyle> workbenchDefaultDagEdgeStyleValue;
    private static final ModConfigSpec.EnumValue<ObjectivePreset> workbenchDefaultPresetValue;
    private static final ModConfigSpec.IntValue workbenchSoftBudgetMsValue;
    private static final ModConfigSpec.IntValue workbenchHardBudgetMsValue;
    private static final ModConfigSpec.BooleanValue workbenchDefaultPersistWorldValue;
    private static final ModConfigSpec.EnumValue<ProgressionPreset> workbenchProgressionPresetValue;
    private static final ModConfigSpec.BooleanValue workbenchGridSnapValue;
    public static final ModConfigSpec spec;

    private static volatile int overlayRate = overlayRateDefault;
    private static volatile boolean overlayTraceEnabled = overlayTraceDefault;
    private static volatile ExecutionObservedStockPolicy observedStockPolicy = observedStockPolicyDefault;
    private static volatile boolean jeiTargetButtonAutoClose = jeiTargetButtonAutoCloseDefault;
    private static volatile boolean calculatorDatabaseAutoRefresh = calculatorDatabaseAutoRefreshDefault;
    private static volatile boolean calculatorProjectionUsesGlobalDatabase = calculatorProjectionUsesGlobalDatabaseDefault;
    private static volatile boolean calculatorRulesReadOtherWorlds = calculatorRulesReadOtherWorldsDefault;
    private static volatile boolean calculatorUseLiveStock = calculatorUseLiveStockDefault;
    private static volatile boolean calculatorRecipeScreeningEnabled = calculatorRecipeScreeningEnabledDefault;
    private static volatile CalculatorRuleSelection calculatorRuleSelection = calculatorRuleSelectionDefault;
    private static volatile boolean calculatorAutoDeepenEnabled = calculatorAutoDeepenEnabledDefault;
    private static volatile int calculatorDeepenMaxGenerated = calculatorDeepenMaxGeneratedDefault;
    private static volatile boolean executionUsesCalculatorPathSettings = executionUsesCalculatorPathSettingsDefault;
    private static volatile WorkbenchThemeMode workbenchTheme = workbenchThemeDefault;
    private static volatile boolean workbenchReducedMotion = workbenchReducedMotionDefault;
    private static volatile DagLayoutMode workbenchDefaultDagLayout = workbenchDefaultDagLayoutDefault;
    private static volatile DagEdgeStyle workbenchDefaultDagEdgeStyle = workbenchDefaultDagEdgeStyleDefault;
    private static volatile ObjectivePreset workbenchDefaultPreset = workbenchDefaultPresetDefault;
    private static volatile int workbenchSoftBudgetMs = workbenchSoftBudgetMsDefault;
    private static volatile int workbenchHardBudgetMs = workbenchHardBudgetMsDefault;
    private static volatile boolean workbenchDefaultPersistWorld = workbenchDefaultPersistWorldDefault;
    private static volatile ProgressionPreset workbenchProgressionPreset = workbenchProgressionPresetDefault;
    private static volatile boolean workbenchGridSnap = workbenchGridSnapDefault;

    static {
        builder.push("execution");
        builder.push("overlay");
        overlayRateValue = builder.comment("Default execution overlay rate used for the recipe auto-step slider.")
                .defineInRange("rate", overlayRateDefault, overlayRateMin, overlayRateMax);
        overlayTraceValue = builder.comment("Whether execution overlay visual trace is enabled by default.")
                .define("trace_enabled", overlayTraceDefault);
        observedStockPolicyValue = builder.comment(
            "How rebuild planning treats newly observed stock. consumeObservedStock uses live inventory by default; preservePlannedStock ignores unexpected additions unless planned."
        ).defineEnum("observed_stock_policy", observedStockPolicyDefault);
        builder.pop();
        builder.push("routing");
        executionUsesCalculatorPathSettingsValue = builder.comment(
            "Whether execution routing uses the calculator path settings, including active recipe database, rule profile, planner mode, and inventory sensitivity."
        ).define("use_calculator_path_settings", executionUsesCalculatorPathSettingsDefault);
        builder.pop();
        builder.push("jei");
        jeiTargetButtonAutoCloseValue = builder.comment(
            "Whether clicking the JEI recipe target button closes the JEI recipes screen after selecting a target."
        ).define("target_button_auto_close", jeiTargetButtonAutoCloseDefault);
        builder.pop();
        builder.push("calculator");
        builder.push("database");
        calculatorDatabaseAutoRefreshValue = builder
                .comment("Whether the calculator database page rebuilds its index from registered systems when opened.")
                .define("auto_refresh", calculatorDatabaseAutoRefreshDefault);
        calculatorProjectionUsesGlobalDatabaseValue = builder.comment(
            "Whether projection details opened from the calculator use the saved global recipe database context. Disabled keeps projection details local to the current calculator recipe."
        ).define("projection_uses_global_database", calculatorProjectionUsesGlobalDatabaseDefault);
        builder.pop();
        builder.push("rules");
        calculatorRulesReadOtherWorldsValue = builder
                .comment("Whether the calculator rules page may list rule files from other saves.")
                .define("read_other_worlds", calculatorRulesReadOtherWorldsDefault);
        calculatorRuleSelectionValue = builder.comment("Routing rule used by the calculator process graph.")
                .defineEnum("calculator_rule", calculatorRuleSelectionDefault);
        builder.pop();
        builder.push("planning");
        calculatorUseLiveStockValue = builder
                .comment("Whether calculator previews read the player's current inventory as routing stock.")
                .define("use_live_stock", calculatorUseLiveStockDefault);
        calculatorRecipeScreeningEnabledValue = builder.comment(
            "Whether recipe screening filters junk/duplicate recipes out of planning and marks them in the database browser."
        ).define("recipe_screening_enabled", calculatorRecipeScreeningEnabledDefault);
        calculatorAutoDeepenEnabledValue = builder.comment(
            "Whether the calculator automatically continues planning with a larger budget when the first wave stops on budget limits with pending inputs."
        ).define("auto_deepen", calculatorAutoDeepenEnabledDefault);
        calculatorDeepenMaxGeneratedValue = builder
                .comment("Upper bound on generated candidates across all auto-deepen waves of one calculator request.")
                .defineInRange(
                    "deepen_max_generated",
                    calculatorDeepenMaxGeneratedDefault,
                    calculatorDeepenMaxGeneratedMin,
                    calculatorDeepenMaxGeneratedMax
                );
        builder.pop();
        builder.push("workbench");
        workbenchThemeValue = builder.comment("Workbench theme used by the fullscreen planning workbench.")
                .defineEnum("workbench_theme", workbenchThemeDefault);
        workbenchReducedMotionValue = builder.comment("Whether the workbench reduces pulse and flow animations.")
                .define("workbench_reduced_motion", workbenchReducedMotionDefault);
        workbenchDefaultDagLayoutValue = builder
                .comment("Default DAG layout direction used when the workbench canvas opens.")
                .defineEnum("workbench_default_dag_layout", workbenchDefaultDagLayoutDefault);
        workbenchDefaultDagEdgeStyleValue = builder
                .comment("Default DAG edge routing style (orthogonal, straight, or curved).")
                .defineEnum("workbench_default_dag_edge_style", workbenchDefaultDagEdgeStyleDefault);
        workbenchDefaultPresetValue = builder.comment("Default objective preset for new planning requests.")
                .defineEnum("workbench_default_preset", workbenchDefaultPresetDefault);
        workbenchSoftBudgetMsValue = builder.comment("Interactive soft planning budget in milliseconds.").defineInRange(
            "workbench_soft_budget_ms",
            workbenchSoftBudgetMsDefault,
            workbenchSoftBudgetMsMin,
            workbenchSoftBudgetMsMax
        );
        workbenchHardBudgetMsValue = builder.comment("Hard planning budget in milliseconds.").defineInRange(
            "workbench_hard_budget_ms",
            workbenchHardBudgetMsDefault,
            workbenchHardBudgetMsMin,
            workbenchHardBudgetMsMax
        );
        workbenchDefaultPersistWorldValue = builder
                .comment("Whether human feedback is persisted to the world save by default.")
                .define("workbench_default_persist_world", workbenchDefaultPersistWorldDefault);
        workbenchProgressionPresetValue = builder
                .comment("Progression preset turned into the planning progression state.")
                .defineEnum("workbench_progression_preset", workbenchProgressionPresetDefault);
        workbenchGridSnapValue = builder
                .comment("Whether the DAG canvas snaps dragged nodes to the 8px grid by default.")
                .define("workbench_grid_snap", workbenchGridSnapDefault);
        builder.pop();
        builder.pop();
        builder.pop();
        spec = builder.build();
    }

    private ExecutionOverlayClientConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, spec);
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == spec) {
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

    public static WorkbenchThemeMode workbenchTheme() {
        return workbenchTheme;
    }

    public static void setWorkbenchTheme(WorkbenchThemeMode theme) {
        workbenchTheme = theme == null ? workbenchThemeDefault : theme;
        workbenchThemeValue.set(workbenchTheme);
        workbenchThemeValue.save();
    }

    public static boolean workbenchReducedMotion() {
        return workbenchReducedMotion;
    }

    public static void setWorkbenchReducedMotion(boolean enabled) {
        workbenchReducedMotion = enabled;
        workbenchReducedMotionValue.set(enabled);
        workbenchReducedMotionValue.save();
    }

    public static DagLayoutMode workbenchDefaultDagLayout() {
        return workbenchDefaultDagLayout;
    }

    public static void setWorkbenchDefaultDagLayout(DagLayoutMode layout) {
        workbenchDefaultDagLayout = layout == null ? workbenchDefaultDagLayoutDefault : layout;
        workbenchDefaultDagLayoutValue.set(workbenchDefaultDagLayout);
        workbenchDefaultDagLayoutValue.save();
    }

    public static DagEdgeStyle workbenchDefaultDagEdgeStyle() {
        return workbenchDefaultDagEdgeStyle;
    }

    public static void setWorkbenchDefaultDagEdgeStyle(DagEdgeStyle style) {
        workbenchDefaultDagEdgeStyle = style == null ? workbenchDefaultDagEdgeStyleDefault : style;
        workbenchDefaultDagEdgeStyleValue.set(workbenchDefaultDagEdgeStyle);
        workbenchDefaultDagEdgeStyleValue.save();
    }

    public static ObjectivePreset workbenchDefaultPreset() {
        return workbenchDefaultPreset;
    }

    public static void setWorkbenchDefaultPreset(ObjectivePreset preset) {
        workbenchDefaultPreset = preset == null ? workbenchDefaultPresetDefault : preset;
        workbenchDefaultPresetValue.set(workbenchDefaultPreset);
        workbenchDefaultPresetValue.save();
    }

    public static int workbenchSoftBudgetMs() {
        return workbenchSoftBudgetMs;
    }

    public static void setWorkbenchSoftBudgetMs(int milliseconds) {
        int clamped = Math.max(workbenchSoftBudgetMsMin, Math.min(workbenchSoftBudgetMsMax, milliseconds));
        workbenchSoftBudgetMs = clamped;
        workbenchSoftBudgetMsValue.set(clamped);
        workbenchSoftBudgetMsValue.save();
    }

    public static int workbenchHardBudgetMs() {
        return workbenchHardBudgetMs;
    }

    public static void setWorkbenchHardBudgetMs(int milliseconds) {
        int clamped = Math.max(workbenchHardBudgetMsMin, Math.min(workbenchHardBudgetMsMax, milliseconds));
        workbenchHardBudgetMs = clamped;
        workbenchHardBudgetMsValue.set(clamped);
        workbenchHardBudgetMsValue.save();
    }

    public static boolean workbenchDefaultPersistWorld() {
        return workbenchDefaultPersistWorld;
    }

    public static void setWorkbenchDefaultPersistWorld(boolean enabled) {
        workbenchDefaultPersistWorld = enabled;
        workbenchDefaultPersistWorldValue.set(enabled);
        workbenchDefaultPersistWorldValue.save();
    }

    public static ProgressionPreset workbenchProgressionPreset() {
        return workbenchProgressionPreset;
    }

    public static void setWorkbenchProgressionPreset(ProgressionPreset preset) {
        workbenchProgressionPreset = preset == null ? workbenchProgressionPresetDefault : preset;
        workbenchProgressionPresetValue.set(workbenchProgressionPreset);
        workbenchProgressionPresetValue.save();
    }

    public static boolean workbenchGridSnap() {
        return workbenchGridSnap;
    }

    public static void setWorkbenchGridSnap(boolean enabled) {
        workbenchGridSnap = enabled;
        workbenchGridSnapValue.set(enabled);
        workbenchGridSnapValue.save();
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
        calculatorRuleSelection = calculatorRuleSelectionValue.get();
        calculatorAutoDeepenEnabled = calculatorAutoDeepenEnabledValue.get();
        calculatorDeepenMaxGenerated = calculatorDeepenMaxGeneratedValue.get();
        executionUsesCalculatorPathSettings = executionUsesCalculatorPathSettingsValue.get();
        workbenchTheme = workbenchThemeValue.get();
        workbenchReducedMotion = workbenchReducedMotionValue.get();
        workbenchDefaultDagLayout = workbenchDefaultDagLayoutValue.get();
        workbenchDefaultDagEdgeStyle = workbenchDefaultDagEdgeStyleValue.get();
        workbenchDefaultPreset = workbenchDefaultPresetValue.get();
        workbenchSoftBudgetMs = Math
                .max(workbenchSoftBudgetMsMin, Math.min(workbenchSoftBudgetMsMax, workbenchSoftBudgetMsValue.get()));
        workbenchHardBudgetMs = Math
                .max(workbenchHardBudgetMsMin, Math.min(workbenchHardBudgetMsMax, workbenchHardBudgetMsValue.get()));
        workbenchDefaultPersistWorld = workbenchDefaultPersistWorldValue.get();
        workbenchProgressionPreset = workbenchProgressionPresetValue.get();
        workbenchGridSnap = workbenchGridSnapValue.get();
    }

    private static int clamp(int value) {
        return Math.max(overlayRateMin, Math.min(overlayRateMax, value));
    }

}
