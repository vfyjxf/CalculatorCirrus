package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class CalculatorRoutingBenchmark {
    @Param({"wide", "deep"})
    public String workload;

    @Param({"1", "64", "1024"})
    public int times;

    @Param({"0", "64", "512"})
    public int competingGearRecipes;

    @Param({"64"})
    public int chainDepth;

    @Param({"4"})
    public int competingRecipesPerStep;

    private CalculatorSyntheticScenario.Scenario scenario;
    private RecipeGraph graph;
    private RoutingService service;
    private RoutingPlan cachedPlan;
    private RecipeApiImpl previousApi;

    @Setup
    public void setup() {
        scenario = switch (workload) {
            case "wide" -> CalculatorSyntheticScenario.create(competingGearRecipes);
            case "deep" -> CalculatorSyntheticScenario.createDeepChain(chainDepth, competingRecipesPerStep);
            default -> throw new IllegalArgumentException("Unknown workload: " + workload);
        };
        previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        graph = scenario.buildGraph();
        service = scenario.newService(graph);
        cachedPlan = route().plan();
        if (cachedPlan == null) {
            throw new IllegalStateException("Synthetic calculator scenario failed to route");
        }
    }

    @TearDown
    public void tearDown() {
        CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
    }

    @Benchmark
    public RecipeGraph buildGraph() {
        return scenario.buildGraph();
    }

    @Benchmark
    public RoutingOutcome routeWithCachedService() {
        return route();
    }

    @Benchmark
    public RoutingOutcome routeWithFreshService() {
        RecipeGraph freshGraph = scenario.buildGraph();
        RoutingService freshService = scenario.newService(freshGraph);
        return freshService.route(scenario.request(times));
    }

    @Benchmark
    public Object convertCachedPlanToProcessGraph() {
        return CalculatorProcessGraphBuilder.fromRoutingPlan(cachedPlan);
    }

    @Benchmark
    public void routeThenConvert(Blackhole blackhole) {
        RoutingOutcome outcome = route();
        blackhole.consume(outcome);
        if (outcome.plan() != null) {
            blackhole.consume(CalculatorProcessGraphBuilder.fromRoutingPlan(outcome.plan()));
        }
    }

    private RoutingOutcome route() {
        return service.route(scenario.request(times));
    }
}
