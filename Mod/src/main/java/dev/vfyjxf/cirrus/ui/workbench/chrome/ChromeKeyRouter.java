package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Dispatches a command resolved by {@link ChromeShortcut} to the shell, a controller or a canvas seam.
 */
public final class ChromeKeyRouter {

    private final WorkbenchShellContext context;
    private @Nullable ChromeSink sink;

    public ChromeKeyRouter(WorkbenchShellContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public ChromeKeyRouter sink(@Nullable ChromeSink sink) {
        this.sink = sink;
        return this;
    }

    public WorkbenchShellContext context() {
        return context;
    }

    public boolean dispatch(int keyCode, ChromeShortcut.Modifiers modifiers, boolean textInputFocused) {
        ChromeShortcut.Command command = ChromeShortcut.resolve(keyCode, modifiers, textInputFocused);
        if (command == null) {
            return false;
        }
        execute(command);
        return true;
    }

    public boolean execute(ChromeShortcut.Command command) {
        switch (command) {
            case togglePalette -> {
                if (context.commandPaletteOpen()) {
                    context.closeCommandPalette();
                } else {
                    context.openCommandPalette();
                }
                return true;
            }
            case undo -> {
                return undo();
            }
            case redo -> {
                return redo();
            }
            case newTab -> {
                context.openTab(List.of(), null);
                return true;
            }
            case closeTab -> {
                context.closeActiveTab();
                return true;
            }
            case sendExecution -> {
                context.sendActivePlanToExecution();
                return true;
            }
            case toggleZen -> {
                context.setZenMode(!context.zenMode());
                return true;
            }
            case modePlanner -> {
                context.setMode(WorkbenchMode.planner);
                return true;
            }
            case modeFacilities -> {
                context.setMode(WorkbenchMode.facilities);
                return true;
            }
            case modeDatabase -> {
                context.setMode(WorkbenchMode.database);
                return true;
            }
            case modeSettings -> {
                context.setMode(WorkbenchMode.settings);
                return true;
            }
            case viewSpatialDag -> {
                return setFlowView(FlowViewMode.spatialDag);
            }
            case viewLedger -> {
                return setFlowView(FlowViewMode.preparationLedger);
            }
            case viewChecklist -> {
                return setFlowView(FlowViewMode.executionChecklist);
            }
            case cycleLayout -> {
                return cycleLayout();
            }
            case autoLayout -> {
                PlannerWorkbenchController controller = context.activeController();
                if (controller == null) {
                    return false;
                }
                controller.clearManualOffsets();
                return true;
            }
            case fitView, centerTarget, declareOwned, openProducers, banStepRecipe -> canvasCommand(command);
        }
        return false;
    }

    public boolean undo() {
        PlannerWorkbenchController controller = context.activeController();
        WorkbenchUiSnapshot snapshot = context.activeSnapshot();
        if (controller == null || !snapshot.canUndo()) {
            return false;
        }
        controller.jumpToHistoryIndex(snapshot.undoHistory().size() - 1);
        return true;
    }

    public boolean redo() {
        PlannerWorkbenchController controller = context.activeController();
        WorkbenchUiSnapshot snapshot = context.activeSnapshot();
        if (controller == null || !snapshot.canRedo()) {
            return false;
        }
        controller.jumpToHistoryIndex(snapshot.undoHistory().size() + 1);
        return true;
    }

    public boolean cycleLayout() {
        PlannerWorkbenchController controller = context.activeController();
        if (controller == null) {
            return false;
        }
        DagLayoutMode next = switch (context.activeSnapshot().dagLayoutMode()) {
            case horizontalLr -> DagLayoutMode.verticalTb;
            case verticalTb -> DagLayoutMode.horizontalRl;
            case horizontalRl -> DagLayoutMode.verticalBt;
            case verticalBt -> DagLayoutMode.systemSwimlane;
            case systemSwimlane -> DagLayoutMode.horizontalLr;
        };
        controller.setDagLayoutMode(next);
        return true;
    }

    private boolean setFlowView(FlowViewMode mode) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller == null) {
            return false;
        }
        controller.setFlowViewMode(mode);
        return true;
    }

    private void canvasCommand(ChromeShortcut.Command command) {
        ChromeSink target = sink;
        if (target == null) {
            target = ChromeSink.current();
        }
        target.canvasCommand(command);
    }
}
