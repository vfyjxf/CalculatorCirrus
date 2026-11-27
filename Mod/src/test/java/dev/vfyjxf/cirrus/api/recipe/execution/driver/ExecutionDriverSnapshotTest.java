package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionDriverSnapshotTest {

    @Test
    void snapshotContainsSessionAndTelemetry() {
        var driver = new TestDriver();

        var snapshot = driver.snapshot();

        assertNotNull(snapshot.session());
        assertNotNull(snapshot.telemetry());
    }

    private static final class TestDriver implements ExecutionDriver {
        private static final TestRecipeSystem recipeSystem = new TestRecipeSystem();
        private final ExecutionSession session = ExecutionSession.start(
            new InteractionExecutionPlan(recipeEntry("test"), 1, Lists.immutable.empty()),
            ExecutionFlowNode
                    .pending("root", Component.literal("root"), Lists.immutable.empty(), null, Lists.immutable.empty())
        );

        @Override
        public ExecutionSession session() {
            return session;
        }

        @Override
        public Namespace interactionId() {
            return Namespace.of("test", "driver_snapshot");
        }

        @Override
        public InteractionApi interactionApi() {
            return SlotExecutionDriver
                    .simpleInteraction(interactionId(), false, ExecutionGuidance.simple(Component.literal("snapshot")));
        }

        @Override
        public ExecutionDriverSnapshot snapshot() {
            return new ExecutionDriverSnapshot(
                session,
                null,
                ExecutionTelemetrySnapshot.from(session, 0.0d, false),
                Lists.immutable.empty(),
                null,
                ExecutionOverlayTraceSnapshot.empty(),
                0.0d,
                false,
                null
            );
        }

        @Override
        public void advanceSteps(int steps) {}

        @Override
        public void pause() {}

        @Override
        public void resume() {}

        @Override
        public void toggleAutoAdvance() {}

        @Override
        public void stopAutoAdvance() {}

        @Override
        public void setRatePerSecond(double rate) {}

        @Override
        public void reset() {}

        @Override
        public void jumpToStep(int index) {}

        @Override
        public void tick() {}

        private static RecipeEntry<TestRecipe> recipeEntry(String id) {
            return RecipeEntry.of(recipeSystem, new TestRecipe(id));
        }
    }

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "driver_snapshot");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public ResourceLocation getId(TestRecipe recipe) {
            byId.put(recipe.id(), recipe);
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return r1.id().equals(r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }
}
