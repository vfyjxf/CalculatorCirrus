package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

/**
 * Translates routing plans into runtime interaction steps without changing planner output semantics.
 *
 * <p>The key rule here is that execution must preserve the planner's intent:
 * exact stock consumption stays exact, while unresolved direct-supply inputs stay expressed as {@link InputContent}.
 */
public record ExecutionTranslator(InteractionApi interactionApi) {

    //region translation

    public InteractionExecutionPlan translate(RoutingPlan plan) {
        MutableList<InteractionStep> steps = Lists.mutable.empty();
        for (var action : plan.actions()) {
            switch (action) {
                case PlanAction.ConsumeStock consume -> translateExactConsume(consume, steps);
                case PlanAction.PendingInput pending -> translatePendingInput(pending, steps);
                case PlanAction.ReserveCatalyst reserve -> translateReserve(reserve, steps);
                case PlanAction.ExecuteRecipe execute -> translateExecute(execute, steps);
            }
        }
        return new InteractionExecutionPlan(plan.targetRecipe(), plan.targetTimes(), steps.toImmutable());
    }

    //endregion

    //region action translation

    /**
     * Translates fully-resolved stock consumption.
     */
    private void translateExactConsume(PlanAction.ConsumeStock consume, MutableList<InteractionStep> steps) {
        translateConsume(
            steps,
            new InteractionStep.AccessTarget.Resource(consume.resource()),
            new InteractionStep.WithdrawTarget.Resource(consume.resource()),
            consume.amount()
        );
    }

    /**
     * Translates execution-profile direct supply that must keep the original input semantics.
     */
    private void translatePendingInput(PlanAction.PendingInput pending, MutableList<InteractionStep> steps) {
        translateConsume(
            steps,
            new InteractionStep.AccessTarget.Input(pending.input()),
            new InteractionStep.WithdrawTarget.Input(pending.input()),
            pending.amount()
        );
    }

    private void translateConsume(
        MutableList<InteractionStep> steps,
        InteractionStep.AccessTarget accessTarget,
        InteractionStep.WithdrawTarget withdrawTarget,
        long amount
    ) {
        steps.add(new InteractionStep.EnsureAccessibleStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.storageAccess,
            accessTarget,
            amount
        ));
        steps.add(new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            withdrawTarget,
            amount
        ));
    }

    private void translateReserve(
            PlanAction.ReserveCatalyst reserve,
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
            PlanAction.ExecuteRecipe execute,
            MutableList<InteractionStep> steps
    ) {
        var probe = interactionApi.probe(execute.recipe());
        Namespace interactionId = probe instanceof InteractionProbeResult.Supported supported
            ? supported.interactionId()
            : execute.recipe().system().systemId();
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
        int remainingTimes = execute.times();
        while (remainingTimes > 0) {
            int batchTimes = resolveBatchTimes(execute, remainingTimes);
            ImmutableList<PlanAction.InputBinding> batchInputs = scaleInputs(execute.boundInputs(), batchTimes, execute.times());
            ImmutableList<PlanAction.OutputBinding> batchOutputs = scaleOutputs(execute.expectedOutputs(), batchTimes, execute.times());

            steps.add(new InteractionStep.PrepareInputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    execute.recipe(),
                    batchInputs
            ));
            steps.add(new InteractionStep.ExecuteRecipeStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionPerform,
                    execute.recipe(),
                    batchTimes,
                    batchInputs,
                    batchOutputs
            ));
            steps.add(new InteractionStep.CollectOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    execute.recipe(),
                    batchOutputs
            ));
            batchOutputs.forEach(output -> steps.add(new InteractionStep.StoreOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.outputStorage,
                    output.resource(),
                    output.amount()
            )));
            remainingTimes -= batchTimes;
        }
    }

    //endregion

    //region batching

    private int resolveBatchTimes(PlanAction.ExecuteRecipe execute, int remainingTimes) {
        int requestedTimes = Math.max(1, remainingTimes);
        if (!canSplitEvenly(execute)) {
            return requestedTimes;
        }
        int batchLimit = interactionApi.maxExecuteBatchSize(new InteractionContext(
            execute.recipe(),
            execute.times(),
            execute.boundInputs(),
            execute.expectedOutputs()
        ));
        return Math.max(1, Math.min(requestedTimes, batchLimit));
    }

    private static boolean canSplitEvenly(PlanAction.ExecuteRecipe execute) {
        for (PlanAction.InputBinding input : execute.boundInputs()) {
            if (input.amount() % execute.times() != 0L) {
                return false;
            }
        }
        for (PlanAction.OutputBinding output : execute.expectedOutputs()) {
            if (output.amount() % execute.times() != 0L) {
                return false;
            }
        }
        return true;
    }

    private static ImmutableList<PlanAction.InputBinding> scaleInputs(
        ImmutableList<PlanAction.InputBinding> inputs,
        int batchTimes,
        int totalTimes
    ) {
        MutableList<PlanAction.InputBinding> scaled = Lists.mutable.empty();
        for (PlanAction.InputBinding input : inputs) {
            long amount = scaleAmount(input.amount(), batchTimes, totalTimes);
            scaled.add(new PlanAction.InputBinding(input.input(), amount));
        }
        return scaled.toImmutable();
    }

    private static ImmutableList<PlanAction.OutputBinding> scaleOutputs(
        ImmutableList<PlanAction.OutputBinding> outputs,
        int batchTimes,
        int totalTimes
    ) {
        MutableList<PlanAction.OutputBinding> scaled = Lists.mutable.empty();
        for (PlanAction.OutputBinding output : outputs) {
            long amount = scaleAmount(output.amount(), batchTimes, totalTimes);
            scaled.add(new PlanAction.OutputBinding(output.resource(), amount));
        }
        return scaled.toImmutable();
    }

    private static long scaleAmount(long totalAmount, int batchTimes, int totalTimes) {
        if (totalTimes <= 0) {
            throw new IllegalArgumentException("totalTimes must be positive");
        }
        if (batchTimes == totalTimes) {
            return totalAmount;
        }
        if (totalAmount % totalTimes != 0L) {
            throw new IllegalArgumentException("execution binding amount must divide evenly by total recipe times");
        }
        long perCraftAmount = totalAmount / totalTimes;
        return Math.multiplyExact(perCraftAmount, batchTimes);
    }

    //endregion
}

