package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record OutputSlot(ComponentStackKey resource, long amountPerBatch) {

    public OutputSlot {
        Objects.requireNonNull(resource, "resource");
        if (amountPerBatch <= 0L) {
            throw new IllegalArgumentException("amountPerBatch must be > 0, got " + amountPerBatch);
        }
    }
}
