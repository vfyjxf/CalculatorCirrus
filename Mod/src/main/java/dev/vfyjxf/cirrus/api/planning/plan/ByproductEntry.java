package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

public record ByproductEntry(ComponentStackKey resource, long amount, String producedByStepId) {

    public ByproductEntry {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(producedByStepId, "producedByStepId");
        if (amount <= 0L) {
            throw new IllegalArgumentException("byproduct amount must be > 0, got " + amount);
        }
    }
}
