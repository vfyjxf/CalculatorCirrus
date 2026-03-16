package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayViewModelContractTest {

    //region projection

    @Test
    void deriveExpandsActiveBranchAndProjectsLaneStatuses() {
        var activeRecipe = recipeEntry("craft_planks");
        var idleRecipe = recipeEntry("smelt_sand");
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            resource(1L),
            1L
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            activeRecipe,
            1,
            Lists.immutable.of(new PlanAction.InputBinding(input(1L), 1L)),
            Lists.immutable.of(new PlanAction.OutputBinding(resource(4L), 4L))
        );
        var idleWithdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            resource(2L),
            2L
        );

        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Craft target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "recipe:active",
                    Component.literal("Craft planks"),
                    Lists.immutable.empty(),
                    null,
                    Lists.immutable.of(
                        ExecutionFlowNode.pending("acquire", Component.literal("Acquire inputs"), Lists.immutable.of(withdraw), null, Lists.immutable.empty()),
                        ExecutionFlowNode.pending("execute", Component.literal("Execute recipe"), Lists.immutable.of(execute), null, Lists.immutable.empty())
                    )
                ),
                ExecutionFlowNode.pending(
                    "recipe:idle",
                    Component.literal("Smelt sand"),
                    Lists.immutable.empty(),
                    null,
                    Lists.immutable.of(
                        ExecutionFlowNode.pending("idle-acquire", Component.literal("Acquire sand"), Lists.immutable.of(idleWithdraw), null, Lists.immutable.empty())
                    )
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(activeRecipe, 1, Lists.immutable.of(withdraw, execute, idleWithdraw)),
            root,
            1,
            Sets.immutable.of(withdraw),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.of(withdraw), Sets.immutable.empty(), null, null)
            )
        );

        var viewModel = ExecutionOverlayViewModel.derive(session);

        assertEquals("Craft target", viewModel.title().getString());
        assertEquals("running", viewModel.statusText());
        assertEquals(1, viewModel.completedStepCount());
        assertEquals(3, viewModel.totalStepCount());
        assertEquals("Client", viewModel.sideHint().getString());
        assertEquals(
            Lists.mutable.of("target", "recipe:active", "acquire", "execute", "recipe:idle"),
            viewModel.visibleNodes().collect(ExecutionOverlayNode::nodeId)
        );

        var executeNode = viewModel.visibleNodes().detect(node -> node.nodeId().equals("execute"));
        assertTrue(executeNode.activeBranch());
        assertTrue(executeNode.expanded());
        assertEquals("running", executeNode.statusText());
        assertEquals(1, executeNode.laneSegments().size());
        assertEquals("running", executeNode.laneSegments().get(0).statusText());
        assertTrue(executeNode.laneSegments().get(0).current());

        var idleRecipeNode = viewModel.visibleNodes().detect(node -> node.nodeId().equals("recipe:idle"));
        assertFalse(idleRecipeNode.activeBranch());
        assertFalse(idleRecipeNode.expanded());
    }

    @Test
    void deriveUsesPauseGuidanceForFooterAndPausedNodeSubtitle() {
        var manual = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            new ExecutionGuidance(
                Component.literal("Press A"),
                Component.literal("Open the machine and confirm the craft"),
                Lists.immutable.of(Component.literal("Keep the machine screen open")),
                Sets.immutable.empty()
            )
        );
        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Craft target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "manual",
                    Component.literal("Manual checkpoint"),
                    Lists.immutable.of(manual),
                    manual.guidance(),
                    Lists.immutable.empty()
                )
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(manual)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.of(manual),
            null,
            new PauseReason(PauseReasons.awaitingPlayerAction, manual.guidance()),
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.of(manual), null, null)
            )
        );

        var viewModel = ExecutionOverlayViewModel.derive(session);

        assertEquals("paused", viewModel.statusText());
        assertEquals("Player action", viewModel.sideHint().getString());
        assertEquals("Press A", viewModel.footerSummary().getString());
        assertEquals("Open the machine and confirm the craft", viewModel.footerDetail().getString());
        assertEquals("Keep the machine screen open", viewModel.footerSteps().get(0).getString());
        assertEquals("Press A", viewModel.visibleNodes().get(1).subtitle().getString());
        assertEquals("paused", viewModel.visibleNodes().get(1).laneSegments().get(0).statusText());
    }

    //endregion

    //region fixtures

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(recipeSystem, new TestRecipe(id));
    }

    private static ContentResource resource(long amount) {
        return ContentValue.fromValue(longValueType, amount);
    }

    private static InputContent input(long amount) {
        return InputContent.fromValue(longValueType, amount);
    }

    private static final ValueType<Long> longValueType = new ValueType<>() {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", "amount");
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long fist, Long second) {
            return fist.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    };

    private static final TestRecipeSystem recipeSystem = new TestRecipeSystem();

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "execution_overlay");
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
        public RecipeProjection resolveRecipe(TestRecipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
