package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorPerformanceProbeTest {
    @Test
    void wideSyntheticCalculatorRouteProducesPhaseMetrics() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(512);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () -> scenario.profile(1024)
        );

        assertEquals(RoutingStatus.success, sample.status(), sample::diagnosticLine);
        assertTrue(sample.actionCount() >= 7, sample::diagnosticLine);
        assertTrue(sample.processGraphNodes() >= 7, sample::diagnosticLine);
        assertTrue(sample.processGraphEdges() >= 6, sample::diagnosticLine);
        assertTrue(sample.graphBuildNanos() > 0, sample::diagnosticLine);
        assertTrue(sample.routeNanos() > 0, sample::diagnosticLine);
        assertTrue(sample.processGraphNanos() > 0, sample::diagnosticLine);
        assertTrue(sample.metrics().generatedCandidates() >= 512, sample::diagnosticLine);
    }

    @Test
    void deepSyntheticCalculatorRouteProducesPhaseMetrics() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createDeepChain(48, 4);

        CalculatorSyntheticScenario.ProbeSample sample = assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () -> scenario.profile(256)
        );

        assertEquals(RoutingStatus.success, sample.status(), sample::diagnosticLine);
        assertTrue(sample.recipeCount() >= 48, sample::diagnosticLine);
        assertTrue(sample.actionCount() >= 48, sample::diagnosticLine);
        assertTrue(sample.processGraphNodes() >= 48, sample::diagnosticLine);
        assertTrue(sample.metrics().generatedCandidates() >= 48, sample::diagnosticLine);
    }

    @Test
    void outputTargetAdapterSyntheticRouteProducesMetrics() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(128);
        var previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeGraph graph = scenario.buildGraph();
            RoutingService service = scenario.newService(graph);
            RoutingOutcome outcome = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> service.route(scenario.outputRequest(512))
            );

            assertEquals(RoutingStatus.success, outcome.status());
            assertTrue(outcome.metrics().adapterAttemptCount() >= 1);
            assertTrue(outcome.metrics().generatedCandidates() >= 1);
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }
}
