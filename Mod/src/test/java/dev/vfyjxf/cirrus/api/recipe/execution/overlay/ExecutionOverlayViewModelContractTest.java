package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
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
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayViewModelContractTest {

    @BeforeAll
    static void bootstrapRenderers() {
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    //region projection

    @Test
    void deriveExpandsActiveBranchAndProjectsLaneStatuses() {
        var activeRecipe = recipeEntry("craft_planks");
        var idleRecipe = recipeEntry("smelt_sand");
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(1L)),
            1L
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            activeRecipe,
            1,
            Lists.immutable.of(new InputBinding(input(1L), 1L)),
            Lists.immutable.of(new OutputBinding(resource(4L), 4L))
        );
        var idleWithdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(2L)),
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
        assertEquals(
            Component.translatable("cirrus.ui.execution.overlay.side.client").getString(),
            viewModel.sideHint().getString()
        );
        assertEquals(
            Lists.mutable.of("target", "recipe:active", "acquire", "execute", "recipe:idle", "idle-acquire"),
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
        assertTrue(idleRecipeNode.expanded());
        var idleAcquireNode = viewModel.visibleNodes().detect(node -> node.nodeId().equals("idle-acquire"));
        assertFalse(idleAcquireNode.activeBranch());
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
        assertEquals(
            Component.translatable("cirrus.ui.execution.overlay.side.player").getString(),
            viewModel.sideHint().getString()
        );
        assertEquals("Press A", viewModel.footerSummary().getString());
        assertEquals("Open the machine and confirm the craft", viewModel.footerDetail().getString());
        assertEquals("Keep the machine screen open", viewModel.footerSteps().get(0).getString());
        assertEquals("Press A", viewModel.visibleNodes().get(1).subtitle().getString());
        assertEquals("paused", viewModel.visibleNodes().get(1).laneSegments().get(0).statusText());
    }

    @Test
    void deriveDoesNotUseBuiltinWorkbenchItemFallbacks() {
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(1L)),
            1L
        );
        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending("acquire", Component.literal("Acquire"), Lists.immutable.of(withdraw), null, Lists.immutable.empty())
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(withdraw)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.empty(), null, null)
            )
        );

        var viewModel = ExecutionOverlayViewModel.derive(session);
        var acquire = viewModel.visibleNodes().detect(node -> node.nodeId().equals("acquire"));
        assertTrue(acquire.content() != null && acquire.content().notEmpty());
        assertEquals(0, RecipeContentRenderer.getRenderer((RecipeContent) acquire.content()).visualCount(acquire.content()));
    }

    @Test
    void recipeNodesUseRecipeOutputInsteadOfCurrentInputStepContent() {
        var recipe = recipeEntry("craft_smoker");
        var prepare = new InteractionStep.PrepareInputsStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            recipe,
            Lists.immutable.of(new InputBinding(input(8L), 8L))
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipe,
            1,
            Lists.immutable.of(new InputBinding(input(8L), 8L)),
            Lists.immutable.of(new OutputBinding(resource(1L), 1L))
        );
        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Craft smoker"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending("recipe:smoker", Component.literal("Smoker"), Lists.immutable.of(prepare, execute), null, Lists.immutable.empty())
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipe, 1, Lists.immutable.of(prepare, execute)),
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            prepare,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.empty(), prepare, null)
            )
        );

        var viewModel = ExecutionOverlayViewModel.derive(session);
        var recipeNode = viewModel.visibleNodes().detect(node -> node.nodeId().equals("recipe:smoker"));

        assertTrue(recipeNode.content() != null && recipeNode.content().notEmpty());
        assertEquals(1L, recipeNode.content().amount());
    }

    @Test
    void timelineNodesCarryRecipePreviewPayloadsFromExecutionSteps() {
        var recipe = recipeEntry("craft_preview");
        var prepare = new InteractionStep.PrepareInputsStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            recipe,
            Lists.immutable.of(new InputBinding(input(2L), 2L))
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipe,
            3,
            Lists.immutable.of(new InputBinding(input(2L), 6L)),
            Lists.immutable.of(new OutputBinding(resource(1L), 3L))
        );
        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Craft preview"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending("recipe:preview", Component.literal("Preview"), Lists.immutable.of(prepare, execute), null, Lists.immutable.empty())
            )
        );
        var session = new ExecutionSession(
            new InteractionExecutionPlan(recipe, 3, Lists.immutable.of(prepare, execute)),
            root,
            1,
            Sets.immutable.of(prepare),
            Sets.immutable.empty(),
            execute,
            null,
            null,
            ExecutionFlowViewModel.derive(
                root,
                new ExecutionFlowViewModel.RuntimeState(Sets.immutable.of(prepare), Sets.immutable.empty(), execute, null)
            )
        );

        var viewModel = ExecutionOverlayViewModel.derive(session);
        var recipeNode = viewModel.visibleNodes().detect(node -> node.nodeId().equals("recipe:preview"));
        var executeSegment = recipeNode.laneSegments().detect(segment -> "execute".equals(segment.stepKey()));

        assertNotNull(recipeNode.recipePreview());
        assertEquals(recipe, recipeNode.recipePreview().recipe());
        assertEquals(3, recipeNode.recipePreview().times());
        assertEquals(6L, recipeNode.recipePreview().inputs().getFirst().amount());
        assertEquals(3L, recipeNode.recipePreview().outputs().getFirst().amount());
        assertNotNull(executeSegment.recipePreview());
        assertTrue(executeSegment.description().getContents() instanceof TranslatableContents);
        assertEquals(
            "cirrus.ui.execution.step.execute.desc",
            ((TranslatableContents) executeSegment.description().getContents()).getKey()
        );
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
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(
                this,
                recipe,
                java.util.List.of(InputContent.fromValue(longValueType, 1L)),
                java.util.List.of(OutputContent.fromValue(longValueType, 1L))
            ));
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
