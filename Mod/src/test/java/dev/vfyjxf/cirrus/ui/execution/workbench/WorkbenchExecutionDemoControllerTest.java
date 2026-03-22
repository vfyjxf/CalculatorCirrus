package dev.vfyjxf.cirrus.ui.execution.workbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchExecutionDemoControllerTest {

    @Test
    void controllerStartsAtInitialWorkbenchStage() {
        var controller = new WorkbenchExecutionDemoController();

        assertNotNull(controller.session().currentStep());
        assertEquals(0, controller.session().completedSteps().size());
        assertFalse(controller.session().paused());
        assertFalse(controller.session().completed());
        assertFalse(controller.autoAdvance());
    }

    @Test
    void controllerStopsAtManualOpenCheckpointUntilConfirmed() {
        var controller = new WorkbenchExecutionDemoController();

        int advanced = controller.advanceSteps(16);

        assertEquals(5, advanced);
        assertTrue(controller.session().paused());
        assertFalse(controller.session().completed());
        assertEquals(4, controller.session().completedSteps().size());
        assertEquals("ManualCheckpointStep", controller.session().currentStep().getClass().getSimpleName());
        assertNotNull(controller.session().pauseReason().guidance().summary());
    }

    @Test
    void controllerCanResumeFromCheckpointsAndClampAtCompletion() {
        var controller = new WorkbenchExecutionDemoController();
        controller.advanceSteps(16);

        int secondAdvance = controller.advanceSteps(16);
        assertTrue(secondAdvance >= 3);
        assertFalse(controller.session().paused());
        assertFalse("ExecuteRecipeStep".equals(controller.session().currentStep().getClass().getSimpleName()));

        int guard = 0;
        while (!controller.session().completed() && guard < 32) {
            controller.advanceSteps(16);
            guard++;
        }

        assertTrue(guard < 32);
        assertFalse(controller.session().paused());
        assertTrue(controller.session().completed());
        assertEquals(controller.session().plan().steps().size(), controller.session().nextStepIndex());
    }

    @Test
    void autoAdvanceStaysEnabledAcrossPausesAndCanBeReset() {
        var controller = new WorkbenchExecutionDemoController();
        controller.toggleAutoAdvance();

        for (int i = 0; i < 80; i++) {
            controller.tick();
        }

        assertFalse(controller.session().completed());
        assertTrue(controller.autoAdvance());
        assertTrue(controller.session().nextStepIndex() > 0);

        controller.reset();

        assertFalse(controller.session().paused());
        assertEquals(0, controller.session().completedSteps().size());
        assertFalse(controller.autoAdvance());
    }

    @Test
    void controllerEmitsMissingStockWarningWhenLogsAreInsufficient() {
        var controller = new WorkbenchExecutionDemoController(1, 0, 6);

        controller.advanceSteps(16);

        assertTrue(controller.session().paused());
        assertEquals("WithdrawResourceStep", controller.session().currentStep().getClass().getSimpleName());
        assertNotNull(controller.session().pauseReason());
        assertNotNull(controller.session().pauseReason().guidance());
        assertNotNull(controller.session().pauseReason().guidance().summary());
    }

    @Test
    void controllerAutoCraftsWithDifferentRequestSourceInstances() {
        var controller = new WorkbenchExecutionDemoController(24, 0, 6, true);

        int guard = 0;
        while (!controller.session().completed() && guard < 16) {
            controller.advanceSteps(16);
            guard++;
        }

        assertTrue(guard < 16);
        assertTrue(controller.session().completed());
        assertFalse(controller.session().paused());
    }
}
