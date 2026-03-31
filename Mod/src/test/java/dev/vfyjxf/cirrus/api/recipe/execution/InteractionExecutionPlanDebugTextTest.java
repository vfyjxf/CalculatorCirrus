package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep.ExecuteRecipeStep;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep.PrepareInputsStep;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class InteractionExecutionPlanDebugTextTest {

    @Test
    void describePlanFormatsRuntimeStepsAndBindings(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new dev.vfyjxf.cirrus.recipe.execution.ExecutionTranslator(
            CraftingTableLikeInteraction.automatic(Namespace.ofMc("crafting_table"))
        );
        InteractionExecutionPlan plan = translator.translate(fixtures.plan(fixtures.craftPlanks(2)));

        String text = InteractionExecutionPlanDebugText.describe(plan);

        assertTrue(text.contains("interaction-plan target=test:craft_planks x2"), text);
        assertTrue(text.contains("steps=5 capabilities=["), text);
        assertTrue(text.contains("[0] open test:craft_planks x2 interaction=minecraft:crafting_table"), text);
        assertTrue(text.contains("[1] prepare test:craft_planks"), text);
        assertTrue(text.contains("inputs: [minecraft:oak_log x2]"), text);
        assertTrue(text.contains("[2] execute test:craft_planks x2"), text);
        assertTrue(text.contains("outputs: [minecraft:oak_planks x8]"), text);
        assertTrue(text.contains("[3] collect test:craft_planks"), text);
        assertTrue(text.contains("[4] store-output minecraft:oak_planks x8"), text);
    }

    @Test
    void describeLinesFormatsSingleStep() {
        var fixtures = new ExecutionTranslatorFixtures();
        InteractionExecutionPlan plan = new dev.vfyjxf.cirrus.recipe.execution.ExecutionTranslator(
            fixtures.automaticInteractionApi()
        ).translate(fixtures.plan(fixtures.craftPlanks(1)));

        PrepareInputsStep prepare = plan.steps().selectInstancesOf(PrepareInputsStep.class).getFirst();
        ExecuteRecipeStep execute = plan.steps().selectInstancesOf(ExecuteRecipeStep.class).getFirst();

        String prepareText = InteractionExecutionPlanDebugText.describeLines(10, prepare).makeString(System.lineSeparator());
        String executeText = InteractionExecutionPlanDebugText.describeLines(11, execute).makeString(System.lineSeparator());

        assertTrue(prepareText.contains("[10] prepare test:craft_planks"), prepareText);
        assertTrue(prepareText.contains("inputs: [minecraft:oak_log x1]"), prepareText);
        assertTrue(executeText.contains("[11] execute test:craft_planks x1"), executeText);
        assertTrue(executeText.contains("outputs: [minecraft:oak_planks x4]"), executeText);
    }
}
