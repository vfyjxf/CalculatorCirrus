package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.internal.debug.DebugProjectionPlugin;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class CalculatorLongChainTimingTest {
    private static final long obviousPauseNanos = Duration.ofMillis(250).toNanos();

    @Test
    void debugLongChainAtTenThousandDoesNotScaleByCraftTimes(MinecraftServer server) {
        assertLongChainCalculationHealthy(10_000);
    }

    @Test
    void debugLongChainAtOneHundredThousandDoesNotScaleByCraftTimes(MinecraftServer server) {
        assertLongChainCalculationHealthy(100_000);
    }

    private static void assertLongChainCalculationHealthy(int times) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previousRecipeApi = RecipeApiImpl.instance();
        ContentApiImpl previousContentApi = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system =
                (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api.findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            DebugProjectionPlugin.DebugProjectionRecipe targetRecipe = system.findRecipe(DebugProjectionPlugin.longChainTargetId);
            assertNotNull(targetRecipe);
            RecipeEntry<DebugProjectionPlugin.DebugProjectionRecipe> target = RecipeEntry.of(system, targetRecipe);

            TimedGraph timed = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> timeGraphBuild(target, times)
            );

            assertTrue(timed.graph().nodeCount() >= DebugProjectionPlugin.longChainDepth, timed::diagnosticLine);
            assertTrue(timed.graph().nodeCount() < DebugProjectionPlugin.longChainDepth * 3, timed::diagnosticLine);
            assertTrue(timed.nanos() < obviousPauseNanos, timed::diagnosticLine);
        } finally {
            if (previousRecipeApi instanceof RecipeApiImpl impl) {
                RecipeApiImpl.attach(impl);
            }
            if (previousContentApi != null) {
                ContentApiImpl.attach(previousContentApi);
            }
        }
    }

    private static TimedGraph timeGraphBuild(RecipeEntry<?> target, int times) {
        long start = System.nanoTime();
        ProcessGraph graph = CalculatorProcessGraphBuilder.build(target, times);
        return new TimedGraph(times, graph, System.nanoTime() - start);
    }

    private static RecipeApiImpl attachDebugRecipeApi() {
        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        InternalPlugin internal = new InternalPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        internal.registerProjectionPlugin(builder);
        internal.registerRecipeClause(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerRecipeClause(builder);
        plugin.registerMachineEntry(builder);
        RecipeApiImpl api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
        return api;
    }

    private static void attachContentApi() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }

    private record TimedGraph(
        int times,
        ProcessGraph graph,
        long nanos
    ) {
        private String diagnosticLine() {
            return "Long-chain calculator: times=" + times
                + ", nodes=" + graph.nodeCount()
                + ", edges=" + graph.edgeCount()
                + ", millis=" + nanos / 1_000_000.0d;
        }
    }
}
