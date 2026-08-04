package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlanDebugText;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingPlanDebugTextTest {

    @Test
    void describePlanFormatsStableReadableLines() {
        var makeSmoker = new RoutingTestFixtures.FakeRecipe(
            "make_smoker",
            java.util.List.of(
                RoutingTestFixtures.in(Items.FURNACE, 1),
                InputContent.fromItemTag(ItemTags.LOGS, 4)
            ),
            java.util.List.of(RoutingTestFixtures.out(Items.SMOKER, 1))
        );
        RecipeGraph graph = RoutingTestFixtures.graph(makeSmoker);
        RecipeEntry<?> target = RoutingTestFixtures.findRecipeById(graph, "make_smoker");
        assertNotNull(target);

        PlanBuilder builder = new PlanBuilder(7L, 11L, target, 16);
        builder.addConsumeStock(
            InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 128),
            ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)),
            128L
        );
        builder.addPendingInput(InputContent.fromItemTag(ItemTags.LOGS, 4), 64L);
        builder.addExecuteRecipe(
            target,
            16,
            Lists.immutable.of(
                new PlanAction.InputBinding(
                    InputContent.fromItemStack(new ItemStack(Items.FURNACE), 1),
                    16L
                ),
                new PlanAction.InputBinding(
                    InputContent.fromItemTag(ItemTags.LOGS, 4),
                    64L
                )
            ),
            Lists.immutable.of(
                new PlanAction.OutputBinding(
                    ContentStack.fromStack(new ItemStack(Items.SMOKER)),
                    16L
                )
            )
        );
        RoutingPlan plan = builder.build();

        String text = RoutingPlanDebugText.describe(plan);

        assertTrue(text.contains("plan target=test:make_smoker x16"), text);
        assertTrue(text.contains("[0|0] consume-stock minecraft:cobblestone x128"), text);
        assertTrue(text.contains("[1|1] pending-input #minecraft:logs x64"), text);
        assertTrue(text.contains("[2|2] execute test:make_smoker x16"), text);
        assertTrue(text.contains("inputs:  [minecraft:furnace x16, #minecraft:logs x64]"), text);
        assertTrue(text.contains("outputs: [minecraft:smoker x16]"), text);
        assertTrue(!text.contains("<= #"), text);
    }
}
