package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTelemetrySnapshotContractTest {

    @Test
    void fromSessionCapturesPausedManualCheckpointGuidanceAsPauseDetails() {
        var currentStep = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            new ExecutionGuidance(
                Component.literal("Need attention"),
                Component.literal("Check the bench"),
                Lists.immutable.of(
                    Component.literal("Open the bench"),
                    Component.literal("Press continue")
                ),
                Sets.immutable.empty()
            )
        );
        var nextStep = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Second step"))
        );
        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "current",
                    Component.literal("Current stage"),
                    Lists.immutable.of(currentStep),
                    currentStep.guidance(),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "next",
                    Component.literal("Next stage"),
                    Lists.immutable.of(nextStep),
                    nextStep.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("telemetry"), 1, Lists.immutable.of(currentStep, nextStep)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.of(currentStep),
            null,
            new PauseReason(PauseReasons.awaitingPlayerAction, currentStep.guidance()),
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.of(currentStep), null, null)
            )
        );

        var snapshot = ExecutionTelemetrySnapshot.from(session, 4.5d, true);

        assertEquals("paused", snapshot.status());
        assertTrue(snapshot.paused());
        assertEquals(0, snapshot.completedSteps());
        assertEquals(2, snapshot.totalSteps());
        assertEquals(0.0d, snapshot.progress());
        assertNotNull(snapshot.current());
        assertEquals("current", snapshot.current().id());
        assertEquals("Current stage", snapshot.current().label());
        assertNotNull(snapshot.next());
        assertEquals("next", snapshot.next().id());
        assertEquals("Next stage", snapshot.next().label());
        assertNotNull(snapshot.parent());
        assertEquals("root", snapshot.parent().id());
        assertEquals(2, snapshot.path().size());
        assertEquals("Root", snapshot.path().getFirst().label());
        assertEquals("Current stage", snapshot.path().getLast().label());
        assertEquals(2, snapshot.warnings().size());
        assertEquals("Open the bench", snapshot.warnings().getFirst());
        assertEquals("Need attention", snapshot.errorSummary());
        assertEquals("Check the bench", snapshot.errorDetail());
        assertEquals(4.5d, snapshot.ratePerSecond());
        assertTrue(snapshot.autoAdvanceEnabled());
    }

    @Test
    void fromSessionCapturesFailureLikePauseReasonsAsErrors() {
        var step = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            new ExecutionGuidance(
                Component.literal("Missing provider"),
                Component.literal("Check the source"),
                Lists.immutable.of(Component.literal("Open the source")),
                Sets.immutable.empty()
            )
        );
        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "current",
                    Component.literal("Current stage"),
                    Lists.immutable.of(step),
                    step.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("failure"), 1, Lists.immutable.of(step)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.of(step),
            null,
            new PauseReason(PauseReasons.noAccessibleProvider, step.guidance()),
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.of(step), null, null)
            )
        );

        var snapshot = ExecutionTelemetrySnapshot.from(session, 1.0d, false);

        assertEquals("paused", snapshot.status());
        assertEquals("Missing provider", snapshot.errorSummary());
        assertEquals("Check the source", snapshot.errorDetail());
        assertEquals(1, snapshot.warnings().size());
        assertEquals("Open the source", snapshot.warnings().getFirst());
    }

    @Test
    void fromSessionMapsRunningStatusForNonPausedSessions() {
        var currentStep = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Current"))
        );
        var nextStep = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Next"))
        );
        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "current",
                    Component.literal("Current stage"),
                    Lists.immutable.of(currentStep),
                    currentStep.guidance(),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "next",
                    Component.literal("Next stage"),
                    Lists.immutable.of(nextStep),
                    nextStep.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("running"), 1, Lists.immutable.of(currentStep, nextStep)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            currentStep,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.of(currentStep), null, null)
            )
        );

        var snapshot = ExecutionTelemetrySnapshot.from(session, 3.0d, false);

        assertEquals("running", snapshot.status());
        assertTrue(snapshot.current() != null);
        assertEquals("current", snapshot.current().id());
        assertTrue(snapshot.next() != null);
        assertEquals("next", snapshot.next().id());
        assertNull(snapshot.errorSummary());
        assertNull(snapshot.errorDetail());
        assertEquals(0.0d, snapshot.progress());
    }

    @Test
    void fromSessionMapsCompletedStatusForEmptyPlansAndKeepsNextNull() {
        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.empty()
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("completed"), 1, Lists.immutable.empty()),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                ExecutionFlowViewModel.RuntimeState.empty()
            )
        );

        var snapshot = ExecutionTelemetrySnapshot.from(session, 0.0d, false);

        assertEquals("completed", snapshot.status());
        assertTrue(snapshot.completed());
        assertEquals(1, snapshot.path().size());
        assertEquals("root", snapshot.current().id());
        assertNull(snapshot.next());
        assertNull(snapshot.errorSummary());
        assertNull(snapshot.errorDetail());
        assertEquals(0.0d, snapshot.progress());
    }

    @Test
    void fromSessionMapsCompletedStatusAtTerminalStepWithNullNext() {
        var step = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Done"))
        );
        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "leaf",
                    Component.literal("Leaf"),
                    Lists.immutable.of(step),
                    step.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("completed"), 1, Lists.immutable.of(step)),
            root,
            1,
            Sets.immutable.of(step),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.of(step), Sets.immutable.empty(), null, null)
            )
        );

        var snapshot = ExecutionTelemetrySnapshot.from(session, 2.0d, false);

        assertEquals("completed", snapshot.status());
        assertTrue(snapshot.completed());
        assertNull(snapshot.next());
        assertEquals(1, snapshot.completedSteps());
        assertEquals(1, snapshot.totalSteps());
        assertEquals(1.0d, snapshot.progress());
        assertNull(snapshot.errorSummary());
        assertNull(snapshot.errorDetail());
    }

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(recipeSystem, new TestRecipe(id));
    }

    private static final TestRecipeSystem recipeSystem = new TestRecipeSystem();

    private record TestRecipe(String id) {
    }

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "telemetry_snapshot");
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
        public TestRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public TestRecipe findRecipe(ResourceLocation registryName) {
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
