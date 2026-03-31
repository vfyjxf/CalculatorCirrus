package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorLargeQuantityTimingTest {
    private static final long obviousPauseNanos = Duration.ofMillis(250).toNanos();

    @Test
    void workbenchStyleRouteAtOneThousandDoesNotCauseObviousPause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(384);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(1_024)
        );

        assertHealthyLargeCalculation(sample);
    }

    @Test
    void machineLineRouteAtFourThousandDoesNotCauseObviousPause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createMachineLineLike(8, 512);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(4_096)
        );

        assertHealthyLargeCalculation(sample);
    }

    @Test
    void broadCandidatePoolRouteAtSixteenThousandDoesNotCauseObviousPause() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(1_024);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(3),
            () -> scenario.profile(16_384)
        );

        assertHealthyLargeCalculation(sample);
    }

    private static void assertHealthyLargeCalculation(CalculatorSyntheticScenario.ProbeSample sample) {
        assertEquals(RoutingStatus.success, sample.status(), sample::diagnosticLine);
        assertTrue(sample.actionCount() > 0, sample::diagnosticLine);
        assertTrue(sample.processGraphNodes() > 0, sample::diagnosticLine);
        assertTrue(
            sample.totalNanos() < obviousPauseNanos,
            () -> sample.diagnosticLine() + ", playerNoticeable=true"
        );
    }
}
