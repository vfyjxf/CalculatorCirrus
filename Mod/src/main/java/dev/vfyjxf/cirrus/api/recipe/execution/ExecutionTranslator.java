package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

/**
 * Translates routing plans into runtime interaction steps without changing planner output.
 */
public final class ExecutionTranslator {

    private final StorageApi storageApi;
    private final InteractionApi interactionApi;

    public ExecutionTranslator(StorageApi storageApi, InteractionApi interactionApi) {
        this.storageApi = Checks.checkNotNull(storageApi, "storageApi");
        this.interactionApi = Checks.checkNotNull(interactionApi, "interactionApi");
    }

    //region translation

    public InteractionExecutionPlan translate(RoutingPlan plan) {
        Checks.checkNotNull(plan, "plan");

        MutableList<InteractionStep> steps = Lists.mutable.empty();
        for (var action : plan.actions()) {
            switch (action) {
                case dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ConsumeStockAction consume ->
                    translateConsume(consume, steps);
                case dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ReserveCatalystAction reserve ->
                    translateReserve(reserve, steps);
                case dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ExecuteRecipeAction execute ->
                    translateExecute(execute, steps);
            }
        }
        return new InteractionExecutionPlan(plan.targetRecipe(), plan.targetTimes(), steps.toImmutable());
    }

    //endregion

    //region state

    public StorageApi storageApi() {
        return storageApi;
    }

    public InteractionApi interactionApi() {
        return interactionApi;
    }

    //endregion

    //region action translation

    private void translateConsume(
        dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ConsumeStockAction consume,
        MutableList<InteractionStep> steps
    ) {
        steps.add(new InteractionStep.EnsureAccessibleStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.storageAccess,
            new InteractionStep.AccessTarget.Resource(consume.resource()),
            consume.amount()
        ));
        steps.add(new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            consume.resource(),
            consume.amount()
        ));
    }

    private void translateReserve(
        dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ReserveCatalystAction reserve,
        MutableList<InteractionStep> steps
    ) {
        steps.add(new InteractionStep.EnsureAccessibleStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.storageAccess,
            new InteractionStep.AccessTarget.Input(reserve.input()),
            reserve.amount()
        ));
        steps.add(new InteractionStep.ReserveCatalystStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            reserve.input(),
            reserve.amount()
        ));
    }

    private void translateExecute(
        dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.ExecuteRecipeAction execute,
        MutableList<InteractionStep> steps
    ) {
        var probe = interactionApi.probe(execute.recipe());
        String interactionId = probe instanceof InteractionProbeResult.Supported supported ? supported.interactionId() : "unknown";
        boolean performerAvailable = probe instanceof InteractionProbeResult.Supported supported && supported.performerAvailable();

        steps.add(new InteractionStep.OpenInteractionStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.interactionAccess,
            execute.recipe(),
            execute.times(),
            interactionId
        ));
        if (!performerAvailable) {
            steps.add(new InteractionStep.ManualCheckpointStep(
                ExecutionSideRequirement.manualPlayerAction,
                ExecutionCapability.manualCheckpoint,
                interactionApi.guide(execute.recipe()).guidance()
            ));
        }
        steps.add(new InteractionStep.PrepareInputsStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            execute.recipe(),
            execute.boundInputs()
        ));
        steps.add(new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.interactionPerform,
            execute.recipe(),
            execute.times(),
            execute.boundInputs(),
            execute.expectedOutputs()
        ));
        steps.add(new InteractionStep.CollectOutputsStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            execute.recipe(),
            execute.expectedOutputs()
        ));
        execute.expectedOutputs().forEach(output -> steps.add(new InteractionStep.StoreOutputsStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.outputStorage,
            output.resource(),
            output.amount()
        )));
    }

    //endregion
}
