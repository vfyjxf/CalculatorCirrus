package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.requestSource;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.singleStepPlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchTargetAndExecutionPathRegressionTest {

    @Test
    void controllerRefreshesGraphVersionImmediatelyOnSetTargets() {
        AtomicLong liveVersion = new AtomicLong(0L);
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            new SessionTabState("tab-1"),
            backend,
            requestSource(),
            sync()
        );
        controller.bindRuntimeVersionSupplier(liveVersion::get);
        controller.onGraphVersionChanged(0L);

        liveVersion.set(1L);
        controller.setTargets(List.of(demand(ironIngot, 2L)), null);

        assertEquals(1, backend.openCount());
        assertEquals(WorkbenchPhase.readyPareto, controller.snapshot().phase());
        assertNotNull(controller.snapshot().result());
        assertEquals(1L, controller.snapshot().runtimeGraphVersion());
    }

    @Test
    void graphBecomingAvailableMarksBackgroundTabStaleAndReplansOnActivate() {
        AtomicLong liveVersion = new AtomicLong(0L);
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
        try (SessionTabManager manager = new SessionTabManager(state -> {
            PlannerWorkbenchController c = new PlannerWorkbenchController(state, backend, requestSource(), sync());
            c.bindRuntimeVersionSupplier(liveVersion::get);
            c.onGraphVersionChanged(liveVersion.get());
            return c;
        })) {
            PlannerWorkbenchController tabA = manager.openOrFocusTab(List.of(demand(ironIngot, 4L)), null);
            PlannerWorkbenchController tabB = manager.openOrFocusTab(List.of(demand(coal, 2L)), null);
            assertEquals(0, backend.openCount());
            assertEquals(tabB.tabId(), manager.activeTabId());

            liveVersion.set(1L);
            manager.onRuntimeGraphVersionChanged(1L);
            assertEquals(1, backend.openCount());
            assertTrue(tabA.state().staleGraph());

            manager.activate(tabA.tabId());
            assertEquals(2, backend.openCount());
            assertNotNull(tabA.snapshot().result());
        }
    }

    @Test
    void openOrFocusTabRecoversBlankTabWithSameDemand() {
        AtomicLong liveVersion = new AtomicLong(0L);
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
        try (SessionTabManager manager = new SessionTabManager(state -> {
            PlannerWorkbenchController c = new PlannerWorkbenchController(state, backend, requestSource(), sync());
            c.bindRuntimeVersionSupplier(liveVersion::get);
            c.onGraphVersionChanged(liveVersion.get());
            return c;
        })) {
            List<TargetDemand> targets = List.of(demand(ironIngot, 1L));
            PlannerWorkbenchController tab = manager.openOrFocusTab(targets, null);
            assertEquals(0, backend.openCount());

            liveVersion.set(1L);
            PlannerWorkbenchController reopened = manager.openOrFocusTab(targets, null);
            assertEquals(tab.tabId(), reopened.tabId());
            assertEquals(1, backend.openCount());
            assertNotNull(reopened.snapshot().result());
        }
    }

    @Test
    void setTargetsClearsResidualViewAndSelectionState() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-same")))
        );
        SessionTabState state = new SessionTabState("tab-1");
        PlannerWorkbenchController controller = new PlannerWorkbenchController(state, backend, requestSource(), sync());
        controller.onGraphVersionChanged(1L);
        controller.setTargets(List.of(demand(ironIngot, 1L)), null);
        state.setCameraState(120f, -80f, 0.75f);
        controller.setSelectedSteps(Set.of("step-1"));
        controller.setManualOffset("step-1", new FloatPos(32f, 16f));
        controller.toggleLoopExpanded("loop-1");

        controller.setTargets(List.of(demand(coal, 2L)), null);

        assertFalse(state.cameraInitialized());
        assertTrue(controller.snapshot().selectedStepIds().isEmpty());
        assertTrue(controller.snapshot().manualNodeOffsets().isEmpty());
        assertTrue(controller.snapshot().expandedLoopStepIds().isEmpty());
        assertEquals(WorkbenchPhase.readyPareto, controller.snapshot().phase());
        assertNotNull(controller.snapshot().result());
    }

    @Test
    void excludeTargetStockRemovesRequestedTargetWhileKeepingIngredientStock() {
        InventorySnapshot inv = new InventorySnapshot(
            new StockLedger(Map.of(ironIngot, 5L, coal, 16L)),
            StockLedger.empty
        );

        InventorySnapshot filtered = PlanningRuntimeBridge.excludeTargetStock(inv, List.of(demand(ironIngot, 1L)));

        assertEquals(0L, filtered.allUsable().getOrDefault(ironIngot, 0L));
        assertEquals(16L, filtered.allUsable().getOrDefault(coal, 0L));
    }
}
