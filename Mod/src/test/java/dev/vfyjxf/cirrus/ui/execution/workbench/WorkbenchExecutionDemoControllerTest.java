package dev.vfyjxf.cirrus.ui.execution.workbench;

import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchExecutionDemoControllerTest {

    @Test
    void controllerBatchesExecuteStepsPerRecipeStage() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6, true);

        int executeSteps = 0;
        for (var step : controller.session().plan().steps()) {
            if (!(step instanceof InteractionStep.ExecuteRecipeStep execute)) {
                continue;
            }
            executeSteps++;
            assertEquals(20, execute.times());
        }

        assertEquals(1, executeSteps);
    }

    @Test
    void controllerStartsAtInitialWorkbenchStage() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);

        assertNotNull(controller.session().currentStep());
        assertEquals(0, controller.session().completedSteps().size());
        assertFalse(controller.session().paused());
        assertFalse(controller.session().completed());
        assertFalse(controller.autoAdvance());
    }

    @Test
    void controllerStopsAtManualOpenCheckpointUntilConfirmed() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);

        int advanced = controller.advanceSteps(16);

        assertEquals(7, advanced);
        assertTrue(controller.session().paused());
        assertFalse(controller.session().completed());
        assertEquals(6, controller.session().completedSteps().size());
        assertEquals("ManualCheckpointStep", controller.session().currentStep().getClass().getSimpleName());
        assertNotNull(controller.session().pauseReason().guidance().summary());
    }

    @Test
    void controllerCanResumeFromCheckpointsAndClampAtCompletion() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);
        controller.advanceSteps(16);

        int secondAdvance = controller.advanceSteps(16);
        assertTrue(secondAdvance >= 1);
        assertFalse(controller.session().paused());
        if (controller.session().currentStep() != null) {
            assertFalse("ExecuteRecipeStep".equals(controller.session().currentStep().getClass().getSimpleName()));
        }

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
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);
        controller.toggleAutoAdvance();

        for (int i = 0; i < 24; i++) {
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
    void telemetrySnapshotTracksCurrentSessionState() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);
        controller.advanceSteps(16);

        ExecutionTelemetrySnapshot snapshot = controller.overlayTelemetrySnapshot();

        assertEquals(controller.session().paused(), snapshot.paused());
        assertEquals(controller.autoAdvance(), snapshot.autoAdvanceEnabled());
        assertEquals(controller.session().nextStepIndex(), snapshot.completedSteps());
        assertEquals(controller.session().plan().steps().size(), snapshot.totalSteps());
        assertNotNull(snapshot.current());
        assertNotNull(snapshot.warnings());
        assertFalse(snapshot.warnings().isEmpty());
        assertNull(snapshot.errorSummary());
        assertEquals(5.0d, snapshot.ratePerSecond(), 0.0001d);
        assertTrue(snapshot.ratePerSecond() >= 0.0d);
    }

    @Test
    void overlayRateCanBeChangedAndDrivesAutoAdvanceCadence() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);

        assertEquals(5.0d, controller.overlayRatePerSecond(), 0.0001d);
        controller.overlaySetRatePerSecond(12.5d);
        assertEquals(12.5d, controller.overlayRatePerSecond(), 0.0001d);
        controller.toggleAutoAdvance();
        assertTrue(controller.overlayAutoAdvanceEnabled());
        controller.overlaySetRatePerSecond(-2.0d);
        assertEquals(1.0d, controller.overlayRatePerSecond(), 0.0001d);
        assertTrue(controller.overlayAutoAdvanceEnabled());

        controller.overlaySetRatePerSecond(12.0d);
        controller.tick();
        assertEquals(0, controller.session().nextStepIndex());
        controller.tick();
        assertTrue(controller.session().nextStepIndex() > 0);
    }

    @Test
    void overlayMaterialRequirementsExposeNeedHaveAndMissing() {
        var controller = new WorkbenchExecutionDemoController(2, 0, 6);

        var requirements = controller.overlayMaterialRequirements();
        assertFalse(requirements.isEmpty());

        var logRequirement = requirements.detect(req -> ItemStack.isSameItemSameComponents(req.stack(), new ItemStack(Items.OAK_LOG)));
        assertNotNull(logRequirement);
        assertEquals(40L, logRequirement.required());
        assertEquals(2L, logRequirement.available());
        assertEquals(38L, logRequirement.missing());

        var stickRequirement = requirements.detect(req -> ItemStack.isSameItemSameComponents(req.stack(), new ItemStack(Items.STICK)));
        assertNotNull(stickRequirement);
        assertEquals(60L, stickRequirement.required());
        assertEquals(60L, stickRequirement.available());
        assertEquals(0L, stickRequirement.missing());
    }

    @Test
    void controllerEmitsMissingStockWarningWhenSticksAreInsufficient() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 0, 6, true, true);

        controller.advanceSteps(16);

        assertTrue(controller.session().paused());
        assertEquals("WithdrawResourceStep", controller.session().currentStep().getClass().getSimpleName());
        assertNotNull(controller.session().pauseReason());
    }

    @Test
    void controllerEmitsMissingCapacityWarningWhenOutputSpaceIsInsufficient() {
        var controller = new WorkbenchExecutionDemoController(48, 60, 64, 0, true, true);

        controller.advanceSteps(16);

        assertTrue(controller.session().paused());
        assertEquals("EnsureCapacityStep", controller.session().currentStep().getClass().getSimpleName());
        assertNotNull(controller.session().pauseReason());
        assertNotNull(controller.session().pauseReason().guidance());
    }

    @Test
    void controllerStaysPausedAtManualCheckpointWhenInteractionIsClosed() {
        var controller = new WorkbenchExecutionDemoController(48, 60, 0, 6, false, true);

        int firstAdvance = controller.advanceSteps(16);
        int secondAdvance = controller.advanceSteps(16);

        assertTrue(firstAdvance > 0);
        assertEquals(0, secondAdvance);
        assertTrue(controller.session().paused());
        assertEquals("ManualCheckpointStep", controller.session().currentStep().getClass().getSimpleName());
    }

    @Test
    void materialRequirementsDecreaseAfterExecutionProgresses() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);
        long initialLogRequired = controller.overlayMaterialRequirements()
                                           .detect(req -> ItemStack.isSameItemSameComponents(req.stack(), new ItemStack(Items.OAK_LOG)))
                                           .required();

        controller.advanceSteps(16);
        controller.advanceSteps(32);

        var progressedRequirement = controller.overlayMaterialRequirements()
                                             .detect(req -> ItemStack.isSameItemSameComponents(req.stack(), new ItemStack(Items.OAK_LOG)));
        long progressedLogRequired = progressedRequirement == null ? 0L : progressedRequirement.required();
        assertTrue(progressedLogRequired < initialLogRequired);
    }

    @Test
    void materialRequirementsAreEmptyAfterCompletion() {
        var controller = new WorkbenchExecutionDemoController(64, 0, 12);
        int guard = 0;
        while (!controller.session().completed() && guard < 48) {
            controller.advanceSteps(16);
            guard++;
        }

        assertTrue(controller.session().completed());
        assertTrue(controller.overlayMaterialRequirements().isEmpty());
    }

    @Test
    void overlayTargetProgressUsesTotalTargetAmountWithoutExtraTargetTimesMultiplier() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 6);

        var progress = controller.overlayTargetProgress();

        assertNotNull(progress);
        assertTrue(ItemStack.isSameItemSameComponents(progress.stack(), new ItemStack(Items.OAK_PLANKS)));
        assertEquals(0L, progress.current());
        assertEquals(160L, progress.required());
    }

    @Test
    void controllerDoesNotRequireInitialStickStockWhenAutoCraftChainIsAvailable() {
        var controller = new WorkbenchExecutionDemoController(48, 0, 0, 6, true, false);

        controller.advanceSteps(16);

        assertFalse(controller.session().paused());
        assertNotNull(controller.session().currentStep());
        assertFalse("WithdrawResourceStep".equals(controller.session().currentStep().getClass().getSimpleName()));
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
        var controller = new WorkbenchExecutionDemoController(48, 0, 6, true);

        int guard = 0;
        while (!controller.session().completed() && guard < 48) {
            controller.advanceSteps(16);
            guard++;
        }

        assertTrue(guard < 48);
        assertTrue(controller.session().completed());
        assertFalse(controller.session().paused());
    }
}
