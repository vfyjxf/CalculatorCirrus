package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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

    //region Types
    public enum ProcessView { TREE, MATRIX, MILESTONES }
    //endregion

    //region State
    private ProcessView processView = ProcessView.TREE;
    private boolean calculated = false;
    private long amount = 1;
    private final ProcessGraph graph = DebugRecipeProvider.createDebugProcessGraph();
    //endregion

    //region Widgets
    private final ControlBarWidget controlBar;
    private final WidgetGroup<Widget> bodyArea;

    private ViewSwitcherWidget viewSwitcher;
    private WidgetGroup<Widget> processViewArea;
    private TreeViewWidget treeView;
    private MatrixViewWidget matrixView;
    private MilestonesViewWidget milestonesView;

    private WidgetGroup<Widget> emptyState;
    //endregion

    //region Init
    public OmniCalculatorView() {
        setFocusable(true);
        useEffect(UIEffects.autoFocus());
        useStyle(
                flexColumn(),
                sizeFull(),
                background(Background.inset)
        );
        onKeyPressed((input, context) -> {
            int key = input.key().getValue();
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                onCalculate();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });

        // ── Control Bar (top, sticky) ──
        controlBar = new ControlBarWidget(this);
        controlBar.setTargetItem(resolveTargetItem(graph));
        addWidget(controlBar);

        // ── Body area (below control bar) ──
        bodyArea = new WidgetGroup<>();
        bodyArea.useStyle(
                flexRow(),
                flexGrow(1),
                minHeight(0),
                background(Background.inset)
        );
        addWidget(bodyArea);

        if ("true".equalsIgnoreCase(System.getenv("CIRRUS_OMNICALL_AUTO_CALC"))) {
            showCalculationResult();
        } else {
            showEmptyState();
        }
    }
    //endregion

    //region View Management
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
                .setColor(textMuted)
                .setShadow(false);
        emptyState.addWidget(readyLabel);

        bodyArea.addWidget(emptyState);
    }

    void showCalculationResult() {
        bodyArea.clear();
        calculated = true;
        treeView = null;
        matrixView = null;
        milestonesView = null;

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
            case TREE -> treeView = createTreeView();
            case MATRIX -> matrixView = createMatrixView();
            case MILESTONES -> milestonesView = createMilestonesView();
        };
        viewWidget.useStyle(flexGrow(1));
        processViewArea.addWidget(viewWidget);

        if (viewSwitcher != null) {
            viewSwitcher.setActiveView(view);
            viewSwitcher.setTreeActionsVisible(view == ProcessView.TREE);
        }
    }
    //endregion

    //region Public API
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
    //endregion

    //region View Factory
    private TreeViewWidget createTreeView() {
        var treeView = new TreeViewWidget();
        treeView.setGraph(graph);
        return treeView;
    }

    private MatrixViewWidget createMatrixView() {
        return new MatrixViewWidget();
    }

    private MilestonesViewWidget createMilestonesView() {
        return new MilestonesViewWidget();
    }
    //endregion

    //region Actions
    public void onCalculate() {
        amount = controlBar.getAmount();
        if (calculated) {
            // Re-calculate
            showEmptyState();
        }
        showCalculationResult();
    }
    //endregion

    //region Helpers
    private static ItemStack resolveTargetItem(ProcessGraph graph) {
        if (graph.sinks().isEmpty()) return ItemStack.EMPTY;
        var sink = graph.sinks().getFirst();
        if (sink.outputs().isEmpty()) return ItemStack.EMPTY;
        var output = sink.outputs().getFirst();
        return output.itemStack() == null ? ItemStack.EMPTY : output.itemStack();
    }

    void toggleTreeMaterialPanel() {
        if (treeView != null) {
            treeView.toggleMaterialPanel();
        }
    }

    void resetTreeViewFocus() {
        if (treeView != null) {
            treeView.resetToFullTree();
        }
    }

    boolean isTreeMaterialPanelVisible() {
        return treeView != null && treeView.debugIsMaterialPanelVisible();
    }

    boolean isTreeFocusedSubgraph() {
        return treeView != null && !treeView.debugIsRootFocused();
    }

    int debugTreeHeaderActionCount() {
        return viewSwitcher == null ? 0 : viewSwitcher.debugTreeActionCount();
    }

    boolean debugTreeHeaderActionsVisible() {
        return viewSwitcher != null && viewSwitcher.debugTreeActionsVisible();
    }
    //endregion
}
