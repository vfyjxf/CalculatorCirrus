package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AssuredPlannerModeTest {

    @Test
    void boundedModeReturnsBestKnownPlanUnderBudget(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_planks");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertNotEquals(PlannerMode.standard, outcome.metrics().effectivePlannerMode());
        assertTrue(outcome.success() || outcome.partial());
        assertNotNull(outcome.plan());
    }

    @Test
    void provenModeRejectedWhenCapabilityGateFails(MinecraftServer server) {
        var makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks);
        var target = RoutingTestFixtures.findRecipeById(service.graph(), "make_planks");
        assertNotNull(target);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredProven)
            .ruleProfileId(DefaultRuleProfiles.assuredBoundedDefaultId)
            .allowModeFallback(false)
            .stock(stock)
            .targetRecipe(target, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.failed());
        assertTrue(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.capabilityGateRejected));
    }

    @Test
    void boundedModeSearchesPastStandardRollbackWindow(MinecraftServer server) {
        var badPartA = new RoutingTestFixtures.FakeRecipe(
            "bad_part_a",
            java.util.List.of(RoutingTestFixtures.in(Items.BEDROCK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.DIRT, 1))
        );
        var badPartB = new RoutingTestFixtures.FakeRecipe(
            "bad_part_b",
            java.util.List.of(RoutingTestFixtures.in(Items.BEDROCK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.COARSE_DIRT, 1))
        );
        var badPartC = new RoutingTestFixtures.FakeRecipe(
            "bad_part_c",
            java.util.List.of(RoutingTestFixtures.in(Items.BEDROCK, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.PODZOL, 1))
        );
        var goodPart = new RoutingTestFixtures.FakeRecipe(
            "good_part",
            java.util.List.of(RoutingTestFixtures.in(Items.COBBLESTONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.STONE, 1))
        );
        var badFurnaceA = new RoutingTestFixtures.FakeRecipe(
            "bad_furnace_a",
            java.util.List.of(RoutingTestFixtures.in(Items.DIRT, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var badFurnaceB = new RoutingTestFixtures.FakeRecipe(
            "bad_furnace_b",
            java.util.List.of(RoutingTestFixtures.in(Items.COARSE_DIRT, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var badFurnaceC = new RoutingTestFixtures.FakeRecipe(
            "bad_furnace_c",
            java.util.List.of(RoutingTestFixtures.in(Items.PODZOL, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var goodFurnace = new RoutingTestFixtures.FakeRecipe(
            "good_furnace_d",
            java.util.List.of(RoutingTestFixtures.in(Items.STONE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.FURNACE, 1))
        );
        var target = new RoutingTestFixtures.FakeRecipe(
            "target_smoker",
            java.util.List.of(RoutingTestFixtures.in(Items.FURNACE, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.SMOKER, 1))
        );
        RoutingService service = RoutingTestFixtures.service(
            badPartA,
            badPartB,
            badPartC,
            goodPart,
            badFurnaceA,
            badFurnaceB,
            badFurnaceC,
            goodFurnace,
            target
        );
        var targetEntry = RoutingTestFixtures.findRecipeById(service.graph(), "target_smoker");
        assertNotNull(targetEntry);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 1)));

        var standard = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(targetEntry, 1)
            .build());

        assertEquals(RoutingStatus.failed, standard.status());
        assertFalse(executedRecipe(standard.plan(), "good_furnace_d"));

        var assured = service.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded)
            .stock(stock)
            .targetRecipe(targetEntry, 1)
            .build());

        assertTrue(assured.success(), () -> assured.issues().makeString(", "));
        assertTrue(executedRecipe(assured.plan(), "good_furnace_d"));
        assertTrue(assured.metrics().explorationBranchesExpanded() >= 3);
        assertEquals(PlannerMode.assuredBounded, assured.metrics().effectivePlannerMode());
    }

    private static boolean executedRecipe(RoutingPlan plan, String recipeId) {
        return plan != null && plan.actions().anySatisfy(action ->
            action instanceof PlanAction.ExecuteRecipe execute
                && execute.recipe().id() != null
                && recipeId.equals(execute.recipe().id().getPath())
        );
    }
}
