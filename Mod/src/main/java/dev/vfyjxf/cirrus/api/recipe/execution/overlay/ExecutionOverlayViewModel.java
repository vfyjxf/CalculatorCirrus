package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

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
    @Nullable RecipeContent footerContent,
    boolean footerWarning,
    ImmutableList<Component> footerSteps,
    @Nullable String focusNodeId,
    int focusVisibleIndex,
    ExecutionOverlayNode root,
    ImmutableList<ExecutionOverlayNode> visibleNodes
) {

    private static final int alwaysExpandedDepth = 1;
    private static final int previewExpandedDepth = 2;
    private static final int previewExpandedChildrenLimit = 8;
    private static final String overlayStatusKeyPrefix = "cirrus.ui.execution.overlay.status.";
    private static final String overlaySideKeyPrefix = "cirrus.ui.execution.overlay.side.";
    private static final String overlayStepKeyPrefix = "cirrus.ui.execution.step.";
    /**
     * Stable translation aliases for existing built-in step kinds.
     * <p>
     * New step types fall back to class-name based conversion in {@link #stepKey(InteractionStep)}.
     */
    private static final Map<Class<? extends InteractionStep>, String> knownStepKeys = Map.of(
        InteractionStep.EnsureAccessibleStep.class,
        "ensure_access",
        InteractionStep.WithdrawResourceStep.class,
        "withdraw",
        InteractionStep.ReserveCatalystStep.class,
        "reserve",
        InteractionStep.EnsureCapacityStep.class,
        "ensure_capacity",
        InteractionStep.OpenInteractionStep.class,
        "open",
        InteractionStep.PrepareInputsStep.class,
        "prepare",
        InteractionStep.ExecuteRecipeStep.class,
        "execute",
        InteractionStep.CollectOutputsStep.class,
        "collect",
        InteractionStep.StoreOutputsStep.class,
        "store",
        InteractionStep.ManualCheckpointStep.class,
        "manual"
    );

    public ExecutionOverlayViewModel {
        Checks.checkArgument(completedStepCount >= 0, "completedStepCount must be non-negative");
        Checks.checkArgument(totalStepCount >= 0, "totalStepCount must be non-negative");
        if (footerContent != null) {
            footerContent = footerContent.copy();
        }
        Checks.checkArgument(focusVisibleIndex >= -1, "focusVisibleIndex must be -1 or greater");
    }

    //region projection

    /**
     * Builds a complete overlay projection from a runtime session snapshot.
     */
    public static ExecutionOverlayViewModel derive(ExecutionSession session) {
        var focusStep = focusStep(session);
        var projectedRoot = project(session.flowViewModel().root(), session, focusStep, 0);
        MutableList<ExecutionOverlayNode> visibleNodes = Lists.mutable.empty();
        flattenVisible(projectedRoot, visibleNodes);
        int focusVisibleIndex = focusVisibleIndex(visibleNodes.toImmutable());
        String focusNodeId = focusVisibleIndex >= 0 ? visibleNodes.get(focusVisibleIndex).nodeId() : null;
        var footerGuidance = footerGuidance(session, focusStep);
        var footerContent = footerContent(session, focusStep);
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
            footerContent,
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

        var laneSegments = source.steps()
                .collectWithIndex((step, index) -> projectSegment(step, index, session, focusStep));
        var immutableChildren = childNodes.toImmutable();
        boolean activeBranch = containsFocusStep(source, focusStep)
                || immutableChildren.anySatisfy(ExecutionOverlayNode::activeBranch);
        boolean expanded = shouldExpandNode(depth, activeBranch, immutableChildren);
        return new ExecutionOverlayNode(
            source.nodeId(),
            source.label(),
            subtitle(source, laneSegments, session, focusStep, depth, activeBranch),
            contentFromNode(source, session, focusStep, depth),
            recipePreviewFromNode(source.steps()),
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
            stepKey(step),
            stepLabel(step),
            stepDescription(step),
            recipePreviewFromStep(step),
            stepStatus(step, session, focusStep, globalStepIndex),
            globalStepIndex >= 0 && globalStepIndex == session.nextStepIndex()
        );
    }

    private static ExecutionOverlayNodeKind nodeKind(ExecutionFlowNode source) {
        return source.steps().anySatisfy(step -> step instanceof InteractionStep.ExecuteRecipeStep)
                && (source.steps().size() > 1 || source.children().notEmpty())
                        ? ExecutionOverlayNodeKind.recipe
                        : ExecutionOverlayNodeKind.operational;
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

    private static boolean shouldExpandNode(
        int depth,
        boolean activeBranch,
        ImmutableList<ExecutionOverlayNode> children
    ) {
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
        return statusKey(status);
    }

    private static Component statusLabel(ExecutionFlowStatus status) {
        return translateByKey(overlayStatusKeyPrefix, statusKey(status));
    }

    private static String statusKey(ExecutionFlowStatus status) {
        return status.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static @Nullable Component subtitle(
        ExecutionFlowNode source,
        ImmutableList<ExecutionOverlayLaneSegment> laneSegments,
        ExecutionSession session,
        @Nullable InteractionStep focusStep,
        int depth,
        boolean activeBranch
    ) {
        if (session.paused()
                && containsFocusStep(source, focusStep)
                && session.pauseReason() != null
                && session.pauseReason().guidance() != null) {
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
        return translateByKey(overlaySideKeyPrefix, sideKey(step.requiredSide()));
    }

    private static @Nullable ExecutionGuidance footerGuidance(
        ExecutionSession session,
        @Nullable InteractionStep focusStep
    ) {
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
        return translateByKey(overlayStepKeyPrefix, stepKey(step));
    }

    private static Component stepDescription(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> switch (ensureAccessibleStep.target()) {
                case InteractionStep.AccessTarget.Resource ignored -> Component.translatable(
                    overlayStepKeyPrefix + "ensure_access.desc",
                    ensureAccessibleStep.requestedAmount()
                );
                case InteractionStep.AccessTarget.Input ignored -> Component.translatable(
                    overlayStepKeyPrefix + "ensure_access.input.desc",
                    ensureAccessibleStep.requestedAmount()
                );
            };
            case InteractionStep.WithdrawResourceStep withdrawResourceStep ->
                Component.translatable(overlayStepKeyPrefix + "withdraw.desc", withdrawResourceStep.requestedAmount());
            case InteractionStep.ReserveCatalystStep reserveCatalystStep ->
                Component.translatable(overlayStepKeyPrefix + "reserve.desc", reserveCatalystStep.amount());
            case InteractionStep.EnsureCapacityStep ensureCapacityStep ->
                Component.translatable(overlayStepKeyPrefix + "ensure_capacity.desc", ensureCapacityStep.amount());
            case InteractionStep.OpenInteractionStep openInteractionStep ->
                Component.translatable(overlayStepKeyPrefix + "open.desc", openInteractionStep.times());
            case InteractionStep.PrepareInputsStep prepareInputsStep ->
                Component.translatable(overlayStepKeyPrefix + "prepare.desc", prepareInputsStep.inputs().size());
            case InteractionStep.ExecuteRecipeStep executeRecipeStep ->
                Component.translatable(overlayStepKeyPrefix + "execute.desc", executeRecipeStep.times());
            case InteractionStep.CollectOutputsStep collectOutputsStep -> Component
                    .translatable(overlayStepKeyPrefix + "collect.desc", collectOutputsStep.expectedOutputs().size());
            case InteractionStep.StoreOutputsStep storeOutputsStep ->
                Component.translatable(overlayStepKeyPrefix + "store.desc", storeOutputsStep.amount());
            case InteractionStep.ManualCheckpointStep manualCheckpointStep -> manualCheckpointStep.guidance().summary();
        };
    }

    //endregion

    //region helpers

    private static Component translateByKey(String keyPrefix, String keySuffix) {
        return Component.translatable(keyPrefix + keySuffix);
    }

    private static String sideKey(ExecutionSideRequirement sideRequirement) {
        if (sideRequirement == ExecutionSideRequirement.environmentAgnostic) {
            return "any";
        }
        return sideRequirement.name();
    }

    private static String stepKey(InteractionStep step) {
        String known = knownStepKeys.get(step.getClass());
        if (known != null) {
            return known;
        }
        // Keep unknown step kinds usable without touching overlay code: FooBarStep -> foo_bar
        String simpleName = step.getClass().getSimpleName();
        String normalized = simpleName.endsWith("Step")
                ? simpleName.substring(0, simpleName.length() - "Step".length())
                : simpleName;
        return camelToSnake(normalized);
    }

    private static String camelToSnake(String value) {
        if (value.isEmpty()) {
            return value;
        }
        StringBuilder buffer = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    buffer.append('_');
                }
                buffer.append(Character.toLowerCase(c));
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    private static @Nullable RecipeContent contentFromNode(
        ExecutionFlowNode source,
        ExecutionSession session,
        @Nullable InteractionStep focusStep,
        int depth
    ) {
        if (depth == 0) {
            RecipeContent targetContent = firstOutputContent(
                executeOutputsForRecipe(session, session.plan().targetRecipe())
            );
            if (targetContent != null && targetContent.notEmpty()) {
                return targetContent;
            }
        }
        RecipeContent recipeContent = recipeContentFromSteps(source.steps());
        if (recipeContent != null && recipeContent.notEmpty()) {
            return recipeContent;
        }
        if (focusStep != null && source.steps().contains(focusStep)) {
            RecipeContent focusContent = contentFromStep(focusStep);
            if (focusContent != null && focusContent.notEmpty()) {
                return focusContent;
            }
        }
        for (InteractionStep step : source.steps()) {
            RecipeContent content = contentFromStep(step);
            if (content != null && content.notEmpty()) {
                return content;
            }
        }
        return null;
    }

    private static @Nullable RecipeContent recipeContentFromSteps(ImmutableList<InteractionStep> steps) {
        for (InteractionStep step : steps) {
            RecipeContent content = recipeOutputFromStep(step);
            if (content != null && content.notEmpty()) {
                return content;
            }
        }
        return null;
    }

    private static @Nullable ExecutionOverlayRecipePreview recipePreviewFromNode(ImmutableList<InteractionStep> steps) {
        for (InteractionStep step : steps) {
            if (!(step instanceof InteractionStep.ExecuteRecipeStep)) {
                continue;
            }
            ExecutionOverlayRecipePreview preview = recipePreviewFromStep(step);
            if (preview != null) {
                return preview;
            }
        }
        for (InteractionStep step : steps) {
            ExecutionOverlayRecipePreview preview = recipePreviewFromStep(step);
            if (preview != null && preview.hasBoundMaterials()) {
                return preview;
            }
        }
        for (InteractionStep step : steps) {
            ExecutionOverlayRecipePreview preview = recipePreviewFromStep(step);
            if (preview != null) {
                return preview;
            }
        }
        return null;
    }

    private static @Nullable ExecutionOverlayRecipePreview recipePreviewFromStep(InteractionStep step) {
        return switch (step) {
            case InteractionStep.OpenInteractionStep openInteractionStep ->
                ExecutionOverlayRecipePreview.recipeOnly(openInteractionStep.recipe(), openInteractionStep.times());
            case InteractionStep.PrepareInputsStep prepareInputsStep -> new ExecutionOverlayRecipePreview(
                prepareInputsStep.recipe(),
                1,
                prepareInputsStep.inputs(),
                Lists.immutable.empty()
            );
            case InteractionStep.ExecuteRecipeStep executeRecipeStep -> new ExecutionOverlayRecipePreview(
                executeRecipeStep.recipe(),
                executeRecipeStep.times(),
                executeRecipeStep.inputs(),
                executeRecipeStep.expectedOutputs()
            );
            case InteractionStep.CollectOutputsStep collectOutputsStep -> new ExecutionOverlayRecipePreview(
                collectOutputsStep.recipe(),
                1,
                Lists.immutable.empty(),
                collectOutputsStep.expectedOutputs()
            );
            default -> null;
        };
    }

    private static @Nullable RecipeContent recipeOutputFromStep(InteractionStep step) {
        return switch (step) {
            case InteractionStep.ExecuteRecipeStep executeRecipeStep ->
                firstOutputContent(executeRecipeStep.expectedOutputs());
            case InteractionStep.CollectOutputsStep collectOutputsStep ->
                firstOutputContent(collectOutputsStep.expectedOutputs());
            default -> null;
        };
    }

    private static @Nullable RecipeContent footerContent(
        ExecutionSession session,
        @Nullable InteractionStep focusStep
    ) {
        if (session.pauseReason() != null && session.pauseReason().guidance() != null && focusStep != null) {
            return contentFromStep(focusStep);
        }
        if (focusStep != null) {
            return contentFromStep(focusStep);
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

    private static @Nullable RecipeContent contentFromStep(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ensureAccessibleStep -> switch (ensureAccessibleStep.target()) {
                case InteractionStep.AccessTarget.Resource resourceTarget ->
                    contentFromResource(resourceTarget.resource(), ensureAccessibleStep.requestedAmount());
                case InteractionStep.AccessTarget.Input inputTarget ->
                    inputTarget.input().copyWithAmount(Math.max(1L, ensureAccessibleStep.requestedAmount()));
            };
            case InteractionStep.WithdrawResourceStep withdrawResourceStep -> switch (withdrawResourceStep.target()) {
                case InteractionStep.WithdrawTarget.Resource resourceTarget ->
                    contentFromResource(resourceTarget.resource(), withdrawResourceStep.requestedAmount());
                case InteractionStep.WithdrawTarget.Input inputTarget ->
                    inputTarget.input().copyWithAmount(Math.max(1L, withdrawResourceStep.requestedAmount()));
            };
            case InteractionStep.ReserveCatalystStep reserveCatalystStep ->
                reserveCatalystStep.input().copyWithAmount(Math.max(1L, reserveCatalystStep.amount()));
            case InteractionStep.EnsureCapacityStep ensureCapacityStep ->
                contentFromResource(ensureCapacityStep.resource(), ensureCapacityStep.amount());
            case InteractionStep.StoreOutputsStep storeOutputsStep ->
                contentFromResource(storeOutputsStep.resource(), storeOutputsStep.amount());
            case InteractionStep.ExecuteRecipeStep executeRecipeStep ->
                firstOutputContent(executeRecipeStep.expectedOutputs());
            case InteractionStep.CollectOutputsStep collectOutputsStep ->
                firstOutputContent(collectOutputsStep.expectedOutputs());
            case InteractionStep.PrepareInputsStep prepareInputsStep -> firstInputContent(prepareInputsStep.inputs());
            default -> null;
        };
    }

    private static @Nullable RecipeContent firstOutputContent(ImmutableList<OutputBinding> outputs) {
        for (var output : outputs) {
            RecipeContent content = contentFromResource(output.resource(), output.amount());
            if (content != null && content.notEmpty()) {
                return content;
            }
        }
        return null;
    }

    private static @Nullable RecipeContent firstInputContent(ImmutableList<InputBinding> inputs) {
        for (var input : inputs) {
            InputContent content = input.input();
            if (content.notEmpty()) {
                return content.copyWithAmount(Math.max(1L, input.amount()));
            }
        }
        return null;
    }

    private static ImmutableList<OutputBinding> executeOutputsForRecipe(
        ExecutionSession session,
        RecipeEntry<?> recipe
    ) {
        MutableList<OutputBinding> outputs = Lists.mutable.empty();
        for (InteractionStep step : session.plan().steps()) {
            if (!(step instanceof InteractionStep.ExecuteRecipeStep executeRecipeStep)) {
                continue;
            }
            if (!recipe.equals(executeRecipeStep.recipe())) {
                continue;
            }
            outputs.addAllIterable(executeRecipeStep.expectedOutputs());
        }
        return outputs.toImmutable();
    }

    private static @Nullable RecipeContent contentFromResource(ContentResource resource, long amount) {
        if (resource instanceof ContentStack<?, ?> stack && !stack.isEmpty()) {
            return OutputContent.fromStack(stack.copyWithAmount(Math.max(1L, amount)));
        }
        if (resource instanceof ContentValue<?> value) {
            return OutputContent.fromValue(value);
        }
        return null;
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
