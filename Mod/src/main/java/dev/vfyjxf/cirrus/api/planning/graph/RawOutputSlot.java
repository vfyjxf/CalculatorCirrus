package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record RawOutputSlot(ComponentStackKey resource, long amount) {

    public RawOutputSlot {
        Objects.requireNonNull(resource, "resource");
    }
}
