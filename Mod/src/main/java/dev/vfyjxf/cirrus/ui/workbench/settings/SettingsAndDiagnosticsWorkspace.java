package dev.vfyjxf.cirrus.ui.workbench.settings;

import com.google.gson.JsonObject;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.config.CalculatorRuleSelection;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSegmentedControlWidget;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import dev.vfyjxf.taffy.style.LengthPercentage;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignSelfStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPx;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Settings and live diagnostics dashboard: settings cards on the left, planning-kernel telemetry on the right.
 */
public class SettingsAndDiagnosticsWorkspace extends CompositeWidget<Widget> {

    public static final int rowHeight = 16;
    public static final int labelWidth = 140;
    public static final int choiceWidth = 114;
    public static final int gaugeSize = 30;
    public static final int statsRowHeight = 12;
    public static final int statsRowCount = 11;

    private final SettingsHost host;
    private final @Nullable WorkbenchShellContext explicitShell;
    private WorkbenchThemeMode fallbackTheme;
    private final Supplier<CirrusPalette> palette;
    private @Nullable CirrusChoiceBoxWidget<WorkbenchThemeMode> themeChoice;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public SettingsAndDiagnosticsWorkspace(SettingsHost host, WorkbenchThemeMode theme) {
        this(host, null, theme);
    }

    public SettingsAndDiagnosticsWorkspace(SettingsHost host, WorkbenchShellContext shellContext) {
        this(host, Objects.requireNonNull(shellContext, "shellContext"), shellContext.theme());
    }

    private SettingsAndDiagnosticsWorkspace(
        SettingsHost host,
        @Nullable WorkbenchShellContext explicitShell,
        @Nullable WorkbenchThemeMode initialTheme
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.explicitShell = explicitShell;
        this.fallbackTheme = initialTheme == null ? WorkbenchThemeMode.defaultMode() : initialTheme;
        this.palette = this::livePalette;
        addStyleClass("workbench-dock");
        useStyle(sizeFull(), flexRow(), gap(8), padding(8), boxSizingBorderBox());
        syncTheme(theme());
        onMount((scene, context, handle) -> CirrusUiTheme.bindThemeListener(this, handle, () -> syncTheme(theme())));
        addWidget(settingsColumn());
        addWidget(diagnosticsColumn());
    }

    public SettingsHost host() {
        return host;
    }

    public WorkbenchThemeMode theme() {
        WorkbenchShellContext shell = resolvedShell();
        return shell != null ? shell.theme() : fallbackTheme;
    }

    public Supplier<CirrusPalette> palette() {
        return palette;
    }

    public @Nullable CirrusChoiceBoxWidget<WorkbenchThemeMode> themeChoice() {
        return themeChoice;
    }

    public void syncTheme(@Nullable WorkbenchThemeMode next) {
        if (next != null) {
            this.fallbackTheme = next;
        }
        WorkbenchThemeMode activeMode = theme();
        if (themeChoice != null && themeChoice.value() != activeMode) {
            themeChoice.value(activeMode);
        }
        if (activeMode == appliedTheme) {
            return;
        }
        appliedTheme = activeMode;
        defaultStyle(UIStyle.of(background(CirrusThemeTextures.of(activeMode).dockIsland())));
    }

    private @Nullable WorkbenchShellContext resolvedShell() {
        if (explicitShell != null) {
            return explicitShell;
        }
        return CirrusUiTheme.shellContext(this);
    }

    private CirrusPalette livePalette() {
        WorkbenchShellContext shell = resolvedShell();
        if (shell != null) {
            return shell.palette();
        }
        return lifecycle().mounted()
                ? CirrusPalette.of(scene().theme(), fallbackTheme)
                : CirrusPalette.of(fallbackTheme);
    }

    private void applySelectedTheme(@Nullable WorkbenchThemeMode selected) {
        if (selected == null) {
            return;
        }
        this.fallbackTheme = selected;
        try {
            ExecutionOverlayClientConfig.setWorkbenchTheme(selected);
        } catch (RuntimeException | LinkageError ignored) {
            // Headless unit tests run without a loaded NeoForge ModConfigSpec.
        }
        WorkbenchShellContext shell = resolvedShell();
        if (shell != null) {
            shell.setTheme(selected);
        } else {
            syncTheme(selected);
        }
    }

