package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class CalculatorHotspotProbeTest {
    private static final int seconds = 30;
    private static final int warmupIterations = 100;
    private static final int targetTimes = 4096;
    private static final int competingCandidates = 1024;

    private static volatile Object objectSink;
    private static volatile long longSink;

    @Test
    void profileWideEndToEndCalculatorPath(MinecraftServer server) {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(competingCandidates);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            runEndToEnd(scenario);
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    private static void runEndToEnd(CalculatorSyntheticScenario.Scenario scenario) {
        RoutingRequest request = scenario.request(targetTimes);
        for (int i = 0; i < warmupIterations; i++) {
            RecipeGraph graph = scenario.buildGraph();
            RoutingOutcome outcome = requireSuccess(scenario.newService(graph).route(request));
            if (outcome.plan() != null) {
                objectSink = CalculatorProcessGraphBuilder.fromRoutingPlan(outcome.plan());
            }
        }

        long started = System.nanoTime();
        long deadline = started + seconds * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            RecipeGraph graph = scenario.buildGraph();
            RoutingService service = scenario.newService(graph);
            RoutingOutcome outcome = requireSuccess(service.route(request));
            RoutingPlan plan = outcome.plan();
            if (plan != null) {
                ProcessGraph processGraph = CalculatorProcessGraphBuilder.fromRoutingPlan(plan);
                consume(processGraph, processGraph.nodeCount() + processGraph.edgeCount());
            }
        }
    }

    private static RoutingOutcome requireSuccess(RoutingOutcome outcome) {
        if (outcome.status() != RoutingStatus.success) {
            throw new IllegalStateException("Synthetic routing failed: " + outcome.status() + " issues=" + outcome.issues());
        }
        return outcome;
    }

    private static void consume(Object object, long value) {
        objectSink = object;
        longSink += value;
    }
}
