package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
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

import java.util.concurrent.TimeUnit;

/**
 * End-to-end routing over component-bearing item stacks: graph build, warm and fresh routing.
 * {@code chainPlain} is the component-free reference for regression checking.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ItemComponentRoutingBenchmark {

    @Param({"chainPlain", "chainComponent", "chainComponentStocked", "wideComponent"})
    public String workload;

    @Param({"64"})
    public int times;

    @Param({"64"})
    public int chainDepth;

    @Param({"4"})
    public int competingRecipesPerStep;

    @Param({"512"})
    public int competingRecipes;

    @Param({"256"})
    public int stockNoiseEntries;

    private ItemComponentRoutingScenario.Scenario scenario;
    private dev.vfyjxf.cirrus.api.recipe.routing.Stock stock;
    private RecipeGraph graph;
    private RoutingService service;
    private RecipeApiImpl previousApi;

    @Setup
    public void setup() {
        McBenchmarkBootstrap.ensureContentApi();
        stock = dev.vfyjxf.cirrus.api.recipe.routing.Stock.empty();
        scenario = switch (workload) {
            case "chainPlain" -> ItemComponentRoutingScenario.createPlainChain(chainDepth, competingRecipesPerStep);
            case "chainComponent" -> ItemComponentRoutingScenario.createComponentChain(chainDepth, competingRecipesPerStep);
            case "chainComponentStocked" -> {
                stock = ItemComponentRoutingScenario.chainComponentStock(chainDepth, competingRecipesPerStep, stockNoiseEntries);
                yield ItemComponentRoutingScenario.createComponentChain(chainDepth, competingRecipesPerStep, true);
            }
            case "wideComponent" -> ItemComponentRoutingScenario.createComponentWide(competingRecipes);
            default -> throw new IllegalArgumentException("Unknown workload: " + workload);
        };
        previousApi = ItemComponentRoutingScenario.attachRecipeApi(scenario.system());
        graph = scenario.buildGraph();
        service = scenario.newService(graph);
        RoutingOutcome warmupOutcome = route();
        if (warmupOutcome.plan() == null) {
            throw new IllegalStateException("Benchmark scenario failed to route: " + warmupOutcome.status());
        }
    }

    @TearDown
    public void tearDown() {
        ItemComponentRoutingScenario.restoreRecipeApi(previousApi);
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
        return freshService.route(scenario.request(times, stock));
    }

    private RoutingOutcome route() {
        return service.route(scenario.request(times, stock));
    }
}
