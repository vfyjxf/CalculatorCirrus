package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.InventorySensitivityProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.config.CalculatorRuleSelection;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSwitchRowWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusRowWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class CalculatorSettingsPage extends CirrusScrollablePageWidget {
    private static final int policyChoiceWidth = 84;
    private static final int ruleChoiceWidth = 104;
    private static final int plannerChoiceWidth = 92;
    private static final int sensitivityChoiceWidth = 82;
    private static final int categoryTitleHeight = 12;
    private static final int categoryPaddingTop = 6;
    private static final int categoryPaddingRight = 6;
    private static final int categoryPaddingBottom = 5;
    private static final int categoryPaddingLeft = 6;
    private static final int categoryGap = 4;
    private static final int bodyGap = 3;
    private static final int rowHeight = 22;
    private static final int rowGap = 6;
    private static final int rowPaddingVertical = 2;
    private static final int rowPaddingHorizontal = 5;
    private static final int labelPaddingLeft = 2;
    private static final int labelPaddingRight = 3;
    private static final int numberInputWidth = 58;

    public CalculatorSettingsPage() {
        CirrusSectionWidget planning = settingsSection(CirrusLangs.Calculator.Settings.categoryPlanning);
        planning.addContent(ruleRow());
        planning.addContent(plannerModeRow(
            CirrusLangs.Calculator.Settings.plannerMode,
            CirrusLangs.Calculator.Settings.plannerModeDesc,
            ExecutionOverlayClientConfig::calculatorPlannerMode,
            ExecutionOverlayClientConfig::setCalculatorPlannerMode
        ));
        planning.addContent(switchRow(
            CirrusLangs.Calculator.Settings.useLiveStock,
            CirrusLangs.Calculator.Settings.useLiveStockDesc,
            ExecutionOverlayClientConfig::calculatorUseLiveStock,
            ExecutionOverlayClientConfig::setCalculatorUseLiveStock
        ));
        planning.addContent(inventorySensitivityRow());
        addWidget(planning);

        CirrusSectionWidget execution = settingsSection(CirrusLangs.Calculator.Settings.categoryExecution);
        execution.addContent(switchRow(
            CirrusLangs.Calculator.Settings.trace,
            CirrusLangs.Calculator.Settings.traceDesc,
            ExecutionOverlayClientConfig::overlayTraceEnabled,
            ExecutionOverlayClientConfig::setOverlayTraceEnabled
        ));
        execution.addContent(switchRow(
            CirrusLangs.Calculator.Settings.jeiTargetAutoClose,
            CirrusLangs.Calculator.Settings.jeiTargetAutoCloseDesc,
            ExecutionOverlayClientConfig::jeiTargetButtonAutoClose,
            ExecutionOverlayClientConfig::setJeiTargetButtonAutoClose
        ));
        execution.addContent(policyRow());
        execution.addContent(switchRow(
            CirrusLangs.Calculator.Settings.executionUseCalculatorPath,
            CirrusLangs.Calculator.Settings.executionUseCalculatorPathDesc,
            ExecutionOverlayClientConfig::executionUsesCalculatorPathSettings,
            ExecutionOverlayClientConfig::setExecutionUsesCalculatorPathSettings
        ));
        execution.addContent(plannerModeRow(
            CirrusLangs.Calculator.Settings.executionPlannerMode,
            CirrusLangs.Calculator.Settings.executionPlannerModeDesc,
            ExecutionOverlayClientConfig::executionPlannerMode,
            ExecutionOverlayClientConfig::setExecutionPlannerMode
        ));
        addWidget(execution);

        CirrusSectionWidget database = settingsSection(CirrusLangs.Calculator.Settings.categoryDatabase);
        database.addContent(switchRow(
            CirrusLangs.Calculator.Settings.databaseAutoRefresh,
            CirrusLangs.Calculator.Settings.databaseAutoRefreshDesc,
            ExecutionOverlayClientConfig::calculatorDatabaseAutoRefresh,
            ExecutionOverlayClientConfig::setCalculatorDatabaseAutoRefresh
        ));
        addWidget(database);

        CirrusSectionWidget rules = settingsSection(CirrusLangs.Calculator.Settings.categoryRules);
        rules.addContent(switchRow(
            CirrusLangs.Calculator.Settings.readOtherWorlds,
            CirrusLangs.Calculator.Settings.readOtherWorldsDesc,
            ExecutionOverlayClientConfig::calculatorRulesReadOtherWorlds,
            ExecutionOverlayClientConfig::setCalculatorRulesReadOtherWorlds
        ));
        addWidget(rules);
    }

    public boolean debugUsesConfigBackedControls() {
        return true;
    }

    private static Widget switchRow(LangEntry label, LangEntry description, BooleanSupplier value, Consumer<Boolean> setter) {
        Supplier<Tooltip> tooltip = () -> switchTooltip(label, description, value);
        return styleSettingsRow(CirrusSwitchRowWidget.create(label, value, setter, tooltip), tooltip);
    }

    private static Widget numberRow(LangEntry label, LangEntry description, LongSupplier value, LongConsumer setter, long min, long max) {
        WidgetGroup<Widget> row = baseRow();
        row.addWidget(rowLabel(label));
        NumberInputWidget input = NumberInputWidget.create(value.getAsLong())
            .setRange(min, max)
            .setScrollStep(10)
            .onValueChanged(setter);
        Supplier<Tooltip> tooltip = () -> numberTooltip(label, description, input.getValue(), min, max);
        input.tooltip(tooltip);
        input.useStyle(sizeOf(numberInputWidth, CirrusChoiceBoxWidget.controlHeight()), flexShrink(0));
        row.addWidget(input);
        return styleSettingsRow(row, tooltip);
    }

    private static Widget policyRow() {
        WidgetGroup<Widget> row = baseRow();
        row.addWidget(rowLabel(CirrusLangs.Calculator.Settings.stockPolicy));
        CirrusChoiceBoxWidget<ExecutionObservedStockPolicy> value = new CirrusChoiceBoxWidget<ExecutionObservedStockPolicy>(policyChoiceWidth)
            .option(ExecutionObservedStockPolicy.consumeObservedStock, CirrusLangs.Calculator.Settings.stockPolicyConsume)
            .option(ExecutionObservedStockPolicy.preservePlannedStock, CirrusLangs.Calculator.Settings.stockPolicyPreserve)
            .value(ExecutionOverlayClientConfig.observedStockPolicy())
            .onChanged(ExecutionOverlayClientConfig::setObservedStockPolicy);
        Supplier<Tooltip> tooltip = () -> choiceTooltip(
            CirrusLangs.Calculator.Settings.stockPolicy,
            CirrusLangs.Calculator.Settings.stockPolicyDesc,
            stockPolicyLabel(value.value()),
            CirrusLangs.Calculator.Settings.stockPolicyConsume,
            CirrusLangs.Calculator.Settings.stockPolicyPreserve
        );
        value.tooltip(tooltip);
        row.addWidget(value);
        return styleSettingsRow(row, tooltip);
    }

    private static CirrusFittedTextWidget rowLabel(LangEntry text) {
        CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, textPrimary);
        label.textPadding(labelPaddingLeft, labelPaddingRight);
        label.useStyle(flexGrow(1), minWidth(0), heightOf(12));
        return label;
    }

    private static WidgetGroup<Widget> baseRow() {
        return styleSettingsRow(CirrusRowWidget.flat(rowHeight, rowGap), null);
    }

    private static <W extends Widget> W styleSettingsRow(W row, Supplier<Tooltip> tooltipSupplier) {
        row.useStyle(
            heightOf(rowHeight),
            gap(rowGap),
            minWidth(0),
            overflow(Overflow.HIDDEN),
            padding(rowPaddingVertical, rowPaddingHorizontal, rowPaddingVertical, rowPaddingHorizontal),
            boxSizingBorderBox()
        );
        if (tooltipSupplier != null) {
            row.onHoverTooltip((mouseX, mouseY) -> tooltipSupplier.get());
        }
        if (row instanceof WidgetGroup<?> group
            && !group.children().isEmpty()
            && group.children().getFirst() instanceof CirrusFittedTextWidget label) {
            label.textPadding(labelPaddingLeft, labelPaddingRight);
            label.useStyle(heightOf(12), minWidth(0));
            if (tooltipSupplier != null) {
                label.onHoverTooltip((mouseX, mouseY) -> tooltipSupplier.get());
            }
        }
        return row;
    }

    private static CirrusSectionWidget settingsSection(LangEntry title) {
        CirrusSectionWidget section = new CirrusSectionWidget(title)
            .chrome(CirrusSectionWidget.Chrome.outlinedFlat);
        section.useStyle(
            padding(categoryPaddingTop, categoryPaddingRight, categoryPaddingBottom, categoryPaddingLeft),
            gap(categoryGap),
            minWidth(0),
            overflow(Overflow.HIDDEN)
        );
        section.titleWidget()
            .textPadding(2, 1)
            .shadow(false)
            .useStyle(heightOf(categoryTitleHeight), minWidth(0));
        section.body().useStyle(gap(bodyGap), minWidth(0), overflow(Overflow.HIDDEN));
        return section;
    }

    public static int policyChoiceWidth() {
        return policyChoiceWidth;
    }

    public static int ruleChoiceWidth() {
        return ruleChoiceWidth;
    }

    public static int plannerChoiceWidth() {
        return plannerChoiceWidth;
    }

    public static int sensitivityChoiceWidth() {
        return sensitivityChoiceWidth;
    }

    public static int settingsCategoryTitleHeight() {
        return categoryTitleHeight;
    }

    public static int settingsCategoryGap() {
        return categoryGap;
    }

    public static int settingsBodyGap() {
        return bodyGap;
    }

    public static int settingsRowHeight() {
        return rowHeight;
    }

    public static int settingsRowGap() {
        return rowGap;
    }

    public static int settingsLabelPaddingLeft() {
        return labelPaddingLeft;
    }

    public static int settingsLabelPaddingRight() {
        return labelPaddingRight;
    }

    public static int settingsNumberInputWidth() {
        return numberInputWidth;
    }

    private static Widget ruleRow() {
        WidgetGroup<Widget> row = baseRow();
        row.addWidget(rowLabel(CirrusLangs.Calculator.Settings.calculatorRule));
        var value = new CirrusChoiceBoxWidget<CalculatorRuleSelection>(ruleChoiceWidth)
            .option(CalculatorRuleSelection.preview, CirrusLangs.Calculator.Settings.calculatorRulePreview)
            .option(CalculatorRuleSelection.active, CirrusLangs.Calculator.Settings.calculatorRuleActive)
            .option(CalculatorRuleSelection.execution, CirrusLangs.Calculator.Settings.calculatorRuleExecution)
            .option(CalculatorRuleSelection.strict, CirrusLangs.Calculator.Settings.calculatorRuleStrict)
            .option(CalculatorRuleSelection.assured, CirrusLangs.Calculator.Settings.calculatorRuleAssured)
            .value(ExecutionOverlayClientConfig.calculatorRuleSelection())
            .onChanged(ExecutionOverlayClientConfig::setCalculatorRuleSelection);
        Supplier<Tooltip> tooltip = () -> choiceTooltip(
            CirrusLangs.Calculator.Settings.calculatorRule,
            CirrusLangs.Calculator.Settings.calculatorRuleDesc,
            calculatorRuleLabel(value.value()),
            CirrusLangs.Calculator.Settings.calculatorRulePreview,
            CirrusLangs.Calculator.Settings.calculatorRuleActive,
            CirrusLangs.Calculator.Settings.calculatorRuleExecution,
            CirrusLangs.Calculator.Settings.calculatorRuleStrict,
            CirrusLangs.Calculator.Settings.calculatorRuleAssured
        );
        value.tooltip(tooltip);
        row.addWidget(value);
        return styleSettingsRow(row, tooltip);
    }

    private static Widget inventorySensitivityRow() {
        WidgetGroup<Widget> row = baseRow();
        row.addWidget(rowLabel(CirrusLangs.Calculator.Settings.inventorySensitivity));
        CirrusChoiceBoxWidget<InventorySensitivityProfile> value = new CirrusChoiceBoxWidget<InventorySensitivityProfile>(sensitivityChoiceWidth)
            .option(InventorySensitivityProfile.high, CirrusLangs.Calculator.Settings.inventorySensitivityHigh)
            .option(InventorySensitivityProfile.balanced, CirrusLangs.Calculator.Settings.inventorySensitivityBalanced)
            .option(InventorySensitivityProfile.low, CirrusLangs.Calculator.Settings.inventorySensitivityLow)
            .value(ExecutionOverlayClientConfig.calculatorInventorySensitivity())
            .onChanged(ExecutionOverlayClientConfig::setCalculatorInventorySensitivity);
        Supplier<Tooltip> tooltip = () -> choiceTooltip(
            CirrusLangs.Calculator.Settings.inventorySensitivity,
            CirrusLangs.Calculator.Settings.inventorySensitivityDesc,
            sensitivityLabel(value.value()),
            CirrusLangs.Calculator.Settings.inventorySensitivityHigh,
            CirrusLangs.Calculator.Settings.inventorySensitivityBalanced,
            CirrusLangs.Calculator.Settings.inventorySensitivityLow
        );
        value.tooltip(tooltip);
        row.addWidget(value);
        return styleSettingsRow(row, tooltip);
    }

    private static Widget plannerModeRow(
        LangEntry label,
        LangEntry description,
        Supplier<PlannerMode> getter,
        Consumer<PlannerMode> setter
    ) {
        WidgetGroup<Widget> row = baseRow();
        row.addWidget(rowLabel(label));
        CirrusChoiceBoxWidget<PlannerMode> value = new CirrusChoiceBoxWidget<PlannerMode>(plannerChoiceWidth)
            .option(PlannerMode.standard, CirrusLangs.Calculator.Settings.plannerModeStandard)
            .option(PlannerMode.assuredBounded, CirrusLangs.Calculator.Settings.plannerModeAssuredBounded)
            .option(PlannerMode.assuredProven, CirrusLangs.Calculator.Settings.plannerModeAssuredProven)
            .value(getter.get())
            .onChanged(setter);
        Supplier<Tooltip> tooltip = () -> choiceTooltip(
            label,
            description,
            plannerModeLabel(value.value()),
            CirrusLangs.Calculator.Settings.plannerModeStandard,
            CirrusLangs.Calculator.Settings.plannerModeAssuredBounded,
            CirrusLangs.Calculator.Settings.plannerModeAssuredProven
        );
        value.tooltip(tooltip);
        row.addWidget(value);
        return styleSettingsRow(row, tooltip);
    }

    private static Tooltip switchTooltip(LangEntry label, LangEntry description, BooleanSupplier value) {
        Tooltip tooltip = settingTooltip(label, description);
        tooltip.add(() -> ExecutionOverlayUi.tooltipAccentText(
            CirrusLangs.Calculator.Settings.switchState.string(
                (value.getAsBoolean()
                    ? CirrusLangs.Calculator.Settings.switchOn
                    : CirrusLangs.Calculator.Settings.switchOff).string()
            )
        ));
        return tooltip;
    }

    private static Tooltip numberTooltip(LangEntry label, LangEntry description, long value, long min, long max) {
        Tooltip tooltip = settingTooltip(label, description);
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(CirrusLangs.Calculator.Settings.currentValue.string(value)));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Settings.range, min, max));
        return tooltip;
    }

    private static Tooltip choiceTooltip(LangEntry label, LangEntry description, LangEntry current, LangEntry... options) {
        Tooltip tooltip = settingTooltip(label, description);
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(CirrusLangs.Calculator.Settings.currentValue.string(current.string())));
        Component optionsLine = CirrusLangs.Calculator.Settings.options.component(joinOptions(options));
        tooltip.add(ExecutionOverlayUi.tooltipBodyText(optionsLine.getString()));
        return tooltip;
    }

    private static Tooltip settingTooltip(LangEntry label, LangEntry description) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(label));
        tooltip.add(ExecutionOverlayUi.tooltipBody(description));
        return tooltip;
    }

    private static String joinOptions(LangEntry... options) {
        StringBuilder out = new StringBuilder();
        for (LangEntry option : options) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(option.string());
        }
        return out.toString();
    }

    private static LangEntry calculatorRuleLabel(CalculatorRuleSelection value) {
        return switch (value) {
            case preview -> CirrusLangs.Calculator.Settings.calculatorRulePreview;
            case active -> CirrusLangs.Calculator.Settings.calculatorRuleActive;
            case execution -> CirrusLangs.Calculator.Settings.calculatorRuleExecution;
            case strict -> CirrusLangs.Calculator.Settings.calculatorRuleStrict;
            case assured -> CirrusLangs.Calculator.Settings.calculatorRuleAssured;
        };
    }

    private static LangEntry sensitivityLabel(InventorySensitivityProfile value) {
        return switch (value) {
            case high -> CirrusLangs.Calculator.Settings.inventorySensitivityHigh;
            case balanced -> CirrusLangs.Calculator.Settings.inventorySensitivityBalanced;
            case low -> CirrusLangs.Calculator.Settings.inventorySensitivityLow;
        };
    }

    private static LangEntry stockPolicyLabel(ExecutionObservedStockPolicy value) {
        return switch (value) {
            case consumeObservedStock -> CirrusLangs.Calculator.Settings.stockPolicyConsume;
            case preservePlannedStock -> CirrusLangs.Calculator.Settings.stockPolicyPreserve;
        };
    }

    private static LangEntry plannerModeLabel(PlannerMode value) {
        return switch (value) {
            case standard -> CirrusLangs.Calculator.Settings.plannerModeStandard;
            case assuredBounded -> CirrusLangs.Calculator.Settings.plannerModeAssuredBounded;
            case assuredProven -> CirrusLangs.Calculator.Settings.plannerModeAssuredProven;
        };
    }
}
