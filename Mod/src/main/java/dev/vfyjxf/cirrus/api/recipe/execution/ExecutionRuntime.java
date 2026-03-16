package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowViewModel;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.impl.factory.Sets;

/**
 * Minimal runtime loop for stepwise execution, pause/resume, and retranslation triggers.
 */
public final class ExecutionRuntime {

    private final StorageApi storageApi;
    private final InteractionApi interactionApi;

    public ExecutionRuntime(StorageApi storageApi, InteractionApi interactionApi) {
        this.storageApi = Checks.checkNotNull(storageApi, "storageApi");
        this.interactionApi = Checks.checkNotNull(interactionApi, "interactionApi");
    }

    //region control

    public ExecutionSession advance(ExecutionSession session) {
        Checks.checkNotNull(session, "session");
        if (session.paused() || session.completed()) {
            return session;
        }

        var step = session.currentStep();
        if (step == null) {
            return session;
        }

        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> complete(session, step);
            case InteractionStep.WithdrawResourceStep withdrawResourceStep -> handleWithdraw(session, withdrawResourceStep);
            case InteractionStep.ReserveCatalystStep reserveCatalystStep -> complete(session, step);
            case InteractionStep.EnsureCapacityStep ensureCapacityStep -> handleCapacity(session, ensureCapacityStep);
            case InteractionStep.OpenInteractionStep openInteractionStep -> complete(session, step);
            case InteractionStep.PrepareInputsStep prepareInputsStep -> complete(session, step);
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> handleExecute(session, executeRecipeStep);
            case InteractionStep.CollectOutputsStep collectOutputsStep -> complete(session, step);
            case InteractionStep.StoreOutputsStep storeOutputsStep -> handleStore(session, storeOutputsStep);
            case InteractionStep.ManualCheckpointStep manualCheckpointStep -> pause(session, step, new PauseReason(PauseReasons.awaitingPlayerAction, manualCheckpointStep.guidance()));
        };
    }

    public ExecutionSession resume(ExecutionSession session) {
        Checks.checkNotNull(session, "session");
        if (!session.paused()) {
            return session;
        }
        var step = session.currentStep();
        if (step == null) {
            return session;
        }
        return complete(clearPause(session), step);
    }

    public ExecutionSession requestReTranslation(ExecutionSession session, ReTranslationHint hint) {
        Checks.checkNotNull(session, "session");
        Checks.checkNotNull(hint, "hint");
        return new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            session.nextStepIndex(),
            session.completedSteps(),
            session.pausedSteps(),
            session.runningStep(),
            session.pauseReason(),
            hint,
            session.flowViewModel()
        );
    }

    //endregion

    //region handlers

    private ExecutionSession handleWithdraw(ExecutionSession session, InteractionStep.WithdrawResourceStep step) {
        var result = storageApi.simulateExtractFirstAvailable(new StorageTransferRequest(step.resource(), step.requestedAmount()));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.noAccessibleProvider, guidanceFrom(result)));
    }

    private ExecutionSession handleCapacity(ExecutionSession session, InteractionStep.EnsureCapacityStep step) {
        var result = storageApi.simulateInsertFirstAvailable(new StorageTransferRequest(step.resource(), step.amount()));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.inventoryCapacityBlocked, guidanceFrom(result)));
    }

    private ExecutionSession handleExecute(ExecutionSession session, InteractionStep.ExecuteRecipeStep step) {
        var result = interactionApi.perform(new InteractionContext(step.recipe(), step.times(), step.inputs(), step.expectedOutputs()));
        return switch (result) {
            case InteractionPerformResult.Performed performed -> complete(session, step);
            case InteractionPerformResult.ManualRequired manualRequired ->
                pause(session, step, new PauseReason(PauseReasons.awaitingPlayerAction, manualRequired.guidance()));
            case InteractionPerformResult.Failed failed ->
                pause(session, step, new PauseReason(PauseReasons.noAccessibleProcessor, failed.guidance()));
        };
    }

    private ExecutionSession handleStore(ExecutionSession session, InteractionStep.StoreOutputsStep step) {
        var result = storageApi.simulateInsertFirstAvailable(new StorageTransferRequest(step.resource(), step.amount()));
        if (result.completed()) {
            return complete(session, step);
        }
        return pause(session, step, new PauseReason(PauseReasons.inventoryCapacityBlocked, guidanceFrom(result)));
    }

    //endregion

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
        org.eclipse.collections.api.set.ImmutableSet<InteractionStep> completedSteps,
        org.eclipse.collections.api.set.ImmutableSet<InteractionStep> pausedSteps,
        InteractionStep runningStep,
        PauseReason pauseReason,
        ReTranslationHint reTranslationHint
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
            ExecutionFlowViewModel.derive(session.flowRoot(), runtimeState)
        );
    }

    private dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance guidanceFrom(dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferResult result) {
        if (result.guidance() == null) {
            return null;
        }
        return new dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance(
            result.guidance().summary(),
            result.guidance().detail(),
            org.eclipse.collections.impl.factory.Lists.immutable.empty(),
            org.eclipse.collections.impl.factory.Sets.immutable.empty()
        );
    }

    //endregion
}
