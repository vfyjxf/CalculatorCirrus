package dev.vfyjxf.cirrus.planning.telemetry;

import dev.vfyjxf.cirrus.api.planning.telemetry.ExecutionFailureCategory;
import dev.vfyjxf.cirrus.api.planning.telemetry.PlanningTelemetryEvent;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Bridges Cirrus {@link ExecutionSession} and {@link ExecutionTelemetrySnapshot} into
 * {@link PlanningTelemetryEvent.CraftingExecutionEvent}s with attributed {@link ExecutionFailureCategory}.
 */
public final class ExecutionTelemetryBridge {

    private static final Namespace unknownSystem = CirrusNamespaces.ofMod("unknown");

    private ExecutionTelemetryBridge() {}

    public static PlanningTelemetryEvent.CraftingExecutionEvent fromSnapshot(
        ExecutionSession session,
        ExecutionTelemetrySnapshot snapshot,
        @Nullable String recipeSemanticKey,
        @Nullable Namespace systemId,
        @Nullable String conditionSignature,
        @Nullable String adapterVersion,
        @Nullable String worldFingerprint,
        @Nullable String packFingerprint
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(snapshot, "snapshot");
        boolean succeeded = snapshot.completed() && !snapshot.paused();
        ExecutionFailureCategory cat = succeeded ? ExecutionFailureCategory.other : classifyPauseReason(session.pauseReason());
        double secPerBatch = snapshot.ratePerSecond() > 1e-6 ? (1.0 / snapshot.ratePerSecond()) : 0.0;
        return new PlanningTelemetryEvent.CraftingExecutionEvent(
            "exec:" + UUID.randomUUID(),
            System.currentTimeMillis(),
            worldFingerprint == null ? "local" : worldFingerprint,
            packFingerprint == null ? "pack" : packFingerprint,
            recipeSemanticKey == null ? "unknown" : recipeSemanticKey,
            systemId == null ? unknownSystem : systemId,
            conditionSignature == null ? "default" : conditionSignature,
            adapterVersion == null ? "cirrus" : adapterVersion,
            Math.max(0, snapshot.completedSteps()),
            secPerBatch,
            succeeded,
            cat
        );
    }

    public static ExecutionFailureCategory classifyPauseReason(PauseReason reason) {
        if (reason == null || reason.key() == null || reason.key().id() == null) {
            return ExecutionFailureCategory.other;
        }
        String id = reason.key().id().toString().toLowerCase(Locale.ROOT);
        if (id.contains("cancel") || id.contains("user") || id.contains("abort")) {
            return ExecutionFailureCategory.userCancel;
        }
        if (id.contains("stock") || id.contains("missing_item") || id.contains("shortage")) {
            return ExecutionFailureCategory.outOfStock;
        }
        if (id.contains("busy") || id.contains("occupied")) {
            return ExecutionFailureCategory.machineBusy;
        }
        if (id.contains("bind") || id.contains("slot") || id.contains("mismatch")) {
            return ExecutionFailureCategory.wrongBinding;
        }
        if (id.contains("condition") || id.contains("require") || id.contains("constraint")) {
            return ExecutionFailureCategory.hiddenRequirement;
        }
        return ExecutionFailureCategory.other;
    }
}
