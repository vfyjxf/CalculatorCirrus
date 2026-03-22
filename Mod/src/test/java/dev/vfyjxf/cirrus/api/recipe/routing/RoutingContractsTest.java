package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RoutingContractsTest {

    @Test
    void routeRecipeTargetInStandardMode(MinecraftServer server) {
        RoutingTestFixtures.FakeRecipe makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks);
        var recipe = RoutingTestFixtures.findRecipeById(service.graph(), "make_planks");
        assertNotNull(recipe);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.standard)
            .stock(stock)
            .targetRecipe(recipe, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success());
        assertNotNull(outcome.plan());
        assertEquals(PlannerMode.standard, outcome.metrics().effectivePlannerMode());
    }

    @Test
    void routeRecipeTargetInAssuredBoundedMode(MinecraftServer server) {
        RoutingTestFixtures.FakeRecipe makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks);
        var recipe = RoutingTestFixtures.findRecipeById(service.graph(), "make_planks");
        assertNotNull(recipe);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded)
            .stock(stock)
            .targetRecipe(recipe, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.success());
        assertNotNull(outcome.plan());
        assertEquals(PlannerMode.assuredBounded, outcome.metrics().effectivePlannerMode());
    }

    @Test
    void rejectProvenModeWhenCapabilitiesAreMissing(MinecraftServer server) {
        RoutingTestFixtures.FakeRecipe makePlanks = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RoutingService service = RoutingTestFixtures.service(makePlanks);
        var recipe = RoutingTestFixtures.findRecipeById(service.graph(), "make_planks");
        assertNotNull(recipe);

        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)));

        RoutingRequest request = RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredProven)
            .ruleProfileId(DefaultRuleProfiles.standardDefaultId)
            .allowModeFallback(false)
            .stock(stock)
            .targetRecipe(recipe, 1)
            .build();

        var outcome = service.route(request);
        assertTrue(outcome.failed());
        assertTrue(outcome.issues().anySatisfy(issue -> issue.code() == RoutingFailureCode.capabilityGateRejected));
    }
}
