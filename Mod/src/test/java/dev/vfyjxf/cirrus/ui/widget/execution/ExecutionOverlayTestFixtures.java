package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ExecutionOverlayTestFixtures {

    private ExecutionOverlayTestFixtures() {
    }

    static ExecutionSession sessionWithWideBranching() {
        var focusStep = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Focus step"))
        );

        var branches = Lists.mutable.<ExecutionFlowNode>empty();
        int focusIndex = 6;
        int branchCount = 12;
        for (int i = 0; i < branchCount; i++) {
            org.eclipse.collections.api.list.ImmutableList<InteractionStep> steps = i == focusIndex
                ? Lists.immutable.of(focusStep)
                : Lists.immutable.<InteractionStep>empty();
            branches.add(ExecutionFlowNode.pending(
                "branch-" + i,
                Component.literal("Branch " + i),
                steps,
                null,
                Lists.immutable.empty()
            ));
        }

        var root = ExecutionFlowNode.pending(
            "root",
            Component.literal("Root"),
            Lists.immutable.empty(),
            null,
            branches.toImmutable()
        );

        var plan = new InteractionExecutionPlan(recipeEntry("wide"), 1, Lists.immutable.of(focusStep));
        var runtime = ExecutionFlowViewModel.RuntimeState.empty();
        return new ExecutionSession(
            plan,
            root,
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            null,
            null,
            null,
            ExecutionFlowViewModel.derive(root, runtime)
        );
    }

    static int countFlowNodes(ExecutionFlowNode root) {
        int count = 1;
        for (ExecutionFlowNode child : root.children()) {
            count += countFlowNodes(child);
        }
        return count;
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
            return Namespace.of("test", "overlay_widget");
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
}

