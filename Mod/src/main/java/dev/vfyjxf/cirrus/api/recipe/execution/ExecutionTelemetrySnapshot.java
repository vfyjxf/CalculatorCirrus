package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

/**
 * UI-agnostic telemetry snapshot for execution state observation.
 */
public record ExecutionTelemetrySnapshot(
    String status,
    boolean paused,
    boolean completed,
    int completedSteps,
    int totalSteps,
    double progress,
    @Nullable NodeContext current,
    @Nullable NodeContext next,
    @Nullable NodeContext parent,
    ImmutableList<NodeContext> path,
    ImmutableList<String> warnings,
    @Nullable String errorSummary,
    @Nullable String errorDetail,
    double ratePerSecond,
    boolean autoAdvanceEnabled
) {

    public ExecutionTelemetrySnapshot {
        Checks.checkNotNull(status, "status");
        Checks.checkArgument(completedSteps >= 0, "completedSteps must be non-negative");
        Checks.checkArgument(totalSteps >= 0, "totalSteps must be non-negative");
        Checks.checkArgument(progress >= 0.0d, "progress must be non-negative");
        Checks.checkArgument(progress <= 1.0d, "progress must be <= 1.0");
        Checks.checkNotNull(path, "path");
        Checks.checkNotNull(warnings, "warnings");
        Checks.checkArgument(ratePerSecond >= 0.0d, "ratePerSecond must be non-negative");
    }

    public record NodeContext(
        String id,
        String label,
        @Nullable String detail
    ) {
        public NodeContext {
            Checks.checkNotNull(id, "id");
            Checks.checkNotNull(label, "label");
        }
    }

    public static ExecutionTelemetrySnapshot from(ExecutionSession session, double ratePerSecond, boolean autoAdvanceEnabled) {
        Checks.checkNotNull(session, "session");

        ExecutionFlowNode root = session.flowViewModel().root();
        InteractionStep currentStep = session.currentStep();
        InteractionStep nextStep = nextStep(session);

        NodePath currentPath = currentStep == null
            ? rootPath(root)
            : findPath(root, currentStep);
        NodeContext current = currentPath.path().getLast();
        NodeContext parent = currentPath.path().size() > 1 ? currentPath.path().get(currentPath.path().size() - 2) : null;
        NodeContext next = nextStep == null ? null : nodeContext(findNode(root, nextStep).orElse(null), nextStep, false);
        ImmutableList<String> warnings = warningStrings(session.pauseReason());
        String errorSummary = errorSummary(session.pauseReason());
        String errorDetail = errorDetail(session.pauseReason());

        int completedStepCount = Math.max(0, Math.min(session.plan().steps().size(), session.nextStepIndex()));
        int totalStepCount = session.plan().steps().size();
        double progress = totalStepCount <= 0 ? 0.0d : Math.min(1.0d, completedStepCount / (double) totalStepCount);

        return new ExecutionTelemetrySnapshot(
            statusText(session),
            session.paused(),
            session.completed(),
            completedStepCount,
            totalStepCount,
            progress,
            current,
            next,
            parent,
            currentPath.path(),
            warnings,
            errorSummary,
            errorDetail,
            ratePerSecond,
            autoAdvanceEnabled
        );
    }

    private static String statusText(ExecutionSession session) {
        if (session.paused()) {
            return "paused";
        }
        if (session.completed()) {
            return "completed";
        }
        if (session.currentStep() != null || session.nextStepIndex() > 0) {
            return "running";
        }
        return "pending";
    }

    private static @Nullable InteractionStep nextStep(ExecutionSession session) {
        int nextIndex = session.nextStepIndex() + 1;
        if (nextIndex < 0 || nextIndex >= session.plan().steps().size()) {
            return null;
        }
        return session.plan().steps().get(nextIndex);
    }

    private static ImmutableList<String> warningStrings(@Nullable PauseReason pauseReason) {
        if (pauseReason == null || pauseReason.guidance() == null) {
            return Lists.immutable.empty();
        }
        return Lists.immutable.ofAll(pauseReason.guidance().steps().collect(Component::getString));
    }

    private static @Nullable String errorSummary(@Nullable PauseReason pauseReason) {
        if (!isFailureLikePauseReason(pauseReason)) {
            return null;
        }
        ExecutionGuidance guidance = pauseReason.guidance();
        if (guidance != null) {
            return guidance.summary().getString();
        }
        return pauseReason.key().id().toString();
    }

    private static @Nullable String errorDetail(@Nullable PauseReason pauseReason) {
        if (!isFailureLikePauseReason(pauseReason) || pauseReason.guidance() == null || pauseReason.guidance().detail() == null) {
            return null;
        }
        return pauseReason.guidance().detail().getString();
    }

    private static boolean isFailureLikePauseReason(@Nullable PauseReason pauseReason) {
        return pauseReason != null && !pauseReason.key().equals(PauseReasons.awaitingPlayerAction);
    }

    private static NodeContext nodeContext(@Nullable ExecutionFlowNode node, @Nullable InteractionStep step, boolean pathNode) {
        if (node == null) {
            return new NodeContext("unknown", pathNode ? "Unknown" : "Unknown step", null);
        }
        String detail = pathNode ? statusText(node.status()) : step == null ? null : step.getClass().getSimpleName();
        return new NodeContext(node.nodeId(), node.label().getString(), detail);
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

    private static NodePath rootPath(ExecutionFlowNode root) {
        return new NodePath(Lists.immutable.of(nodeContext(root, null, true)));
    }

    private static NodePath findPath(ExecutionFlowNode root, InteractionStep target) {
        MutableList<NodeContext> path = Lists.mutable.empty();
        if (!appendPath(root, target, path)) {
            path.add(nodeContext(root, null, true));
        }
        return new NodePath(path.toImmutable());
    }

    private static boolean appendPath(ExecutionFlowNode node, InteractionStep target, MutableList<NodeContext> path) {
        path.add(nodeContext(node, null, true));
        if (node.steps().contains(target)) {
            return true;
        }
        for (ExecutionFlowNode child : node.children()) {
            if (appendPath(child, target, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    private static java.util.Optional<ExecutionFlowNode> findNode(ExecutionFlowNode node, InteractionStep target) {
        if (node.steps().contains(target)) {
            return java.util.Optional.of(node);
        }
        for (ExecutionFlowNode child : node.children()) {
            var found = findNode(child, target);
            if (found.isPresent()) {
                return found;
            }
        }
        return java.util.Optional.empty();
    }

    private record NodePath(ImmutableList<NodeContext> path) {
        private NodePath {
            Checks.checkNotNull(path, "path");
        }
    }
}
