package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionFlowViewModelContractTest {

    //region tree shape

    @Test
    void treePreservesRecipeCenteredHierarchy() {
        var recipe = recipeEntry("craft_planks");
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(1L)),
            1L
        );
        var open = new InteractionStep.OpenInteractionStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionAccess,
            recipe,
            1,
            Namespace.of("test", "crafting_table")
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipe,
            1,
            Lists.immutable.of(new InputBinding(input(1L), 1L)),
            Lists.immutable.of(new OutputBinding(resource(4L), 4L))
        );
        var store = new InteractionStep.StoreOutputsStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.outputStorage,
            resource(4L),
            4L
        );

        var acquireNode = ExecutionFlowNode.pending(
            "acquire",
            Component.literal("Acquire inputs"),
            Lists.immutable.of(withdraw),
            null,
            Lists.immutable.empty()
        );
        var interactNode = ExecutionFlowNode.pending(
            "interact",
            Component.literal("Open workstation"),
            Lists.immutable.of(open),
            ExecutionGuidance.simple(Component.literal("Open the crafting table")),
            Lists.immutable.empty()
        );
        var executeNode = ExecutionFlowNode.pending(
            "execute",
            Component.literal("Craft planks"),
            Lists.immutable.of(execute),
            null,
            Lists.immutable.empty()
        );
        var outputNode = ExecutionFlowNode.pending(
            "output",
            Component.literal("Store outputs"),
            Lists.immutable.of(store),
            null,
            Lists.immutable.empty()
        );
        var recipeNode = ExecutionFlowNode.pending(
            "recipe:craft_planks",
            Component.literal("Craft oak planks"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(acquireNode, interactNode, executeNode, outputNode)
        );
        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(recipeNode)
        );

        var viewModel = ExecutionFlowViewModel.derive(
            root,
            new ExecutionFlowViewModel.RuntimeState(Sets.immutable.empty(), Sets.immutable.empty(), null, null)
        );

        assertEquals("target", viewModel.root().nodeId());
        assertEquals(1, viewModel.root().children().size());
        assertEquals("recipe:craft_planks", viewModel.root().children().get(0).nodeId());
        assertEquals(4, viewModel.root().children().get(0).children().size());
    }

    //endregion

    //region status projection

    @Test
    void statusesAreDerivedFromRuntimeStepState() {
        var recipe = recipeEntry("craft_planks");
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(1L)),
            1L
        );
        var manual = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.player,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Press A"))
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipe,
            1,
            Lists.immutable.of(new InputBinding(input(1L), 1L)),
            Lists.immutable.of(new OutputBinding(resource(4L), 4L))
        );

        var root = ExecutionFlowNode.pending(
            "target",
            Component.literal("Target"),
            Lists.immutable.empty(),
            null,
            Lists.immutable.of(
                ExecutionFlowNode.pending(
                    "acquire",
                    Component.literal("Acquire inputs"),
                    Lists.immutable.of(withdraw),
                    null,
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "manual",
                    Component.literal("Manual interaction"),
                    Lists.immutable.of(manual),
                    manual.guidance(),
                    Lists.immutable.empty()
                ),
                ExecutionFlowNode.pending(
                    "execute",
                    Component.literal("Craft"),
                    Lists.immutable.of(execute),
                    null,
                    Lists.immutable.empty()
                )
            )
        );

        var running = ExecutionFlowViewModel.derive(
            root,
            new ExecutionFlowViewModel.RuntimeState(Sets.immutable.of(withdraw), Sets.immutable.empty(), execute, null)
        );
        var paused = ExecutionFlowViewModel.derive(
            root,
            new ExecutionFlowViewModel.RuntimeState(Sets.immutable.of(withdraw), Sets.immutable.of(manual), null, null)
        );
        var completed = ExecutionFlowViewModel.derive(
            root,
            new ExecutionFlowViewModel.RuntimeState(
                Sets.immutable.of(withdraw, manual, execute),
                Sets.immutable.empty(),
                null,
                null
            )
        );

        assertSame(ExecutionFlowStatus.running, running.root().status());
        assertSame(ExecutionFlowStatus.completed, running.root().children().get(0).status());
        assertSame(ExecutionFlowStatus.running, running.root().children().get(2).status());

        assertSame(ExecutionFlowStatus.paused, paused.root().status());
        assertSame(ExecutionFlowStatus.paused, paused.root().children().get(1).status());

        assertSame(ExecutionFlowStatus.completed, completed.root().status());
        assertSame(ExecutionFlowStatus.completed, completed.root().children().get(2).status());
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
            return Namespace.of("test", "execution");
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
            throw new UnsupportedOperationException("Test fixture does not resolve recipes");
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
