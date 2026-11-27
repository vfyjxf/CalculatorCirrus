package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.workbench.layout.CriticalPathCalculator.CriticalPathSchedule;
import dev.vfyjxf.cirrus.ui.workbench.layout.CriticalPathCalculator.GanttEntry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalPathCalculatorTest {

    private static final double epsilon = 1e-9;

    private static final ProductionPlan diamond = WorkbenchLayoutFixtures.diamondPlan();

    @Test
    void diamondScheduleMatchesHandComputedCpm() {
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(diamond);
        Map<String, GanttEntry> entries = index(schedule);

        assertTime(entries, "d1", 0.0, 10.0, 0.0);
        assertTime(entries, "d2", 10.0, 20.0, 10.0);
        assertTime(entries, "d3", 10.0, 12.0, 18.0);
        assertTime(entries, "d4", 20.0, 21.0, 20.0);
        assertTime(entries, "d5", 21.0, 21.5, 21.0);

        assertEquals(21.5, schedule.makespanSeconds(), epsilon, "makespan = max EF");
        assertEquals(17.0, schedule.totalHumanSeconds(), epsilon);
        assertEquals(6.0, schedule.totalPassiveSeconds(), epsilon);

        assertEquals(0.0, entry(entries, "d1").slack(), epsilon);
        assertEquals(0.0, entry(entries, "d2").slack(), epsilon);
        assertEquals(8.0, entry(entries, "d3").slack(), epsilon, "the short branch floats by 8s");
        assertEquals(0.0, entry(entries, "d4").slack(), epsilon);
        assertEquals(0.0, entry(entries, "d5").slack(), epsilon);

        assertEquals(10.0, entry(entries, "d1").activeEnd(), epsilon, "d1 has no passive wait");
        assertEquals(14.0, entry(entries, "d2").activeEnd(), epsilon, "d2 spends 4s active then 6s waiting");
        assertEquals(21.0, entry(entries, "d5").activeEnd(), epsilon, "zero-cost step keeps zero active width");
        assertEquals(21.5, entry(entries, "d5").earliestFinish(), epsilon, "zero-cost step still reserves 0.5s");
    }

    @Test
    void criticalPathIsTheUniqueDiamondSpine() {
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(diamond);
        assertEquals(Set.of("d1", "d2", "d4", "d5"), schedule.criticalStepIds());
        Map<String, GanttEntry> entries = index(schedule);
        for (String stepId : Set.of("d1", "d2", "d4", "d5")) {
            assertTrue(entry(entries, stepId).critical(), stepId + " belongs to the critical path");
        }
        assertFalse(entry(entries, "d3").critical(), "the short branch is not critical");
    }

    @Test
    void parallelTracksArePackedOptimally() {
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(diamond);
        assertEquals(2, schedule.trackCount(), "at most two steps ever wait on each other");
        Map<Integer, Integer> perTrack = new HashMap<>();
        for (GanttEntry entry : schedule.entries()) {
            perTrack.merge(entry.trackIndex(), 1, Integer::sum);
        }
        assertEquals(2, perTrack.size());
        assertTrue(
            Objects.requireNonNull(perTrack.get(0)) >= Objects.requireNonNull(perTrack.get(1)),
            "greedy packing fills the first track first"
        );
        Map<String, GanttEntry> entries = index(schedule);
        assertEquals(
            entry(entries, "d1").trackIndex(),
            entry(entries, "d2").trackIndex(),
            "d2 starts exactly when d1 ends and may reuse its track"
        );
        assertFalse(
            entry(entries, "d3").trackIndex() == entry(entries, "d2").trackIndex(),
            "d3 runs while d2 still waits"
        );
    }

    @Test
    void topBottleneckIsTheLargestPassiveWait() {
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(diamond);
        GanttEntry bottleneck = schedule.topBottleneck();
        assertNotNull(bottleneck);
        assertEquals("d2", bottleneck.stepId());
        assertEquals("minecraft:furnace", bottleneck.bottleneckMachineId());
        assertEquals(1, bottleneck.suggestedOwnedCount(), "6s of passive wait does not justify a second machine");
    }

    @Test
    void heavyPassiveWaitSuggestsASecondMachine() {
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(WorkbenchLayoutFixtures.heavyBottleneckPlan());
        GanttEntry bottleneck = schedule.topBottleneck();
        assertNotNull(bottleneck);
        assertEquals("h1", bottleneck.stepId());
        assertEquals("minecraft:furnace", bottleneck.bottleneckMachineId());
        assertEquals(2, bottleneck.suggestedOwnedCount(), "more than 20s of passive wait suggests two machines");
        assertEquals(25.5, schedule.makespanSeconds(), epsilon);
    }

    @Test
    void planWithoutPassiveWaitHasNoBottleneck() {
        ProductionPlan plan = WorkbenchLayoutFixtures.plan(
            "plan-no-wait",
            List.of(
                WorkbenchLayoutFixtures
                        .simpleCraft("n1", List.of(), WorkbenchLayoutFixtures.cost(3.0, 0.0), Optional.empty())
            ),
            PreparationSummary.empty,
            List.of()
        );
        CriticalPathSchedule schedule = CriticalPathCalculator.compute(plan);
        assertNull(schedule.topBottleneck());
        assertEquals(3.0, schedule.makespanSeconds(), epsilon);
        assertEquals(1, schedule.trackCount());
    }

    @Test
    void emptyPlanYieldsFiniteEmptySchedule() {
        CriticalPathSchedule schedule = CriticalPathCalculator
                .compute(WorkbenchLayoutFixtures.plan("plan-empty", List.of(), PreparationSummary.empty, List.of()));
        assertTrue(schedule.entries().isEmpty());
        assertEquals(0.0, schedule.makespanSeconds(), epsilon);
        assertEquals(0.0, schedule.totalHumanSeconds(), epsilon);
        assertEquals(0.0, schedule.totalPassiveSeconds(), epsilon);
        assertNull(schedule.topBottleneck());
        assertTrue(Double.isFinite(schedule.makespanSeconds()));
    }

    private static GanttEntry entry(Map<String, GanttEntry> entries, String stepId) {
        return Objects.requireNonNull(entries.get(stepId), stepId);
    }

    private static void assertTime(
        Map<String, GanttEntry> entries,
        String stepId,
        double earliestStart,
        double earliestFinish,
        double latestStart
    ) {
        GanttEntry entry = entries.get(stepId);
        assertNotNull(entry, "missing entry " + stepId);
        assertEquals(earliestStart, entry.earliestStart(), epsilon, stepId + " ES");
        assertEquals(earliestFinish, entry.earliestFinish(), epsilon, stepId + " EF");
        assertEquals(latestStart, entry.latestStart(), epsilon, stepId + " LS");
    }

    private static Map<String, GanttEntry> index(CriticalPathSchedule schedule) {
        Map<String, GanttEntry> byId = new HashMap<>();
        for (GanttEntry entry : schedule.entries()) {
            byId.put(entry.stepId(), entry);
        }
        return byId;
    }
}
