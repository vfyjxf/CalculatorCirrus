package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record OverrideStockFeedback(ComponentStackKey resource, long newAvailableAmount) implements HumanFeedback {

    public OverrideStockFeedback {
        Objects.requireNonNull(resource, "resource");
        if (newAvailableAmount < 0L) {
            throw new IllegalArgumentException("newAvailableAmount must be >= 0, got " + newAvailableAmount);
        }
    }
}
