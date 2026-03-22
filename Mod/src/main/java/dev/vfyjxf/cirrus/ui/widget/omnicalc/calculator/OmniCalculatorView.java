package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cirrus.ui.widget.flowchart.RecipeStepsWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.ui.widget.ButtonWidget;
import dev.vfyjxf.cloudlib.ui.widget.DividerWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Main Calculator view page — replicates omnicalc-pro's CalculatorView.
 * <p>
 * Layout:
 * <pre>
 * ┌────────────────────────────────────────────────────────┐
 * │                   Control Bar                          │
 * ├────────────────────────────────────────────────────────┤
 * │  ┌────────────────────────────────────────────────────┐│
 * │  │   ViewSwitcher Tabs                                ││
 * │  ├────────────────────────────────────────────────────┤│
 * │  │   Process View (Tree/Matrix/Milestones)            ││
 * │  └────────────────────────────────────────────────────┘│
 * └────────────────────────────────────────────────────────┘
 * </pre>
 */
public class OmniCalculatorView extends CompositeWidget<Widget> {

    public enum ProcessView { TREE, MATRIX, MILESTONES }

    // ── State ──
    private ProcessView processView = ProcessView.TREE;
    private boolean calculated = false;
    private long amount = 1;

    // ── Widgets ──
    private final ControlBarWidget controlBar;
    private final WidgetGroup<Widget> bodyArea;

    // Post-calculation widgets
    private ViewSwitcherWidget viewSwitcher;
    private WidgetGroup<Widget> processViewArea;

    // Pre-calculation widgets
    private WidgetGroup<Widget> emptyState;

    public OmniCalculatorView() {
        useStyle(
                flexColumn(),
                sizeFull(),
                background(OmniCalcColors.TEX_BG_PRIMARY)
        );

        // ── Control Bar (top, sticky) ──
        controlBar = new ControlBarWidget(this);
        addWidget(controlBar);

        // ── Body area (below control bar) ──
        bodyArea = new WidgetGroup<>();
        bodyArea.useStyle(
                flexRow(),
                flexGrow(1),
                minHeight(0)
        );
        addWidget(bodyArea);

        // Show empty state initially
        showEmptyState();
    }

    // ═══════════════════ View Management ═══════════════════

    private void showEmptyState() {
        bodyArea.clear();
        calculated = false;

        emptyState = new WidgetGroup<>();
        emptyState.useStyle(
                flexColumn(),
                flexGrow(1),
                alignItemsCenter(),
                justifyCenter()
        );

        // Big icon placeholder (large gear at 10% opacity feel)
        var iconLabel = LabelWidget.of(Component.literal("\u2699"))
                .setColor(0x1AE5E7EB)  // ~10% opacity white
                .setShadow(false);
        iconLabel.useStyle(marginBottom(4), sizeOf(24, 24));
        emptyState.addWidget(iconLabel);

        // Ready text
        var readyLabel = LabelWidget.of(Component.literal("Ready: Awaiting Calculation"))
                .setColor(TEXT_MUTED)
                .setShadow(false);
        emptyState.addWidget(readyLabel);

        bodyArea.addWidget(emptyState);
    }

    void showCalculationResult() {
        bodyArea.clear();
        calculated = true;

        // ── Left area: View Switcher + Process View ──
        var leftArea = new WidgetGroup<>();
        leftArea.useStyle(
                flexColumn(),
                flexGrow(1),
                minWidth(0)
        );

        // View switcher tabs
        viewSwitcher = new ViewSwitcherWidget(this);
        leftArea.addWidget(viewSwitcher);

        // Process view container
        processViewArea = new WidgetGroup<>();
        processViewArea.useStyle(
                flexGrow(1),
                minHeight(0)
        );
        leftArea.addWidget(processViewArea);
        bodyArea.addWidget(leftArea);

        // Show initial process view
        switchProcessView(ProcessView.TREE);
    }

    void switchProcessView(ProcessView view) {
        this.processView = view;
        if (processViewArea == null) return;
        processViewArea.clear();

        Widget viewWidget = switch (view) {
            case TREE -> createTreeView();
            case MATRIX -> createMatrixView();
            case MILESTONES -> createMilestonesView();
        };
        viewWidget.useStyle(flexGrow(1));
        processViewArea.addWidget(viewWidget);

        if (viewSwitcher != null) viewSwitcher.setActiveView(view);
    }

    ProcessView currentProcessView() {
        return processView;
    }

    boolean isCalculated() {
        return calculated;
    }

    long getAmount() {
        return amount;
    }

    void setAmount(long amount) {
        this.amount = amount;
    }

    // ═══════════════════ View Factories ═══════════════════

    private Widget createTreeView() {
        var treeView = new TreeViewWidget();
        // Connect to debug data for now
        var graph = DebugRecipeProvider.createDebugProcessGraph();
        treeView.setGraph(graph);
        return treeView;
    }

    private Widget createMatrixView() {
        return new MatrixViewWidget();
    }

    private Widget createMilestonesView() {
        return new MilestonesViewWidget();
    }

    // ═══════════════════ Actions ═══════════════════

    public void onCalculate() {
        if (calculated) {
            // Re-calculate
            showEmptyState();
        }
        showCalculationResult();
    }
}
