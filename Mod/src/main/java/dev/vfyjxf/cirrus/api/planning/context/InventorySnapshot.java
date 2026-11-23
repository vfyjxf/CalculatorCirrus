package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record InventorySnapshot(StockLedger available, StockLedger reserved) {

    public static final InventorySnapshot EMPTY = new InventorySnapshot(StockLedger.EMPTY, StockLedger.EMPTY);

    public InventorySnapshot {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(reserved, "reserved");
    }

    public long usableAmount(ResourceKey key) {
        return Math.max(0L, available.amountOf(key) - reserved.amountOf(key));
    }

    public Map<ResourceKey, Long> allUsable() {
        if (available.isEmpty()) {
            return Map.of();
        }
        TreeMap<ResourceKey, Long> out = new TreeMap<>();
        for (var e : available.entries().entrySet()) {
            long usable = Math.max(0L, e.getValue() - reserved.amountOf(e.getKey()));
            if (usable > 0L) {
                out.put(e.getKey(), usable);
            }
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }
}
