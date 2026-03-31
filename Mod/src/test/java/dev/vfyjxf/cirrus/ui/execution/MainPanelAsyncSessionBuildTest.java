package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPanelAsyncSessionBuildTest {
    private static final Namespace interactionId = Namespace.of("test", "async_panel");

    @AfterEach
    void clearExecutionTarget() {
        ExecutionTargetRecipeState.clear();
    }

    @Test
    void tickStartsSessionBuildAndAppliesItOnlyWhenFutureCompletes() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(64);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 8);
            MainPanel panel = new MainPanel(handle());

            panel.tick();

            assertTrue(panel.debugSessionBuildPending());
            assertTrue(panel.calculating());
            assertNotNull(panel.snapshot());

            panel.debugCompletePendingSessionBuild();

            assertFalse(panel.debugSessionBuildPending());
            assertFalse(panel.calculating());
            assertNotNull(panel.snapshot());
            assertNotNull(panel.snapshot().session());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void targetTimesChangeStopsRunningExecutionBeforeDelayedRebuild() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(8);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 2);
            MainPanel panel = new MainPanel(handle());
            panel.tick();
            panel.debugCompletePendingSessionBuild();
            assertNotNull(panel.snapshot());
            panel.driver().toggleAutoAdvance();
            assertTrue(panel.driver().snapshot().autoAdvanceEnabled());

            panel.setTargetTimes(4);

            assertFalse(panel.driver().snapshot().autoAdvanceEnabled());
            assertFalse(panel.debugSessionBuildPending());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void pendingTargetTimesChangeDoesNotRebuildOldSelection() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(8);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 2);
            MainPanel panel = new MainPanel(handle());
            panel.tick();
            panel.debugCompletePendingSessionBuild();

            panel.setTargetTimes(4);
            panel.tick();

            assertFalse(panel.debugSessionBuildPending());
            assertNotNull(panel.snapshot());
            assertFalse(panel.snapshot().autoAdvanceEnabled());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void rebuiltSessionStartsStoppedAfterTargetTimesCommit() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(8);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 2);
            MainPanel panel = new MainPanel(handle());
            panel.tick();
            panel.debugCompletePendingSessionBuild();
            assertNotNull(panel.snapshot());
            panel.driver().toggleAutoAdvance();

            panel.setTargetTimes(4);
            panel.flushTargetTimes();

            assertTrue(panel.debugSessionBuildPending());
            panel.debugCompletePendingSessionBuild();

            assertNotNull(panel.snapshot());
            assertNotNull(panel.snapshot().session());
            assertFalse(panel.snapshot().autoAdvanceEnabled());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void activeRecipeContextBuildRunsAfterSessionBuildCompletes() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(64);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 8);
            MainPanel panel = new MainPanel(handle());
            panel.tick();
            panel.debugCompletePendingSessionBuild();

            panel.tick();

            assertTrue(panel.debugActiveContextBuildPending());
            assertFalse(panel.calculating());
            assertTrue(panel.debugActiveRecipeContext() == null);

            panel.debugCompletePendingActiveContextBuild();

            assertFalse(panel.debugActiveContextBuildPending());
            assertNotNull(panel.debugActiveRecipeContext());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    private static ExecutionDriverHandle handle() {
        return new ExecutionDriverHandle(
            lookup -> new SlotExecutionDriver(
                interactionId,
                SlotExecutionDriver.automaticInteraction(interactionId),
                (ExecutionRuntime) null
            ),
            DriverLookup.of()
        );
    }
}
