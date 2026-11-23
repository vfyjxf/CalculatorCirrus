package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record OutputSlot(ResourceKey resource, long amountPerBatch) {

    public OutputSlot {
        Objects.requireNonNull(resource, "resource");
        if (amountPerBatch <= 0L) {
            throw new IllegalArgumentException("amountPerBatch must be > 0, got " + amountPerBatch);
        }
    }
}
