package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.requestSource;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.singleStepPlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.state;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchSessionLeakTest {

    private static WorkbenchStateFixtures.FakeBackend backendByTargetAmount() {
        return new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
    }

    private static PlannerWorkbenchController singleTargetController(
        WorkbenchStateFixtures.FakeBackend backend,
        Executor executor
    ) {
        return new PlannerWorkbenchController(
            state("tab-leak", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            executor
        );
    }

    @Test
    void replansAndSettingChangesKeepSessionCountsBalanced() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        PlannerWorkbenchController controller = singleTargetController(backend, sync());

        controller.replan();
        controller.replan();
        controller.onGraphVersionChanged(2L);
        controller.replan();
        controller.setProfile(
            new PlanningProfile(
                "profile.min_human",
                ObjectivePreset.quickHandcraft,
                CostWeights.forPreset(ObjectivePreset.quickHandcraft),
                0.25,
                3
            )
        );
        controller.setBudget(
            new PlanningBudget(Duration.ofMillis(50), Duration.ofMillis(200), 1000, 2000, 2, 3, true, true)
        );
        controller.setUseLiveStock(false);
        controller.setSessionRules(
            List.of(
                new MachineRule(
                    "rule-machine",
                    GuidanceScope.session,
                    RuleStrength.softPrefer,
                    "minecraft:furnace",
                    true
                )
            )
        );
        controller.refine(
            new ApplyRulesFeedback(
                List.of(
                    new MachineRule(
                        "rule-refine",
                        GuidanceScope.session,
                        RuleStrength.softPrefer,
                        "minecraft:furnace",
                        true
                    )
                ),
                false
            ),
            null
        );

        assertTrue(backend.openCount() >= 6, "each generation change must open a fresh session");
        assertEquals(
            controller.openSessionCount() - 1,
            controller.closedSessionCount(),
            "exactly one session may stay open while the tab is alive"
        );
        for (WorkbenchStateFixtures.FakeSession session : backend.sessions) {
            assertTrue(session.closeCount <= 1, "session " + session.index + " closed more than once");
        }

        controller.close();

        assertEquals(controller.openSessionCount(), controller.closedSessionCount());
        for (WorkbenchStateFixtures.FakeSession session : backend.sessions) {
            assertEquals(1, session.closeCount, "session " + session.index + " must be closed exactly once");
        }
    }

    @Test
    void closedControllerDoesNotNotifyListenersAnymore() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        PlannerWorkbenchController controller = singleTargetController(backend, sync());
        controller.replan();

        AtomicInteger callbacks = new AtomicInteger();
        controller.addListener(callbacks::incrementAndGet);
        int beforeClose = callbacks.get();

        controller.close();
        int afterClose = callbacks.get();
        assertEquals(beforeClose, afterClose, "closing must not publish a snapshot");

        controller.setTargets(List.of(demand(ironIngot, 4L)), null);
        controller.replan();
        controller.setFlowViewMode(FlowViewMode.preparationLedger);
        controller.setProfile(PlanningProfile.defaultBalanced());
        controller.close();

        assertEquals(afterClose, callbacks.get(), "closed controller must stay silent");
        assertEquals(controller.openSessionCount(), controller.closedSessionCount());
        assertEquals(WorkbenchPhase.closed, controller.snapshot().phase());
    }

    @Test
    void closeWithQueuedPlanningTaskStillReleasesEverySession() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-leak", 1L, List.of()),
            backend,
            requestSource(),
            executor
        );
        AtomicInteger callbacks = new AtomicInteger();
        controller.addListener(callbacks::incrementAndGet);

        controller.setTargets(List.of(demand(ironIngot, 3L)), null);
        int beforeClose = callbacks.get();
        controller.close();
        executor.runAll();

        assertEquals(beforeClose, callbacks.get());
        assertEquals(1, backend.openCount(), "queued task still opens, then discards, its session");
        assertEquals(1, controller.openSessionCount());
        assertEquals(1, controller.closedSessionCount());
        assertEquals(1, backend.sessions.get(0).closeCount);
        assertEquals(WorkbenchPhase.closed, controller.snapshot().phase());
    }

    @Test
    void tabSwitchingAndHotReloadKeepEveryControllerBalanced() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        SessionTabManager manager = new SessionTabManager(
            state -> new PlannerWorkbenchController(state, backend, requestSource(), sync())
        );

        PlannerWorkbenchController iron = manager.openOrFocusTab(List.of(demand(ironIngot, 1L)), null);
        PlannerWorkbenchController coalTab = manager.openOrFocusTab(List.of(demand(coal, 2L)), null);
        assertEquals(2, manager.tabs().size());

        manager.onRuntimeGraphVersionChanged(1L);
        manager.onRuntimeGraphVersionChanged(2L);
        manager.activate(iron.tabId());
        manager.activate(coalTab.tabId());
        manager.activate(iron.tabId());

        for (PlannerWorkbenchController tab : List.of(iron, coalTab)) {
            assertTrue(tab.openSessionCount() >= 1, "tab " + tab.tabId() + " never planned");
            assertEquals(
                tab.openSessionCount() - 1,
                tab.closedSessionCount(),
                "tab " + tab.tabId() + " leaked a planning session"
            );
        }

        manager.closeAll();

        for (PlannerWorkbenchController tab : List.of(iron, coalTab)) {
            assertEquals(tab.openSessionCount(), tab.closedSessionCount());
            assertEquals(WorkbenchPhase.closed, tab.snapshot().phase());
        }
        assertTrue(manager.tabs().isEmpty());
        manager.close();
    }
}
