package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ExecutionTargetRecipeStateTest {

    @Test
    void buildPlanReturnsPausedFailureSessionWhenRoutingCannotProducePlan(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe impossible = new TestRecipe(
            "impossible_target",
            List.of(new UnmaterializableInput("mystery_dust", 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.STICK), 1))
        );
        system.add(impossible);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<TestRecipe> recipe = RecipeEntry.of(system, impossible);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(recipe, 2);
            ExecutionInteraction interaction = craftingTableInteraction();

            InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);
            ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, interaction);
            assertFalse(plan.isEmpty());
            assertFalse(session.paused());
            assertEquals(0, session.nextStepIndex());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildSessionSurfacesSpecificRoutingIssuesAndFollowupWarnings(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makeSticks = new TestRecipe(
            "make_sticks",
            List.of(
                new UnmaterializableInput("mystery_dust", 1),
                InputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 2)
            ),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.STICK), 4))
        );
        system.add(makeSticks);
        RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSticks);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 1);
            ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, craftingTableInteraction());
            assertFalse(session.paused());
            assertFalse(session.plan().isEmpty());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildSessionShowsOnlyRootMissingInputInsteadOfIntermediateProduct(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makePlanks = new TestRecipe(
            "make_planks",
            List.of(new UnmaterializableInput("mystery_dust", 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        TestRecipe makeSticks = new TestRecipe(
            "make_sticks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 2)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.STICK), 4))
        );
        system.add(makePlanks);
        system.add(makeSticks);
        RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSticks);
        RoutingService routingService = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.of(
            RecipeEntry.of(system, makePlanks),
            target
        )));
        RoutingOutcome outcome = routingService.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        ExecutionGuidance guidance = routingFailureGuidance(new ExecutionTargetRecipeState.Selection(target, 1, 1L), outcome);
        String exposed = guidance.summary().getString()
            + " "
            + (guidance.detail() == null ? "" : guidance.detail().getString())
            + " "
            + guidance.steps().collect(component -> component.getString()).makeString(" ");
        assertTrue(exposed.contains("mystery_dust"), exposed);
        assertFalse(exposed.contains("oak_planks"), exposed);
    }

    @Test
    void buildPlanKeepsExpandedRouteWhenLeafInputsNeedExternalSupply(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makePlanks = new TestRecipe(
            "make_planks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        TestRecipe makeSticks = new TestRecipe(
            "make_sticks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 2)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.STICK), 4))
        );
        system.add(makePlanks);
        system.add(makeSticks);
        RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSticks);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 1);
            InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, craftingTableInteraction());
            ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, craftingTableInteraction());

            assertTrue(!plan.isEmpty(), "execution plan should stay available with missing leaf stock");
            assertTrue(plan.steps().size() > 0, "translated execution plan should contain steps");
            assertTrue(!session.paused(), "session should no longer pause on routing when only stock is currently missing");
            assertEquals(0, session.nextStepIndex());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildPlanUsesInteractionPlanningSnapshotInsteadOfGlobalMenuState(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_snapshot"));
        TestRecipe makePlanks = new TestRecipe(
            "make_planks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        system.add(makePlanks);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            Stock stock = Stock.empty();
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
            InteractionApi interactionApi = new SnapshotInteractionApi(Namespace.of("test", "snapshot"), stock, 41L);
            ExecutionInteraction interaction = new ExecutionInteraction(Namespace.of("test", "snapshot"), interactionApi);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(RecipeEntry.of(system, makePlanks), 1);

            InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

            assertFalse(plan.isEmpty());
            assertTrue(plan.steps().notEmpty());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildSessionUsesObservedStockConsumptionByDefault(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_snapshot_default_policy"));
        TestRecipe makePlanks = new TestRecipe(
            "make_planks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        system.add(makePlanks);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            Stock stock = Stock.empty();
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
            ExecutionInteraction interaction = new ExecutionInteraction(
                Namespace.of("test", "snapshot"),
                new SnapshotInteractionApi(Namespace.of("test", "snapshot"), stock, 141L)
            );

            ExecutionSession session = ExecutionTargetRecipeState.buildSession(
                ExecutionTargetRecipeState.set(RecipeEntry.of(system, makePlanks), 1),
                interaction
            );

            assertEquals(ExecutionObservedStockPolicy.consumeObservedStock, session.observedStockPolicy());
            assertEquals(1L, session.baselinePlanningSnapshot().stock().countMatching(ContentStack.fromStack(new ItemStack(Items.OAK_LOG))));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void suffixRetranslationKeepsUnfinishedCleanupWhenTargetAlreadyObserved(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_suffix_cleanup"));
        TestRecipe makePlanks = new TestRecipe(
            "make_planks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        system.add(makePlanks);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makePlanks);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 1);
            ContentResource output = ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS, 4));
            PlanAction.OutputBinding outputBinding = new PlanAction.OutputBinding(output, 4);
            InteractionExecutionPlan plan = new InteractionExecutionPlan(
                target,
                1,
                Lists.immutable.of(
                    new InteractionStep.CollectOutputsStep(
                        dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement.clientSide,
                        dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability.inventoryTransfer,
                        target,
                        Lists.immutable.of(outputBinding)
                    ),
                    new InteractionStep.StoreOutputsStep(
                        dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement.clientSide,
                        dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability.outputStorage,
                        output,
                        4
                    )
                )
            );
            ExecutionFlowNode root = ExecutionFlowNode.pending(
                "root",
                Component.literal("root"),
                plan.steps(),
                null,
                Lists.immutable.empty()
            );
            ExecutionSession current = new ExecutionRuntime(null, null).advance(ExecutionSession.start(
                plan,
                root,
                ExecutionPlanningSnapshot.empty(),
                ExecutionObservedStockPolicy.consumeObservedStock
            ));
            assertTrue(current.currentStep() instanceof InteractionStep.StoreOutputsStep);

            Stock observed = Stock.empty();
            observed.add(ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS, 4)));
            ExecutionInteraction observedInteraction = new ExecutionInteraction(
                Namespace.ofMc("crafting_table"),
                new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), observed, 9111L)
            );

            ExecutionSession rebuilt = ExecutionTargetRecipeState.rebuildSession(selection, observedInteraction, current);

            assertFalse(rebuilt.completed());
            assertEquals(1, rebuilt.nextStepIndex());
            assertTrue(rebuilt.plan().steps().get(0) instanceof InteractionStep.CollectOutputsStep);
            assertTrue(rebuilt.currentStep() instanceof InteractionStep.StoreOutputsStep);
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildSessionDoesNotExposeIntermediateProductAsMissingRequirement(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makeCobble = new TestRecipe(
            "make_cobble",
            List.of(new UnmaterializableInput("mystery_stone", 8)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 8))
        );
        TestRecipe makeFurnace = new TestRecipe(
            "make_furnace",
            List.of(InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 8)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.FURNACE), 1))
        );
        TestRecipe makeSmoker = new TestRecipe(
            "make_smoker",
            List.of(
                InputContent.fromItemStack(new ItemStack(Items.FURNACE), 1),
                InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 4)
            ),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.SMOKER), 1))
        );
        system.add(makeCobble);
        system.add(makeFurnace);
        system.add(makeSmoker);
        RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSmoker);
        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RoutingService routingService = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.of(
                RecipeEntry.of(system, makeCobble),
                RecipeEntry.of(system, makeFurnace),
                target
            )));
            RoutingOutcome outcome = routingService.route(
                RoutingRequest.builder()
                    .targetRecipe(target, 1)
                    .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                    .budget(RoutingBudget.assuredDefault())
                    .build()
            );

            assertFalse(outcome.issues().anySatisfy(issue ->
                issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                    && "test:make_furnace".equals(shortage.visibleRecipe())
            ));

            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 1);
            ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, craftingTableInteraction());
            assertFalse(session.paused());
            assertFalse(session.plan().isEmpty());
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildPlanForSmokerOnlyWithdrawsBaseMaterials(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makeCobble = new TestRecipe(
            "make_cobble",
            List.of(new UnmaterializableInput("mystery_stone", 8)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 8))
        );
        TestRecipe makeFurnace = new TestRecipe(
            "make_furnace",
            List.of(InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 8)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.FURNACE), 1))
        );
        TestRecipe makeSmoker = new TestRecipe(
            "make_smoker",
            List.of(
                InputContent.fromItemStack(new ItemStack(Items.FURNACE), 1),
                InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 4)
            ),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.SMOKER), 1))
        );
        system.add(makeCobble);
        system.add(makeFurnace);
        system.add(makeSmoker);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 128)));

        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSmoker);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 16);
            ExecutionInteraction interaction = new ExecutionInteraction(
                Namespace.ofMc("crafting_table"),
                new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), stock, 91L)
            );

            InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

            assertFalse(plan.isEmpty());
            assertEquals(2, plan.steps().selectInstancesOf(InteractionStep.WithdrawResourceStep.class).size());
            assertNotNull(findWithdrawForItem(plan, Items.OAK_LOG));
            assertNotNull(findWithdrawForItem(plan, Items.COBBLESTONE));
            assertNull(findWithdrawForItem(plan, Items.FURNACE));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void buildPlanForSmokerKeepsExpectedExecuteBatchAmounts(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem(Namespace.of("test", "target_recipe_state"));
        TestRecipe makeFurnace = new TestRecipe(
            "make_furnace",
            List.of(InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 8)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.FURNACE), 1))
        );
        TestRecipe makeSmoker = new TestRecipe(
            "make_smoker",
            List.of(
                InputContent.fromItemStack(new ItemStack(Items.FURNACE), 1),
                InputContent.fromItemTag(ItemTags.LOGS, 4)
            ),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.SMOKER), 1))
        );
        system.add(makeFurnace);
        system.add(makeSmoker);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 128)));

        RecipeApiImpl previous = attachRecipeApi(system);
        try {
            RecipeEntry<TestRecipe> target = RecipeEntry.of(system, makeSmoker);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(target, 16);
            ExecutionInteraction interaction = new ExecutionInteraction(
                Namespace.ofMc("crafting_table"),
                new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), stock, 93L)
            );

            InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

            var executes = plan.steps().selectInstancesOf(InteractionStep.ExecuteRecipeStep.class);
            assertEquals(3, executes.size());

            var furnaceExecutes = executes.select(step -> "make_furnace".equals(step.recipe().id().getPath()));
            var smokerExecutes = executes.select(step -> "make_smoker".equals(step.recipe().id().getPath()));

            assertEquals(2, furnaceExecutes.size());
            assertTrue(furnaceExecutes.allSatisfy(step ->
                step.times() == 8
                    && step.inputs().size() == 1
                    && step.inputs().getFirst().input().satisfy(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)))
                    && step.inputs().getFirst().amount() == 64L
            ));

            assertEquals(1, smokerExecutes.size());
            InteractionStep.ExecuteRecipeStep smokerExecute = smokerExecutes.getFirst();
            assertEquals(16, smokerExecute.times());
            assertTrue(smokerExecute.inputs().anySatisfy(input ->
                input.input().satisfy(ContentStack.fromStack(new ItemStack(Items.FURNACE)))
                    && input.amount() == 16L
            ));
            assertTrue(smokerExecute.inputs().anySatisfy(input ->
                input.input() instanceof TagStackContent<?> tag
                    && tag.tagKey() == ItemTags.LOGS
                    && input.amount() == 64L
            ));
        } finally {
            restoreRecipeApi(previous);
        }
    }

    @Test
    void itemIngredientFactoryPreservesTagIngredient(MinecraftServer server) {
        Ingredient tagIngredient = Ingredient.of(ItemTags.PLANKS);
        InputContent result = InputContent.fromItemIngredient(tagIngredient);

        assertTrue(result instanceof TagStackContent<?>, "tag ingredient should stay as tag content");
    }

    private static RecipeApiImpl attachRecipeApi(RecipeSystem<?>... systems) {
        RecipeApi previousApi = RecipeApiImpl.instance();
        RecipeApiImpl previous = previousApi instanceof RecipeApiImpl impl ? impl : null;
        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        for (RecipeSystem<?> system : systems) {
            builder.registerSystem(system);
        }
        RecipeApiImpl.attach(new RecipeApiImpl(builder));
        return previous;
    }

    private static void restoreRecipeApi(@Nullable RecipeApiImpl previous) {
        if (previous != null) {
            RecipeApiImpl.attach(previous);
        }
    }

    private static ExecutionInteraction craftingTableInteraction() {
        Namespace interactionId = Namespace.ofMc("crafting_table");
        return new ExecutionInteraction(interactionId, CraftingTableLikeInteraction.automatic(interactionId));
    }

    private static ExecutionGuidance routingFailureGuidance(
        ExecutionTargetRecipeState.Selection selection,
        RoutingOutcome outcome
    ) {
        try {
            Method method = ExecutionTargetRecipeState.class.getDeclaredMethod(
                "routingFailureGuidance",
                ExecutionTargetRecipeState.Selection.class,
                RoutingOutcome.class
            );
            method.setAccessible(true);
            return (ExecutionGuidance) method.invoke(null, selection, outcome);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke routingFailureGuidance", exception);
        }
    }

    private record TestRecipe(String id, @Nullable List<InputContent> inputs, @Nullable List<OutputContent> outputs) {
        private TestRecipe(String id) {
            this(id, null, null);
        }
    }

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private static final ValueType<Long> amountType = new TestAmountType();
        private final Namespace systemId;
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        private TestRecipeSystem(Namespace systemId) {
            this.systemId = systemId;
        }

        private void add(TestRecipe recipe) {
            byId.put(recipe.id(), recipe);
        }

        @Override
        public Namespace systemId() {
            return systemId;
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
            if (recipe.inputs() != null && recipe.outputs() != null) {
                return ResolveResult.resolved(RecipeProjection.create(
                    RecipeEntry.of(this, recipe),
                    recipe.inputs(),
                    recipe.outputs()
                ));
            }
            return ResolveResult.resolved(RecipeProjection.create(
                RecipeEntry.of(this, recipe),
                List.of(InputContent.fromValue(amountType, 1L)),
                List.of(OutputContent.fromValue(amountType, 1L))
            ));
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    private record SnapshotInteractionApi(
        Namespace interactionId,
        Stock stock,
        long fingerprint
    ) implements InteractionApi {
        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(interactionId, true, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("snapshot")));
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            return new InteractionPerformResult.Performed();
        }

        @Override
        public int maxExecuteBatchSize(InteractionContext context) {
            return CraftingTableLikeInteraction.automatic(interactionId).maxExecuteBatchSize(context);
        }

        @Override
        public ExecutionPlanningSnapshot planningSnapshot() {
            return new ExecutionPlanningSnapshot(stock, fingerprint);
        }
    }

    private static @Nullable InteractionStep.WithdrawResourceStep findWithdrawForItem(
        InteractionExecutionPlan plan,
        Item item
    ) {
        return plan.steps()
            .selectInstancesOf(InteractionStep.WithdrawResourceStep.class)
            .detect(step -> switch (step.target()) {
                case InteractionStep.WithdrawTarget.Resource resource ->
                    resource.resource().matches(ContentStack.fromStack(new ItemStack(item)), MatchStrategy.identityAndComponent);
                case InteractionStep.WithdrawTarget.Input input ->
                    input.input().satisfy(ContentStack.fromStack(new ItemStack(item)));
            });
    }

    private record UnmaterializableInput(String label, long amount) implements ComplexContent {
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public float chance() {
            return RecipeContent.MAX_CHANCE;
        }

        @Override
        public RecipeContentType<? extends RecipeContent> recipeType() {
            return RecipeContentType.COMPLEX;
        }

        @Override
        public UnmaterializableInput copy() {
            return this;
        }

        @Override
        public UnmaterializableInput copyWithAmount(long amount) {
            return new UnmaterializableInput(label, amount);
        }

        @Override
        public UnmaterializableInput copyWithChance(float chance) {
            return this;
        }

        @Override
        public UnmaterializableInput copyWithAmountAndChance(long amount, float chance) {
            return new UnmaterializableInput(label, amount);
        }

        @Override
        public boolean mergeable(RecipeContent other) {
            return other instanceof UnmaterializableInput input && label.equals(input.label);
        }

        @Override
        public boolean satisfy(ContentVariant actual) {
            return false;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class TestAmountType implements ValueType<Long> {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", "target_recipe_state_amount");
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long first, Long second) {
            return first.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    }
}
