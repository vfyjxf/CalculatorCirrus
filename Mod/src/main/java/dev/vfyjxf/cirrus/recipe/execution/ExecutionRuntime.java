package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferResult;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.jspecify.annotations.Nullable;

/**
 * Minimal runtime loop for stepwise execution, pause/resume, and retranslation triggers.
 */
public final class ExecutionRuntime {

    private final StorageApi storageApi;
    private final InteractionApi interactionApi;

    public ExecutionRuntime(StorageApi storageApi, InteractionApi interactionApi) {
        this.storageApi = storageApi;
        this.interactionApi = interactionApi;
    }

    //region control

    public ExecutionSession advance(ExecutionSession session) {
        if (session.paused() || session.completed()) {
            return session;
        }

        var step = session.currentStep();
        if (step == null) {
            return session;
        }

        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> complete(session, step);
            case InteractionStep.WithdrawResourceStep withdrawResourceStep ->
                handleWithdraw(session, withdrawResourceStep);
            case InteractionStep.ReserveCatalystStep reserveCatalystStep -> complete(session, step);
            case InteractionStep.EnsureCapacityStep ensureCapacityStep -> handleCapacity(session, ensureCapacityStep);
            case InteractionStep.OpenInteractionStep openInteractionStep -> complete(session, step);
            case InteractionStep.PrepareInputsStep prepareInputsStep -> complete(session, step);
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> handleExecute(session, executeRecipeStep);
            case InteractionStep.CollectOutputsStep collectOutputsStep -> complete(session, step);
            case InteractionStep.StoreOutputsStep storeOutputsStep -> handleStore(session, storeOutputsStep);
            case InteractionStep.ManualCheckpointStep manualCheckpointStep -> pause(
                session,
                step,
                new PauseReason(PauseReasons.awaitingPlayerAction, manualCheckpointStep.guidance())
            );
        };
    }

    public ExecutionSession resume(ExecutionSession session) {
        if (!session.paused()) {
            return session;
        }
        var step = session.currentStep();
        if (step == null) {
            return session;
        }
        ExecutionSession resumed = clearPause(session);
        if (step instanceof InteractionStep.ManualCheckpointStep) {
            return complete(resumed, step);
        }
        return advance(resumed);
    }

    public ExecutionSession requestReTranslation(ExecutionSession session, ReTranslationHint hint) {
        return new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            session.nextStepIndex(),
            session.completedSteps(),
            session.pausedSteps(),
            session.runningStep(),
            session.pauseReason(),
            hint,
            session.flowViewModel(),
            session.baselinePlanningSnapshot(),
            session.observedStockPolicy()
        );
    }

    //endregion

    //region handlers

    private ExecutionSession handleWithdraw(ExecutionSession session, InteractionStep.WithdrawResourceStep step) {
        var result = storageApi.extractFirstAvailable(withdrawRequest(step));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.noAccessibleProvider, guidanceFrom(result)));
    }

    private ExecutionSession handleCapacity(ExecutionSession session, InteractionStep.EnsureCapacityStep step) {
        var result = storageApi
                .simulateInsertFirstAvailable(StorageTransferRequest.forResource(step.resource(), step.amount()));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.inventoryCapacityBlocked, guidanceFrom(result)));
    }

    private ExecutionSession handleExecute(ExecutionSession session, InteractionStep.ExecuteRecipeStep step) {
        var result = interactionApi
                .perform(new InteractionContext(step.recipe(), step.times(), step.inputs(), step.expectedOutputs()));
        return switch (result) {
            case InteractionPerformResult.Performed performed -> complete(session, step);
            case InteractionPerformResult.ReTranslationRequired reTranslationRequired ->
                requestReTranslation(session, reTranslationRequired.hint());
            case InteractionPerformResult.ManualRequired manualRequired ->
                pause(session, step, new PauseReason(PauseReasons.awaitingPlayerAction, manualRequired.guidance()));
            case InteractionPerformResult.Failed failed ->
                pause(session, step, new PauseReason(PauseReasons.noAccessibleProcessor, failed.guidance()));
        };
    }

    private ExecutionSession handleStore(ExecutionSession session, InteractionStep.StoreOutputsStep step) {
        var result = storageApi
                .insertFirstAvailable(StorageTransferRequest.forResource(step.resource(), step.amount()));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.inventoryCapacityBlocked, guidanceFrom(result)));
    }

    //endregion

    /**
     * Builds the runtime storage request that matches the planner-translated withdraw semantics.
     */
    private StorageTransferRequest withdrawRequest(InteractionStep.WithdrawResourceStep step) {
        return switch (step.target()) {
            case InteractionStep.WithdrawTarget.Resource resource ->
                StorageTransferRequest.forResource(resource.resource(), step.requestedAmount());
            case InteractionStep.WithdrawTarget.Input input ->
                StorageTransferRequest.forInput(input.input(), step.requestedAmount());
        };
    }

    //region state transitions

    private ExecutionSession complete(ExecutionSession session, InteractionStep step) {
        var completedSteps = session.completedSteps().newWith(step).toImmutable();
        return rebuild(
            session,
            session.nextStepIndex() + 1,
            completedSteps,
            Sets.immutable.empty(),
            null,
            null,
            session.reTranslationHint()
        );
    }

    private ExecutionSession pause(ExecutionSession session, InteractionStep step, PauseReason reason) {
        return rebuild(
            session,
            session.nextStepIndex(),
            session.completedSteps(),
            Sets.immutable.of(step),
            null,
            reason,
            session.reTranslationHint()
        );
    }

    private ExecutionSession clearPause(ExecutionSession session) {
        return rebuild(
            session,
            session.nextStepIndex(),
            session.completedSteps(),
            Sets.immutable.empty(),
            null,
            null,
            session.reTranslationHint()
        );
    }

    private ExecutionSession rebuild(
        ExecutionSession session,
        int nextStepIndex,
        ImmutableSet<InteractionStep> completedSteps,
        ImmutableSet<InteractionStep> pausedSteps,
        @Nullable InteractionStep runningStep,
        @Nullable PauseReason pauseReason,
        @Nullable ReTranslationHint reTranslationHint
    ) {
        var runtimeState = new ExecutionFlowViewModel.RuntimeState(completedSteps, pausedSteps, runningStep, null);
        return new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            nextStepIndex,
            completedSteps,
            pausedSteps,
            runningStep,
            pauseReason,
            reTranslationHint,
            ExecutionFlowViewModel.derive(session.flowRoot(), runtimeState),
            session.baselinePlanningSnapshot(),
            session.observedStockPolicy()
        );
    }

    //region guidance

    private @Nullable ExecutionGuidance guidanceFrom(StorageTransferResult result) {
        var guidance = result.guidance();
        if (guidance == null) {
            return null;
        }
        return new ExecutionGuidance(
            guidance.summary(),
            guidance.detail(),
            Lists.immutable.empty(),
            Sets.immutable.empty()
        );
    }

    //endregion
}
