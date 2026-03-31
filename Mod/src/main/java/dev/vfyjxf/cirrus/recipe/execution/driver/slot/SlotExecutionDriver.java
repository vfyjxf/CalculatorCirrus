package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotAccess;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPlanningApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.util.PauseReasonKey;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class SlotExecutionDriver implements ExecutionDriver {

    // Standard flow must remain stateless; create per driver if stateful behavior is introduced.
    private static final SlotFlow standardFlow = new StandardSlotFlow();
    private static final SlotFlow autoFlow = new AutoSlotFlow();
    private static final String planningDriftSummaryKey = "cirrus.ui.execution.slot.retranslate.inventory_changed.summary";
    private static final String forceReopenSummaryKey = "cirrus.ui.execution.slot.retranslate.force_reopen.summary";
    private static final String manualPauseSummaryKey = "cirrus.ui.execution.slot.pause.manual.summary";
    private static final double autoAdvanceTicksPerRateUnit = 40.0d;

    private final Namespace interactionId;
    private final InteractionApi interactionApi;
    private final @Nullable SlotFlow flow;
    private final @Nullable SlotFlow.Context flowContext;
    private final @Nullable ExecutionRuntime runtime;
    private @Nullable ExecutionSession session;
    private double ratePerSecond;
    private boolean autoAdvanceEnabled;
    private double autoAdvanceProgress;
    private boolean forceReopenRequested;
    private int pausedAutoRetryFingerprint;
    private boolean pausedAutoRetryFingerprintInitialized;
    private long planningBaselineFingerprint;
    private boolean planningBaselineFingerprintInitialized;

    public record Profile(
        Namespace interactionId,
        InteractionApi interactionApi,
        SlotLayout layout,
        SlotAccess access,
        @Nullable SlotAccess.Interaction interaction,
        @Nullable SlotFlow.PlacementResolver placementResolver,
        @Nullable SlotFlow flow
    ) {
        public Profile(
            Namespace interactionId,
            SlotLayout layout,
            SlotAccess access,
            @Nullable SlotAccess.Interaction interaction,
            @Nullable SlotFlow.PlacementResolver placementResolver,
            @Nullable SlotFlow flow
        ) {
            this(
                interactionId,
                interaction != null && placementResolver != null ? automaticInteraction(interactionId) : guidedInteraction(interactionId),
                layout,
                access,
                interaction,
                placementResolver,
                flow
            );
        }
    }

    public static Profile profile(Namespace interactionId, SlotLayout layout, SlotAccess access) {
        return new Profile(interactionId, guidedInteraction(interactionId), layout, access, null, null, null);
    }

    public static SlotExecutionDriver standard(Profile profile) {
        return new SlotExecutionDriver(profile);
    }

    protected SlotExecutionDriver(Profile profile) {
        this(profile.interactionId(), profile.interactionApi(), resolveProfileFlow(profile), createProfileContext(profile), null);
    }

    private static SlotFlow resolveProfileFlow(Profile profile) {
        SlotFlow resolvedFlow = profile.flow();
        if (resolvedFlow == null) {
            if (profile.interaction() != null && profile.placementResolver() != null) {
                resolvedFlow = autoFlow;
            } else {
                resolvedFlow = standardFlow;
            }
        }
        return resolvedFlow;
    }

    private static SlotFlow.Context createProfileContext(Profile profile) {
        return new SlotFlow.Context(
            profile.layout(),
            profile.access(),
            profile.interaction(),
            profile.placementResolver()
        );
    }

    public SlotExecutionDriver(Namespace interactionId, InteractionApi interactionApi, ExecutionRuntime runtime) {
        this(interactionId, interactionApi, null, null, runtime);
    }

    public SlotExecutionDriver(
        Namespace interactionId,
        InteractionApi interactionApi,
        @Nullable SlotFlow flow,
        @Nullable SlotFlow.Context flowContext,
        @Nullable ExecutionRuntime runtime
    ) {
        this.interactionId = interactionId;
        this.interactionApi = interactionApi;
        this.flow = flow;
        this.flowContext = flowContext;
        this.runtime = runtime;
    }

    public static InteractionApi simpleInteraction(
        Namespace interactionId,
        boolean performerAvailable,
        ExecutionGuidance guidance
    ) {
        return new SimpleInteractionApi(interactionId, performerAvailable, guidance);
    }

    public static InteractionApi guidedInteraction(Namespace interactionId) {
        return simpleInteraction(
            interactionId,
            false,
            ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.slot.manual.execute"))
        );
    }

    public static InteractionApi automaticInteraction(Namespace interactionId) {
        return simpleInteraction(
            interactionId,
            true,
            ExecutionGuidance.simple(Component.translatable("cirrus.ui.execution.slot.manual.execute"))
        );
    }

    @Override
    public void initialize(ExecutionSession session) {
        this.session = session;
        this.forceReopenRequested = false;
        this.autoAdvanceEnabled = false;
        this.autoAdvanceProgress = 0.0d;
        clearPausedAutoRetryFingerprint();
        refreshPlanningBaseline();
    }

    @Override
    public ExecutionSession session() {
        if (session == null) {
            throw new IllegalStateException("Execution driver has not been initialized");
        }
        return session;
    }

    @Override
    public Namespace interactionId() {
        return interactionId;
    }

    @Override
    public InteractionApi interactionApi() {
        return interactionApi;
    }

    @Override
    public ExecutionInteraction interaction() {
        InteractionApi planningAwareApi = new InteractionPlanningView(
            interactionApi,
            flowContext == null ? ExecutionPlanningSnapshot.empty() : planningSnapshot(flowContext.layout(), flowContext.access())
        );
        return new ExecutionInteraction(interactionId, planningAwareApi);
    }

    protected @Nullable ExecutionSession currentSession() {
        return session;
    }

    protected @Nullable SlotFlow.Context currentFlowContext() {
        return flowContext;
    }

    protected double currentRatePerSecond() {
        return ratePerSecond;
    }

    protected boolean currentAutoAdvanceEnabled() {
        return autoAdvanceEnabled;
    }

    protected @Nullable ReTranslationHint currentReTranslationHint() {
        return session == null ? null : session.reTranslationHint();
    }

    protected TrackingInput captureTrackingInput() {
        SlotFlow.Context context = flowContext;
        if (context == null) {
            return new TrackingInput(null, 0L, null);
        }
        SlotAccess.State state = context.access().state();
        LinkedHashMap<SlotLayout.Reference, ItemStack> stacks = new LinkedHashMap<>();
        long fingerprint = 1L;
        fingerprint = 31L * fingerprint + (state.open() ? 1 : 0);
        fingerprint = 31L * fingerprint + state.side().hashCode();
        fingerprint = 31L * fingerprint + state.interactionId().hashCode();
        fingerprint = snapshotRoleStacks(context, SlotLayout.Role.input, stacks, fingerprint);
        fingerprint = snapshotRoleStacks(context, SlotLayout.Role.output, stacks, fingerprint);
        fingerprint = snapshotRoleStacks(context, SlotLayout.Role.inventory, stacks, fingerprint);
        fingerprint = snapshotRoleStacks(context, SlotLayout.Role.catalyst, stacks, fingerprint);
        fingerprint = snapshotRoleStacks(context, SlotLayout.Role.interaction, stacks, fingerprint);
        SlotAccess snapshotAccess = new SnapshotSlotAccess(state, stacks);
        return new TrackingInput(new SlotFlow.Context(context.layout(), snapshotAccess, null, null), fingerprint, null);
    }

    @Override
    public ExecutionDriverSnapshot snapshot() {
        ExecutionTelemetrySnapshot telemetry = session == null
            ? new ExecutionTelemetrySnapshot(
                "pending",
                false,
                false,
                0,
                0,
                0.0d,
                null,
                null,
                null,
                Lists.immutable.empty(),
                Lists.immutable.empty(),
                null,
                null,
                ratePerSecond,
                autoAdvanceEnabled
            ) : ExecutionTelemetrySnapshot.from(session, ratePerSecond, autoAdvanceEnabled);
        SlotFlow.Tracking tracking = buildTracking(session);
        return new ExecutionDriverSnapshot(
            session,
            tracking.viewModel(),
            telemetry,
            tracking.materials(),
            tracking.targetProgress(),
            tracking.trace(),
            ratePerSecond,
            autoAdvanceEnabled,
            session == null ? null : session.reTranslationHint()
        );
    }

    protected static long snapshotRoleStacks(
        SlotFlow.Context context,
        SlotLayout.Role role,
        LinkedHashMap<SlotLayout.Reference, ItemStack> stacks,
        long fingerprint
    ) {
        SlotLayout.Index index = context.layout().slots(role);
        if (index.keys().isEmpty()) {
            return fingerprint;
        }
        ArrayList<SlotLayout.Key> orderedKeys = new ArrayList<>(index.keys());
        orderedKeys.sort(index.order());
        long out = fingerprint;
        for (SlotLayout.Key key : orderedKeys) {
            SlotLayout.Reference reference = index.get(key);
            if (reference == null) {
                continue;
            }
            ItemStack stack = context.access().stack(reference);
            stacks.put(reference, stack.copy());
            out = 31L * out + reference.hashCode();
            out = 31L * out + fingerprintStack(stack);
        }
        return out;
    }

    protected static long fingerprintStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        long out = stack.getItem().hashCode();
        out = 31L * out + stack.getCount();
        out = 31L * out + stack.getComponentsPatch().hashCode();
        return out;
    }

    protected static record TrackingInput(
        @Nullable SlotFlow.Context context,
        long fingerprint,
        @Nullable Object extra
    ) {}

    private static final class SnapshotSlotAccess implements SlotAccess {
        private final SlotAccess.State state;
        private final Map<SlotLayout.Reference, ItemStack> stacks;

        private SnapshotSlotAccess(SlotAccess.State state, Map<SlotLayout.Reference, ItemStack> stacks) {
            this.state = state;
            this.stacks = Map.copyOf(stacks);
        }

        @Override
        public SlotAccess.State state() {
            return state;
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            ItemStack stack = stacks.get(slot);
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }

    protected SlotFlow.Tracking customizeTracking(@Nullable ExecutionSession session, SlotFlow.Tracking tracking) {
        return tracking;
    }

    @Override
    public void advanceSteps(int steps) {
        Checks.checkArgument(steps >= 0, "steps must be non-negative");
        if (session == null || steps == 0) {
            return;
        }
        for (int i = 0; i < steps; i++) {
            if (session.paused() || session.completed()) {
                break;
            }
            session = advanceOne(session);
            stopAutoAdvanceIfSessionCompleted();
        }
    }

    @Override
    public void pause() {
        if (session == null || session.paused()) {
            return;
        }
        session = pause(session, session.currentStep(), new PauseReason(PauseReasons.awaitingPlayerAction, ExecutionGuidance.simple(Component.translatable(manualPauseSummaryKey))));
        clearPausedAutoRetryFingerprint();
    }

    @Override
    public void resume() {
        if (session == null || !session.paused()) {
            return;
        }
        session = runtime == null ? clearPause(session) : runtime.resume(session);
        stopAutoAdvanceIfSessionCompleted();
        clearPausedAutoRetryFingerprint();
    }

    @Override
    public void toggleAutoAdvance() {
        autoAdvanceEnabled = !autoAdvanceEnabled;
        autoAdvanceProgress = 0.0d;
        clearPausedAutoRetryFingerprint();
    }

    @Override
    public void stopAutoAdvance() {
        autoAdvanceEnabled = false;
        autoAdvanceProgress = 0.0d;
        clearPausedAutoRetryFingerprint();
    }

    @Override
    public void setRatePerSecond(double rate) {
        ratePerSecond = Math.max(0.0d, rate);
    }

    @Override
    public void reset() {
        if (session == null) {
            return;
        }
        autoAdvanceEnabled = false;
        autoAdvanceProgress = 0.0d;
        forceReopenRequested = false;
        clearPausedAutoRetryFingerprint();
        session = new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            0,
            Sets.immutable.empty(),
            Sets.immutable.empty(),
            null,
            null,
            session.reTranslationHint(),
            session.flowViewModel(),
            session.baselinePlanningSnapshot(),
            session.observedStockPolicy()
        );
        stopAutoAdvanceIfSessionCompleted();
        refreshPlanningBaseline();
    }

    @Override
    public void forceReopen() {
        if (session == null) {
            return;
        }
        forceReopenRequested = true;
    }

    @Override
    public void jumpToStep(int index) {
        if (session == null) {
            return;
        }
        autoAdvanceProgress = 0.0d;
        clearPausedAutoRetryFingerprint();
        int clamped = Math.max(0, Math.min(index, session.plan().steps().size()));
        session = new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            clamped,
            session.completedSteps(),
            session.pausedSteps(),
            session.currentStep(),
            session.pauseReason(),
            session.reTranslationHint(),
            session.flowViewModel(),
            session.baselinePlanningSnapshot(),
            session.observedStockPolicy()
        );
        refreshPlanningBaseline();
    }

    @Override
    public void tick() {
        if (session == null || session.completed()) {
            autoAdvanceEnabled = false;
            autoAdvanceProgress = 0.0d;
            clearPausedAutoRetryFingerprint();
            return;
        }
        if (forceReopenRequested) {
            session = requestReTranslation(session, Component.translatable(forceReopenSummaryKey));
            forceReopenRequested = false;
            autoAdvanceProgress = 0.0d;
            clearPausedAutoRetryFingerprint();
            return;
        }
        if (pollManualProgress()) {
            autoAdvanceProgress = 0.0d;
            clearPausedAutoRetryFingerprint();
            return;
        }
        if (retryAutoProgressIfStateChanged()) {
            autoAdvanceProgress = 0.0d;
            return;
        }
        if (requestReTranslationIfPlanningStateChanged()) {
            autoAdvanceProgress = 0.0d;
            clearPausedAutoRetryFingerprint();
            return;
        }
        if (!autoAdvanceEnabled) {
            autoAdvanceProgress = 0.0d;
            clearPausedAutoRetryFingerprint();
            return;
        }
        if (session.paused()) {
            autoAdvanceProgress = 0.0d;
            return;
        }
        if (ratePerSecond <= 0.0d) {
            return;
        }
        autoAdvanceProgress += ratePerSecond / autoAdvanceTicksPerRateUnit;
        int stepsToAdvance = (int) Math.floor(autoAdvanceProgress);
        if (stepsToAdvance <= 0) {
            return;
        }
        autoAdvanceProgress -= stepsToAdvance;
        advanceAutoFragments(stepsToAdvance);
    }

    private void advanceAutoFragments(int fragments) {
        Checks.checkArgument(fragments >= 0, "fragments must be non-negative");
        if (session == null || fragments == 0) {
            return;
        }
        for (int i = 0; i < fragments; i++) {
            if (session == null || session.paused() || session.completed()) {
                return;
            }
            advanceAutoFragment();
        }
    }

    private void advanceAutoFragment() {
        if (session == null || session.paused() || session.completed()) {
            return;
        }
        int safety = 0;
        while (session != null && !session.paused() && !session.completed()) {
            if (safety++ > 256) {
                return;
            }
            if (session.currentStep() == null) {
                return;
            }
            session = advanceOne(session);
            stopAutoAdvanceIfSessionCompleted();
            if (session == null || session.paused() || session.completed()) {
                return;
            }
            if (hasVisibleAutoTrace(session)) {
                return;
            }
        }
    }

    protected SlotFlow.StepResult pollOpenProgress(SlotFlow.Context context, InteractionStep.OpenInteractionStep step) {
        return context.access().isOpen() ? SlotFlow.StepResult.success() : SlotFlow.StepResult.blocked();
    }

    protected SlotFlow.StepResult pollPrepareProgress(SlotFlow.Context context, InteractionStep.PrepareInputsStep step) {
        if (!context.access().isOpen()) {
            return SlotFlow.StepResult.blocked();
        }
        SlotFlow.PlacementResolver resolver = context.placementResolver();
        if (resolver == null) {
            return SlotFlow.StepResult.blocked();
        }
        ImmutableList<SlotFlow.Placement> placements = resolver.resolve(context, step.recipe(), step.inputs());
        if (placements == null || placements.isEmpty()) {
            return SlotFlow.StepResult.blocked();
        }
        return SlotTrace.placementsSatisfied(context, placements) ? SlotFlow.StepResult.success() : SlotFlow.StepResult.blocked();
    }

    protected SlotFlow.StepResult pollExecuteProgress(SlotFlow.Context context, InteractionStep.ExecuteRecipeStep step) {
        if (!context.access().isOpen()) {
            return SlotFlow.StepResult.blocked();
        }
        ImmutableList<SlotLayout.Reference> outputSlots = orderedSlotReferences(context, SlotLayout.Role.output);
        if (outputSlots.isEmpty()) {
            return SlotFlow.StepResult.blocked();
        }
        ImmutableList<ItemStack> expectedOutputs = normalizedExpectedOutputs(step.expectedOutputs());
        for (SlotLayout.Reference outputSlot : outputSlots) {
            ItemStack current = normalize(context.access().stack(outputSlot));
            if (current.isEmpty()) {
                continue;
            }
            if (expectedOutputs.isEmpty()) {
                return SlotFlow.StepResult.blocked();
            }
            for (ItemStack expected : expectedOutputs) {
                if (sameItem(current, expected) && current.getCount() >= expected.getCount()) {
                    return SlotFlow.StepResult.success();
                }
            }
        }
        return SlotFlow.StepResult.blocked();
    }

    protected SlotFlow.StepResult pollCollectProgress(SlotFlow.Context context, InteractionStep.CollectOutputsStep step) {
        if (!context.access().isOpen()) {
            return SlotFlow.StepResult.blocked();
        }
        ImmutableList<SlotLayout.Reference> outputSlots = orderedSlotReferences(context, SlotLayout.Role.output);
        if (outputSlots.isEmpty()) {
            return SlotFlow.StepResult.blocked();
        }
        for (SlotLayout.Reference outputSlot : outputSlots) {
            if (!context.access().stack(outputSlot).isEmpty()) {
                return SlotFlow.StepResult.blocked();
            }
        }
        return SlotFlow.StepResult.success();
    }

    protected static ImmutableList<SlotLayout.Reference> orderedSlotReferences(SlotFlow.Context context, SlotLayout.Role role) {
		SlotLayout.Index index = context.layout().slots(role);
        if (index.keys().isEmpty()) {
            return Lists.immutable.empty();
        }
        ArrayList<SlotLayout.Key> orderedKeys = new ArrayList<>(index.keys());
        orderedKeys.sort(index.order());
        MutableList<SlotLayout.Reference> ordered = Lists.mutable.empty();
        for (SlotLayout.Key key : orderedKeys) {
            SlotLayout.Reference ref = index.get(key);
            if (ref != null) {
                ordered.add(ref);
            }
        }
        return ordered.toImmutable();
    }

    protected ImmutableList<ItemStack> normalizedExpectedOutputs(ImmutableList<?> outputs) {
        if (outputs.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<ItemStack> normalized = Lists.mutable.empty();
        for (Object output : outputs) {
            if (!(output instanceof PlanAction.OutputBinding binding)) {
                continue;
            }
            ItemStack stack = normalize(SlotExecutionDriverSupport.stackFromOutputBinding(binding));
            if (!stack.isEmpty()) {
                normalized.add(stack);
            }
        }
        return normalized.toImmutable();
    }

    protected ExecutionSession requestReTranslation(ExecutionSession current, Component summary) {
		return requestReTranslation(current, new ReTranslationHint(summary));
    }

    protected ExecutionSession requestReTranslation(ExecutionSession current, ReTranslationHint hint) {
        return runtime == null
            ? rebuildSession(current, current.nextStepIndex(), current.completedSteps(), current.pausedSteps(), current.runningStep(), current.pauseReason(), hint)
            : runtime.requestReTranslation(current, hint);
    }

    private boolean retryAutoProgressIfStateChanged() {
        if (session == null || !session.paused() || !autoAdvanceEnabled || flow == null || flowContext == null) {
            clearPausedAutoRetryFingerprint();
            return false;
        }
        int fingerprint = observedFlowStateFingerprint(flowContext);
        if (!pausedAutoRetryFingerprintInitialized) {
            pausedAutoRetryFingerprint = fingerprint;
            pausedAutoRetryFingerprintInitialized = true;
            return false;
        }
        if (pausedAutoRetryFingerprint == fingerprint) {
            return false;
        }
        session = clearPause(session);
        advanceSteps(1);
        if (session != null
            && session.paused()
            && session.reTranslationHint() == null
            && isFlowManagedStep(session.currentStep())
            && planningStateDrifted()) {
            session = requestReTranslation(session, planningDriftHint());
            clearPausedAutoRetryFingerprint();
            return true;
        }
        if (session == null || !session.paused()) {
            clearPausedAutoRetryFingerprint();
        } else {
            pausedAutoRetryFingerprint = observedFlowStateFingerprint(flowContext);
            pausedAutoRetryFingerprintInitialized = true;
        }
        return true;
    }

    private boolean pollManualProgress() {
        if (session == null || !session.paused() || flow == null || flowContext == null) {
            return false;
        }
        InteractionStep step = session.currentStep();
        if (step == null) {
            return false;
        }
        SlotFlow.StepResult progress = switch (step) {
            case InteractionStep.OpenInteractionStep openInteractionStep ->
                pollOpenProgress(flowContext, openInteractionStep);
            case InteractionStep.PrepareInputsStep prepareInputsStep ->
                pollPrepareProgress(flowContext, prepareInputsStep);
            case InteractionStep.ExecuteRecipeStep executeRecipeStep ->
                pollExecuteProgress(flowContext, executeRecipeStep);
            case InteractionStep.CollectOutputsStep collectOutputsStep ->
                pollCollectProgress(flowContext, collectOutputsStep);
            default -> SlotFlow.StepResult.blocked();
        };
        if (!progress.completed()) {
            return false;
        }
        session = completeStep(clearPause(session), step);
        return true;
    }

    private int observedFlowStateFingerprint(SlotFlow.Context context) {
        return observedFlowStateFingerprint(context, session == null ? null : session.currentStep());
    }

    private int observedFlowStateFingerprint(SlotFlow.Context context, @Nullable InteractionStep step) {
        int fingerprint = 1;
        fingerprint = 31 * fingerprint + (context.access().isOpen() ? 1 : 0);
        fingerprint = appendSlotFingerprint(fingerprint, context.access().state());
        fingerprint = appendStackFingerprint(fingerprint, context.interaction() == null ? ItemStack.EMPTY : context.interaction().carried());
        if (step instanceof InteractionStep.OpenInteractionStep) {
            return fingerprint;
        }
        if (step instanceof InteractionStep.PrepareInputsStep) {
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.input), context);
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.inventory), context);
            return fingerprint;
        }
        if (step instanceof InteractionStep.ExecuteRecipeStep) {
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.input), context);
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.output), context);
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.inventory), context);
            return fingerprint;
        }
        if (step instanceof InteractionStep.CollectOutputsStep) {
            fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.output), context);
            return fingerprint;
        }
        fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.input), context);
        fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.output), context);
        fingerprint = appendSlotFingerprint(fingerprint, orderedSlotReferences(context, SlotLayout.Role.inventory), context);
        return fingerprint;
    }

    private int appendSlotFingerprint(int fingerprint, @Nullable SlotAccess.State state) {
        if (state == null) {
            return fingerprint;
        }
        int out = fingerprint;
        out = 31 * out + (state.open() ? 1 : 0);
        out = 31 * out + state.side().hashCode();
        out = 31 * out + state.interactionId().hashCode();
        return out;
    }

    private int appendSlotFingerprint(
        int fingerprint,
        ImmutableList<SlotLayout.Reference> slots,
        SlotFlow.Context context
    ) {
        int out = fingerprint;
        for (SlotLayout.Reference slot : slots) {
            out = appendStackFingerprint(out, context.access().stack(slot));
        }
        return out;
    }

    private static int appendStackFingerprint(int fingerprint, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 31 * fingerprint;
        }
        ItemStack normalized = normalize(stack);
        int out = fingerprint;
        out = 31 * out + normalized.getItem().hashCode();
        out = 31 * out + normalized.getCount();
        out = 31 * out + normalized.getComponentsPatch().hashCode();
        return out;
    }

    private void clearPausedAutoRetryFingerprint() {
        pausedAutoRetryFingerprint = 0;
        pausedAutoRetryFingerprintInitialized = false;
    }

    private void stopAutoAdvanceIfSessionCompleted() {
        if (session != null && session.completed()) {
            stopAutoAdvance();
        }
    }

    private ExecutionSession advanceOne(ExecutionSession current) {
        InteractionStep step = current.currentStep();
        if (step == null) {
            return current;
        }
        if (flow != null && flowContext != null) {
            return advanceWithFlow(current, flowContext, flow);
        }
        return runtime == null ? completeStep(current, step) : runtime.advance(current);
    }

    private ExecutionSession advanceWithFlow(ExecutionSession current, SlotFlow.Context context, SlotFlow flow) {
        InteractionStep step = current.currentStep();
        if (step == null) {
            return current;
        }
        SlotFlow.Validation validation = flow.validate(context);
        if (!validation.valid()) {
            PauseReason reason = validation.pauseReason();
            if (reason == null) {
                reason = flow.pauseReason(context, new SlotFlow.Failure(step, SlotFlow.Failure.FailureKind.validation, null));
            }
            return pause(current, step, reason);
        }
        switch (step) {
            case InteractionStep.OpenInteractionStep openInteractionStep -> {
                SlotFlow.StepResult result = flow.open(context, openInteractionStep);
                if (result.completed()) {
                    return completeStep(current, step);
                }
                return pause(current, step, resolvePauseReason(result, flow, context, step, SlotFlow.Failure.FailureKind.openBlocked));
            }
            case InteractionStep.PrepareInputsStep prepareInputsStep -> {
                SlotFlow.StepResult result = flow.prepare(context, prepareInputsStep);
                if (result.completed()) {
                    return completeStep(current, step);
                }
                return pause(current, step, resolvePauseReason(result, flow, context, step, SlotFlow.Failure.FailureKind.prepareBlocked));
            }
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> {
                InteractionPerformResult result = flow.execute(context, executeRecipeStep);
                return switch (result) {
                    case InteractionPerformResult.Performed performed -> completeStep(current, step);
                    case InteractionPerformResult.ReTranslationRequired reTranslationRequired ->
                        requestReTranslation(current, reTranslationRequired.hint());
                    case InteractionPerformResult.ManualRequired manualRequired ->
                        pause(current, step, new PauseReason(PauseReasons.awaitingPlayerAction, manualRequired.guidance()));
                    case InteractionPerformResult.Failed failed ->
                        pause(current, step, new PauseReason(PauseReasons.noAccessibleProcessor, failed.guidance()));
                };
            }
            case InteractionStep.CollectOutputsStep collectOutputsStep -> {
                SlotFlow.StepResult result = flow.collect(context, collectOutputsStep);
                if (result.completed()) {
                    return completeStep(current, step);
                }
                return pause(current, step, resolvePauseReason(result, flow, context, step, SlotFlow.Failure.FailureKind.collectBlocked));
            }
            default -> {
            }
        }
        return runtime == null ? completeStep(current, step) : runtime.advance(current);
    }

    private PauseReason resolvePauseReason(
        SlotFlow.StepResult result,
        SlotFlow flow,
        SlotFlow.Context context,
        InteractionStep step,
        SlotFlow.Failure.FailureKind kind
    ) {
        if (result.pauseReason() != null) {
            return result.pauseReason();
        }
		return flow.pauseReason(context, new SlotFlow.Failure(step, kind, null));
	}

    private ExecutionSession completeStep(ExecutionSession current, InteractionStep step) {
        clearPausedAutoRetryFingerprint();
        ExecutionSession completed = rebuildSession(
            current,
            current.nextStepIndex() + 1,
            current.completedSteps().newWith(step).toImmutable(),
            Sets.immutable.empty(),
            null,
            null,
            current.reTranslationHint()
        );
        if (completed.completed()) {
            autoAdvanceEnabled = false;
            autoAdvanceProgress = 0.0d;
        }
        refreshPlanningBaseline();
        return completed;
    }

    private ExecutionSession pause(ExecutionSession current, InteractionStep step, PauseReason pauseReason) {
		ExecutionSession paused = rebuildSession(
            current,
            current.nextStepIndex(),
            current.completedSteps(),
				Sets.immutable.of(step),
				step,
            pauseReason,
            current.reTranslationHint()
        );
        if (flowContext == null) {
            clearPausedAutoRetryFingerprint();
        } else {
            pausedAutoRetryFingerprint = observedFlowStateFingerprint(flowContext);
            pausedAutoRetryFingerprintInitialized = true;
        }
        return paused;
    }

    private ExecutionSession clearPause(ExecutionSession current) {
        clearPausedAutoRetryFingerprint();
        return rebuildSession(
            current,
            current.nextStepIndex(),
            current.completedSteps(),
            Sets.immutable.empty(),
            null,
            null,
            current.reTranslationHint()
        );
    }

    private boolean requestReTranslationIfPlanningStateChanged() {
        if (session == null || session.completed() || session.reTranslationHint() != null) {
            return false;
        }
        if (flowContext != null && isFlowManagedStep(session.currentStep())) {
            return false;
        }
        if (!planningStateDrifted()) {
            return false;
        }
        session = requestReTranslation(session, planningDriftHint());
        return true;
    }

    private static boolean isFlowManagedStep(@Nullable InteractionStep step) {
        return step instanceof InteractionStep.OpenInteractionStep
            || step instanceof InteractionStep.PrepareInputsStep
            || step instanceof InteractionStep.ExecuteRecipeStep
            || step instanceof InteractionStep.CollectOutputsStep;
    }

    private boolean planningStateDrifted() {
        long fingerprint = currentPlanningFingerprint();
        if (!planningBaselineFingerprintInitialized) {
            planningBaselineFingerprint = fingerprint;
            planningBaselineFingerprintInitialized = true;
            return false;
        }
        return planningBaselineFingerprint != fingerprint;
    }

    private void refreshPlanningBaseline() {
        planningBaselineFingerprint = currentPlanningFingerprint();
        planningBaselineFingerprintInitialized = true;
    }

    private SlotFlow.Tracking buildTracking(@Nullable ExecutionSession sourceSession) {
        SlotFlow.Tracking tracking = SlotFlow.Tracking.empty();
        if (sourceSession != null && flow != null && flowContext != null) {
            tracking = flow.track(flowContext, sourceSession);
        }
        return customizeTracking(sourceSession, tracking);
    }

    private boolean hasVisibleAutoTrace(@Nullable ExecutionSession sourceSession) {
        var trace = buildTracking(sourceSession).trace();
        return trace != null && trace.available();
    }

    private long currentPlanningFingerprint() {
        if (flowContext != null) {
            return planningSnapshot(flowContext.layout(), flowContext.access()).fingerprint();
        }
        return interactionApi.planningSnapshot().fingerprint();
    }

    private ReTranslationHint planningDriftHint() {
        return new ReTranslationHint(Component.translatable(planningDriftSummaryKey));
    }

    private ExecutionSession rebuildSession(
        ExecutionSession current,
        int nextStepIndex,
        ImmutableSet<InteractionStep> completedSteps,
        ImmutableSet<InteractionStep> pausedSteps,
        @Nullable InteractionStep runningStep,
        @Nullable PauseReason pauseReason,
        @Nullable ReTranslationHint reTranslationHint
    ) {
        return new ExecutionSession(
            current.plan(),
            current.flowRoot(),
            nextStepIndex,
            completedSteps,
            pausedSteps,
            runningStep,
            pauseReason,
            reTranslationHint,
            current.flowViewModel(),
            current.baselinePlanningSnapshot(),
            current.observedStockPolicy()
        );
    }

    protected static ItemStack normalize(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(Math.max(1, copy.getCount()));
        return copy;
    }

    protected static boolean sameItem(@Nullable ItemStack stack, @Nullable ItemStack template) {
        if (stack == null || template == null) {
            return false;
        }
        return !stack.isEmpty() && !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
    }

    protected static ItemStack normalizedTemplate(@Nullable ItemStack template) {
        if (template == null) {
            return ItemStack.EMPTY;
        }
        return ExecutionOverlayContentHelper.normalizeStack(template);
    }

    protected static long countMatchingStacks(
        SlotFlow.Context context,
        SlotLayout.Role role,
        ItemStack template
    ) {
        if (template == null || template.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (SlotLayout.Reference slot : orderedSlotReferences(context, role)) {
            ItemStack stack = context.access().stack(slot);
            if (!stack.isEmpty() && sameItem(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static PauseReason pauseReason(PauseReasonKey key, String summaryKey, @Nullable String detailKey) {
        return new PauseReason(key, guidance(summaryKey, detailKey));
    }

    private static ExecutionGuidance guidance(String summaryKey, @Nullable String detailKey) {
        return new ExecutionGuidance(
            Component.translatable(summaryKey),
            detailKey == null ? null : Component.translatable(detailKey),
            Lists.immutable.empty(),
            Sets.immutable.empty()
        );
    }

    private static PauseReason interactionClosedPause(String summaryKey, String detailKey) {
        return pauseReason(PauseReasons.awaitingPlayerAction, summaryKey, detailKey);
    }

    private static PauseReason processorPause(String summaryKey, String detailKey) {
        return pauseReason(PauseReasons.noAccessibleProcessor, summaryKey, detailKey);
    }

    private static final class StandardSlotFlow implements SlotFlow {
        private static final String interactionClosedSummaryKey = "cirrus.ui.execution.slot.manual.closed";
        private static final String interactionClosedDetailKey = "cirrus.ui.execution.slot.pause.open.closed.detail";
        private static final String manualPrepareSummaryKey = "cirrus.ui.execution.slot.manual.prepare";
        private static final String manualPrepareDetailKey = "cirrus.ui.execution.slot.pause.prepare.manual.detail";
        private static final String manualExecuteSummaryKey = "cirrus.ui.execution.slot.manual.execute";
        private static final String manualExecuteDetailKey = "cirrus.ui.execution.slot.pause.execute.manual.detail";
        private static final String manualCollectSummaryKey = "cirrus.ui.execution.slot.manual.collect";
        private static final String manualCollectDetailKey = "cirrus.ui.execution.slot.pause.collect.manual.detail";
        private static final String missingInputSummaryKey = "cirrus.ui.execution.slot.validation.missing_input";
        private static final String missingInputDetailKey = "cirrus.ui.execution.slot.pause.validation.missing_input.detail";
        private static final String missingOutputSummaryKey = "cirrus.ui.execution.slot.validation.missing_output";
        private static final String missingOutputDetailKey = "cirrus.ui.execution.slot.pause.validation.missing_output.detail";

        @Override
        public Validation validate(Context context) {
            SlotLayout.Index inputSlots = context.layout().slots(SlotLayout.Role.input);
            if (inputSlots.keys().isEmpty()) {
                return Validation.invalid(processorPause(missingInputSummaryKey, missingInputDetailKey));
            }
            SlotLayout.Index outputSlots = context.layout().slots(SlotLayout.Role.output);
            if (outputSlots.keys().isEmpty()) {
                return Validation.invalid(processorPause(missingOutputSummaryKey, missingOutputDetailKey));
            }
            context.access().state();
            return Validation.ok();
        }

        @Override
        public StepResult open(Context context, InteractionStep.OpenInteractionStep step) {
            if (context.access().isOpen()) {
                return StepResult.success();
            }
            return StepResult.paused(awaitingInteraction());
        }

        @Override
        public StepResult prepare(Context context, InteractionStep.PrepareInputsStep step) {
            if (!context.access().isOpen()) {
                return StepResult.paused(awaitingInteraction());
            }
            return StepResult.paused(manualPrepareRequired());
        }

        @Override
        public InteractionPerformResult execute(Context context, InteractionStep.ExecuteRecipeStep step) {
            if (!context.access().isOpen()) {
                return new InteractionPerformResult.ManualRequired(awaitingInteraction().guidance());
            }
            return new InteractionPerformResult.ManualRequired(manualExecuteRequired().guidance());
        }

        @Override
        public StepResult collect(Context context, InteractionStep.CollectOutputsStep step) {
            if (!context.access().isOpen()) {
                return StepResult.paused(awaitingInteraction());
            }
            return StepResult.paused(manualCollectRequired());
        }

        @Override
        public PauseReason pauseReason(Context context, Failure failure) {
            return awaitingInteraction();
        }

        @Override
        public Tracking track(Context context, ExecutionSession session) {
            return Tracking.empty();
        }

        private static PauseReason awaitingInteraction() {
            return interactionClosedPause(interactionClosedSummaryKey, interactionClosedDetailKey);
        }

        private static PauseReason manualPrepareRequired() {
            return SlotExecutionDriver.pauseReason(PauseReasons.awaitingPlayerAction, manualPrepareSummaryKey, manualPrepareDetailKey);
        }

        private static PauseReason manualExecuteRequired() {
            return SlotExecutionDriver.pauseReason(PauseReasons.awaitingPlayerAction, manualExecuteSummaryKey, manualExecuteDetailKey);
        }

        private static PauseReason manualCollectRequired() {
            return SlotExecutionDriver.pauseReason(PauseReasons.awaitingPlayerAction, manualCollectSummaryKey, manualCollectDetailKey);
        }
    }

    private static final class AutoSlotFlow implements SlotFlow {
        private static final String interactionClosedSummaryKey = "cirrus.ui.execution.slot.pause.open.closed.summary";
        private static final String interactionClosedDetailKey = "cirrus.ui.execution.slot.pause.open.closed.detail";
        private static final String prepareClosedSummaryKey = "cirrus.ui.execution.slot.pause.prepare.closed.summary";
        private static final String prepareClosedDetailKey = "cirrus.ui.execution.slot.pause.prepare.closed.detail";
        private static final String prepareNoInteractionSummaryKey = "cirrus.ui.execution.slot.pause.prepare.no_interaction.summary";
        private static final String prepareNoInteractionDetailKey = "cirrus.ui.execution.slot.pause.prepare.no_interaction.detail";
        private static final String prepareNoResolverSummaryKey = "cirrus.ui.execution.slot.pause.prepare.no_resolver.summary";
        private static final String prepareNoResolverDetailKey = "cirrus.ui.execution.slot.pause.prepare.no_resolver.detail";
        private static final String prepareNoPlacementsSummaryKey = "cirrus.ui.execution.slot.pause.prepare.no_placements.summary";
        private static final String prepareNoPlacementsDetailKey = "cirrus.ui.execution.slot.pause.prepare.no_placements.detail";
        private static final String prepareClickFailedSummaryKey = "cirrus.ui.execution.slot.pause.prepare.click_failed.summary";
        private static final String prepareClickFailedDetailKey = "cirrus.ui.execution.slot.pause.prepare.click_failed.detail";
        private static final String executeClosedSummaryKey = "cirrus.ui.execution.slot.pause.execute.closed.summary";
        private static final String executeClosedDetailKey = "cirrus.ui.execution.slot.pause.execute.closed.detail";
        private static final String executeNoInteractionSummaryKey = "cirrus.ui.execution.slot.pause.execute.no_interaction.summary";
        private static final String executeNoInteractionDetailKey = "cirrus.ui.execution.slot.pause.execute.no_interaction.detail";
        private static final String executeNoResolverSummaryKey = "cirrus.ui.execution.slot.pause.execute.no_resolver.summary";
        private static final String executeNoResolverDetailKey = "cirrus.ui.execution.slot.pause.execute.no_resolver.detail";
        private static final String executeNoPlacementsSummaryKey = "cirrus.ui.execution.slot.pause.execute.no_placements.summary";
        private static final String executeNoPlacementsDetailKey = "cirrus.ui.execution.slot.pause.execute.no_placements.detail";
        private static final String executeMissingOutputSummaryKey = "cirrus.ui.execution.slot.pause.execute.missing_output.summary";
        private static final String executeMissingOutputDetailKey = "cirrus.ui.execution.slot.pause.execute.missing_output.detail";
        private static final String executeClickFailedSummaryKey = "cirrus.ui.execution.slot.pause.execute.click_failed.summary";
        private static final String executeClickFailedDetailKey = "cirrus.ui.execution.slot.pause.execute.click_failed.detail";
        private static final String collectClosedSummaryKey = "cirrus.ui.execution.slot.pause.collect.closed.summary";
        private static final String collectClosedDetailKey = "cirrus.ui.execution.slot.pause.collect.closed.detail";
        private static final String collectNoInteractionSummaryKey = "cirrus.ui.execution.slot.pause.collect.no_interaction.summary";
        private static final String collectNoInteractionDetailKey = "cirrus.ui.execution.slot.pause.collect.no_interaction.detail";
        private static final String collectClickFailedSummaryKey = "cirrus.ui.execution.slot.pause.collect.click_failed.summary";
        private static final String collectClickFailedDetailKey = "cirrus.ui.execution.slot.pause.collect.click_failed.detail";
        private static final String missingInputSummaryKey = "cirrus.ui.execution.slot.validation.missing_input";
        private static final String missingInputDetailKey = "cirrus.ui.execution.slot.pause.validation.missing_input.detail";
        private static final String missingOutputSummaryKey = "cirrus.ui.execution.slot.validation.missing_output";
        private static final String missingOutputDetailKey = "cirrus.ui.execution.slot.pause.validation.missing_output.detail";

        @Override
        public Validation validate(Context context) {
            SlotLayout.Index inputSlots = context.layout().slots(SlotLayout.Role.input);
            if (inputSlots.keys().isEmpty()) {
                return Validation.invalid(processorPause(missingInputSummaryKey, missingInputDetailKey));
            }
            SlotLayout.Index outputSlots = context.layout().slots(SlotLayout.Role.output);
            if (outputSlots.keys().isEmpty()) {
                return Validation.invalid(processorPause(missingOutputSummaryKey, missingOutputDetailKey));
            }
            context.access().state();
            return Validation.ok();
        }

        @Override
        public StepResult open(Context context, InteractionStep.OpenInteractionStep step) {
            if (context.access().isOpen()) {
                return StepResult.success();
            }
            SlotAccess.Interaction interaction = context.interaction();
            if (interaction != null) {
                interaction.open();
                if (context.access().isOpen()) {
                    return StepResult.success();
                }
            }
            return StepResult.paused(awaitingInteraction());
        }

        @Override
        public StepResult prepare(Context context, InteractionStep.PrepareInputsStep step) {
            if (!context.access().isOpen()) {
                return StepResult.paused(interactionClosedPause(prepareClosedSummaryKey, prepareClosedDetailKey));
            }
            SlotAccess.Interaction interaction = context.interaction();
            SlotFlow.PlacementResolver resolver = context.placementResolver();
            if (interaction == null) {
                return StepResult.paused(SlotExecutionDriver.pauseReason(PauseReasons.awaitingPlayerAction, prepareNoInteractionSummaryKey, prepareNoInteractionDetailKey));
            }
            if (resolver == null) {
                return StepResult.paused(processorPause(prepareNoResolverSummaryKey, prepareNoResolverDetailKey));
            }
            ImmutableList<SlotFlow.Placement> placements = resolver.resolve(context, step.recipe(), step.inputs());
            if (placements.isEmpty()) {
                return StepResult.paused(processorPause(prepareNoPlacementsSummaryKey, prepareNoPlacementsDetailKey));
            }
            if (!clearConflictingInputSlots(context, interaction, placements)) {
                return StepResult.paused(processorPause(prepareClickFailedSummaryKey, prepareClickFailedDetailKey));
            }
            if (!placePlacements(context, interaction, placements)) {
                return StepResult.paused(processorPause(prepareClickFailedSummaryKey, prepareClickFailedDetailKey));
            }
            return StepResult.success();
        }

        @Override
        public InteractionPerformResult execute(Context context, InteractionStep.ExecuteRecipeStep step) {
            if (!context.access().isOpen()) {
                return new InteractionPerformResult.ManualRequired(guidance(executeClosedSummaryKey, executeClosedDetailKey));
            }
            SlotAccess.Interaction interaction = context.interaction();
            SlotFlow.PlacementResolver resolver = context.placementResolver();
            if (interaction == null) {
                return new InteractionPerformResult.ManualRequired(guidance(executeNoInteractionSummaryKey, executeNoInteractionDetailKey));
            }
            if (resolver == null) {
                return new InteractionPerformResult.Failed(guidance(executeNoResolverSummaryKey, executeNoResolverDetailKey));
            }
            ImmutableList<SlotFlow.Placement> placements = resolver.resolve(context, step.recipe(), step.inputs());
            if (placements.isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(executeNoPlacementsSummaryKey, executeNoPlacementsDetailKey));
            }
            ImmutableList<SlotLayout.Reference> outputSlots = orderedSlotReferences(context, SlotLayout.Role.output);
            if (outputSlots.isEmpty()) {
                return new InteractionPerformResult.Failed(guidance(executeMissingOutputSummaryKey, executeMissingOutputDetailKey));
            }
            if (!clearConflictingInputSlots(context, interaction, placements)) {
                return new InteractionPerformResult.Failed(guidance(executeClickFailedSummaryKey, executeClickFailedDetailKey));
            }
            if (!placePlacements(context, interaction, placements)) {
                return new InteractionPerformResult.Failed(guidance(executeClickFailedSummaryKey, executeClickFailedDetailKey));
            }
            SlotLayout.Reference outputSlot = findFirstOutputSlot(context, outputSlots);
            if (outputSlot == null) {
                return new InteractionPerformResult.Failed(guidance(executeMissingOutputSummaryKey, executeMissingOutputDetailKey));
            }
            if (!interaction.click(new SlotAccess.Click(outputSlot, ClickType.QUICK_MOVE, 0))) {
                return new InteractionPerformResult.Failed(guidance(executeClickFailedSummaryKey, executeClickFailedDetailKey));
            }
            return new InteractionPerformResult.Performed();
        }

        @Override
        public StepResult collect(Context context, InteractionStep.CollectOutputsStep step) {
            if (!context.access().isOpen()) {
                return StepResult.paused(interactionClosedPause(collectClosedSummaryKey, collectClosedDetailKey));
            }
            SlotAccess.Interaction interaction = context.interaction();
            if (interaction == null) {
                return StepResult.paused(SlotExecutionDriver.pauseReason(PauseReasons.awaitingPlayerAction, collectNoInteractionSummaryKey, collectNoInteractionDetailKey));
            }
            ImmutableList<SlotLayout.Reference> outputSlots = orderedSlotReferences(context, SlotLayout.Role.output);
            for (SlotLayout.Reference outputSlot : outputSlots) {
                int safety = 0;
                while (true) {
                    ItemStack before = context.access().stack(outputSlot);
                    if (before.isEmpty()) {
                        break;
                    }
                    if (safety++ > 256) {
                        return StepResult.paused(processorPause(collectClickFailedSummaryKey, collectClickFailedDetailKey));
                    }
                    if (!interaction.click(new SlotAccess.Click(outputSlot, ClickType.QUICK_MOVE, 0))) {
                        return StepResult.paused(processorPause(collectClickFailedSummaryKey, collectClickFailedDetailKey));
                    }
                    ItemStack after = context.access().stack(outputSlot);
                    if (after.isEmpty()) {
                        break;
                    }
                    if (!sameItem(before, after) || after.getCount() >= before.getCount()) {
                        return StepResult.paused(processorPause(collectClickFailedSummaryKey, collectClickFailedDetailKey));
                    }
                }
            }
            return StepResult.success();
        }

        @Override
        public PauseReason pauseReason(Context context, Failure failure) {
            return switch (failure.kind()) {
                case validation -> processorPause(missingInputSummaryKey, missingInputDetailKey);
                case openBlocked -> awaitingInteraction();
                case prepareBlocked -> processorPause(prepareClickFailedSummaryKey, prepareClickFailedDetailKey);
                case collectBlocked -> processorPause(collectClickFailedSummaryKey, collectClickFailedDetailKey);
                case executeFailed -> processorPause(executeClickFailedSummaryKey, executeClickFailedDetailKey);
            };
        }

        @Override
        public Tracking track(Context context, ExecutionSession session) {
            return Tracking.empty();
        }

        private static PauseReason awaitingInteraction() {
            return interactionClosedPause(interactionClosedSummaryKey, interactionClosedDetailKey);
        }

        private static boolean clearConflictingInputSlots(
            Context context,
            SlotAccess.Interaction interaction,
            ImmutableList<SlotFlow.Placement> placements
        ) {
            ImmutableList<SlotLayout.Reference> inputSlots = orderedSlotReferences(context, SlotLayout.Role.input);
            ImmutableList<SlotLayout.Reference> inventorySlots = orderedSlotReferences(context, SlotLayout.Role.inventory);
            LinkedHashMap<SlotLayout.Reference, SlotFlow.Placement> targetPlacements = new LinkedHashMap<>();
            for (SlotFlow.Placement placement : placements) {
                targetPlacements.put(placement.slot(), placement);
            }
            for (SlotLayout.Reference slot : inputSlots) {
                ItemStack stack = context.access().stack(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                SlotFlow.Placement placement = targetPlacements.get(slot);
                if (placement != null) {
                    ItemStack template = resolvePlacementTemplate(context, inventorySlots, placement);
                    if (!template.isEmpty() && sameItem(stack, template)) {
                        continue;
                    }
                }
                if (!interaction.click(new SlotAccess.Click(slot, ClickType.QUICK_MOVE, 0))) {
                    return false;
                }
                ItemStack after = context.access().stack(slot);
                if (!after.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private static boolean placePlacements(
            Context context,
            SlotAccess.Interaction interaction,
            ImmutableList<SlotFlow.Placement> placements
        ) {
            if (placements.isEmpty()) {
                return false;
            }
            ImmutableList<SlotLayout.Reference> inventorySlots = orderedSlotReferences(context, SlotLayout.Role.inventory);
            for (SlotFlow.Placement placement : placements) {
                if (!placePlacement(context, interaction, inventorySlots, placement)) {
                    return false;
                }
            }
            return returnCarriedToInventory(context, interaction, inventorySlots, null);
        }

        private static boolean placePlacement(
            Context context,
            SlotAccess.Interaction interaction,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            SlotFlow.Placement placement
        ) {
			ItemStack template = resolvePlacementTemplate(context, inventorySlots, placement);
            long targetAmount = Math.max(0L, placement.amount());
            if (template.isEmpty() || targetAmount == 0L) {
                return false;
            }
            if (targetAmount > template.getMaxStackSize()) {
                return false;
            }
            SlotLayout.Reference targetSlot = placement.slot();
            ItemStack slotStack = context.access().stack(targetSlot);
            if (!slotStack.isEmpty() && !sameItem(slotStack, template)) {
                return false;
            }
            if (!slotStack.isEmpty() && slotStack.getCount() >= targetAmount) {
                return true;
            }
            int safety = 0;
            SlotLayout.Reference lastSource = null;
            while (slotStack.getCount() < targetAmount) {
                if (safety++ > 256) {
                    return false;
                }
                ItemStack carried = interaction.carried();
                if (carried.isEmpty() || !sameItem(carried, template)) {
                    SlotLayout.Reference source = findInventorySlot(context, inventorySlots, placement, template);
                    if (source == null) {
                        return false;
                    }
                    lastSource = source;
                    if (!interaction.click(new SlotAccess.Click(source, ClickType.PICKUP, 0))) {
                        return false;
                    }
                    carried = interaction.carried();
                    if (carried.isEmpty() || !sameItem(carried, template)) {
                        return cleanupCarried(context, interaction, inventorySlots, lastSource);
                    }
                }
                int before = slotStack.getCount();
                if (!interaction.click(new SlotAccess.Click(targetSlot, ClickType.PICKUP, 1))) {
                    return cleanupCarried(context, interaction, inventorySlots, lastSource);
                }
                slotStack = context.access().stack(targetSlot);
                if (slotStack.isEmpty() || !sameItem(slotStack, template) || slotStack.getCount() <= before) {
                    return cleanupCarried(context, interaction, inventorySlots, lastSource);
                }
            }
            return returnCarriedToInventory(context, interaction, inventorySlots, lastSource);
        }

        private static @Nullable SlotLayout.Reference findInventorySlot(
            Context context,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            SlotFlow.Placement placement,
            ItemStack template
        ) {
            if (template.isEmpty()) {
                for (SlotLayout.Reference slot : inventorySlots) {
                    ItemStack stack = context.access().stack(slot);
                    if (!stack.isEmpty() && placement.matches(stack)) {
                        return slot;
                    }
                }
                return null;
            }
            for (SlotLayout.Reference slot : inventorySlots) {
                ItemStack stack = context.access().stack(slot);
                if (!stack.isEmpty() && sameItem(stack, template)) {
                    return slot;
                }
            }
            for (SlotLayout.Reference slot : inventorySlots) {
                ItemStack stack = context.access().stack(slot);
                if (!stack.isEmpty() && placement.matches(stack)) {
                    return slot;
                }
            }
            return null;
        }

        private static ItemStack resolvePlacementTemplate(
            Context context,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            SlotFlow.Placement placement
        ) {
            ItemStack selected = normalizedTemplate(placement.template());
            ItemStack current = context.access().stack(placement.slot());
            if (!current.isEmpty() && placement.matches(current)) {
                ItemStack currentTemplate = normalizedTemplate(current);
                if (!currentTemplate.isEmpty()
                    && availableExactAmount(context, inventorySlots, placement.slot(), currentTemplate) >= placement.amount()) {
                    return currentTemplate;
                }
            }
            return selected;
        }

        private static long availableExactAmount(
            Context context,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            SlotLayout.Reference targetSlot,
            ItemStack template
        ) {
            if (template.isEmpty()) {
                return 0L;
            }
            long total = 0L;
            ItemStack current = context.access().stack(targetSlot);
            if (!current.isEmpty() && sameItem(current, template)) {
                total += current.getCount();
            }
            for (SlotLayout.Reference slot : inventorySlots) {
                ItemStack stack = context.access().stack(slot);
                if (!stack.isEmpty() && sameItem(stack, template)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private static @Nullable SlotLayout.Reference findFirstOutputSlot(
            Context context,
            ImmutableList<SlotLayout.Reference> outputSlots
        ) {
            for (SlotLayout.Reference slot : outputSlots) {
                ItemStack stack = context.access().stack(slot);
                if (!stack.isEmpty()) {
                    return slot;
                }
            }
            return null;
        }

        private static boolean cleanupCarried(
            Context context,
            SlotAccess.Interaction interaction,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            @Nullable SlotLayout.Reference preferred
        ) {
            returnCarriedToInventory(context, interaction, inventorySlots, preferred);
            return false;
        }

        private static boolean returnCarriedToInventory(
            Context context,
            SlotAccess.Interaction interaction,
            ImmutableList<SlotLayout.Reference> inventorySlots,
            @Nullable SlotLayout.Reference preferred
        ) {
            ItemStack carried = interaction.carried();
            if (carried.isEmpty()) {
                return true;
            }
            if (preferred != null && canMerge(context, preferred, carried)) {
                interaction.click(new SlotAccess.Click(preferred, ClickType.PICKUP, 0));
                if (interaction.carried().isEmpty()) {
                    return true;
                }
            }
            for (SlotLayout.Reference slot : inventorySlots) {
                if (!canMerge(context, slot, carried)) {
                    continue;
                }
                interaction.click(new SlotAccess.Click(slot, ClickType.PICKUP, 0));
                if (interaction.carried().isEmpty()) {
                    return true;
                }
            }
            return interaction.carried().isEmpty();
        }

        private static boolean canMerge(Context context, SlotLayout.Reference slot, ItemStack carried) {
            if (carried.isEmpty()) {
                return false;
            }
            ItemStack stack = context.access().stack(slot);
            if (stack.isEmpty()) {
                return true;
            }
            if (!sameItem(stack, carried)) {
                return false;
            }
            return stack.getCount() < stack.getMaxStackSize();
        }

    }

    private static final class SlotExecutionDriverSupport {

        private static ItemStack stackFromOutputBinding(PlanAction.OutputBinding output) {
            if (output.resource() == null) {
                return ItemStack.EMPTY;
            }
            if (!(output.resource() instanceof ContentStack<?, ?> contentStack) || !contentStack.isItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = contentStack.<Item, ItemStack>cast().value().copy();
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(Math.max(1, (int) output.amount()));
            return stack;
        }
    }

    protected static ExecutionPlanningSnapshot planningSnapshot(SlotLayout layout, SlotAccess access) {
        if (ContentApiHolder.unavailable()) {
            return ExecutionPlanningSnapshot.empty();
        }
        Stock stock = Stock.empty();
        int fingerprint = 1;
        fingerprint = appendPlanningRole(layout, access, SlotLayout.Role.inventory, stock, fingerprint);
        fingerprint = appendPlanningRole(layout, access, SlotLayout.Role.input, stock, fingerprint);
        fingerprint = appendPlanningRole(layout, access, SlotLayout.Role.output, stock, fingerprint);
        return new ExecutionPlanningSnapshot(stock, Integer.toUnsignedLong(fingerprint));
    }

    private static int appendPlanningRole(
        SlotLayout layout,
        SlotAccess access,
        SlotLayout.Role role,
        Stock stock,
        int fingerprint
    ) {
        SlotLayout.Index index = layout.slots(role);
        if (index.keys().isEmpty()) {
            return 31 * fingerprint;
        }
        ArrayList<SlotLayout.Key> orderedKeys = new ArrayList<>(index.keys());
        orderedKeys.sort(index.order());
        int out = fingerprint;
        for (SlotLayout.Key key : orderedKeys) {
            SlotLayout.Reference ref = index.get(key);
            ItemStack stack = ref == null ? ItemStack.EMPTY : normalize(access.stack(ref));
            out = appendStackFingerprint(out, stack);
            if (!stack.isEmpty()) {
                stock.add(ContentStack.fromStack(stack));
            }
        }
        return out;
    }

    private record InteractionPlanningView(
        InteractionApi delegate,
        ExecutionPlanningSnapshot planningSnapshot
    ) implements InteractionPlanningApi {

        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return delegate.probe(recipe);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return delegate.guide(recipe);
        }

        @Override
        public int maxExecuteBatchSize(InteractionContext context) {
            return delegate.maxExecuteBatchSize(context);
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            return delegate.perform(context);
        }

        @Override
        public ExecutionPlanningSnapshot planningSnapshot() {
            return planningSnapshot;
        }
    }

	private record SimpleInteractionApi(
			Namespace interactionId, boolean performerAvailable,
			ExecutionGuidance guidance, ExecutionPlanningSnapshot planningSnapshot
	) implements InteractionPlanningApi {

		private SimpleInteractionApi(
				Namespace interactionId,
				boolean performerAvailable,
				ExecutionGuidance guidance
		) {
			this(interactionId, performerAvailable, guidance, ExecutionPlanningSnapshot.empty());
		}

		@Override
		public InteractionProbeResult probe(RecipeEntry<?> recipe) {
			return new InteractionProbeResult.Supported(
					interactionId,
					performerAvailable,
					false
			);
		}

		@Override
		public InteractionGuideResult guide(RecipeEntry<?> recipe) {
			return new InteractionGuideResult(guidance);
		}

		@Override
		public InteractionPerformResult perform(InteractionContext context) {
			return performerAvailable
					? new InteractionPerformResult.Performed()
					: new InteractionPerformResult.ManualRequired(guidance);
		}

        @Override
        public ExecutionPlanningSnapshot planningSnapshot() {
            return planningSnapshot;
        }
	}

    private static final class ContentApiHolder {
        private static boolean unavailable() {
            try {
                dev.vfyjxf.cirrus.api.content.ContentApi.instance();
                return false;
            } catch (IllegalStateException ignored) {
                return true;
            }
        }
    }
}


