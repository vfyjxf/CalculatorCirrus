package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record AutomatedLine(
    String lineId,
    ComponentStackKey output,
    double throughputPerMinute,
    double humanAttentionSecondsPerPull,
    long bufferedStock,
    boolean continuous
) {

    public AutomatedLine {
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(output, "output");
        if (lineId.isEmpty()) {
            throw new IllegalArgumentException("lineId must not be empty");
        }
        if (!Double.isFinite(throughputPerMinute) || throughputPerMinute <= 0.0) {
            throw new IllegalArgumentException("throughputPerMinute must be > 0, got " + throughputPerMinute);
        }
        if (!Double.isFinite(humanAttentionSecondsPerPull) || humanAttentionSecondsPerPull < 0.0) {
            throw new IllegalArgumentException("humanAttentionSecondsPerPull must be >= 0");
        }
        if (bufferedStock < 0L) {
            throw new IllegalArgumentException("bufferedStock must be >= 0");
        }
    }
}
