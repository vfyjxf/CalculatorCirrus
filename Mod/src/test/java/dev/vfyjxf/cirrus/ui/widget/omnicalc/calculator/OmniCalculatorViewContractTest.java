package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OmniCalculatorViewContractTest {

    @Test
    void calculateSwitchesFromEmptyStateToTreeResultView() {
        OmniCalculatorView view = new OmniCalculatorView();

        assertFalse(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());

        view.onCalculate();

        assertTrue(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void repeatedCalculateReturnsToTreeResultView() {
        OmniCalculatorView view = new OmniCalculatorView();

        view.onCalculate();
        view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
        view.onCalculate();

        assertTrue(view.isCalculated());
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void treeResultStartsWithMaterialPanelHidden() {
        TreeViewWidget treeView = new TreeViewWidget();

        treeView.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        assertFalse(treeView.debugIsMaterialPanelVisible());
    }

    @Test
    void treeResultShowsHeaderActionsNextToViewTabs() {
        OmniCalculatorView view = new OmniCalculatorView();

        view.onCalculate();

        assertEquals(2, view.debugTreeHeaderActionCount());
        assertTrue(view.debugTreeHeaderActionsVisible());

        view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);

        assertFalse(view.debugTreeHeaderActionsVisible());
    }

    @Test
    void switchingProcessTabsRepeatedlyDoesNotReuseDestroyedWidgets() {
        OmniCalculatorView view = new OmniCalculatorView();
        view.onCalculate();

        assertDoesNotThrow(() -> {
            view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
            view.switchProcessView(OmniCalculatorView.ProcessView.MILESTONES);
            view.switchProcessView(OmniCalculatorView.ProcessView.TREE);
            view.switchProcessView(OmniCalculatorView.ProcessView.MATRIX);
            view.switchProcessView(OmniCalculatorView.ProcessView.TREE);
        });
        assertEquals(OmniCalculatorView.ProcessView.TREE, view.currentProcessView());
    }

    @Test
    void treeResultBuildsNonEmptyMaterialEntries() {
        TreeViewWidget treeView = new TreeViewWidget();

        treeView.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        assertTrue(treeView.debugMaterialEntryCount() > 0);
    }

    @Test
    void controlBarUsesCompactFixedMetrics() {
        ControlBarWidget.ControlBarMetrics metrics = ControlBarWidget.debugMetrics();

        assertEquals(30, metrics.height());
        assertEquals(18, metrics.targetSlotSize());
        assertEquals(36, metrics.amountInputWidth());
        assertEquals(60, metrics.calculateButtonWidth());
        assertFalse(ControlBarWidget.debugUsesLegacyStripTexture());
        assertTrue(ControlBarWidget.debugUsesStandardButtonTexture());
    }

    @Test
    void viewSwitcherActionsStayCompactWhenTreeViewIsActive() {
        ViewSwitcherWidget.ViewSwitcherMetrics metrics = ViewSwitcherWidget.debugMetrics();

        assertEquals(18, metrics.height());
        assertEquals(16, metrics.actionButtonSize());
    }

}
