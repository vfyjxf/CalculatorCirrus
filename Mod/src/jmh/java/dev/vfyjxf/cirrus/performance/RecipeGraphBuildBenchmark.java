package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
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
@Fork(1)
public class RecipeGraphBuildBenchmark {
    @State(Scope.Benchmark)
    public static class WideCatalog {
        @Param({"4096", "16384", "65536"})
        public int competingRecipes;

        private CalculatorSyntheticScenario.Scenario scenario;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            scenario = CalculatorSyntheticScenario.create(competingRecipes);
            previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @State(Scope.Benchmark)
    public static class RetainedWideCatalog {
        @Param({"65536"})
        public int competingRecipes;

        private CalculatorSyntheticScenario.Scenario scenario;
        private RecipeGraph graph;
        private RecipeApiImpl previousApi;

        @Setup(Level.Trial)
        public void setup() {
            scenario = CalculatorSyntheticScenario.create(competingRecipes);
            previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
            graph = scenario.buildGraph();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            graph = null;
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
        }
    }

    @Benchmark
    public RecipeGraph buildWideGraph(WideCatalog catalog) {
        return catalog.scenario.buildGraph();
    }

    @Benchmark
    public int retainedWideGraphNodeCount(RetainedWideCatalog catalog) throws InterruptedException {
        Thread.sleep(50L);
        return catalog.graph.nodes().size();
    }
}
