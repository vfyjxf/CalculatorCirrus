package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTranslator;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.impl.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ExecutionTranslatorContractTest {

    //region consume stock

    @Test
    void consumeStockTranslatesIntoAccessibilityThenWithdrawSteps(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.consumeOakLog(3)));

        assertStepTypes(
            translated,
            InteractionStep.EnsureAccessibleStep.class,
            InteractionStep.WithdrawResourceStep.class
        );

        var withdraw = (InteractionStep.WithdrawResourceStep) translated.steps().get(1);
        assertTrue(withdraw.target() instanceof InteractionStep.WithdrawTarget.Resource);
        assertEquals(fixtures.oakLogResource(), ((InteractionStep.WithdrawTarget.Resource) withdraw.target()).resource());
        assertEquals(3L, withdraw.requestedAmount());
    }

    @Test
    void consumeStockWithPreservedSemanticInputStillWithdrawsResolvedResource(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.consumeAnyPlankAsResolvedOak(4)));

        assertStepTypes(
            translated,
            InteractionStep.EnsureAccessibleStep.class,
            InteractionStep.WithdrawResourceStep.class
        );

        var ensure = (InteractionStep.EnsureAccessibleStep) translated.steps().get(0);
        var withdraw = (InteractionStep.WithdrawResourceStep) translated.steps().get(1);
        assertTrue(ensure.target() instanceof InteractionStep.AccessTarget.Resource);
        assertEquals(fixtures.oakPlanksResource(), ((InteractionStep.AccessTarget.Resource) ensure.target()).resource());
        assertTrue(withdraw.target() instanceof InteractionStep.WithdrawTarget.Resource);
        assertEquals(fixtures.oakPlanksResource(), ((InteractionStep.WithdrawTarget.Resource) withdraw.target()).resource());
        assertEquals(4L, withdraw.requestedAmount());
    }

    @Test
    void consumeUncertainStockKeepsOriginalInputSemanticsThroughTranslation(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.consumeAnyPlank(4)));

        assertStepTypes(
            translated,
            InteractionStep.EnsureAccessibleStep.class,
            InteractionStep.WithdrawResourceStep.class
        );

        var ensure = (InteractionStep.EnsureAccessibleStep) translated.steps().get(0);
        var withdraw = (InteractionStep.WithdrawResourceStep) translated.steps().get(1);
        assertTrue(ensure.target() instanceof InteractionStep.AccessTarget.Input);
        assertTrue(((InteractionStep.AccessTarget.Input) ensure.target()).input() instanceof TagStackContent<?>);
        assertTrue(withdraw.target() instanceof InteractionStep.WithdrawTarget.Input);
        assertTrue(((InteractionStep.WithdrawTarget.Input) withdraw.target()).input() instanceof TagStackContent<?>);
        assertEquals(4L, withdraw.requestedAmount());
    }

    //endregion

    //region reserve catalyst

    @Test
    void reserveCatalystTranslatesIntoAccessibilityThenReserveSteps(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.reserveWorkbenchCatalyst(1)));

        assertStepTypes(
            translated,
            InteractionStep.EnsureAccessibleStep.class,
            InteractionStep.ReserveCatalystStep.class
        );

        var reserve = (InteractionStep.ReserveCatalystStep) translated.steps().get(1);
        assertEquals(fixtures.workbenchCatalyst(), reserve.input());
        assertEquals(1L, reserve.amount());
    }

    //endregion

    //region execute recipe

    @Test
    void executeRecipeTranslatesIntoInteractionAndOutputHandlingSteps(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.craftPlanks(1)));

        assertStepTypes(
            translated,
            InteractionStep.OpenInteractionStep.class,
            InteractionStep.PrepareInputsStep.class,
            InteractionStep.ExecuteRecipeStep.class,
            InteractionStep.CollectOutputsStep.class,
            InteractionStep.StoreOutputsStep.class
        );

        var storeOutputs = (InteractionStep.StoreOutputsStep) translated.steps().get(4);
        assertEquals(fixtures.oakPlanksResource(), storeOutputs.resource());
        assertEquals(4L, storeOutputs.amount());
    }

    @Test
    void executeRecipeKeepsAggregatedInputAndOutputAmounts(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.craftPlanks(3)));

        var open = (InteractionStep.OpenInteractionStep) translated.steps().get(0);
        var prepare = (InteractionStep.PrepareInputsStep) translated.steps().get(1);
        var execute = (InteractionStep.ExecuteRecipeStep) translated.steps().get(2);
        var storeOutputs = (InteractionStep.StoreOutputsStep) translated.steps().get(4);

        assertEquals(3, open.times());
        assertEquals(3, execute.times());
        assertEquals(3L, prepare.inputs().getFirst().amount());
        assertEquals(3L, execute.inputs().getFirst().amount());
        assertEquals(12L, execute.expectedOutputs().getFirst().amount());
        assertEquals(12L, storeOutputs.amount());
    }

    @Test
    void executeRecipeKeepsTagInputsThroughTranslation(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.automaticInteractionApi());
        var recipe = fixtures.craftPlanks(1).recipe();
        var action = new PlanAction.ExecuteRecipe(
            0,
            0,
            true,
            recipe,
            2,
            Lists.immutable.of(
                new PlanAction.InputBinding(
                    InputContent.fromItemTag(ItemTags.PLANKS, 4),
                    8
                )
            ),
            Lists.immutable.of(
                new PlanAction.OutputBinding(fixtures.oakPlanksResource(), 8)
            )
        );
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(action));

        var prepare = (InteractionStep.PrepareInputsStep) translated.steps().get(1);
        var execute = (InteractionStep.ExecuteRecipeStep) translated.steps().get(2);

        assertTrue(prepare.inputs().getFirst().input() instanceof TagStackContent<?>);
        assertTrue(execute.inputs().getFirst().input() instanceof TagStackContent<?>);
        assertEquals(8L, prepare.inputs().getFirst().amount());
        assertEquals(8L, execute.inputs().getFirst().amount());
    }

    @Test
    void executeRecipeSplitsLargeCraftingTableBatchesByGridStackLimit(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(CraftingTableLikeInteraction.automatic(Namespace.ofMc("crafting_table")));
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.craftPlanks(130)));

        assertStepTypes(
            translated,
            InteractionStep.OpenInteractionStep.class,
            InteractionStep.PrepareInputsStep.class,
            InteractionStep.ExecuteRecipeStep.class,
            InteractionStep.CollectOutputsStep.class,
            InteractionStep.StoreOutputsStep.class,
            InteractionStep.PrepareInputsStep.class,
            InteractionStep.ExecuteRecipeStep.class,
            InteractionStep.CollectOutputsStep.class,
            InteractionStep.StoreOutputsStep.class,
            InteractionStep.PrepareInputsStep.class,
            InteractionStep.ExecuteRecipeStep.class,
            InteractionStep.CollectOutputsStep.class,
            InteractionStep.StoreOutputsStep.class
        );

        var firstPrepare = (InteractionStep.PrepareInputsStep) translated.steps().get(1);
        var firstExecute = (InteractionStep.ExecuteRecipeStep) translated.steps().get(2);
        var secondPrepare = (InteractionStep.PrepareInputsStep) translated.steps().get(5);
        var secondExecute = (InteractionStep.ExecuteRecipeStep) translated.steps().get(6);
        var thirdPrepare = (InteractionStep.PrepareInputsStep) translated.steps().get(9);
        var thirdExecute = (InteractionStep.ExecuteRecipeStep) translated.steps().get(10);

        assertEquals(64L, firstPrepare.inputs().getFirst().amount());
        assertEquals(64, firstExecute.times());
        assertEquals(256L, firstExecute.expectedOutputs().getFirst().amount());

        assertEquals(64L, secondPrepare.inputs().getFirst().amount());
        assertEquals(64, secondExecute.times());
        assertEquals(256L, secondExecute.expectedOutputs().getFirst().amount());

        assertEquals(2L, thirdPrepare.inputs().getFirst().amount());
        assertEquals(2, thirdExecute.times());
        assertEquals(8L, thirdExecute.expectedOutputs().getFirst().amount());
    }

    //endregion

    //region manual fallback

    @Test
    void executeRecipeInsertsManualCheckpointWhenNoPerformerExists(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.guidedOnlyInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.craftPlanks(1)));

        assertEquals(InteractionStep.OpenInteractionStep.class, translated.steps().get(0).getClass());
        assertTrue(translated.steps().anySatisfy(step -> step instanceof InteractionStep.ManualCheckpointStep));

        var manualCheckpoint = (InteractionStep.ManualCheckpointStep) translated.steps()
            .detect(step -> step instanceof InteractionStep.ManualCheckpointStep);
        assertEquals(fixtures.guidedOnlyInteractionApi().guidanceSummary(), manualCheckpoint.guidance().summary().getString());
    }

    //endregion

    //region helpers

    private static void assertStepTypes(InteractionExecutionPlan translated, Class<?>... expectedTypes) {
        assertEquals(expectedTypes.length, translated.steps().size());
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], translated.steps().get(i).getClass());
        }
    }

    //endregion
}
