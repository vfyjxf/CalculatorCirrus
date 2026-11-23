package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record RawOutputSlot(ResourceKey resource, long amount) {

    public RawOutputSlot {
        Objects.requireNonNull(resource, "resource");
    }
}
