package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.ui.execution.workbench.WorkbenchExecutionDemoController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlaySandboxScreenContractTest {

    @Test
    void sandboxScreenExposesStandaloneComponentPreviewCount() {
        assertEquals(0, ExecutionOverlaySandboxScreen.debugPreviewComponentCount());
        assertTrue(ExecutionOverlaySandboxScreen.debugUsesSharedOverlayController());
        assertEquals("cirrus exec_overlay", ExecutionOverlayCommandEntry.debugCommandPath());
    }

    @Test
    void componentRendererBuildsReusableModelsFromOverlayProjection() {
        var controller = new WorkbenchExecutionDemoController();
        controller.advanceSteps(1);
        ExecutionOverlayViewModel viewModel = ExecutionOverlayViewModel.derive(controller.session());

        var header = ExecutionOverlayComponentRenderer.headerModelOf(viewModel);
        var footer = ExecutionOverlayComponentRenderer.footerModelOf(viewModel, 0.92f);
        var card = ExecutionOverlayComponentRenderer.cardModelOf(viewModel.visibleNodes().getFirst(), 42);

        assertEquals(viewModel.totalStepCount(), header.totalSteps());
        assertEquals(viewModel.completedStepCount(), header.completedSteps());
        assertFalse(footer.secondaryText().isBlank());
        assertNotNull(card.node());
    }
}
