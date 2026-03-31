package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RoutingPlanActionsTest {

    @Test
    void buildActionsWithStableOrderAndIds(MinecraftServer server) {
        var fake = new RoutingTestFixtures.FakeRecipe(
            "make_planks",
            java.util.List.of(RoutingTestFixtures.in(Items.OAK_LOG, 1)),
            java.util.List.of(RoutingTestFixtures.out(Items.OAK_PLANKS, 4))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(fake);
        RecipeEntry<?> recipe = RoutingTestFixtures.findRecipeById(graph, "make_planks");
        assertNotNull(recipe);

        PlanBuilder builder = new PlanBuilder(1L, 2L, recipe, 1);
        builder.addConsumeStock(
            RoutingTestFixtures.in(Items.OAK_LOG, 1),
            ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 1)),
            1
        );
        builder.addReserveCatalyst(RoutingTestFixtures.in(Items.STICK, 1), 1, recipe);
        builder.addExecuteRecipe(
            recipe,
            1,
            Lists.immutable.of(new PlanAction.InputBinding(RoutingTestFixtures.in(Items.OAK_LOG, 1), 1)),
            Lists.immutable.of(new PlanAction.OutputBinding(ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS, 4)), 4))
        );

        RoutingPlan plan = builder.build();
        assertEquals(3, plan.actions().size());
        assertEquals(0, plan.actions().get(0).actionId());
        assertEquals(1, plan.actions().get(1).actionId());
        assertEquals(2, plan.actions().get(2).actionId());
        assertEquals(0, plan.actions().get(0).actionIndex());
        assertEquals(1, plan.actions().get(1).actionIndex());
        assertEquals(2, plan.actions().get(2).actionIndex());
    }
}
