package dev.vfyjxf.cirrus.ui.workbench.layout;

import java.util.List;
import java.util.Objects;

public record PlanDagLayoutResult(
    List<DagNodeBox> nodes,
    List<DagEdgeRoute> edges,
    List<SwimlaneBand> swimlanes,
    float contentMinX,
    float contentMinY,
    float contentMaxX,
    float contentMaxY
) {

    public static final PlanDagLayoutResult empty = new PlanDagLayoutResult(
        List.of(),
        List.of(),
        List.of(),
        0.0f,
        0.0f,
        0.0f,
        0.0f
    );

    public PlanDagLayoutResult {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(swimlanes, "swimlanes");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        swimlanes = List.copyOf(swimlanes);
    }

    public float contentWidth() {
        return contentMaxX - contentMinX;
    }

    public float contentHeight() {
        return contentMaxY - contentMinY;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
