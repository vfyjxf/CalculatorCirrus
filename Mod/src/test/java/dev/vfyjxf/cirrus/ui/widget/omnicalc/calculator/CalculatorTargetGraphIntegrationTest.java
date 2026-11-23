package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class CalculatorTargetGraphIntegrationTest {

    @AfterEach
    void clearCalculatorTarget() {
        CalculatorTargetRecipeState.clear();
        ExecutionTargetRecipeState.clear();
    }

    @Test
    void connectedRecipeTargetBuildsProcessGraphOnCalculatorOpen(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(input(Items.OAK_PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePlanks, makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<FakeRecipe> target = RecipeEntry.of(system, makeSticks);
            CalculatorTargetRecipeState.set(target, 1);

            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            assertTrue(view.debugShowingCalculatingState());
            view.debugCompletePendingCalculation();

            assertFalse(view.debugShowingCalculatingState());
            assertTrue(view.isCalculated());
            assertEquals(3, view.debugGraphNodeCount());
            assertEquals(2, view.debugGraphEdgeCount());
            assertTrue(view.debugCalculationDiagnostics().contains("target=test:make_sticks"));
            assertTrue(view.debugCalculationDiagnostics().contains("graph=3/2"));
            assertTrue(view.debugCalculationDiagnostics().contains("summary"));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void calculatorCanSetExecutorTarget(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(input(Items.OAK_PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePlanks, makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<FakeRecipe> target = RecipeEntry.of(system, makeSticks);
            CalculatorTargetRecipeState.set(target, 4);

            OmniCalculatorView view = new OmniCalculatorView();
            view.debugCompletePendingCalculation();

            assertTrue(view.debugCurrentPathCanSetExecutionTarget());
            view.setCurrentPathAsExecutionTarget();

            assertNotNull(ExecutionTargetRecipeState.current());
            assertEquals(target, ExecutionTargetRecipeState.current().recipe());
            assertEquals(4, ExecutionTargetRecipeState.current().times());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void mountedCalculatorRefreshesWhenDatabaseSetsTarget(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(input(Items.OAK_PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePlanks, makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            OmniCalculatorView view = new OmniCalculatorView();
            assertFalse(view.debugCalculationPending());

            CalculatorTargetRecipeState.set(RecipeEntry.of(system, makeSticks), 1);
            view.tick();

            assertTrue(view.debugCalculationPending());
            view.debugCompletePendingCalculation();
            assertEquals(RecipeEntry.of(system, makeSticks), view.debugCurrentCalculatorTargetRecipe());
            assertEquals(3, view.debugGraphNodeCount());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void projectionDetailDefersCalculatorTargetRefreshUntilReturn(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(input(Items.OAK_PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePlanks, makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<FakeRecipe> target = RecipeEntry.of(system, makeSticks);
            CalculatorTargetRecipeState.set(target, 1);
            OmniCalculatorView view = new OmniCalculatorView();
            view.debugCompletePendingCalculation();

            view.debugOpenProjectionDetail(target);
            assertTrue(view.debugProjectionDetailOpen());

            view.debugSetCalculatorTargetFromProjectionDetail();
            view.tick();

            assertTrue(view.debugProjectionDetailOpen());
            assertFalse(view.debugCalculationPending());

            view.debugCloseProjectionDetail();

            assertTrue(view.debugCalculationPending());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    private static InputContent input(Item item, long amount) {
        return InputContent.fromItemStack(new ItemStack(item), amount);
    }

    private static OutputContent output(Item item, long amount) {
        return OutputContent.fromItemStack(new ItemStack(item), amount);
    }

    private static RecipeApiImpl attachRecipeApi(RecipeSystem<?> system) {
        RecipeApi previousApi = RecipeApiImpl.instance();
        RecipeApiImpl previous = previousApi instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerSystem(system);
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    private static void restoreRecipeApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private FakeRecipeSystem(RichIterable<FakeRecipe> recipes) {
            this.recipes = recipes;
            recipes.forEach(recipe -> byId.put(recipe.id(), recipe));
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "calculator_target");
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe left, FakeRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }
}
