package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.requestSource;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.singleStepPlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.smeltIron;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.state;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchPhaseTransitionTest {

    @Test
    void noGraphPhaseUntilGraphVersionIsPublished() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-ready")))
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 0L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            sync()
        );

        assertEquals(WorkbenchPhase.noGraph, controller.snapshot().phase());
        controller.setTargets(List.of(demand(ironIngot, 1L)), null);
        assertEquals(WorkbenchPhase.noGraph, controller.snapshot().phase());
        assertEquals(0, backend.openCount(), "graph-less tab must not open a planning session");

        controller.onGraphVersionChanged(1L);
        assertEquals(WorkbenchPhase.idleNoTarget, controller.snapshot().phase());
        assertEquals(0, backend.openCount());

        controller.replan();
        assertEquals(WorkbenchPhase.readyPareto, controller.snapshot().phase());
        assertEquals(1, backend.openCount());
    }

    @Test
    void planningPhaseObservedBeforeReadyPareto() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-ready")))
        );
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 1L, List.of()),
            backend,
            requestSource(),
            executor
        );
        assertEquals(WorkbenchPhase.idleNoTarget, controller.snapshot().phase());

        List<WorkbenchPhase> observed = new ArrayList<>();
        controller.addListener(() -> observed.add(controller.snapshot().phase()));

        controller.setTargets(List.of(demand(ironIngot, 1L)), null);
        assertEquals(WorkbenchPhase.planning, controller.snapshot().phase());
        assertTrue(controller.snapshot().planningInFlight());
        assertEquals(1, executor.pending());

        executor.runAll();

        assertEquals(WorkbenchPhase.readyPareto, controller.snapshot().phase());
        assertFalse(controller.snapshot().planningInFlight());
        assertEquals(List.of(WorkbenchPhase.planning, WorkbenchPhase.readyPareto), observed);
        assertEquals("plan-ready", controller.snapshot().selectedPlanId());
        assertSame(controller.snapshot().result(), controller.snapshot().lastFeasibleResult());
    }

    @Test
    void budgetHitPhaseKeepsParetoPlans() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.noPlanWithinBudget, List.of(singleStepPlan("plan-budget")))
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            sync()
        );

        controller.replan();

        assertEquals(WorkbenchPhase.readyBudgetHit, controller.snapshot().phase());
        assertEquals(
            PlanningStatus.noPlanWithinBudget,
            Objects.requireNonNull(controller.snapshot().result()).status()
        );
        assertSame(controller.snapshot().result(), controller.snapshot().lastFeasibleResult());
        assertEquals("plan-budget", controller.snapshot().selectedPlan().orElseThrow().planId());
    }

    @Test
    void conflictPhaseKeepsLastFeasibleResultForGhostOverlay() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-ok")))
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-conflict", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            sync()
        );
        controller.replan();
        PlanningResult feasible = controller.snapshot().result();

        backend.refineResult = result(PlanningStatus.infeasibleByHumanRules, List.of());
        controller.refine(
            new ApplyRulesFeedback(
                List.of(new RecipeRule("rule-ban", GuidanceScope.session, RuleStrength.hardBan, smeltIron)),
                false
            ),
            null
        );

        assertEquals(WorkbenchPhase.conflictInfeasible, controller.snapshot().phase());
        assertEquals(
            PlanningStatus.infeasibleByHumanRules,
            Objects.requireNonNull(controller.snapshot().result()).status()
        );
        assertSame(feasible, controller.snapshot().lastFeasibleResult());
        assertTrue(controller.snapshot().result().paretoPlans().isEmpty());
        assertTrue(controller.snapshot().selectedPlan().isEmpty());
        assertNull(controller.snapshot().selectedPlanId());
        assertTrue(controller.snapshot().canUndo());
        assertEquals(1, backend.last().refineCount);
        assertEquals(
            List.of("rule-ban"),
            controller.snapshot().activeSessionRules().stream().map(rule -> rule.ruleId()).toList()
        );
    }

    @Test
    void unreachablePhaseClearsSelectedPlan() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.provenInfeasible, List.of())
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            sync()
        );

        controller.replan();

        assertEquals(WorkbenchPhase.unreachable, controller.snapshot().phase());
        assertNull(controller.snapshot().selectedPlanId());
        assertNull(controller.snapshot().lastFeasibleResult());
        assertTrue(controller.snapshot().selectedPlan().isEmpty());
    }

    @Test
    void graphVersionZeroReturnsToNoGraphAndReleasesSession() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-hot")))
        );
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            sync()
        );
        controller.replan();
        assertEquals(WorkbenchPhase.readyPareto, controller.snapshot().phase());

        controller.onGraphVersionChanged(0L);

        assertEquals(WorkbenchPhase.noGraph, controller.snapshot().phase());
        assertNull(controller.snapshot().result());
        assertNull(controller.snapshot().lastFeasibleResult());
        assertEquals(controller.openSessionCount(), controller.closedSessionCount());
        assertEquals(1, backend.sessions.get(0).closeCount);
    }

    @Test
    void closedPhaseIsTerminalAndIgnoresLateMutations() {
        WorkbenchStateFixtures.FakeBackend backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(PlanningStatus.feasible, List.of(singleStepPlan("plan-closed")))
        );
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = new PlannerWorkbenchController(
            state("tab-phase", 1L, List.of(demand(ironIngot, 1L))),
            backend,
            requestSource(),
            executor
        );

        controller.close();
        controller.close();

        assertEquals(WorkbenchPhase.closed, controller.snapshot().phase());
        controller.setTargets(List.of(demand(ironIngot, 5L)), null);
        controller.replan();
        controller.setFlowViewMode(FlowViewMode.preparationLedger);
        assertEquals(WorkbenchPhase.closed, controller.snapshot().phase());
        assertEquals(0, backend.openCount(), "closed controller must not open planning sessions");
        executor.runAll();
        assertEquals(0, backend.openCount());
        assertTrue(controller.snapshot().targets().stream().anyMatch(t -> t.amount() == 1L));
        assertEquals(0, controller.openSessionCount());
        assertEquals(0, controller.closedSessionCount());
    }
}
