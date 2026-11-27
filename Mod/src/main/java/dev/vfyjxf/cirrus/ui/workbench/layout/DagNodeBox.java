package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record DagNodeBox(
    String stepId,
    PlanStep step,
    int layer,
    int laneIndex,
    @Nullable String swimlaneId,
    float x,
    float y,
    float width,
    float height,
    boolean expandedLoop,
    List<DagNodeBox> innerLoopNodes,
    DiffStatus diffStatus
) {

    public enum DiffStatus {
        kept, added, removed
    }

    public DagNodeBox {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(diffStatus, "diffStatus");
        innerLoopNodes = innerLoopNodes == null ? List.of() : List.copyOf(innerLoopNodes);
    }

    public float maxX() {
        return x + width;
    }

    public float maxY() {
        return y + height;
    }

    public float centerX() {
        return x + width * 0.5f;
    }

    public float centerY() {
        return y + height * 0.5f;
    }
}
