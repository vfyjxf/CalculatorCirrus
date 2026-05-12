package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms6g", "-Xmx16g"})
public class RecipeRoutingScaleBenchmark {
    @State(Scope.Benchmark)
    public static class BuildCatalog {
        @Param({"65536", "262144", "1000000"})
        public int recipeCount;

        @Param({"48"})
        public int pathDepth;

        @Param({"4"})
        public int maxInputs;

        @Param({"64"})
        public int noiseFanout;

        @Param({"3405691582"})
        public long seed;

        private CalculatorSyntheticScenario.Scenario scenario;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            scenario = scenario(recipeCount, pathDepth, maxInputs, noiseFanout, seed);
            previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @State(Scope.Benchmark)
    public static class RouteCatalog {
        @Param({"65536", "262144", "1000000"})
        public int recipeCount;

        @Param({"48"})
        public int pathDepth;

        @Param({"4"})
        public int maxInputs;

        @Param({"64"})
        public int noiseFanout;

        @Param({"3405691582"})
        public long seed;

        private CalculatorSyntheticScenario.Scenario scenario;
        private RecipeGraph graph;
        private RoutingService service;
        private RoutingRequest recipeRequest;
        private RoutingRequest outputRequest;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            scenario = scenario(recipeCount, pathDepth, maxInputs, noiseFanout, seed);
            previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
            graph = scenario.buildGraph();
            service = scenario.newService(graph);
            recipeRequest = scenario.request(1);
            outputRequest = scenario.outputRequest(1);
            assertRoutes("recipe", service.route(recipeRequest));
            assertRoutes("output", service.route(outputRequest));
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            graph = null;
            service = null;
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @Benchmark
    public RecipeGraph buildRandomComplexGraph(BuildCatalog catalog) {
        return catalog.scenario.buildGraph();
    }

    @Benchmark
    public RoutingOutcome routeRandomComplexRecipeWarm(RouteCatalog catalog) {
        return catalog.service.route(catalog.recipeRequest);
    }

    @Benchmark
    public RoutingOutcome routeRandomComplexOutputWarm(RouteCatalog catalog) {
        return catalog.service.route(catalog.outputRequest);
    }

    @Benchmark
    public int retainedRandomComplexGraphNodeCount(RouteCatalog catalog) throws InterruptedException {
        Thread.sleep(50L);
        return catalog.graph.nodes().size();
    }

    private static CalculatorSyntheticScenario.Scenario scenario(
        int recipeCount,
        int pathDepth,
        int maxInputs,
        int noiseFanout,
        long seed
    ) {
        return CalculatorSyntheticScenario.createRandomComplexCatalog(recipeCount, pathDepth, maxInputs, noiseFanout, seed);
    }

    private static void assertRoutes(String target, RoutingOutcome outcome) {
        if (outcome.status() != RoutingStatus.success || outcome.plan() == null) {
            throw new IllegalStateException("Random complex " + target + " route failed: " + outcome.status());
        }
    }
}
