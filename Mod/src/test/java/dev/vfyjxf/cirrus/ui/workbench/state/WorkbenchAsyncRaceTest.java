package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.requestSource;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.singleStepPlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkbenchAsyncRaceTest {

    private static final int generations = 20;

    private static PlannerWorkbenchController racingController(
        WorkbenchStateFixtures.FakeBackend backend,
        WorkbenchStateFixtures.QueuedExecutor executor
    ) {
        return new PlannerWorkbenchController(state("tab-race", 1L, List.of()), backend, requestSource(), executor);
    }

    private static WorkbenchStateFixtures.FakeBackend backendByTargetAmount() {
        return new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
    }

    private static List<String> appliedPlanIds(List<WorkbenchUiSnapshot> observed) {
        return observed.stream()
                .filter(snapshot -> snapshot.result() != null && !snapshot.result().paretoPlans().isEmpty())
                .map(snapshot -> snapshot.result().paretoPlans().get(0).planId()).distinct().toList();
    }

    @Test
    void reversedCompletionAppliesOnlyLatestGeneration() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = racingController(backend, executor);
        List<WorkbenchUiSnapshot> observed = new ArrayList<>();
        controller.addListener(() -> observed.add(controller.snapshot()));

        for (int i = 1; i <= generations; i++) {
            controller.setTargets(List.of(demand(ironIngot, i)), null);
        }

        assertEquals(generations, executor.pending());
        assertEquals(0, backend.openCount());
        assertEquals(WorkbenchPhase.planning, controller.snapshot().phase());

        executor.runAllReversed();

        assertEquals(generations, backend.openCount());
        assertEquals(generations, controller.openSessionCount());
        assertEquals(generations - 1, controller.closedSessionCount());
        assertEquals("plan-20", controller.snapshot().selectedPlan().orElseThrow().planId());
        assertEquals("plan-20", controller.snapshot().selectedPlanId());
        assertFalse(controller.snapshot().planningInFlight());
        assertEquals(List.of("plan-20"), appliedPlanIds(observed));

        assertEquals("plan-20", backend.sessions.get(0).planId);
        assertEquals(0, backend.sessions.get(0).closeCount, "winning generation keeps its session open");
        for (int i = 1; i < generations; i++) {
            assertEquals(1, backend.sessions.get(i).closeCount, "stale session " + i + " must be closed exactly once");
        }

        controller.close();
        executor.runAll();
        assertEquals(generations, controller.openSessionCount());
        assertEquals(generations, controller.closedSessionCount());
        assertEquals(1, backend.sessions.get(0).closeCount);
    }

    @Test
    void inOrderCompletionAlsoDiscardsEveryStaleSession() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = racingController(backend, executor);
        List<WorkbenchUiSnapshot> observed = new ArrayList<>();
        controller.addListener(() -> observed.add(controller.snapshot()));

        for (int i = 1; i <= generations; i++) {
            controller.setTargets(List.of(demand(ironIngot, i)), null);
        }
        executor.runAll();

        assertEquals(generations, controller.openSessionCount());
        assertEquals(generations - 1, controller.closedSessionCount());
        assertEquals("plan-20", controller.snapshot().selectedPlanId());
        assertEquals(List.of("plan-20"), appliedPlanIds(observed));
        assertEquals("plan-20", backend.sessions.get(generations - 1).planId);
        assertEquals(0, backend.sessions.get(generations - 1).closeCount);
        for (int i = 0; i < generations - 1; i++) {
            assertEquals(1, backend.sessions.get(i).closeCount);
        }

        controller.close();
        executor.runAll();
        assertEquals(generations, controller.openSessionCount());
        assertEquals(generations, controller.closedSessionCount());
    }

    @Test
    void replanQueuedBehindTargetChangeStillWinsOnlyOnce() {
        WorkbenchStateFixtures.FakeBackend backend = backendByTargetAmount();
        WorkbenchStateFixtures.QueuedExecutor executor = new WorkbenchStateFixtures.QueuedExecutor();
        PlannerWorkbenchController controller = racingController(backend, executor);

        controller.setTargets(List.of(demand(ironIngot, 7L)), null);
        controller.replan();
        assertEquals(2, executor.pending());

        executor.runAllReversed();

        assertEquals(2, backend.openCount());
        assertEquals(2, controller.openSessionCount());
        assertEquals(1, controller.closedSessionCount());
        assertEquals(0, backend.sessions.get(0).closeCount);
        assertEquals(1, backend.sessions.get(1).closeCount);
        assertEquals("plan-7", controller.snapshot().selectedPlanId());
        assertFalse(controller.snapshot().planningInFlight());
    }
}
