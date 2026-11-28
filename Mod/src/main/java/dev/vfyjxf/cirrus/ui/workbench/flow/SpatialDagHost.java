package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Narrow bridge from the spatial DAG surface to the workbench shell.
 */
public interface SpatialDagHost {

    WorkbenchUiSnapshot snapshot();

    CirrusPalette palette();

    void onSelectSteps(Set<String> stepIds);

    void onNodeMoved(String stepId, FloatPos offset);

    void onClearManualOffsets();

    void onHoverStep(@Nullable String stepId);

    void onHoverEdge(@Nullable String fromStepId, @Nullable String toStepId, @Nullable ComponentStackKey resource);

    void onInlineBeaconClick(String promptId, String stepId);

    void onNodeContextMenu(String stepId, double screenX, double screenY, boolean shiftDown);

    void onCanvasContextMenu(double screenX, double screenY);

    void onDrop(DragPayload payload, @Nullable String targetStepId);

    void onSetLayoutMode(DagLayoutMode mode);

    boolean isReducedMotion();
}
