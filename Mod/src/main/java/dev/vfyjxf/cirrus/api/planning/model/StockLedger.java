package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record StockLedger(Map<ResourceKey, Long> entries) {

    public static final StockLedger empty = new StockLedger(Map.of());

    public StockLedger {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            entries = Map.of();
        } else {
            TreeMap<ResourceKey, Long> sorted = new TreeMap<>();
            for (var entry : entries.entrySet()) {
                ResourceKey key = Objects.requireNonNull(entry.getKey(), "stock key");
                Long value = Objects.requireNonNull(entry.getValue(), "stock value");
                if (value < 0L) {
                    throw new IllegalArgumentException("stock amount must be >= 0 for " + key + ", got " + value);
                }
                if (value > 0L) {
                    sorted.put(key, value);
                }
            }
            entries = sorted.isEmpty() ? Map.of() : Collections.unmodifiableMap(sorted);
        }
    }

    public long amountOf(ResourceKey key) {
        return entries.getOrDefault(key, 0L);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
