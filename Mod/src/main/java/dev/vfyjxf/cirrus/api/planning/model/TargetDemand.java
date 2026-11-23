package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Objects;

public record TargetDemand(ResourceKey resource, long amount) {

    public TargetDemand {
        Objects.requireNonNull(resource, "resource");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be > 0, got " + amount);
        }
    }
}
