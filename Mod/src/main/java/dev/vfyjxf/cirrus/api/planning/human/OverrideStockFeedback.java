package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record OverrideStockFeedback(
        ResourceKey resource,
        long newAvailableAmount
) implements HumanFeedback {

    public OverrideStockFeedback {
        Objects.requireNonNull(resource, "resource");
        if (newAvailableAmount < 0L) {
            throw new IllegalArgumentException("newAvailableAmount must be >= 0, got " + newAvailableAmount);
        }
    }
}
