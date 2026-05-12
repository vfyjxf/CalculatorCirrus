package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalculatorJfrProbeTest {
    @Test
    void profileRouteThenConvertWhenEnabled() throws Exception {
        assumeTrue(Boolean.getBoolean("cirrus.jfr.probe.enabled"));

        String workload = System.getProperty("cirrus.jfr.probe.workload", "wide");
        int times = Integer.getInteger("cirrus.jfr.probe.times", 1024);
        int iterations = Integer.getInteger("cirrus.jfr.probe.iterations", 200);
        int warmup = Integer.getInteger("cirrus.jfr.probe.warmup", 20);
        boolean fullAllocations = Boolean.getBoolean("cirrus.jfr.probe.fullAllocations");
        Path destination = Path.of(System.getProperty(
            "cirrus.jfr.probe.file",
            "build/reports/cirrus-calculator-probe.jfr"
        )).toAbsolutePath();

        CalculatorSyntheticScenario.Scenario scenario = switch (workload) {
            case "deep" -> CalculatorSyntheticScenario.createDeepChain(64, 4);
            case "machine" -> CalculatorSyntheticScenario.createMachineLineLike(8, 512);
            case "workbench" -> CalculatorSyntheticScenario.createWorkbenchLike(384);
            case "wide" -> CalculatorSyntheticScenario.create(1024);
            default -> throw new IllegalArgumentException("Unknown workload: " + workload);
        };

        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeGraph graph = scenario.buildGraph();
            RoutingService service = scenario.newService(graph);

            long checksum = 0L;
            for (int i = 0; i < warmup; i++) {
                checksum += routeThenConvert(service, scenario, times);
            }

            Files.createDirectories(destination.getParent());
            try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
                recording.setName("cirrus-calculator-" + workload);
                recording.setToDisk(true);
                recording.setMaxAge(Duration.ofMinutes(10));
                recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                if (fullAllocations) {
                    recording.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ZERO).withStackTrace();
                    recording.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ZERO).withStackTrace();
                }
                recording.enable("jdk.ObjectCountAfterGC");
                recording.enable("jdk.GCHeapSummary");
                recording.enable("jdk.G1HeapSummary");
                recording.start();

                for (int i = 0; i < iterations; i++) {
                    checksum += routeThenConvert(service, scenario, times);
                }

                System.gc();
                Thread.sleep(250L);
                recording.stop();
                recording.dump(destination);
            }

            assertTrue(checksum > 0L, "probe must consume routing and graph conversion results");
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    private static long routeThenConvert(
        RoutingService service,
        CalculatorSyntheticScenario.Scenario scenario,
        int times
    ) {
        RoutingOutcome outcome = service.route(scenario.request(times));
        RoutingPlan plan = outcome.plan();
        long checksum = outcome.metrics().expandedNodes() + outcome.metrics().generatedCandidates();
        if (plan != null) {
            ProcessGraph graph = CalculatorProcessGraphBuilder.fromRoutingPlan(plan);
            checksum += plan.actions().size();
            checksum += graph.nodeCount();
            checksum += graph.edgeCount();
        }
        return checksum;
    }
}
