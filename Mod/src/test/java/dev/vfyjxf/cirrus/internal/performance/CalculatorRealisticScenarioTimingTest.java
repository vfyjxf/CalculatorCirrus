package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorRealisticScenarioTimingTest {
    private static final long obviousPauseNanos = Duration.ofMillis(250).toNanos();

    @Test
    void workbenchStyleTargetDoesNotCauseObviousSingleCalculatePause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(384);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(64)
        );

        assertHealthySingleCalculation(sample);
        assertTrue(sample.processGraphNodes() >= 7, sample::diagnosticLine);
    }

    @Test
    void machineLineTargetDoesNotCauseObviousSingleCalculatePause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createMachineLineLike(8, 512);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(128)
        );

        assertHealthySingleCalculation(sample);
        assertTrue(sample.processGraphNodes() >= 10, sample::diagnosticLine);
    }

    @Test
    void broadCandidatePoolTargetDoesNotCauseObviousSingleCalculatePause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(1024);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(1024)
        );

        assertHealthySingleCalculation(sample);
        assertTrue(sample.metrics().generatedCandidates() >= 1024, sample::diagnosticLine);
    }

    private static void assertHealthySingleCalculation(CalculatorSyntheticScenario.ProbeSample sample) {
        assertEquals(RoutingStatus.success, sample.status(), sample::diagnosticLine);
        assertTrue(sample.actionCount() > 0, sample::diagnosticLine);
        assertTrue(sample.processGraphNodes() > 0, sample::diagnosticLine);
        assertTrue(
            sample.totalNanos() < obviousPauseNanos,
            () -> sample.diagnosticLine() + ", playerNoticeable=true"
        );
    }
}
