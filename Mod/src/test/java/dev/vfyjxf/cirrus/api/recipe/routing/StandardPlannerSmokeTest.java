package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.OutputSelectionPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingVariantKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StandardPlannerSmokeTest {

    @Test
    void generateRecipeCenteredPlan(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());
        assertTrue(outcome.plan().actions().size() >= 2, "expected at least consume+execute actions");
    }

    @Test
    void flatHeuristicBuilderCanSuppressBadIntermediateRecipe(MinecraftServer server) {
        RecipeRoutingHeuristics.clear();
        try {
            RecipeRoutingHeuristics.add("test:bad_recycling")
                .recipeIdContains("recycling")
                .recycling()
                .targetOnly()
                .penalty(10_000)
                .register();

            var makeNormalPlanks = new RoutingTestFixtures.FakeRecipe(
                "make_normal_planks",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
                java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
            );
            var makeRecyclingPlanks = new RoutingTestFixtures.FakeRecipe(
                "make_recycling_planks",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_DOOR, 1)),
                java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
            );
            var makeStick = new RoutingTestFixtures.FakeRecipe(
                "make_stick",
                java.util.List.of(RoutingTestFixtures.in(Items.OAK_PLANKS, 2)),
                java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
            );
            RoutingService service = RoutingTestFixtures.service(makeNormalPlanks, makeRecyclingPlanks, makeStick);
            var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
            assertNotNull(target);

            Stock stock = Stock.empty();
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
            stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_DOOR, 1)));

            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(PlannerMode.standard)
                .stock(stock)
                .targetRecipe(target, 1)
                .build();

            var outcome = service.route(request);
            assertTrue(outcome.success() || outcome.partial());
            assertNotNull(outcome.plan());
            assertTrue(outcome.plan().actions().anySatisfy(action ->
                action instanceof PlanAction.ExecuteRecipe execute
                    && execute.recipe().id() != null
                    && "make_normal_planks".equals(execute.recipe().id().getPath())
            ));
            assertFalse(outcome.plan().actions().anySatisfy(action ->
                action instanceof PlanAction.ExecuteRecipe execute
                    && execute.recipe().id() != null
                    && "make_recycling_planks".equals(execute.recipe().id().getPath())
            ));

            var recyclingTarget = RoutingTestFixtures.findRecipeById(service.graph(), "make_recycling_planks");
            assertNotNull(recyclingTarget);
            var directOutcome = service.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard)
                .stock(stock)
                .targetRecipe(recyclingTarget, 1)
                .build());
            assertTrue(directOutcome.success() || directOutcome.partial(), "targetOnly recipe should still be routable as the target");
        } finally {
            RecipeRoutingHeuristics.clear();
        }
    }

    @Test
    void routeTagInputAcrossIntermediateRecipe(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        boolean executedPlanks = false;
        boolean executedStick = false;
        for (var action : outcome.plan().actions()) {
            if (!(action instanceof PlanAction.ExecuteRecipe execute) || execute.recipe().id() == null) {
                continue;
            }
            String path = execute.recipe().id().getPath();
            if ("make_planks".equals(path)) {
                executedPlanks = true;
            }
            if ("make_stick".equals(path)) {
                executedStick = true;
            }
        }
        assertTrue(executedPlanks, "expected planner to synthesize tag ingredient via make_planks");
        assertTrue(executedStick, "expected planner to execute target recipe");
    }

    @Test
    void routeTagInputKeepsTagBindingOnExecuteAction(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(InputContent.fromItemTag(ItemTags.PLANKS, 2)),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        PlanAction.ExecuteRecipe stickAction = outcome.plan().actions()
													  .selectInstancesOf(PlanAction.ExecuteRecipe.class)
													  .detect(action -> "make_stick".equals(action.recipe().id().getPath()));

        assertNotNull(stickAction);
        assertTrue(stickAction.boundInputs().getFirst().input() instanceof TagStackContent<?>);
        assertEquals(2L, stickAction.boundInputs().getFirst().amount());
    }

    @Test
    void preferredTagVariantReroutesToMatchingProducer(MinecraftServer server) {
        var makeOakPlanks = new RoutingTestFixtures.FakeRecipe(
            "make_oak_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        var makeBirchPlanks = new RoutingTestFixtures.FakeRecipe(
            "make_birch_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.BIRCH_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.BIRCH_PLANKS, 4))
        );
        InputContent planks = InputContent.fromItemTag(ItemTags.PLANKS, 2);
        var makeStick = new RoutingTestFixtures.FakeRecipe(
            "make_stick",
            java.util.List.of(planks),
            java.util.List.of(RoutingTestFixtures.out(Items.STICK, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makeOakPlanks, makeBirchPlanks, makeStick);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_stick");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.BIRCH_LOG, 1)));

        String birchVariantKey = RoutingVariantKeys.variantKey(ContentKind.fromContent(CirrusTypes.Contents.ITEM, Items.BIRCH_PLANKS));
        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .preferVariant(planks, birchVariantKey)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_birch_planks".equals(execute.recipe().id().getPath())
        ));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_oak_planks".equals(execute.recipe().id().getPath())
        ));
    }

    @Test
    void preferInventoryCoveredIntermediateRecipe(MinecraftServer server) {
        var cobbleFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var blackstoneFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_blackstone",
            java.util.List.of(RoutingTestFixtures.in(Items.BLACKSTONE, 8)),
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
        RoutingService service = RoutingTestFixtures.service(cobbleFurnace, blackstoneFurnace, makeSmoker);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_smoker");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 4)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_furnace_from_cobble".equals(execute.recipe().id().getPath())
        ));
        assertFalse(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && "make_furnace_from_blackstone".equals(execute.recipe().id().getPath())
        ));
    }

    @Test
    void preferInventoryCoveredProducerForOutputTarget(MinecraftServer server) {
        var cobbleFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_cobble",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var blackstoneFurnace = new RoutingTestFixtures.FakeRecipe(
            "make_furnace_from_blackstone",
            java.util.List.of(RoutingTestFixtures.in(Items.BLACKSTONE, 8)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        RoutingService service = RoutingTestFixtures.service(cobbleFurnace, blackstoneFurnace);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 64)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .adapterSelectionPolicy(OutputSelectionPolicy.bestEffortSingleRecipe)
            .targetOutput(ContentStack.fromStack(new ItemStack(Items.FURNACE, 1)), 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());

        PlanAction.ExecuteRecipe furnaceAction = outcome.plan().actions()
            .selectInstancesOf(PlanAction.ExecuteRecipe.class)
            .getFirst();
        assertNotNull(furnaceAction.recipe().id());
        assertEquals("make_furnace_from_cobble", furnaceAction.recipe().id().getPath());
    }
}
