package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagHost;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Adapter from the spatial DAG panel to the workbench shell: state forwards to the active planning controller, interaction intents land in pending slots.
 */
public final class ShellSpatialDagHost implements SpatialDagHost {

    /**
     * A request for the shell to open a decision card after an inline decision beacon was clicked.
     */
    public record InlineBeaconClick(String promptId, String stepId) {}

    /**
     * A request for the shell to open a context menu; {@code stepId} is {@code null} for empty canvas space.
     */
    public record ContextMenuRequest(
        @Nullable String stepId,
        double screenX,
        double screenY,
        boolean shiftDown,
        boolean canvas
    ) {}

    /**
     * Full description of one hovered edge, so the shell can highlight it and show a hint.
     */
    public record HoveredEdge(@Nullable String fromStepId, @Nullable String toStepId, @Nullable ComponentStackKey resource) {}

    /**
     * A drop request held while {@code CoPilotDnd} is not wired; the shell consumes it once wired.
     */
    public record PendingDrop(DragPayload payload, @Nullable String targetStepId) {}

    private final WorkbenchShellContext context;
    private final BooleanSupplier reducedMotion;
    private final Consumer<Component> toastSink;
    private @Nullable BiConsumer<DragPayload, String> dropSink;
    private @Nullable BiConsumer<String, String> beaconSink;

    private @Nullable InlineBeaconClick pendingBeacon;
    private @Nullable ContextMenuRequest pendingContextMenu;
    private @Nullable HoveredEdge hoveredEdge;
    private @Nullable DragPayload lastDropPayload;
    private @Nullable String lastDropTargetStepId;
    private @Nullable PendingDrop pendingDrop;

    public ShellSpatialDagHost(WorkbenchShellContext context) {
        this(context, () -> false, null, message -> context.toast(message));
    }

    public ShellSpatialDagHost(WorkbenchShellContext context, @Nullable BooleanSupplier reducedMotion) {
        this(context, reducedMotion, null, message -> context.toast(message));
    }

    public ShellSpatialDagHost(
        WorkbenchShellContext context,
        @Nullable BooleanSupplier reducedMotion,
        @Nullable BiConsumer<DragPayload, String> dropSink,
        @Nullable Consumer<Component> toastSink
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.reducedMotion = reducedMotion == null ? () -> false : reducedMotion;
        this.dropSink = dropSink;
        this.toastSink = toastSink == null ? message -> context.toast(message) : toastSink;
    }

    @Override
    public WorkbenchUiSnapshot snapshot() {
        return context.activeSnapshot();
    }

    @Override
    public CirrusPalette palette() {
        return context.palette();
    }

    @Override
    public void onSelectSteps(Set<String> stepIds) {
        if (stepIds == null) {
            return;
        }
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setSelectedSteps(stepIds);
        }
    }

    @Override
    public void onNodeMoved(String stepId, FloatPos offset) {
        if (stepId == null) {
            return;
        }
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setManualOffset(stepId, offset);
        }
    }

    @Override
    public void onClearManualOffsets() {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.clearManualOffsets();
        }
    }

    @Override
    public void onHoverStep(@Nullable String stepId) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setHoveredStep(stepId);
        }
    }

    @Override
    public void onHoverEdge(@Nullable String fromStepId, @Nullable String toStepId, @Nullable ComponentStackKey resource) {
        hoveredEdge = fromStepId == null && toStepId == null && resource == null
                ? null
                : new HoveredEdge(fromStepId, toStepId, resource);
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setHoveredResource(resource);
        }
    }

    @Override
    public void onInlineBeaconClick(String promptId, String stepId) {
        BiConsumer<String, String> sink = beaconSink;
        if (sink == null) {
            pendingBeacon = new InlineBeaconClick(promptId, stepId);
        } else {
            pendingBeacon = null;
            sink.accept(promptId, stepId);
        }
        toastSink.accept(CirrusLangs.Workbench.Copilot.tabPrompts.get());
    }

    @Override
    public void onNodeContextMenu(String stepId, double screenX, double screenY, boolean shiftDown) {
        pendingContextMenu = new ContextMenuRequest(stepId, screenX, screenY, shiftDown, false);
    }

    @Override
    public void onCanvasContextMenu(double screenX, double screenY) {
        pendingContextMenu = new ContextMenuRequest(null, screenX, screenY, false, true);
    }

    @Override
    public void onDrop(DragPayload payload, @Nullable String targetStepId) {
        lastDropPayload = payload;
        lastDropTargetStepId = targetStepId;
        BiConsumer<DragPayload, String> sink = dropSink;
        if (sink == null) {
            pendingDrop = new PendingDrop(payload, targetStepId);
            return;
        }
        pendingDrop = null;
        sink.accept(payload, targetStepId);
    }

    @Override
    public void onSetLayoutMode(DagLayoutMode mode) {
        PlannerWorkbenchController controller = context.activeController();
        if (controller != null) {
            controller.setDagLayoutMode(mode);
        }
    }

    @Override
    public boolean isReducedMotion() {
        return reducedMotion.getAsBoolean();
    }

    /* region slots awaiting WP-UI-7B wiring */

    /**
     * Wires (or unwires) the drop receiver; after unwiring, new drops land in {@link #pendingDrop()} again.
     */
    public void dropSink(@Nullable BiConsumer<DragPayload, String> sink) {
        this.dropSink = sink;
        if (sink != null) {
            pendingDrop = null;
        }
    }

    /**
     * Wires (or unwires) the inline beacon click receiver; after unwiring, new clicks land in {@link #pendingBeacon()} again.
     */
    public void beaconSink(@Nullable BiConsumer<String, String> sink) {
        this.beaconSink = sink;
        if (sink != null) {
            pendingBeacon = null;
        }
    }

    public @Nullable PendingDrop pendingDrop() {
        return pendingDrop;
    }

    public @Nullable PendingDrop consumePendingDrop() {
        PendingDrop consumed = pendingDrop;
        pendingDrop = null;
        return consumed;
    }

    public @Nullable InlineBeaconClick pendingBeacon() {
        return pendingBeacon;
    }

    public @Nullable InlineBeaconClick consumePendingBeacon() {
        InlineBeaconClick consumed = pendingBeacon;
        pendingBeacon = null;
        return consumed;
    }

    public @Nullable ContextMenuRequest pendingContextMenu() {
        return pendingContextMenu;
    }

    public @Nullable ContextMenuRequest consumePendingContextMenu() {
        ContextMenuRequest consumed = pendingContextMenu;
        pendingContextMenu = null;
        return consumed;
    }

    public @Nullable HoveredEdge hoveredEdge() {
        return hoveredEdge;
    }

    public @Nullable DragPayload lastDropPayload() {
        return lastDropPayload;
    }

    public @Nullable String lastDropTargetStepId() {
        return lastDropTargetStepId;
    }

    /* endregion */
}
