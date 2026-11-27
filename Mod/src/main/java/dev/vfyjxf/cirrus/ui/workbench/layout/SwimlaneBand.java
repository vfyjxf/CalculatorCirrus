package dev.vfyjxf.cirrus.ui.workbench.layout;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record SwimlaneBand(
    String laneId,
    String titleKey,
    @Nullable String iconMachineId,
    float y,
    float height,
    int stepCount,
    double totalH,
    double totalP
) {

    public SwimlaneBand {
        Objects.requireNonNull(laneId, "laneId");
        Objects.requireNonNull(titleKey, "titleKey");
    }

    public float maxY() {
        return y + height;
    }
}
