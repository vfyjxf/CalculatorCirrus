package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record DagEdgeRoute(
    String fromStepId,
    String toStepId,
    EdgeKind kind,
    @Nullable ResourceKey resource,
    long amountFlow,
    long sourceTotalProduced,
    float x0,
    float y0,
    float cx0,
    float cy0,
    float cx1,
    float cy1,
    float x1,
    float y1,
    boolean criticalPath,
    DagNodeBox.DiffStatus diffStatus
) {

    public enum EdgeKind {
        material, catalyst, machine, autoLine, loopBack
    }

    public DagEdgeRoute {
        Objects.requireNonNull(fromStepId, "fromStepId");
        Objects.requireNonNull(toStepId, "toStepId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(diffStatus, "diffStatus");
    }
}
