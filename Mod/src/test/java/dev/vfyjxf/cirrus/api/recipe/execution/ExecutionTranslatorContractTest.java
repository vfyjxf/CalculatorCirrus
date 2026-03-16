package dev.vfyjxf.cirrus.api.recipe.execution;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
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
        var translator = new ExecutionTranslator(fixtures.storageApi(), fixtures.automaticInteractionApi());
        InteractionExecutionPlan translated = translator.translate(fixtures.plan(fixtures.consumeOakLog(3)));

        assertStepTypes(
            translated,
            InteractionStep.EnsureAccessibleStep.class,
            InteractionStep.WithdrawResourceStep.class
        );

        var withdraw = (InteractionStep.WithdrawResourceStep) translated.steps().get(1);
        assertEquals(fixtures.oakLogResource(), withdraw.resource());
        assertEquals(3L, withdraw.requestedAmount());
    }

    //endregion

    //region reserve catalyst

    @Test
    void reserveCatalystTranslatesIntoAccessibilityThenReserveSteps(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.storageApi(), fixtures.automaticInteractionApi());
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
        var translator = new ExecutionTranslator(fixtures.storageApi(), fixtures.automaticInteractionApi());
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

    //endregion

    //region manual fallback

    @Test
    void executeRecipeInsertsManualCheckpointWhenNoPerformerExists(MinecraftServer server) {
        var fixtures = new ExecutionTranslatorFixtures();
        var translator = new ExecutionTranslator(fixtures.storageApi(), fixtures.guidedOnlyInteractionApi());
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
