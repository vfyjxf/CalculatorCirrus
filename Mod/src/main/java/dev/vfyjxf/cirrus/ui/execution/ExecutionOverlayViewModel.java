package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only projection consumed by the standalone execution overlay widget.
 */
public record ExecutionOverlayViewModel(
    Component title,
    ExecutionFlowStatus status,
    int completedStepCount,
    int totalStepCount,
    @Nullable Component sideHint,
    @Nullable Component footerSummary,
    @Nullable Component footerDetail,
    ImmutableList<Component> footerSteps,
    ExecutionOverlayNode root,
    ImmutableList<ExecutionOverlayNode> visibleNodes
) {

    public ExecutionOverlayViewModel {
        Checks.checkNotNull(title, "title");
        Checks.checkNotNull(status, "status");
        Checks.checkArgument(completedStepCount >= 0, "completedStepCount must be non-negative");
        Checks.checkArgument(totalStepCount >= 0, "totalStepCount must be non-negative");
        Checks.checkNotNull(footerSteps, "footerSteps");
        Checks.checkNotNull(root, "root");
        Checks.checkNotNull(visibleNodes, "visibleNodes");
    }

    //region projection

    public static ExecutionOverlayViewModel derive(ExecutionSession session) {
        Checks.checkNotNull(session, "session");

        var focusStep = focusStep(session);
        var projectedRoot = project(session.flowViewModel().root(), session, focusStep, 0);
        MutableList<ExecutionOverlayNode> visibleNodes = Lists.mutable.empty();
        flattenVisible(projectedRoot, visibleNodes);
        var footerGuidance = footerGuidance(session, focusStep);
        return new ExecutionOverlayViewModel(
            session.flowViewModel().root().label(),
            overallStatus(session, focusStep),
            session.completedSteps().size(),
            session.plan().steps().size(),
            sideHint(focusStep),
            footerGuidance == null ? null : footerGuidance.summary(),
            footerGuidance == null ? null : footerGuidance.detail(),
            footerGuidance == null ? Lists.immutable.empty() : footerGuidance.steps(),
            projectedRoot,
            visibleNodes.toImmutable()
        );
    }

    private static ExecutionOverlayNode project(
        ExecutionFlowNode source,
        ExecutionSession session,
        @Nullable InteractionStep focusStep,
        int depth
    ) {
        MutableList<ExecutionOverlayNode> childNodes = Lists.mutable.empty();
        for (ExecutionFlowNode child : source.children()) {
            childNodes.add(project(child, session, focusStep, depth + 1));
        }

        var laneSegments = source.steps().collectWithIndex((step, index) -> projectSegment(step, index, session, focusStep));
        var immutableChildren = childNodes.toImmutable();
        boolean activeBranch = containsFocusStep(source, focusStep) || immutableChildren.anySatisfy(ExecutionOverlayNode::activeBranch);
        boolean expanded = depth == 0 || activeBranch;
        return new ExecutionOverlayNode(
            source.nodeId(),
            source.label(),
            subtitle(source, laneSegments, session, focusStep),
            nodeStatus(source, laneSegments, immutableChildren, session, activeBranch),
            depth,
            activeBranch,
            expanded,
            laneSegments,
            immutableChildren
        );
    }

    private static ExecutionOverlayLaneSegment projectSegment(
        InteractionStep step,
        int index,
        ExecutionSession session,
        @Nullable InteractionStep focusStep
    ) {
        return new ExecutionOverlayLaneSegment(
            step.getClass().getSimpleName() + ":" + index,
            stepLabel(step),
            stepStatus(step, session, focusStep),
            step.equals(focusStep)
        );
    }

    private static void flattenVisible(ExecutionOverlayNode node, MutableList<ExecutionOverlayNode> out) {
        out.add(node);
        if (!node.expanded()) {
            return;
        }
        for (ExecutionOverlayNode child : node.children()) {
            flattenVisible(child, out);
        }
    }

    //endregion

    //region status derivation

    private static ExecutionFlowStatus overallStatus(ExecutionSession session, @Nullable InteractionStep focusStep) {
        if (session.paused()) {
            return ExecutionFlowStatus.paused;
        }
        if (session.completed()) {
            return ExecutionFlowStatus.completed;
        }
        if (focusStep != null) {
            return ExecutionFlowStatus.running;
        }
        return session.flowViewModel().root().status();
    }

    private static ExecutionFlowStatus nodeStatus(
        ExecutionFlowNode source,
        ImmutableList<ExecutionOverlayLaneSegment> laneSegments,
        ImmutableList<ExecutionOverlayNode> childNodes,
        ExecutionSession session,
        boolean activeBranch
    ) {
        if (laneSegments.anySatisfy(segment -> segment.status() == ExecutionFlowStatus.failed)
            || childNodes.anySatisfy(child -> child.status() == ExecutionFlowStatus.failed)) {
            return ExecutionFlowStatus.failed;
        }
        if (laneSegments.anySatisfy(segment -> segment.status() == ExecutionFlowStatus.paused)
            || childNodes.anySatisfy(child -> child.status() == ExecutionFlowStatus.paused)) {
            return ExecutionFlowStatus.paused;
        }
        if (laneSegments.anySatisfy(segment -> segment.status() == ExecutionFlowStatus.running)
            || childNodes.anySatisfy(child -> child.status() == ExecutionFlowStatus.running)
            || activeBranch && !session.paused() && !session.completed()) {
            return ExecutionFlowStatus.running;
        }
        boolean ownCompleted = laneSegments.allSatisfy(segment -> segment.status() == ExecutionFlowStatus.completed);
        boolean childrenCompleted = childNodes.allSatisfy(child -> child.status() == ExecutionFlowStatus.completed);
        if ((laneSegments.notEmpty() || childNodes.notEmpty()) && ownCompleted && childrenCompleted) {
            return ExecutionFlowStatus.completed;
        }
        return source.status();
    }

    private static ExecutionFlowStatus stepStatus(
        InteractionStep step,
        ExecutionSession session,
        @Nullable InteractionStep focusStep
    ) {
        if (session.completedSteps().contains(step)) {
            return ExecutionFlowStatus.completed;
        }
        if (session.pausedSteps().contains(step)) {
            return ExecutionFlowStatus.paused;
        }
        if (session.runningStep() != null && session.runningStep().equals(step)) {
            return ExecutionFlowStatus.running;
        }
        if (!session.paused() && !session.completed() && focusStep != null && focusStep.equals(step)) {
            return ExecutionFlowStatus.running;
        }
        return ExecutionFlowStatus.pending;
    }

    //endregion

    //region text derivation

    public String statusText() {
        return switch (status) {
            case ExecutionFlowStatus.Pending pending -> "pending";
            case ExecutionFlowStatus.Running running -> "running";
            case ExecutionFlowStatus.Paused paused -> "paused";
            case ExecutionFlowStatus.Completed completed -> "completed";
            case ExecutionFlowStatus.Failed failed -> "failed";
        };
    }

    private static @Nullable Component subtitle(
        ExecutionFlowNode source,
        ImmutableList<ExecutionOverlayLaneSegment> laneSegments,
        ExecutionSession session,
        @Nullable InteractionStep focusStep
    ) {
        if (session.paused() && containsFocusStep(source, focusStep) && session.pauseReason() != null && session.pauseReason().guidance() != null) {
            return session.pauseReason().guidance().summary();
        }
        if (source.guidance() != null) {
            return source.guidance().summary();
        }
        if (focusStep != null && source.steps().contains(focusStep)) {
            return sideHint(focusStep);
        }
        if (laneSegments.notEmpty()) {
            return Component.literal(laneSegments.count(segment -> segment.status() == ExecutionFlowStatus.completed) + "/" + laneSegments.size());
        }
        return null;
    }

    private static @Nullable Component sideHint(@Nullable InteractionStep step) {
        if (step == null) {
            return null;
        }
        return Component.literal(sideText(step.requiredSide()));
    }

    private static String sideText(ExecutionSideRequirement requirement) {
        return switch (requirement) {
            case ExecutionSideRequirement.EnvironmentAgnostic ignored -> "Any side";
            case ExecutionSideRequirement.Client ignored -> "Client";
            case ExecutionSideRequirement.Server ignored -> "Server";
            case ExecutionSideRequirement.Player ignored -> "Player action";
        };
    }

    private static @Nullable ExecutionGuidance footerGuidance(ExecutionSession session, @Nullable InteractionStep focusStep) {
        if (session.pauseReason() != null && session.pauseReason().guidance() != null) {
            return session.pauseReason().guidance();
        }
        if (focusStep instanceof InteractionStep.ManualCheckpointStep manualCheckpointStep) {
            return manualCheckpointStep.guidance();
        }
        if (focusStep != null) {
            return ExecutionGuidance.simple(stepLabel(focusStep));
        }
        return null;
    }

    private static Component stepLabel(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> Component.literal("Check access");
            case InteractionStep.WithdrawResourceStep withdrawResourceStep -> Component.literal("Withdraw resources");
            case InteractionStep.ReserveCatalystStep reserveCatalystStep -> Component.literal("Reserve catalyst");
            case InteractionStep.EnsureCapacityStep ensureCapacityStep -> Component.literal("Check capacity");
            case InteractionStep.OpenInteractionStep openInteractionStep -> Component.literal("Open interaction");
            case InteractionStep.PrepareInputsStep prepareInputsStep -> Component.literal("Prepare inputs");
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> Component.literal("Execute recipe");
            case InteractionStep.CollectOutputsStep collectOutputsStep -> Component.literal("Collect outputs");
            case InteractionStep.StoreOutputsStep storeOutputsStep -> Component.literal("Store outputs");
            case InteractionStep.ManualCheckpointStep manualCheckpointStep -> Component.literal("Manual checkpoint");
        };
    }

    //endregion

    //region helpers

    private static @Nullable InteractionStep focusStep(ExecutionSession session) {
        if (session.runningStep() != null) {
            return session.runningStep();
        }
        if (session.currentStep() != null) {
            return session.currentStep();
        }
        return null;
    }

    private static boolean containsFocusStep(ExecutionFlowNode node, @Nullable InteractionStep focusStep) {
        return focusStep != null && node.steps().contains(focusStep);
    }

    //endregion
}