    static LangEntry le(String suffix) {
        return LangEntry.of("cirrus.ui.workbench.settings." + suffix);
    }

    static Component tr(String suffix, Object... args) {
        return Component.translatable("cirrus.ui.workbench.settings." + suffix, args);
    }

    private Widget settingsColumn() {
        SettingsColumn column = new SettingsColumn();
        column.useStyle(
            widthOf(TaffyDimension.AUTO),
            heightOf(TaffyDimension.from(LengthPercentage.percent(1.0f))),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            minWidth(0),
            minHeight(0),
            boxSizingBorderBox()
        );

        CirrusSectionWidget appearance = column.section(CirrusLangs.Workbench.Settings.appearance);
        themeChoice = new CirrusChoiceBoxWidget<WorkbenchThemeMode>(choiceWidth)
                .option(WorkbenchThemeMode.obsidianBlueprint, CirrusLangs.Workbench.Theme.obsidianBlueprint)
                .option(WorkbenchThemeMode.atelierStone, CirrusLangs.Workbench.Theme.atelierStone).value(theme())
                .onChanged(this::applySelectedTheme);
        appearance.addContent(labelRow(tr("theme"), themeChoice));
        appearance.addContent(
            switchRow(
                CirrusLangs.Workbench.Settings.reducedMotion,
                ExecutionOverlayClientConfig::workbenchReducedMotion,
                ExecutionOverlayClientConfig::setWorkbenchReducedMotion
            )
        );
        appearance.addContent(
            labelRow(
                tr("dag_layout"),
                new CirrusChoiceBoxWidget<DagLayoutMode>(choiceWidth)
                        .option(DagLayoutMode.horizontalLr, CirrusLangs.Workbench.Flow.layoutLr)
                        .option(DagLayoutMode.verticalTb, CirrusLangs.Workbench.Flow.layoutTb)
                        .option(DagLayoutMode.horizontalRl, CirrusLangs.Workbench.Flow.layoutRl)
                        .option(DagLayoutMode.verticalBt, CirrusLangs.Workbench.Flow.layoutBt)
                        .option(DagLayoutMode.systemSwimlane, CirrusLangs.Workbench.Flow.layoutSwimlane)
                        .value(ExecutionOverlayClientConfig.workbenchDefaultDagLayout())
                        .onChanged(ExecutionOverlayClientConfig::setWorkbenchDefaultDagLayout)
            )
        );
        appearance.addContent(
            labelRow(
                tr("dag_edge_style"),
                new CirrusChoiceBoxWidget<DagEdgeStyle>(choiceWidth)
                        .option(DagEdgeStyle.orthogonal, CirrusLangs.Workbench.Flow.edgeOrthogonal)
                        .option(DagEdgeStyle.straight, CirrusLangs.Workbench.Flow.edgeStraight)
                        .option(DagEdgeStyle.curved, CirrusLangs.Workbench.Flow.edgeCurved)
                        .value(ExecutionOverlayClientConfig.workbenchDefaultDagEdgeStyle())
                        .onChanged(ExecutionOverlayClientConfig::setWorkbenchDefaultDagEdgeStyle)
            )
        );
        appearance.addContent(
            switchRow(
                CirrusLangs.Workbench.Settings.gridSnap,
                ExecutionOverlayClientConfig::workbenchGridSnap,
                ExecutionOverlayClientConfig::setWorkbenchGridSnap
            )
        );

        CirrusSectionWidget planner = column.section(CirrusLangs.Workbench.Settings.planner);
        CirrusChoiceBoxWidget<ObjectivePreset> presetChoice = new CirrusChoiceBoxWidget<ObjectivePreset>(choiceWidth)
                .option(ObjectivePreset.autoSmart, CirrusLangs.Planning.Strategy.balanced)
                .option(ObjectivePreset.stayAtBase, CirrusLangs.Planning.Strategy.noNewMachines)
                .option(ObjectivePreset.quickHandcraft, CirrusLangs.Planning.Strategy.minHumanTime)
                .option(ObjectivePreset.factoryScale, CirrusLangs.Planning.Strategy.investAutomation)
                .option(ObjectivePreset.frugalMaterials, CirrusLangs.Planning.Strategy.minRawMaterials)
                .option(ObjectivePreset.lowCognitive, CirrusLangs.Planning.Strategy.alternative);
        presetChoice.value(ExecutionOverlayClientConfig.workbenchDefaultPreset())
                .onChanged(ExecutionOverlayClientConfig::setWorkbenchDefaultPreset);
        planner.addContent(labelRow(tr("default_preset"), presetChoice));
        planner.addContent(
            numberRow(
                CirrusLangs.Workbench.Settings.softBudget,
                ExecutionOverlayClientConfig.workbenchSoftBudgetMs(),
                ExecutionOverlayClientConfig.workbenchSoftBudgetMsMin,
                ExecutionOverlayClientConfig.workbenchSoftBudgetMsMax,
                value -> ExecutionOverlayClientConfig.setWorkbenchSoftBudgetMs((int) value)
            )
        );
        planner.addContent(
            numberRow(
                CirrusLangs.Workbench.Settings.hardBudget,
                ExecutionOverlayClientConfig.workbenchHardBudgetMs(),
                ExecutionOverlayClientConfig.workbenchHardBudgetMsMin,
                ExecutionOverlayClientConfig.workbenchHardBudgetMsMax,
                value -> ExecutionOverlayClientConfig.setWorkbenchHardBudgetMs((int) value)
            )
        );
        planner.addContent(
            labelRow(
                CirrusLangs.Workbench.Settings.defaultScope.component(),
                new CirrusSegmentedControlWidget<String>(rowHeight)
                        .option("session", CirrusLangs.Workbench.Copilot.scopeSession, 52)
                        .option("world", CirrusLangs.Workbench.Copilot.scopeWorld, 66)
                        .value(ExecutionOverlayClientConfig.workbenchDefaultPersistWorld() ? "world" : "session")
                        .onChanged(
                            value -> ExecutionOverlayClientConfig.setWorkbenchDefaultPersistWorld("world".equals(value))
                        )
            )
        );
        planner.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.useLiveStock,
                ExecutionOverlayClientConfig::calculatorUseLiveStock,
                ExecutionOverlayClientConfig::setCalculatorUseLiveStock
            )
        );
        CirrusChoiceBoxWidget<ProgressionPreset> progressionChoice = new CirrusChoiceBoxWidget<ProgressionPreset>(
            choiceWidth
        );
        for (ProgressionPreset preset : ProgressionPreset.values()) {
            progressionChoice.option(preset, preset.labelKey());
        }
        progressionChoice.value(ExecutionOverlayClientConfig.workbenchProgressionPreset())
                .onChanged(ExecutionOverlayClientConfig::setWorkbenchProgressionPreset);
        planner.addContent(labelRow(CirrusLangs.Workbench.Settings.progression.component(), progressionChoice));
        planner.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.recipeScreening,
                ExecutionOverlayClientConfig::calculatorRecipeScreeningEnabled,
                ExecutionOverlayClientConfig::setCalculatorRecipeScreeningEnabled
            )
        );
        planner.addContent(
            labelRow(
                CirrusLangs.Calculator.Settings.calculatorRule.component(),
                new CirrusChoiceBoxWidget<CalculatorRuleSelection>(choiceWidth)
                        .option(CalculatorRuleSelection.preview, CirrusLangs.Calculator.Settings.calculatorRulePreview)
                        .option(CalculatorRuleSelection.active, CirrusLangs.Calculator.Settings.calculatorRuleActive)
                        .option(
                            CalculatorRuleSelection.execution,
                            CirrusLangs.Calculator.Settings.calculatorRuleExecution
                        ).option(CalculatorRuleSelection.strict, CirrusLangs.Calculator.Settings.calculatorRuleStrict)
                        .option(CalculatorRuleSelection.assured, CirrusLangs.Calculator.Settings.calculatorRuleAssured)
                        .value(ExecutionOverlayClientConfig.calculatorRuleSelection())
                        .onChanged(ExecutionOverlayClientConfig::setCalculatorRuleSelection)
            )
        );
        planner.addContent(
            switchRow(
                tr("persist_world"),
                ExecutionOverlayClientConfig::workbenchDefaultPersistWorld,
                ExecutionOverlayClientConfig::setWorkbenchDefaultPersistWorld
            )
        );

        CirrusSectionWidget execution = column.section(CirrusLangs.Workbench.Settings.execution);
        execution.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.trace,
                ExecutionOverlayClientConfig::overlayTraceEnabled,
                ExecutionOverlayClientConfig::setOverlayTraceEnabled
            )
        );
        execution.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.jeiTargetAutoClose,
                ExecutionOverlayClientConfig::jeiTargetButtonAutoClose,
                ExecutionOverlayClientConfig::setJeiTargetButtonAutoClose
            )
        );
        execution.addContent(
            labelRow(
                CirrusLangs.Calculator.Settings.stockPolicy.component(),
                new CirrusChoiceBoxWidget<ExecutionObservedStockPolicy>(choiceWidth)
                        .option(
                            ExecutionObservedStockPolicy.consumeObservedStock,
                            CirrusLangs.Calculator.Settings.stockPolicyConsume
                        )
                        .option(
                            ExecutionObservedStockPolicy.preservePlannedStock,
                            CirrusLangs.Calculator.Settings.stockPolicyPreserve
                        ).value(ExecutionOverlayClientConfig.observedStockPolicy())
                        .onChanged(ExecutionOverlayClientConfig::setObservedStockPolicy)
            )
        );
        execution.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.executionUseCalculatorPath,
                ExecutionOverlayClientConfig::executionUsesCalculatorPathSettings,
                ExecutionOverlayClientConfig::setExecutionUsesCalculatorPathSettings
            )
        );

        CirrusSectionWidget database = column.section(CirrusLangs.Workbench.Settings.database);
        database.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.databaseAutoRefresh,
                ExecutionOverlayClientConfig::calculatorDatabaseAutoRefresh,
                ExecutionOverlayClientConfig::setCalculatorDatabaseAutoRefresh
            )
        );
        database.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.projectionUsesGlobalDatabase,
                ExecutionOverlayClientConfig::calculatorProjectionUsesGlobalDatabase,
                ExecutionOverlayClientConfig::setCalculatorProjectionUsesGlobalDatabase
            )
        );
        database.addContent(
            switchRow(
                CirrusLangs.Calculator.Settings.readOtherWorlds,
                ExecutionOverlayClientConfig::calculatorRulesReadOtherWorlds,
                ExecutionOverlayClientConfig::setCalculatorRulesReadOtherWorlds
            )
        );
        return column;
    }

    private Widget diagnosticsColumn() {
        SettingsColumn column = new SettingsColumn();
        column.useStyle(
            widthOf(TaffyDimension.AUTO),
            heightOf(TaffyDimension.from(LengthPercentage.percent(1.0f))),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            minWidth(0),
            minHeight(0),
            boxSizingBorderBox()
        );

        CirrusSectionWidget diagnostics = column.section(CirrusLangs.Workbench.Settings.diagnostics);
        WidgetGroup<Widget> gauges = new WidgetGroup<>();
        gauges.useStyle(flexRow(), alignItemsCenter(), alignSelfStretch(), gap(4), minWidth(0));
        gauges.addWidget(gaugeCard(tr("keep_rate"), this::keepRate, this::keepRateText, "info"));
        gauges.addWidget(gaugeCard(tr("compile_rate"), this::compileRate, this::compileRateText, "ok"));
        gauges.addWidget(gaugeCard(tr("system_health"), this::systemHealth, this::systemHealthText, "ok"));
        diagnostics.addContent(gauges);
        StatsTable table = new StatsTable(host, palette);
        table.useStyle(flexGrow(1), minWidth(0), heightOf(statsRowCount * statsRowHeight + 6));
        diagnostics.addContent(table);
        WidgetGroup<Widget> actions = new WidgetGroup<>();
        actions.useStyle(flexRow(), alignItemsCenter(), heightOf(rowHeight), gap(6), minWidth(0));
        CirrusButtonWidget recompileBtn = new CirrusButtonWidget(104, rowHeight)
                .label(CirrusLangs.Workbench.Settings.recompile).onPress(this::recompile);
        recompileBtn.useStyle(flexShrink(0));
        actions.addWidget(recompileBtn);
        CirrusButtonWidget exportBtn = new CirrusButtonWidget(88, rowHeight)
                .label(CirrusLangs.Workbench.Settings.exportStats).onPress(this::exportDiagnostics);
        exportBtn.useStyle(flexShrink(0));
        actions.addWidget(exportBtn);
        actions.addWidget(
            new LiveText(
                () -> host.compiling()
                        ? le("recompile_running").component()
                        : tr("recompile_done", publishedVersion(), host.lastCompileMillis()),
                palette,
                flexGrow(1),
                false
            )
        );
        diagnostics.addContent(actions);
        return column;
    }

    private void recompile() {
        host.recompile();
    }

    private void exportDiagnostics() {
        JsonObject root = diagnosticsJson();
        SettingsHost.Transfer transfer = host.writeDiagnostics(root);
        host.toast(transfer.message());
    }

    private long publishedVersion() {
        return host.published().map(CirrusPlanningRuntime.PublishedSnapshot::version).orElse(0L);
    }

    private double keepRate() {
        return host.published().map(snapshot -> {
            CirrusCatalogAdapter.CaptureStats capture = snapshot.captureStats();
            if (capture.recipesVisited() <= 0) {
                return ratio(snapshot.graph().stats().compiledRecipes(), snapshot.graph().stats().totalRawRecipes());
            }
            return ratio(capture.recipesKept(), capture.recipesVisited());
        }).orElse(0.0);
    }

    private double compileRate() {
        return host.published().map(
            snapshot -> ratio(snapshot.graph().stats().compiledRecipes(), snapshot.graph().stats().totalRawRecipes())
        ).orElse(0.0);
    }

    private double systemHealth() {
        return host.published().map(snapshot -> {
            CirrusCatalogAdapter.CaptureStats capture = snapshot.captureStats();
            if (capture.systemsVisited() <= 0) {
                return 1.0;
            }
            return 1.0 - ratio(capture.systemsFailed(), capture.systemsVisited());
        }).orElse(0.0);
    }

    private Component keepRateText() {
        if (host.published().isEmpty()) {
            return Component.literal("-");
        }
        CirrusCatalogAdapter.CaptureStats capture = host.published().get().captureStats();
        return Component.literal(capture.recipesKept() + " / " + capture.recipesVisited());
    }

    private Component compileRateText() {
        if (host.published().isEmpty()) {
            return Component.literal("-");
        }
        GraphCompilationStats stats = host.published().get().graph().stats();
        return Component.literal(stats.compiledRecipes() + " / " + stats.totalRawRecipes());
    }

    private Component systemHealthText() {
        if (host.published().isEmpty()) {
            return Component.literal("-");
        }
        CirrusCatalogAdapter.CaptureStats capture = host.published().get().captureStats();
        return Component
                .literal((capture.systemsVisited() - capture.systemsFailed()) + " / " + capture.systemsVisited());
    }

    private Widget gaugeCard(
        Component label,
        DoubleSupplier fraction,
        Supplier<Component> readout,
        String variantClass
    ) {
        WidgetGroup<Widget> card = new WidgetGroup<>();
        card.useStyle(
            flexColumn(),
            alignItemsCenter(),
            flexGrow(1),
            flexShrink(1),
            flexBasisPx(0f),
            gap(3),
            minWidth(0)
        );
        CirrusFittedTextWidget title = CirrusFittedTextWidget.secondary(label)
                .align(CirrusFittedTextWidget.Align.center);
        title.useStyle(alignSelfStretch(), heightOf(12), minWidth(0));
        card.addWidget(title);
        RingWidget gauge = RingWidget.of(fraction).setThickness(4);
        gauge.addStyleClass(variantClass);
        gauge.useStyle(sizeOf(gaugeSize, gaugeSize), flexShrink(0));
        card.addWidget(gauge);
        LiveText value = new LiveText(readout, palette, alignSelfStretch(), true);
        card.addWidget(value);
        return card;
    }

    private Widget labelRow(Component label, Widget control) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(
            flexRow(),
            alignItemsCenter(),
            alignSelfStretch(),
            heightOf(rowHeight),
            minWidth(0),
            gap(6),
            boxSizingBorderBox()
        );
        CirrusFittedTextWidget caption = CirrusFittedTextWidget.primary(label);
        caption.useStyle(flexGrow(1), flexShrink(1), flexBasisPx(0f), heightOf(rowHeight), minWidth(0));
        row.addWidget(caption);
        row.addWidget(control);
        return row;
    }

    private Widget switchRow(Component label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Slide slide = new Slide(getter, setter, () -> null);
        slide.useStyle(sizeOf(20, 13), flexShrink(0));
        return labelRow(label, slide);
    }

    private Widget switchRow(LangEntry label, BooleanSupplier getter, Consumer<Boolean> setter) {
        return switchRow(label.component(), getter, setter);
    }

    private Widget numberRow(LangEntry label, int initial, int min, int max, LongConsumer setter) {
        NumberInputWidget input = NumberInputWidget.create(initial).setRange(min, max).setScrollStep(10);
        input.onValueChanged(setter);
        input.useStyle(sizeOf(72, rowHeight), flexShrink(0));
        return labelRow(label.component(), input);
    }

    private JsonObject diagnosticsJson() {
        JsonObject root = new JsonObject();
        Optional<CirrusPlanningRuntime.PublishedSnapshot> snapshot = host.published();
        if (snapshot.isEmpty()) {
            root.addProperty("plannerAvailable", host.plannerAvailable());
            root.addProperty("published", false);
            return root;
        }
        CirrusPlanningRuntime.PublishedSnapshot published = snapshot.get();
        GraphCompilationStats graphStats = published.graph().stats();
        CirrusCatalogAdapter.CaptureStats capture = published.captureStats();
        root.addProperty("plannerAvailable", true);
        root.addProperty("published", true);
        root.addProperty("version", published.version());
        root.addProperty("attachEpoch", published.attachEpoch());
        JsonObject graph = new JsonObject();
        graph.addProperty("totalRawRecipes", graphStats.totalRawRecipes());
        graph.addProperty("compiledRecipes", graphStats.compiledRecipes());
        graph.addProperty("excludedInvalidOrComplex", graphStats.excludedInvalidOrComplex());
        graph.addProperty("excludedDisplaySystems", graphStats.excludedDisplaySystems());
        graph.addProperty("deduplicatedRecipes", graphStats.deduplicatedRecipes());
        graph.addProperty("reversibleEdgesDetected", graphStats.reversibleEdgesDetected());
        graph.addProperty("cycleMacrosMined", graphStats.cycleMacrosMined());
        graph.addProperty("largestSccSize", graphStats.largestSccSize());
        graph.addProperty("excludedByPolicy", graphStats.excludedByPolicy());
        graph.addProperty("excludedInsaneAmount", graphStats.excludedInsaneAmount());
        graph.addProperty("excludedNoInputs", graphStats.excludedNoInputs());
        graph.addProperty("normalizedResourceIds", graphStats.normalizedResourceIds());
        graph.addProperty("discouragedRecipes", graphStats.discouragedRecipes());
        graph.addProperty("lastResortRecipes", graphStats.lastResortRecipes());
        root.add("graphStats", graph);
        JsonObject captureStats = new JsonObject();
        captureStats.addProperty("systemsVisited", capture.systemsVisited());
        captureStats.addProperty("systemsFailed", capture.systemsFailed());
        captureStats.addProperty("recipesVisited", capture.recipesVisited());
        captureStats.addProperty("recipesKept", capture.recipesKept());
        captureStats.addProperty("recipesResolveFailed", capture.recipesResolveFailed());
        captureStats.addProperty("recipesExceptionFailed", capture.recipesExceptionFailed());
        captureStats.addProperty("unresolvedOutputsRecorded", capture.unresolvedOutputsRecorded());
        root.add("captureStats", captureStats);
        root.addProperty("producibleResources", published.graph().allProducibleResources().size());
        root.addProperty("unresolvedOutputs", published.graph().unresolvedOutputs().size());
        root.addProperty("systemsVisited", capture.systemsVisited());
        return root;
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, numerator / (double) denominator));
    }

    /**
     * Scrollable settings column: only lays out vertically and scrolls.
     */
    private static final class SettingsColumn extends CirrusScrollablePageWidget {
        private SettingsColumn() {
            super(5, 4);
        }

        private CirrusSectionWidget section(LangEntry title) {
            CirrusSectionWidget section = new CirrusSectionWidget(title);
            section.chrome(CirrusSectionWidget.Chrome.outlinedFlat);
            section.useStyle(flexShrink(0));
            addWidget(section);
            return section;
        }
    }

    /**
     * Diagnostics table: reads the runtime snapshot at render time, no timer.
     */
    private static final class StatsTable extends Widget {

        private final SettingsHost host;
        private final Supplier<CirrusPalette> palette;

        private StatsTable(SettingsHost host, Supplier<CirrusPalette> palette) {
            this.host = host;
            this.palette = palette;
            useStyle(flexGrow(1), minWidth(0), heightOf(statsRowCount * statsRowHeight + 6));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette theme = palette.get();
            boolean dark = CirrusUiTheme.isDark(this);
            canvas.fill(0, 0, width(), height(), dark ? theme.recessedSurface() : theme.cardSurface());
            canvas.strokeRect(0, 0, width(), height(), theme.hairlineBorder(), 1);
            Optional<CirrusPlanningRuntime.PublishedSnapshot> snapshot = host.published();
            if (snapshot.isEmpty()) {
                ChromeText.drawFitted(
                    canvas,
                    tr("no_graph"),
                    6,
                    ChromeText.centeredY(0, height(), canvas),
                    Math.max(0, width() - 12),
                    theme.textMuted()
                );
                return;
            }
            CirrusPlanningRuntime.PublishedSnapshot published = snapshot.get();
            GraphCompilationStats stats = published.graph().stats();
            CirrusCatalogAdapter.CaptureStats capture = published.captureStats();
            List<Component> labels = List.of(
                tr("stat.published_version"),
                tr("stat.attach_epoch"),
                tr("stat.total_raw"),
                tr("stat.compiled"),
                tr("stat.excluded_display"),
                tr("stat.reversible_edges"),
                tr("stat.cycle_macros"),
                tr("stat.largest_scc"),
                tr("stat.systems_visited"),
                tr("stat.systems_failed"),
                tr("stat.capture_failed")
            );
            List<String> values = List.of(
                Long.toString(published.version()),
                Long.toString(published.attachEpoch()),
                Integer.toString(stats.totalRawRecipes()),
                Integer.toString(stats.compiledRecipes()),
                Integer.toString(stats.excludedDisplaySystems()),
                Integer.toString(stats.reversibleEdgesDetected()),
                Integer.toString(stats.cycleMacrosMined()),
                Integer.toString(stats.largestSccSize()),
                Integer.toString(capture.systemsVisited()),
                Integer.toString(capture.systemsFailed()),
                Integer.toString(capture.recipesExceptionFailed())
            );
            for (int index = 0; index < values.size() && index < labels.size(); index++) {
                int rowY = 3 + index * statsRowHeight;
                if (rowY + statsRowHeight > height()) {
                    break;
                }
                int textY = ChromeText.centeredY(rowY, statsRowHeight, canvas);
                Component valueText = Component.literal(values.get(index));
                int valueWidth = ChromeText.width(canvas, valueText);
                int maxLabelWidth = Math.max(0, width() - 16 - valueWidth);
                ChromeText.drawFitted(canvas, labels.get(index), 6, textY, maxLabelWidth, theme.textSecondary());
                ChromeText.drawRight(canvas, valueText, width() - 6, textY, theme.textPrimary());
            }
        }
    }

    /**
     * Render-time text: reads a supplier so no widget state changes during a tick.
     */
    private static final class LiveText extends Widget {

        private final Supplier<Component> text;
        private final Supplier<CirrusPalette> palette;
        private final boolean centered;

        private LiveText(
            Supplier<Component> text,
            Supplier<CirrusPalette> palette,
            dev.vfyjxf.cloudlib.api.ui.style.key.@Nullable StyleEntry width,
            boolean centered
        ) {
            this.text = text;
            this.palette = palette;
            this.centered = centered;
            if (width == null) {
                useStyle(heightOf(12), minWidth(0));
            } else {
                useStyle(heightOf(12), minWidth(0), width);
            }
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            Component value = text.get();
            Component fitted = ChromeText.fit(canvas, value == null ? Component.empty() : value, width());
            int textY = ChromeText.centeredY(0, height(), canvas);
            int textX = centered ? Math.max(0, (width() - ChromeText.width(canvas, fitted)) / 2) : 0;
            ChromeText.draw(canvas, fitted, textX, textY, palette.get().textPrimary());
        }
    }
}
