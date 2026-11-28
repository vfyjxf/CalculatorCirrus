package dev.vfyjxf.cirrus.api.planning.model;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record TargetDemand(ComponentStackKey resource, long amount) {

    public TargetDemand {
        Objects.requireNonNull(resource, "resource");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be > 0, got " + amount);
        }
    }
}
