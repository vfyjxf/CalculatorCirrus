package dev.vfyjxf.cirrus.internal.performance;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionPipeline;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.internal.debug.DebugProjectionPlugin;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class CalculatorLongChainTimingTest {
    private static final long obviousPauseNanos = Duration.ofMillis(250).toNanos();

    @Test
    void debugLongChainAtTenThousandDoesNotScaleByCraftTimes(MinecraftServer server) {
        assertLongChainProjectionHealthy(10_000);
    }

    @Test
    void debugLongChainAtOneHundredThousandDoesNotScaleByCraftTimes(MinecraftServer server) {
        assertLongChainProjectionHealthy(100_000);
    }

    private static void assertLongChainProjectionHealthy(int times) {
        assumeTrue(DebugProjectionPlugin.debugEnabled());

        RecipeApi previousRecipeApi = RecipeApiImpl.instance();
        ContentApiImpl previousContentApi = ContentApiImpl.instance();
        attachContentApi();
        RecipeApiImpl api = attachDebugRecipeApi();
        try {
            @SuppressWarnings("unchecked")
            RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe> system = (RecipeSystem<DebugProjectionPlugin.DebugProjectionRecipe>) api
                    .findSystem(DebugProjectionPlugin.systemId);
            assertNotNull(system);
            DebugProjectionPlugin.DebugProjectionRecipe targetRecipe = system
                    .findRecipe(DebugProjectionPlugin.longChainTargetId);
            assertNotNull(targetRecipe);
            RecipeEntry<DebugProjectionPlugin.DebugProjectionRecipe> target = RecipeEntry.of(system, targetRecipe);

            ProjectionTiming baseline = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> timeProjection(target, 1)
            );
            ProjectionTiming timed = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> timeProjection(target, times)
            );

            assertTrue(timed.inputCount() > 0, timed::diagnosticLine);
            assertEquals(baseline.inputCount(), timed.inputCount(), timed::diagnosticLine);
            assertEquals(baseline.outputCount(), timed.outputCount(), timed::diagnosticLine);
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

    private static ProjectionTiming timeProjection(RecipeEntry<?> target, int times) {
        RecipeProjection projection = target.resolveResult().projection();
        assertNotNull(projection, "debug chain target must resolve");
        ExecutionContext context = ProjectionContexts.calculator(
            ProjectionContexts.global(),
            new CalculatorProjectionContext(target, Math.max(1, times), 0L)
        );
        long start = System.nanoTime();
        ProjectedRecipe projected = ProjectionPipeline.fromRegistry().project(context, projection);
        long nanos = System.nanoTime() - start;
        return new ProjectionTiming(times, projected.inputs().size(), projected.outputs().size(), nanos);
    }

    private static RecipeApiImpl attachDebugRecipeApi() {
        DebugProjectionPlugin plugin = new DebugProjectionPlugin();
        InternalPlugin internal = new InternalPlugin();
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        internal.registerProjectionPlugin(builder);
        internal.registerProjectionEffect(builder);
        plugin.registerProjectionPlugin(builder);
        plugin.registerProjectionContextOption(builder);
        plugin.registerRecipeSystem(builder);
        plugin.registerProjectionEffect(builder);
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

    private record ProjectionTiming(int times, int inputCount, int outputCount, long nanos) {
        private String diagnosticLine() {
            return "Long-chain projection: times=" + times + ", inputs=" + inputCount + ", outputs=" + outputCount
                    + ", millis=" + nanos / 1_000_000.0d;
        }
    }
}
