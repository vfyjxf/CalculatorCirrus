package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlanDebugText;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssue;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingIssuePayload;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.clause.FluidContainerUnpackClause;
import dev.vfyjxf.cirrus.recipe.clause.GuaranteedOutputClause;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeGraphTest {

    @BeforeEach
    void ensureVanillaRecipeSystems() {
        var current = RecipeApiImpl.instance();
        if (current == null || !current.hasSystem(CraftingRecipeSystem.systemId)) {
            var canonical = RecipeApiImpl.canonicalInstance();
            if (canonical != null) {
                RecipeApiImpl.attach(canonical);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildGraph(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        var recipes = craftingSystem.createLookup()
                                    .withInput(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)))
                                    .get();
        RecipeGraph recipeGraph = RecipeGraph.buildGraph(
            recipes
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        System.out.println(recipeGraph);

    }

    @SuppressWarnings("unchecked")
    @Test
    void buildGraphWithAllCraftingRecipes(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        var entries = Lists.mutable.<RecipeEntry<?>>empty();
        craftingSystem.allRecipes().forEach(recipe -> entries.add(RecipeEntry.of(craftingSystem, recipe)));

        RecipeGraph recipeGraph = RecipeGraph.buildGraph(entries);

        assertFalse(recipeGraph.nodes().isEmpty(), "graph should contain resolved crafting recipes");
        assertTrue(recipeGraph.nodes().size() <= entries.size(), "graph cannot have more nodes than source recipes");
    }

    @Test
    void graphCandidateBucketsDoNotExposeComponentMismatches(MinecraftServer server) {
        ItemStack producerStack = new ItemStack(Items.STONE);
        producerStack.set(DataComponents.CUSTOM_NAME, Component.literal("producer"));
        ItemStack consumerStack = new ItemStack(Items.STONE);
        consumerStack.set(DataComponents.CUSTOM_NAME, Component.literal("consumer"));

        OutputContent producerOutput = OutputContent.fromItemStack(producerStack, 1);
        InputContent consumerInput = InputContent.fromItemStack(consumerStack, 1);
        var produceNamedStone = new RoutingTestFixtures.FakeRecipe(
            "produce_named_stone",
            java.util.List.of(RoutingTestFixtures.in(Items.DIRT, 1)),
            java.util.List.of(producerOutput)
        );
        var consumeNamedStone = new RoutingTestFixtures.FakeRecipe(
            "consume_named_stone",
            java.util.List.of(consumerInput),
            java.util.List.of(RoutingTestFixtures.out(Items.DIAMOND, 1))
        );

        RecipeGraph graph = RoutingTestFixtures.graph(produceNamedStone, consumeNamedStone);

        assertTrue(graph.producersFor(consumerInput).isEmpty());
        assertTrue(graph.producersFor(ContentStack.fromStack(consumerStack)).isEmpty());
        assertFalse(graph.producersFor(ContentStack.fromStack(producerStack)).isEmpty());
        assertTrue(graph.consumersFor(producerOutput).stream().noneMatch(ref ->
            "consume_named_stone".equals(Objects.requireNonNull(ref.node().recipe().id()).getPath())
        ));
    }

    @Test
    void buildGraphSkipsUnresolvedRecipe(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem();
        TestRecipe resolved = new TestRecipe("resolved", true);
        TestRecipe unresolved = new TestRecipe("unresolved", false);

        RecipeEntry<TestRecipe> resolvedEntry = RecipeEntry.of(system, resolved);
        RecipeEntry<TestRecipe> unresolvedEntry = RecipeEntry.of(system, unresolved);
        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.of(resolvedEntry, unresolvedEntry));

        assertEquals(1, graph.nodes().size());
        assertNotNull(graph.findNode(resolvedEntry));
        assertNull(graph.findNode(unresolvedEntry));
    }

    @Test
    void buildGraphSkipsRecipeRejectedDuringProjection(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem();
        TestRecipe resolved = new TestRecipe("resolved", true);
        TestRecipe probabilistic = new TestRecipe("probabilistic", true, true);

        RecipeEntry<TestRecipe> resolvedEntry = RecipeEntry.of(system, resolved);
        RecipeEntry<TestRecipe> probabilisticEntry = RecipeEntry.of(system, probabilistic);
        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.of(resolvedEntry, probabilisticEntry));

        assertEquals(1, graph.nodes().size());
        assertNotNull(graph.findNode(resolvedEntry));
        assertNull(graph.findNode(probabilisticEntry));
    }

    @Test
    void buildGraphDetectsProjectionClausesFromRecipeProjection(MinecraftServer server) {
        ClausedRecipeSystem system = new ClausedRecipeSystem();
        ClausedRecipe recipe = new ClausedRecipe("guaranteed_output", ClausedRecipeKind.guaranteedOutput);
        RecipeEntry<ClausedRecipe> entry = RecipeEntry.of(system, recipe);

        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.of(entry));
        RecipeGraph.Node node = graph.findNode(entry);

        assertNotNull(node);
        assertEquals(1, node.current().outputs().size());
        var outputResource = node.current().outputs().getFirst().asResource();
        assertTrue(outputResource instanceof ContentStack<?, ?>);
        ContentStack<?, ?> outputStack = (ContentStack<?, ?>) outputResource;
        assertEquals(Items.STONE, outputStack.<net.minecraft.world.item.Item, ItemStack>cast().content());
    }

    @Test
    void buildGraphDetectsContentClausesFromRecipeProjection(MinecraftServer server) {
        ClausedRecipeSystem system = new ClausedRecipeSystem();
        ClausedRecipe recipe = new ClausedRecipe("fluid_container", ClausedRecipeKind.fluidContainer);
        RecipeEntry<ClausedRecipe> entry = RecipeEntry.of(system, recipe);

        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.of(entry));
        RecipeGraph.Node node = graph.findNode(entry);

        assertNotNull(node);
        assertEquals(2, node.current().inputs().size());
        assertTrue(node.current().inputs().anySatisfy(InputContent::notConsumed));
        assertTrue(node.current().inputs().anySatisfy(input ->
            input.satisfy(ContentStack.fromStack(new FluidStack(Fluids.WATER, 1000)))
        ));
    }

    @Test
    void routingRejectsUnresolvedTargetWithoutCrashing(MinecraftServer server) {
        TestRecipeSystem system = new TestRecipeSystem();
        TestRecipe resolved = new TestRecipe("resolved", true);
        TestRecipe unresolved = new TestRecipe("unresolved", false);

        RecipeEntry<TestRecipe> resolvedEntry = RecipeEntry.of(system, resolved);
        RecipeEntry<TestRecipe> unresolvedEntry = RecipeEntry.of(system, unresolved);
        RecipeGraph graph = RecipeGraph.buildGraph(Lists.immutable.of(resolvedEntry, unresolvedEntry));
        RoutingService service = new RoutingService(graph);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(unresolvedEntry, 1)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.failed() || outcome.partial());
        assertFalse(outcome.issues().isEmpty());
        assertEquals(RoutingFailureCode.invalidRequest, outcome.issues().getFirst().code());
    }

    @Test
    void routingIssuesContainSpecificInputDetails(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeSticks = new RoutingTestFixtures.FakeRecipe(
            "make_sticks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeSticks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_sticks");

        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.failed() || outcome.partial());

        RoutingIssue noSource = outcome.issues().detect(issue -> issue.code() == RoutingFailureCode.noFeasibleSource);
        assertNotNull(noSource);
        assertEquals("cirrus.routing.no_source", noSource.messageKey());
        assertEquals("minecraft:oak_log", noSource.messageArgs().get("arg0_input"));
        assertEquals("1", noSource.messageArgs().get("arg2_amount"));
        assertTrue(noSource.payload() instanceof RoutingIssuePayload.InputShortage);
        RoutingIssuePayload.InputShortage shortage = (RoutingIssuePayload.InputShortage) noSource.payload();
        assertEquals("test:make_sticks", shortage.visibleRecipe());
        assertEquals("test:make_planks", shortage.actualRecipe());
        assertFalse(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.unsatisfiedInput));
    }

    @Test
    void routingFailureOnlyReportsRootMissingInputInsteadOfIntermediateProduct(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeSticks = new RoutingTestFixtures.FakeRecipe(
            "make_sticks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeSticks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_sticks");

        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.failed() || outcome.partial());
        assertTrue(outcome.issues().anySatisfy(issue ->
            "minecraft:oak_log".equals(issue.messageArgs().get("arg0_input"))
        ));
        assertFalse(outcome.issues().anySatisfy(issue ->
            "minecraft:oak_planks".equals(issue.messageArgs().get("arg0_input"))
        ));
    }

    @Test
    void routingUsesExecutionProfileForDirectSupplyOnMissingLeafInput(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeSticks = new RoutingTestFixtures.FakeRecipe(
            "make_sticks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeSticks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_sticks");

        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success());
        assertNotNull(outcome.plan());
        assertTrue(outcome.issues().anySatisfy(issue ->
            issue.code() == RoutingFailureCode.noFeasibleSource
                && issue.severity() == RoutingIssue.Severity.warning
                && "minecraft:oak_log".equals(issue.messageArgs().get("arg0_input"))
                && "1".equals(issue.messageArgs().get("arg2_amount"))
        ));

        assertEquals(3, outcome.plan().actions().size());
        assertTrue(outcome.plan().actions().get(0) instanceof PlanAction.PendingInput);
        PlanAction.PendingInput pending = (PlanAction.PendingInput) outcome.plan().actions().get(0);
        assertTrue(pending.input().satisfy(ContentStack.fromStack(new ItemStack(Items.OAK_LOG))));
        assertEquals(1L, pending.amount());

        assertTrue(outcome.plan().actions().get(1) instanceof PlanAction.ExecuteRecipe);
        assertTrue(outcome.plan().actions().get(2) instanceof PlanAction.ExecuteRecipe);
        PlanAction.ExecuteRecipe makePlanksAction = (PlanAction.ExecuteRecipe) outcome.plan().actions().get(1);
        PlanAction.ExecuteRecipe makeSticksAction = (PlanAction.ExecuteRecipe) outcome.plan().actions().get(2);
        assertEquals("make_planks", Objects.requireNonNull(makePlanksAction.recipe().id()).getPath());
        assertEquals("make_sticks", Objects.requireNonNull(makeSticksAction.recipe().id()).getPath());
    }

    @Test
    void routingExecutionProfileKeepsTagInputForDirectSupplyInsteadOfCollapsingToConcreteStack(MinecraftServer server) {
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");

        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());
        assertTrue(outcome.plan().actions().getFirst() instanceof PlanAction.PendingInput);
        PlanAction.PendingInput pending = (PlanAction.PendingInput) outcome.plan().actions().getFirst();
        assertTrue(pending.input() instanceof dev.vfyjxf.cirrus.api.recipe.content.TagStackContent<?>);
        assertEquals(2L, pending.amount());
    }

    @Test
    void routingFailureKeepsOriginalTagShortageWhenExpandedTagCandidatesAllFail(MinecraftServer server) {
        var craftOakLog = new RoutingTestFixtures.FakeRecipe(
            "craft_oak_log",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_LOG, 1))
        );
        var craftBirchLog = new RoutingTestFixtures.FakeRecipe(
            "craft_birch_log",
            java.util.List.of(RoutingTestFixtures.in(Items.BIRCH_PLANKS, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.BIRCH_LOG, 1))
        );
        var makeTarget = new RoutingTestFixtures.FakeRecipe(
            "make_target",
            java.util.List.of(InputContent.fromItemTag(ItemTags.LOGS, 64)),
            java.util.List.of(RoutingTestFixtures.out(Items.CHEST, 1))
        );
        RoutingService service = RoutingTestFixtures.service(craftOakLog, craftBirchLog, makeTarget);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_target");

        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 48)));

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .stock(stock)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.failed() || outcome.partial());
        assertTrue(outcome.issues().anySatisfy(issue ->
            issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                && "#minecraft:logs".equals(shortage.input())
                && "test:make_target".equals(shortage.visibleRecipe())
                && shortage.amount() == 16L
        ));
        assertFalse(outcome.issues().anySatisfy(issue ->
            issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                && (
                "minecraft:oak_planks".equals(shortage.input())
                    || "minecraft:birch_planks".equals(shortage.input())
                    || "minecraft:oak_log".equals(shortage.input())
                    || "minecraft:birch_log".equals(shortage.input())
            )
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingExecutionProfileKeepsOnlyRootShortagesForVanillaSmokerWithPartialStock(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);

        var smokerRecipes = craftingSystem.createLookup()
            .withOutput(ContentStack.fromStack(new ItemStack(Items.SMOKER)))
            .get()
            .select(recipe -> !(recipe.value() instanceof CustomRecipe))
            .collect(recipe -> RecipeEntry.of(craftingSystem, recipe));
        assertFalse(smokerRecipes.isEmpty(), "expected at least one smoker recipe");

        RecipeEntry<?> smokerRecipe = smokerRecipes.getFirst();
        RecipeGraph graph = RecipeGraph.buildGraph(
            craftingSystem.allRecipes()
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        RoutingService service = new RoutingService(graph);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 44)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 64)));

        var request = RoutingRequest.builder()
            .targetRecipe(smokerRecipe, 16)
            .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
            .stock(stock)
            .budget(RoutingBudget.assuredDefault())
            .build();
        var outcome = service.route(request);

        assertNotNull(outcome.plan(), () -> describePlan(outcome.plan()));

        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)), MatchStrategy.identityAndComponent)
                && consume.amount() == 44L
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)), MatchStrategy.identityAndComponent)
                && consume.amount() == 64L
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.PendingInput consume
                && consume.input() instanceof TagStackContent<?> tag
                && "minecraft:logs".equals(tag.tagKey().location().toString())
                && consume.amount() == 20L
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.PendingInput consume
                && consume.input() instanceof TagStackContent<?> tag
                && "minecraft:stone_crafting_materials".equals(tag.tagKey().location().toString())
                && consume.amount() == 64L
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "minecraft:acacia_wood".equals(exec.recipe().id().toString())
        ), () -> describePlan(outcome.plan()));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.PendingInput consume
                && consume.input() instanceof StackContent<?, ?> stack
                && (
                stack.content().matches(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)), MatchStrategy.identityAndComponent)
                    || stack.content().matches(ContentStack.fromStack(new ItemStack(Items.ACACIA_LOG)), MatchStrategy.identityAndComponent)
            )
        ), () -> describePlan(outcome.plan()));

        assertEquals(2, outcome.plan().actions().selectInstancesOf(PlanAction.ExecuteRecipe.class).size(), () -> describePlan(outcome.plan()));
        PlanAction.ExecuteRecipe smokerAction = outcome.plan().actions()
            .selectInstancesOf(PlanAction.ExecuteRecipe.class)
            .detect(action -> "minecraft:smoker".equals(Objects.requireNonNull(action.recipe().id()).toString()));
        assertNotNull(smokerAction);
        assertTrue(smokerAction.boundInputs().anySatisfy(input ->
            input.input() instanceof TagStackContent<?> tag
                && "minecraft:logs".equals(tag.tagKey().location().toString())
                && input.amount() == 64L
        ), () -> describePlan(outcome.plan()));
    }
    @SuppressWarnings("unchecked")
    @Test
    void routingExecutionProfileDoesNotExposeIntermediateFurnaceForSmoker(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);

        var smokerRecipes = craftingSystem.createLookup()
            .withOutput(ContentStack.fromStack(new ItemStack(Items.SMOKER)))
            .get()
            .select(recipe -> !(recipe.value() instanceof CustomRecipe))
            .collect(recipe -> RecipeEntry.of(craftingSystem, recipe));
        assertFalse(smokerRecipes.isEmpty(), "expected at least one smoker recipe");

        RecipeEntry<?> smokerRecipe = smokerRecipes.getFirst();
        RecipeGraph graph = RecipeGraph.buildGraph(
            craftingSystem.allRecipes()
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        RoutingService service = new RoutingService(graph);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(smokerRecipe, 1)
                .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertNotNull(outcome.plan(), "execution profile should still produce a plan for smoker");
        assertFalse(outcome.issues().anySatisfy(issue ->
            issue.payload() instanceof RoutingIssuePayload.InputShortage shortage
                && "minecraft:furnace".equals(shortage.visibleRecipe())
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void routingCalculatorPreviewExpandsVanillaStickyPistonThroughPiston(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);

        var stickyPistonRecipes = craftingSystem.createLookup()
            .withOutput(ContentStack.fromStack(new ItemStack(Items.STICKY_PISTON)))
            .get()
            .select(recipe -> !(recipe.value() instanceof CustomRecipe))
            .collect(recipe -> RecipeEntry.of(craftingSystem, recipe));
        assertFalse(stickyPistonRecipes.isEmpty(), "expected at least one sticky piston recipe");

        RecipeEntry<?> stickyPistonRecipe = stickyPistonRecipes.getFirst();
        RecipeGraph graph = RecipeGraph.buildGraph(
            craftingSystem.allRecipes()
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        RoutingService service = new RoutingService(graph);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(stickyPistonRecipe, 1)
                .ruleProfileId(DefaultRuleDefinitions.calculatorPreviewProfileId)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "minecraft:piston".equals(exec.recipe().id().toString())
        ), () -> describePlan(outcome.plan()));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "minecraft:sticky_piston".equals(exec.recipe().id().toString())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void candidateBudgetTruncatesCandidateCollectionWithoutBlockingChosenPath(MinecraftServer server) {
        var makeCobble = new RoutingTestFixtures.FakeRecipe(
            "make_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.STONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.COBBLESTONE, 1))
        );
        var makeFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makeCobble, makeFurnace);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_furnace");
        assertNotNull(target);

        var outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(target, 1)
                .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
                .budget(new RoutingBudget(32, 1, 30, 32, 0.05, dev.vfyjxf.cirrus.api.recipe.routing.service.LoopBudget.standardDefault()))
                .build()
        );

        assertTrue(outcome.success() || outcome.partial(), () -> describeIssues(outcome));
        assertNotNull(outcome.plan(), () -> describeIssues(outcome));
        assertTrue(outcome.metrics().budgetExhausted(), "candidate budget should be recorded as exhausted");
        assertEquals("maxGeneratedCandidates", outcome.metrics().budgetStopReason());
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe exec
                && exec.recipe().id() != null
                && "make_cobble".equals(exec.recipe().id().getPath())
        ), () -> describePlan(outcome.plan()));
    }

    @Test
    void routingExecutionProfileKeepsAggregatedSmokerAmountsForMultiTarget(MinecraftServer server) {
        var makeCobble = new RoutingTestFixtures.FakeRecipe(
            "make_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.STONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.COBBLESTONE, 8))
        );
        var makeFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var makeSmoker = new RoutingTestFixtures.FakeRecipe(
            "make_smoker",
            java.util.List.of(
                RoutingTestFixtures.in(Items.FURNACE, 1),
                RoutingTestFixtures.in(Items.OAK_LOG, 4)
            ),
            java.util.List.of(RoutingTestFixtures.out(Items.SMOKER, 1))
        );
        RoutingService service = RoutingTestFixtures.service(makeCobble, makeFurnace, makeSmoker);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_smoker");

        assertNotNull(target);

        var request = RoutingRequest.builder()
            .targetRecipe(target, 16)
            .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
            .stock(stockForAggregatedSmokerTarget())
            .budget(RoutingBudget.assuredDefault())
            .build();
        var outcome = service.route(request);

        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());
        var verification = RoutingPlanReverseVerifier.verify(request, outcome, service.graph());
        assertTrue(verification.valid(), () -> verification.errors().makeString("; "));

        PlanAction.ExecuteRecipe cobbleAction = outcome.plan().actions()
													   .selectInstancesOf(PlanAction.ExecuteRecipe.class)
													   .detect(action -> "make_cobble".equals(Objects.requireNonNull(action.recipe().id()).getPath()));
        PlanAction.ExecuteRecipe furnaceAction = outcome.plan().actions()
														.selectInstancesOf(PlanAction.ExecuteRecipe.class)
														.detect(action -> "make_furnace".equals(Objects.requireNonNull(action.recipe().id()).getPath()));
        PlanAction.ExecuteRecipe smokerAction = outcome.plan().actions()
													   .selectInstancesOf(PlanAction.ExecuteRecipe.class)
													   .detect(action -> "make_smoker".equals(Objects.requireNonNull(action.recipe().id()).getPath()));

        assertNotNull(cobbleAction);
        assertNotNull(furnaceAction);
        assertNotNull(smokerAction);

        assertEquals(16, cobbleAction.times());
        assertEquals(128L, cobbleAction.boundInputs().getFirst().amount());
        assertEquals(128L, cobbleAction.expectedOutputs().getFirst().amount());

        assertEquals(16, furnaceAction.times());
        assertEquals(128L, furnaceAction.boundInputs().getFirst().amount());
        assertEquals(16L, furnaceAction.expectedOutputs().getFirst().amount());

        assertEquals(16, smokerAction.times());
        assertTrue(smokerAction.boundInputs().anySatisfy(input ->
            input.input().satisfy(ContentStack.fromStack(new ItemStack(Items.FURNACE)))
                && input.amount() == 16L
        ));
        assertTrue(smokerAction.boundInputs().anySatisfy(input ->
            input.input().satisfy(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)))
                && input.amount() == 64L
        ));
        assertEquals(16L, smokerAction.expectedOutputs().getFirst().amount());
    }

    private static Stock stockForAggregatedSmokerTarget() {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.STONE, 128)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 64)));
        return stock;
    }

    private static String describeIssues(dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome outcome) {
        return outcome.issues().collect(issue -> switch (issue.payload()) {
            case RoutingIssuePayload.InputShortage shortage ->
                shortage.input() + "@" + shortage.amount()
                    + " visible=" + shortage.visibleRecipe()
                    + (shortage.actualRecipe() == null ? "" : " actual=" + shortage.actualRecipe());
            default -> issue.code() + ":" + issue.messageArgs();
        }).makeString("; ");
    }

    private static String describePlan(dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan plan) {
        return RoutingPlanDebugText.describe(plan);
    }

    private enum ClausedRecipeKind {
        guaranteedOutput,
        fluidContainer
    }

    private record ClausedRecipe(String id, ClausedRecipeKind kind) {
    }

    private static final class ClausedRecipeSystem implements RecipeSystem<ClausedRecipe> {

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "claused_graph");
        }

        @Override
        public Class<?> basicType() {
            return ClausedRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(ClausedRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable ClausedRecipe findRecipe(String uniqueId) {
            return null;
        }

        @Override
        public @Nullable ClausedRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(ClausedRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(ClausedRecipe r1, ClausedRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, ClausedRecipe recipe) {
            RecipeEntry<ClausedRecipe> entry = RecipeEntry.of(this, recipe);
            RecipeProjection projection = switch (recipe.kind()) {
                case guaranteedOutput -> RecipeProjection.create(
                    entry,
                    List.of(InputContent.fromItemStack(new ItemStack(Items.STICK))),
                    List.of(
                        OutputContent.fromItemStack(new ItemStack(Items.DIAMOND), 1, 0.25f),
                        OutputContent.fromItemStack(new ItemStack(Items.STONE))
                    )
                );
                case fluidContainer -> RecipeProjection.create(
                    entry,
                    List.of(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET))),
                    List.of(OutputContent.fromItemStack(new ItemStack(Items.STONE)))
                );
            };
            if (recipe.kind() == ClausedRecipeKind.guaranteedOutput) {
                projection.addClause(GuaranteedOutputClause.instance);
            } else {
                projection.inputs().getFirst().addClause(FluidContainerUnpackClause.instance);
            }
            return ResolveResult.resolved(projection);
        }

        @Override
        public RichIterable<ClausedRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }

    private record TestRecipe(String id, boolean resolvable, boolean probabilistic) {
        private TestRecipe(String id, boolean resolvable) {
            this(id, resolvable, false);
        }
    }

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "graph");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(TestRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return null;
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            if (!recipe.resolvable()) {
                return ResolveResult.unsupported();
            }
            return ResolveResult.resolved(RecipeProjection.create(
                this,
                recipe,
                List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG))),
                List.of(OutputContent.fromItemStack(
                    new ItemStack(Items.OAK_PLANKS),
                    1,
                    recipe.probabilistic() ? 0.5f : 1.0f
                ))
            ));
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}

