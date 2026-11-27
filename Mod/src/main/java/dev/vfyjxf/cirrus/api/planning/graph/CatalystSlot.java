package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;

public record CatalystSlot(int slotIndex, List<ResourceKey> candidates, int durabilityCostPerBatch, int maxDurability) {

    public CatalystSlot {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0, got " + slotIndex);
        }
        if (durabilityCostPerBatch < 0) {
            throw new IllegalArgumentException("durabilityCostPerBatch must be >= 0, got " + durabilityCostPerBatch);
        }
        if (maxDurability < 0) {
            throw new IllegalArgumentException("maxDurability must be >= 0, got " + maxDurability);
        }
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("CatalystSlot candidates must not be empty");
        }
    }
}
