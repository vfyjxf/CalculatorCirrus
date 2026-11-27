package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record RejectStepFeedback(String planId, String stepId, boolean banSystemInstead, boolean persistToWorld)
        implements
            HumanFeedback {

    public RejectStepFeedback {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(stepId, "stepId");
    }
}
