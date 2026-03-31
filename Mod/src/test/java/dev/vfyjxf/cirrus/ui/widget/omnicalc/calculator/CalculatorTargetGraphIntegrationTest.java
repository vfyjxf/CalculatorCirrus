package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
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
    void calculatorCanSetExecutorTargetFromPrecomputedRoute(MinecraftServer server) {
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
            assertNotNull(ExecutionTargetRecipeState.current().routingPlan());
            assertEquals(4, ExecutionTargetRecipeState.current().routingPlan().targetTimes());
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

    @Test
    void calculatorPreviewRuleKeepsTagIntermediateRecipeExpandedWhenLeafStockIsMissing(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePlanks, makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            CalculatorTargetRecipeState.set(RecipeEntry.of(system, makeSticks), 1);

            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            assertTrue(view.debugShowingCalculatingState());
            view.debugCompletePendingCalculation();

            assertFalse(view.debugShowingCalculatingState());
            assertTrue(view.isCalculated());
            assertEquals(3, view.debugGraphNodeCount());
            assertEquals(2, view.debugGraphEdgeCount());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void calculatorPreviewStillExpandsTargetWhenAnotherRecipeCannotEnterRoutingGraph(MinecraftServer server) {
        FakeRecipe makePiston = new FakeRecipe(
            "make_piston",
            java.util.List.of(
                InputContent.fromItemTag(ItemTags.PLANKS, 3),
                input(Items.COBBLESTONE, 4),
                input(Items.IRON_INGOT, 1),
                input(Items.REDSTONE, 1)
            ),
            java.util.List.of(output(Items.PISTON, 1))
        );
        FakeRecipe makeStickyPiston = new FakeRecipe(
            "make_sticky_piston",
            java.util.List.of(
                input(Items.PISTON, 1),
                input(Items.SLIME_BALL, 1)
            ),
            java.util.List.of(output(Items.STICKY_PISTON, 1))
        );
        FakeRecipe probabilisticByproduct = new FakeRecipe(
            "probabilistic_byproduct",
            java.util.List.of(input(Items.DIRT, 1)),
            java.util.List.of(OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.5f))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makePiston, makeStickyPiston, probabilisticByproduct));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            CalculatorTargetRecipeState.set(RecipeEntry.of(system, makeStickyPiston), 1);

            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            assertTrue(view.debugShowingCalculatingState());
            view.debugCompletePendingCalculation();

            assertFalse(view.debugShowingCalculatingState());
            assertTrue(view.isCalculated());
            assertEquals(7, view.debugGraphNodeCount());
            assertEquals(6, view.debugGraphEdgeCount());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void calculatorPreviewSkipsActivePathProducerAndUsesAlternativeProducer(MinecraftServer server) {
        FakeRecipe makeLoopA = new FakeRecipe(
            "make_loop_a",
            java.util.List.of(input(Items.STONE, 1)),
            java.util.List.of(output(Items.STONE, 1))
        );
        FakeRecipe makeGoodStone = new FakeRecipe(
            "make_good_stone",
            java.util.List.of(input(Items.DIRT, 1)),
            java.util.List.of(output(Items.STONE, 1))
        );
        FakeRecipe makeTarget = new FakeRecipe(
            "make_target",
            java.util.List.of(input(Items.STONE, 1)),
            java.util.List.of(output(Items.DIAMOND, 1))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makeLoopA, makeGoodStone, makeTarget));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            CalculatorTargetRecipeState.set(RecipeEntry.of(system, makeTarget), 1);

            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            view.debugCompletePendingCalculation();

            assertTrue(view.isCalculated());
            assertTrue(view.debugGraphNodeCount() >= 4);
            assertTrue(view.debugGraphContainsRecipe("make_good_stone"));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void longComplexCalculatorPreviewUsesRoutingPlanByDefault(MinecraftServer server) {
        FakeRecipe makePlanks = new FakeRecipe(
            "make_planks",
            java.util.List.of(input(Items.OAK_LOG, 1)),
            java.util.List.of(output(Items.OAK_PLANKS, 4))
        );
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipe makeTorches = new FakeRecipe(
            "make_torches",
            java.util.List.of(
                input(Items.STICK, 1),
                input(Items.COAL, 1)
            ),
            java.util.List.of(output(Items.TORCH, 4))
        );
        FakeRecipe makeLantern = new FakeRecipe(
            "make_lantern",
            java.util.List.of(
                input(Items.TORCH, 1),
                input(Items.IRON_NUGGET, 8)
            ),
            java.util.List.of(output(Items.LANTERN, 1))
        );
        FakeRecipe makeChain = new FakeRecipe(
            "make_chain",
            java.util.List.of(
                input(Items.IRON_NUGGET, 2),
                input(Items.IRON_INGOT, 1)
            ),
            java.util.List.of(output(Items.CHAIN, 1))
        );
        FakeRecipe makeHangingSign = new FakeRecipe(
            "make_hanging_sign",
            java.util.List.of(
                input(Items.CHAIN, 2),
                InputContent.fromItemTag(ItemTags.PLANKS, 6),
                input(Items.LANTERN, 1)
            ),
            java.util.List.of(output(Items.OAK_HANGING_SIGN, 6))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(
            makePlanks,
            makeSticks,
            makeTorches,
            makeLantern,
            makeChain,
            makeHangingSign
        ));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            CalculatorTargetRecipeState.set(RecipeEntry.of(system, makeHangingSign), 256);

            OmniCalculatorView view = new OmniCalculatorView();
            assertTrue(view.debugCalculationPending());
            view.debugCompletePendingCalculation();

            assertTrue(view.isCalculated());
            assertTrue(view.debugCurrentPathCanSetExecutionTarget(), "complex default preview should keep a routing plan");
            assertTrue(view.debugGraphContainsRecipe("make_planks"));
            assertTrue(view.debugGraphContainsRecipe("make_sticks"));
            assertTrue(view.debugGraphContainsRecipe("make_lantern"));
            assertTrue(view.debugGraphContainsRecipe("make_chain"));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void executionRouteGraphLeavesUnresolvedInputsAsPendingSources(MinecraftServer server) {
        FakeRecipe makeSticks = new FakeRecipe(
            "make_sticks",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(output(Items.STICK, 4))
        );
        FakeRecipeSystem system = new FakeRecipeSystem(Lists.immutable.of(makeSticks));
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<FakeRecipe> target = RecipeEntry.of(system, makeSticks);
            PlanBuilder builder = new PlanBuilder(1L, 1L, target, 1);
            builder.addPendingInput(InputContent.fromItemTag(ItemTags.PLANKS, 2), 2L);
            builder.addExecuteRecipe(
                target,
                1,
                Lists.immutable.of(new PlanAction.InputBinding(InputContent.fromItemTag(ItemTags.PLANKS, 2), 2L)),
                Lists.immutable.of(new PlanAction.OutputBinding(ContentStack.fromStack(new ItemStack(Items.STICK)), 4L))
            );
            RoutingPlan plan = builder.build();
            ProcessGraph graph = CalculatorProcessGraphBuilder.fromRoutingPlan(plan, CalculatorProcessGraphBuilder.RouteMode.execution);

            assertEquals(2, graph.nodeCount(), "execution graph should stop at one pending leaf");
            assertEquals(1, graph.edgeCount(), "pending leaf should connect to the recipe");
            assertTrue(graph.sources().stream().anyMatch(node ->
                "Pending".equals(node.machine())
                    && node.name().contains("minecraft:planks")
            ));
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
