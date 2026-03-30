package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    Component statusLabel,
    @Nullable Component footerSummary,
    @Nullable Component footerDetail,
    @Nullable ItemStack footerIcon,
    boolean footerWarning,
    ImmutableList<Component> footerSteps,
    @Nullable String focusNodeId,
    int focusVisibleIndex,
    ExecutionOverlayNode root,
    ImmutableList<ExecutionOverlayNode> visibleNodes
) {

    private static final Namespace workbenchFallbackValueTypeId = Namespace.of("cirrus", "workbench_fallback_amount");
    private static final Namespace workbenchFallbackLogTypeId = Namespace.of("cirrus", "workbench_fallback_log");
    private static final Namespace workbenchFallbackPlankTypeId = Namespace.of("cirrus", "workbench_fallback_plank");
    private static final int alwaysExpandedDepth = 1;
    private static final int previewExpandedDepth = 2;
    private static final int previewExpandedChildrenLimit = 8;

    public ExecutionOverlayViewModel {
        Checks.checkNotNull(title, "title");
        Checks.checkNotNull(status, "status");
        Checks.checkArgument(completedStepCount >= 0, "completedStepCount must be non-negative");
        Checks.checkArgument(totalStepCount >= 0, "totalStepCount must be non-negative");
        Checks.checkNotNull(statusLabel, "statusLabel");
        if (footerIcon != null) {
            footerIcon = footerIcon.copy();
        }
        Checks.checkNotNull(footerSteps, "footerSteps");
        Checks.checkArgument(focusVisibleIndex >= -1, "focusVisibleIndex must be -1 or greater");
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
        int focusVisibleIndex = focusVisibleIndex(visibleNodes.toImmutable());
        String focusNodeId = focusVisibleIndex >= 0 ? visibleNodes.get(focusVisibleIndex).nodeId() : null;
        var footerGuidance = footerGuidance(session, focusStep);
        var footerIcon = footerIcon(session, focusStep);
        int completedStepCount = Math.max(0, Math.min(session.plan().steps().size(), session.nextStepIndex()));
        return new ExecutionOverlayViewModel(
            session.flowViewModel().root().label(),
            overallStatus(session, focusStep),
            completedStepCount,
            session.plan().steps().size(),
            sideHint(focusStep),
            statusLabel(overallStatus(session, focusStep)),
            footerGuidance == null ? null : footerGuidance.summary(),
            footerGuidance == null ? null : footerGuidance.detail(),
            footerIcon,
            footerWarning(session),
            footerGuidance == null ? Lists.immutable.empty() : footerGuidance.steps(),
            focusNodeId,
            focusVisibleIndex,
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
        boolean expanded = shouldExpandNode(depth, activeBranch, immutableChildren);
        return new ExecutionOverlayNode(
            source.nodeId(),
            source.label(),
            subtitle(source, laneSegments, session, focusStep, depth, activeBranch),
            iconFromNode(source, focusStep),
            nodeStatus(source, laneSegments, immutableChildren, session, activeBranch),
            nodeKind(source),
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
        int globalStepIndex = globalStepIndex(session, step);
        return new ExecutionOverlayLaneSegment(
            step.getClass().getSimpleName() + ":" + index,
            stepLabel(step),
            stepStatus(step, session, focusStep, globalStepIndex),
            globalStepIndex >= 0 && globalStepIndex == session.nextStepIndex()
        );
    }

    private static ExecutionOverlayNodeKind nodeKind(ExecutionFlowNode source) {
        return source.steps().anySatisfy(step -> step instanceof InteractionStep.ExecuteRecipeStep)
            && (source.steps().size() > 1 || source.children().notEmpty())
            ? ExecutionOverlayNodeKind.RECIPE
            : ExecutionOverlayNodeKind.OPERATIONAL;
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

    private static boolean shouldExpandNode(int depth, boolean activeBranch, ImmutableList<ExecutionOverlayNode> children) {
        if (children.isEmpty()) {
            return activeBranch;
        }
        if (depth <= alwaysExpandedDepth) {
            return true;
        }
        if (activeBranch) {
            return true;
        }
        return depth <= previewExpandedDepth && children.size() <= previewExpandedChildrenLimit;
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
        if (focusStep != null || session.currentStep() != null || session.nextStepIndex() > 0) {
            return ExecutionFlowStatus.running;
        }
        return ExecutionFlowStatus.pending;
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
        @Nullable InteractionStep focusStep,
        int globalStepIndex
    ) {
        if (globalStepIndex >= 0) {
            if (globalStepIndex < session.nextStepIndex()) {
                return ExecutionFlowStatus.completed;
            }
            if (globalStepIndex == session.nextStepIndex()) {
                return session.paused() ? ExecutionFlowStatus.paused : ExecutionFlowStatus.running;
            }
            return ExecutionFlowStatus.pending;
        }
        if (session.pausedSteps().contains(step)) {
            return ExecutionFlowStatus.paused;
        }
        if (session.completedSteps().contains(step)) {
            return ExecutionFlowStatus.completed;
        }
        if (session.runningStep() != null && session.runningStep() == step) {
            return ExecutionFlowStatus.running;
        }
        if (!session.paused() && !session.completed() && focusStep != null && focusStep == step) {
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

    private static Component statusLabel(ExecutionFlowStatus status) {
        return Component.translatable("cirrus.ui.execution.overlay.status." + statusText(status));
    }

    private static String statusText(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Pending ignored -> "pending";
            case ExecutionFlowStatus.Running ignored -> "running";
            case ExecutionFlowStatus.Paused ignored -> "paused";
            case ExecutionFlowStatus.Completed ignored -> "completed";
            case ExecutionFlowStatus.Failed ignored -> "failed";
        };
    }

    private static @Nullable Component subtitle(
        ExecutionFlowNode source,
        ImmutableList<ExecutionOverlayLaneSegment> laneSegments,
        ExecutionSession session,
        @Nullable InteractionStep focusStep,
        int depth,
        boolean activeBranch
    ) {
        if (session.paused() && containsFocusStep(source, focusStep) && session.pauseReason() != null && session.pauseReason().guidance() != null) {
            return session.pauseReason().guidance().summary();
        }
        if (focusStep != null && source.steps().contains(focusStep) && depth > 0) {
            return sideHint(focusStep);
        }
        if (depth <= 1 && source.guidance() != null) {
            return source.guidance().summary();
        }
        if (activeBranch && laneSegments.notEmpty()) {
            return Component.translatable(
                "cirrus.ui.execution.overlay.node.progress",
                laneSegments.count(segment -> segment.status() == ExecutionFlowStatus.completed),
                laneSegments.size()
            );
        }
        return null;
    }

    private static @Nullable Component sideHint(@Nullable InteractionStep step) {
        if (step == null) {
            return null;
        }
        return switch (step.requiredSide()) {
            case ExecutionSideRequirement.EnvironmentAgnostic ignored ->
                Component.translatable("cirrus.ui.execution.overlay.side.any");
            case ExecutionSideRequirement.Client ignored ->
                Component.translatable("cirrus.ui.execution.overlay.side.client");
            case ExecutionSideRequirement.Server ignored ->
                Component.translatable("cirrus.ui.execution.overlay.side.server");
            case ExecutionSideRequirement.Player ignored ->
                Component.translatable("cirrus.ui.execution.overlay.side.player");
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
            case InteractionStep.EnsureAccessibleStep ignored -> Component.translatable("cirrus.ui.execution.step.ensure_access");
            case InteractionStep.WithdrawResourceStep ignored -> Component.translatable("cirrus.ui.execution.step.withdraw");
            case InteractionStep.ReserveCatalystStep ignored -> Component.translatable("cirrus.ui.execution.step.reserve");
            case InteractionStep.EnsureCapacityStep ignored -> Component.translatable("cirrus.ui.execution.step.ensure_capacity");
            case InteractionStep.OpenInteractionStep ignored -> Component.translatable("cirrus.ui.execution.step.open");
            case InteractionStep.PrepareInputsStep ignored -> Component.translatable("cirrus.ui.execution.step.prepare");
            case InteractionStep.ExecuteRecipeStep ignored -> Component.translatable("cirrus.ui.execution.step.execute");
            case InteractionStep.CollectOutputsStep ignored -> Component.translatable("cirrus.ui.execution.step.collect");
            case InteractionStep.StoreOutputsStep ignored -> Component.translatable("cirrus.ui.execution.step.store");
            case InteractionStep.ManualCheckpointStep ignored -> Component.translatable("cirrus.ui.execution.step.manual");
        };
    }

    //endregion

    //region helpers

    private static @Nullable ItemStack iconFromNode(ExecutionFlowNode source, @Nullable InteractionStep focusStep) {
        if (focusStep != null && source.steps().contains(focusStep)) {
            ItemStack focusIcon = iconFromStep(focusStep);
            if (focusIcon != null) {
                return focusIcon;
            }
        }
        for (InteractionStep step : source.steps()) {
            ItemStack icon = iconFromStep(step);
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    private static @Nullable ItemStack footerIcon(ExecutionSession session, @Nullable InteractionStep focusStep) {
        if (session.pauseReason() != null && session.pauseReason().guidance() != null && focusStep != null) {
            return iconFromStep(focusStep);
        }
        if (focusStep != null) {
            return iconFromStep(focusStep);
        }
        return null;
    }

    private static boolean footerWarning(ExecutionSession session) {
        if (session.pauseReason() == null) {
            return false;
        }
        var key = session.pauseReason().key();
        return key.equals(PauseReasons.noAccessibleProvider)
            || key.equals(PauseReasons.noAccessibleReceiver)
            || key.equals(PauseReasons.inventoryCapacityBlocked);
    }

    private static @Nullable ItemStack iconFromStep(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> switch (ensureAccessibleStep.target()) {
                case InteractionStep.AccessTarget.Resource resourceTarget -> iconFromResource(resourceTarget.resource());
                case InteractionStep.AccessTarget.Input ignored -> null;
            };
            case InteractionStep.WithdrawResourceStep withdrawResourceStep -> iconFromResource(withdrawResourceStep.resource());
            case InteractionStep.EnsureCapacityStep ensureCapacityStep -> iconFromResource(ensureCapacityStep.resource());
            case InteractionStep.StoreOutputsStep storeOutputsStep -> iconFromResource(storeOutputsStep.resource());
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> firstOutputIcon(executeRecipeStep.expectedOutputs());
            case InteractionStep.CollectOutputsStep collectOutputsStep -> firstOutputIcon(collectOutputsStep.expectedOutputs());
            case InteractionStep.PrepareInputsStep prepareInputsStep -> firstInputIcon(prepareInputsStep.inputs());
            default -> null;
        };
    }

    private static @Nullable ItemStack firstOutputIcon(ImmutableList<dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.OutputBinding> outputs) {
        for (var output : outputs) {
            ItemStack icon = iconFromResource(output.resource());
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    private static @Nullable ItemStack firstInputIcon(ImmutableList<dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction.InputBinding> inputs) {
        for (var input : inputs) {
            var variants = input.input().enumerate();
            for (var variant : variants) {
                if (variant instanceof ContentStack<?, ?> stack && stack.isItem()) {
                    return toItemStack(stack);
                }
            }
        }
        return null;
    }

    private static @Nullable ItemStack iconFromResource(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack && stack.isItem()) {
            return toItemStack(stack);
        }
        if (resource instanceof ContentValue<?> value && value.type().typeId().equals(workbenchFallbackLogTypeId)) {
            return new ItemStack(Items.OAK_LOG);
        }
        if (resource instanceof ContentValue<?> value && value.type().typeId().equals(workbenchFallbackPlankTypeId)) {
            return new ItemStack(Items.OAK_PLANKS);
        }
        if (resource instanceof ContentValue<?> value && value.type().typeId().equals(workbenchFallbackValueTypeId)) {
            if (value.value() == 1L) {
                return new ItemStack(Items.OAK_LOG);
            }
            if (value.value() == 2L) {
                return new ItemStack(Items.OAK_PLANKS);
            }
        }
        return null;
    }

    private static @Nullable ItemStack toItemStack(ContentStack<?, ?> stack) {
        ItemStack value = stack.<Item, ItemStack>cast().value();
        if (value.isEmpty()) {
            return null;
        }
        ItemStack copy = value.copy();
        copy.setCount((int) Math.max(1, Math.min(Integer.MAX_VALUE, stack.amount())));
        return copy;
    }

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
        if (focusStep == null) {
            return false;
        }
        for (InteractionStep step : node.steps()) {
            if (step == focusStep) {
                return true;
            }
        }
        return false;
    }

    private static int globalStepIndex(ExecutionSession session, InteractionStep step) {
        for (int i = 0; i < session.plan().steps().size(); i++) {
            if (session.plan().steps().get(i) == step) {
                return i;
            }
        }
        return session.plan().steps().indexOf(step);
    }

    private static int focusVisibleIndex(ImmutableList<ExecutionOverlayNode> visibleNodes) {
        int bestIndex = -1;
        int bestDepth = -1;
        for (int i = 0; i < visibleNodes.size(); i++) {
            ExecutionOverlayNode node = visibleNodes.get(i);
            boolean hasCurrentLane = node.laneSegments().anySatisfy(ExecutionOverlayLaneSegment::current);
            if (!hasCurrentLane) {
                continue;
            }
            if (node.depth() >= bestDepth) {
                bestDepth = node.depth();
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    //endregion
}
