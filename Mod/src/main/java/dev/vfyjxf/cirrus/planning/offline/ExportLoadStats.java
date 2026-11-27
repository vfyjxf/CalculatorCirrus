package dev.vfyjxf.cirrus.planning.offline;

import dev.vfyjxf.cirrus.planning.adapter.DropReason;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ExportLoadStats(long rawRecipes, long keptRecipes, Map<DropReason, Long> droppedByReason) {

    public ExportLoadStats {
        if (rawRecipes < 0L) {
            throw new IllegalArgumentException("rawRecipes must be >= 0");
        }
        if (keptRecipes < 0L) {
            throw new IllegalArgumentException("keptRecipes must be >= 0");
        }
        Objects.requireNonNull(droppedByReason, "droppedByReason");
        EnumMap<DropReason, Long> normalized = new EnumMap<>(DropReason.class);
        for (DropReason reason : DropReason.values()) {
            long count = droppedByReason.getOrDefault(reason, 0L);
            if (count < 0L) {
                throw new IllegalArgumentException("Drop count must be >= 0 for " + reason);
            }
            normalized.put(reason, count);
        }
        droppedByReason = Collections.unmodifiableMap(normalized);
    }

    public long countOf(DropReason reason) {
        return droppedByReason.getOrDefault(reason, 0L);
    }

    public long totalDropped() {
        long sum = 0L;
        for (long v : droppedByReason.values()) {
            sum += v;
        }
        return sum;
    }
}
